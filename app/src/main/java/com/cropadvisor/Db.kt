package com.cropadvisor

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * On-device SQLite database (app-private storage, never leaves the phone).
 *   users : one row per mobile number — salted PBKDF2 password hash, optional security question
 *   scans : every scan, linked to the user's mobile number
 */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "uzhavan.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE users(
            phone TEXT PRIMARY KEY, name TEXT NOT NULL, village TEXT NOT NULL DEFAULT '',
            pass TEXT NOT NULL, question INTEGER NOT NULL DEFAULT -1, answer TEXT NOT NULL DEFAULT '',
            created INTEGER NOT NULL, failed INTEGER NOT NULL DEFAULT 0, locked_until INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE scans(
            id TEXT PRIMARY KEY, phone TEXT NOT NULL, label TEXT NOT NULL, conf REAL, sev REAL, top3 TEXT,
            time INTEGER, img TEXT, inf INTEGER, pre INTEGER, leaf REAL)""")
        db.execSQL("CREATE INDEX scans_by_user ON scans(phone, time DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------------- users ----------------
    data class Account(val user: User, val pass: String, val question: Int, val answer: String,
                       val failed: Int, val lockedUntil: Long)

    fun account(phone: String): Account? =
        readableDatabase.rawQuery("SELECT * FROM users WHERE phone=?", arrayOf(phone)).use { c ->
            if (!c.moveToFirst()) null else Account(
                User(c.getString(c.getColumnIndexOrThrow("phone")), c.getString(c.getColumnIndexOrThrow("name")),
                    c.getString(c.getColumnIndexOrThrow("village")), c.getLong(c.getColumnIndexOrThrow("created")),
                    c.getInt(c.getColumnIndexOrThrow("question")) >= 0),
                c.getString(c.getColumnIndexOrThrow("pass")), c.getInt(c.getColumnIndexOrThrow("question")),
                c.getString(c.getColumnIndexOrThrow("answer")), c.getInt(c.getColumnIndexOrThrow("failed")),
                c.getLong(c.getColumnIndexOrThrow("locked_until")))
        }

    fun insertUser(u: User, pass: String, question: Int, answer: String) {
        writableDatabase.insertWithOnConflict("users", null, ContentValues().apply {
            put("phone", u.phone); put("name", u.name); put("village", u.village); put("pass", pass)
            put("question", question); put("answer", answer); put("created", u.created)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun update(phone: String, values: ContentValues) {
        writableDatabase.update("users", values, "phone=?", arrayOf(phone))
    }

    /** Deletes the user and all their scans; returns the scan image paths so the files can be removed. */
    fun deleteUser(phone: String): List<String> {
        val imgs = clearScans(phone)
        writableDatabase.delete("users", "phone=?", arrayOf(phone))
        return imgs
    }

    // ---------------- scans ----------------
    fun scans(phone: String, limit: Int = 200): List<Scan> =
        readableDatabase.rawQuery("SELECT * FROM scans WHERE phone=? ORDER BY time DESC LIMIT $limit", arrayOf(phone)).use { c ->
            val out = ArrayList<Scan>()
            while (c.moveToNext()) {
                val t = try { JSONArray(c.getString(c.getColumnIndexOrThrow("top3"))) } catch (e: Exception) { JSONArray() }
                out += Scan(
                    c.getString(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("label")),
                    c.getFloat(c.getColumnIndexOrThrow("conf")), c.getFloat(c.getColumnIndexOrThrow("sev")),
                    List(t.length()) { i -> t.getJSONObject(i).let { j -> j.getString("l") to j.getDouble("p").toFloat() } },
                    c.getLong(c.getColumnIndexOrThrow("time")), c.getString(c.getColumnIndexOrThrow("img")),
                    c.getLong(c.getColumnIndexOrThrow("inf")), c.getLong(c.getColumnIndexOrThrow("pre")),
                    c.getFloat(c.getColumnIndexOrThrow("leaf")))
            }
            out
        }

    fun insertScan(phone: String, s: Scan) {
        val top3 = JSONArray().apply { s.top3.forEach { put(JSONObject().put("l", it.first).put("p", it.second.toDouble())) } }
        writableDatabase.insertWithOnConflict("scans", null, ContentValues().apply {
            put("id", s.id); put("phone", phone); put("label", s.label); put("conf", s.confidence); put("sev", s.severityPct)
            put("top3", top3.toString()); put("time", s.time); put("img", s.imagePath); put("inf", s.inferenceMs)
            put("pre", s.preprocessMs); put("leaf", s.leafPct)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteScan(id: String) { writableDatabase.delete("scans", "id=?", arrayOf(id)) }

    fun clearScans(phone: String): List<String> {
        val imgs = readableDatabase.rawQuery("SELECT img FROM scans WHERE phone=?", arrayOf(phone)).use { c ->
            val l = ArrayList<String>(); while (c.moveToNext()) l += c.getString(0); l
        }
        writableDatabase.delete("scans", "phone=?", arrayOf(phone))
        return imgs
    }

    fun countUsers(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM users", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    companion object {
        private const val ITER = 20_000
        private val rng = SecureRandom()

        /** Salted PBKDF2-HMAC-SHA256, stored as  p2$<salt>$<hash>. */
        fun hashSecret(secret: String): String {
            val salt = ByteArray(16).also { rng.nextBytes(it) }
            return "p2$" + hex(salt) + "$" + hex(pbkdf2(secret, salt))
        }

        /** Also accepts the v1 format (unsalted SHA-256 of the old 4-digit PIN) so old accounts still sign in. */
        fun verify(secret: String, stored: String, phone: String): Boolean {
            if (stored.startsWith("p2$")) {
                val parts = stored.split("$")
                if (parts.size != 3) return false
                return MessageDigest.isEqual(pbkdf2(secret, unhex(parts[1])), unhex(parts[2]))
            }
            return hex(MessageDigest.getInstance("SHA-256").digest("vayal:$phone:$secret".toByteArray())) == stored
        }

        /** Answers are compared case- and space-insensitively ("Madurai " == "madurai"). */
        fun normAnswer(a: String) = a.trim().lowercase().replace(Regex("\\s+"), " ")

        private fun pbkdf2(secret: String, salt: ByteArray): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(secret.toCharArray(), salt, ITER, 256)).encoded

        private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
