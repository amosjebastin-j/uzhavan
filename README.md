# Uzhavan (உழவன்) — offline crop disease & pest advisor
Bharat AI-SoC Challenge 2026–27 · PS1 · runs fully offline on an Arm Android phone (Nothing Phone 3a, Snapdragon 7s Gen 3).

A farmer photographs a leaf → the phone detects the crop and disease, estimates severity, and explains what to do
in English, Tamil or Hindi — with no internet.

| Lane | Model | Runtime |
|---|---|---|
| Vision | MobileNetV3-Large, 24 classes (tomato, potato, bell pepper, maize, rice + "other crop") | ExecuTorch + XNNPACK, Arm CPU |
| Severity | Leaf / lesion pixel segmentation (row-span leaf mask) | Kotlin, on device |
| Advisory | Llama 3.2 1B Instruct, 4-bit (8da4w), 1024-token context, grounded on `assets/kb.json` | ExecuTorch, Arm CPU |

## Results (measured on the phone, Arm CPU)
On-device field-photo test: 236 real farm photos (PlantDoc test split, never used for training), run through the app's
own pipeline (`Insights → Run field-photo test`).

| Metric | v2 model |
|---|---|
| Disease correct, 5 supported crops | **58.6 %** (75/128) |
| Crop correct | **89.0 %** |
| Photos answered with confidence ≥ 60 % | 77.5 % — of these **87.4 %** correct |
| Lab photos (PlantVillage test) | 98.9 % |
| Vision latency | **48 ms** (3-view test-time augmentation) + 4 ms preprocessing |
| Vision model load | 192 ms |
| Llama load | ~2.5 s |

The phone's 58.6 % matches the PyTorch result on Kaggle (59.4 %, 76/128) to within one photo, so ExecuTorch/XNNPACK
conversion loses no accuracy. Full per-class results: `docs/eval_summary_v2.txt`.

## Safety
- No leaf in the photo (< 5 % leaf pixels), an unsupported crop, or confidence < 60 % → **no treatment advice**;
  the farmer is asked to retake the photo or call the Kisan helpline (1800-180-1551).
- Llama may only rephrase the approved guidance in `kb.json`; it is told never to add a chemical or dose.
- If Llama ever crashes, it is switched off automatically and the app falls back to the approved guidance.

## Accounts & data
- Offline accounts in an on-device SQLite database (`Db.kt`): mobile number + password (salted PBKDF2-HMAC-SHA256),
  5 wrong tries → 30 s lock-out. Scan history is stored per user in the same database.
- Forgot password: answer the security question chosen at sign-up → set a new password, with no internet or SMS.
  If that is forgotten too, the account can be removed from the phone and created again.

## Limitations
- Real-field accuracy (≈ 59 %) is far below lab accuracy (99 %): field photos have clutter, shadows and mixed symptoms,
  and public field datasets are small (4–12 test photos per class, so per-class numbers are rough).
- Pests such as leaf miner are not in the training classes → the app answers "not sure" instead of guessing.
- Severity is a colour heuristic, not a trained segmentation model.

## Build & run
Laptop with the phone connected: `bash uzhavan.sh` (exports Llama once, pushes it, builds and installs the app).
Training: `training/train_v2.py` / `train_v3.py` (Kaggle, T4 GPU) → `training/export_vision.py` → `crop_mnv3.pte`.
Field test: `python3 training/prepare_eval.py` → Insights → Run field-photo test.

Model files (`*.pte`, `*.pth`) are not in the repo (too large); they are produced by the scripts above.
Fonts: Poppins & DM Serif Display (SIL OFL, see FONTS_LICENSE_OFL.txt).
