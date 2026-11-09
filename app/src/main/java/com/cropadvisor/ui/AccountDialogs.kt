package com.cropadvisor.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cropadvisor.AppState

@Composable
fun ChangePasswordDialog(state: AppState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var old by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Rounded.Lock, null, tint = Palette.GoldText) },
        title = { Text(state.t("change_pw")) },
        text = {
            Column(Modifier.shakeOn(shake).verticalScroll(rememberScrollState())) {
                AuthField(old, { old = it; error = null }, state.t("current_pw"), Icons.Rounded.Lock, password = true)
                Spacer(Modifier.height(8.dp))
                AuthField(pw, { pw = it; error = null }, state.t("password_new"), Icons.Rounded.Lock,
                    password = true, hint = state.t("pw_hint"))
                AuthField(pw2, { pw2 = it; error = null }, state.t("password_confirm"), Icons.Rounded.Lock, password = true)
                StrengthBar(pw)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Palette.Terracotta, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton({
                val problem = when {
                    pw.length < AppState.MIN_PASSWORD -> state.t("pw_short")
                    pw != pw2 -> state.t("pw_mismatch")
                    else -> state.changePassword(old, pw)
                }
                if (problem != null) { error = problem; shake++ }
                else { Toast.makeText(ctx, state.t("pw_changed"), Toast.LENGTH_SHORT).show(); onClose() }
            }) { Text(state.t("save"), color = Palette.Forest, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClose) { Text(state.t("cancel"), color = Palette.InkSoft) } },
        containerColor = Palette.Card, titleContentColor = Palette.Ink, textContentColor = Palette.Ink,
    )
}
