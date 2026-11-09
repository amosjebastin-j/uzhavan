#!/usr/bin/env bash
# Uzhavan — one command to prepare, build, install and open the app on the connected phone.
# Usage (from the CropAdvisor folder):   bash uzhavan.sh
set -u
cd "$(dirname "$0")"
ok()   { echo -e "\033[1;32m✔ $*\033[0m"; }
step() { echo -e "\n\033[1;33m▶ $*\033[0m"; }
die()  { echo -e "\033[1;31m✘ $*\033[0m"; exit 1; }

ET=~/executorch
LLAMA_DIR=~/models/Llama-3.2-1B-Instruct/original
MARK=$ET/.llama_seq1024

step "1/5  Checking project files"
[ -f gradlew ] || cp -r $ET/extension/android/gradle $ET/extension/android/gradlew . || die "gradlew missing"
chmod +x gradlew
mkdir -p app/libs app/src/main/assets
[ -f app/libs/executorch.aar ] || cp "$(find $ET/extension/android -name '*release*.aar' | head -1)" app/libs/executorch.aar || die "executorch.aar not found — run: bash $ET/scripts/build_android_library.sh"
# newest trained weights (v3, else v2) → convert to crop_mnv3.pte if the .pte is older than them
W=$(ls -t ~/crop/crop_mnv3_v3.pth ~/crop/crop_mnv3_v2.pth 2>/dev/null | head -1)
if [ -n "$W" ] && { [ "$W" -nt ~/crop/crop_mnv3.pte ] || [ ! ~/crop/.pte_ok -nt ~/crop/crop_mnv3.pte ]; }; then
    echo "   Converting $(basename "$W") → crop_mnv3.pte (ExecuTorch + XNNPACK)…"
    # shellcheck disable=SC1090
    source ~/et-env/bin/activate || die "~/et-env not found"
    [ -f ~/crop/crop_mnv3.pte ] && cp ~/crop/crop_mnv3.pte ~/crop/crop_mnv3_prev.pte
    ( cd ~/crop && python ~/CropAdvisor/training/export_vision.py ) || die "vision export failed"
    # never install a broken model: compare the .pte with PyTorch, else go back to the previous one
    if ( cd ~/crop && python ~/CropAdvisor/training/verify_pte.py "$W" ); then
        touch ~/crop/.pte_ok; ok "Vision model converted and verified"
    else
        [ -f ~/crop/crop_mnv3_prev.pte ] && mv ~/crop/crop_mnv3_prev.pte ~/crop/crop_mnv3.pte
        touch ~/crop/crop_mnv3.pte ~/crop/.pte_ok
        echo "   ⚠ New model failed the check — keeping the previous working model."
    fi
fi
cp ~/crop/crop_mnv3.pte app/src/main/assets/ || die "~/crop/crop_mnv3.pte missing"   # always the verified model
cp ~/crop/labels.json app/src/main/assets/ || die "~/crop/labels.json missing"
[ -f local.properties ] || echo "sdk.dir=$HOME/Android/Sdk" > local.properties
grep -q java.home gradle.properties || echo "org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64" >> gradle.properties
ok "Project files ready"

step "2/5  Llama model with 1024-token memory (needed for the AI advisor)"
if [ ! -f "$MARK" ]; then
    echo "   Re-exporting Llama 3.2 1B (one time only, about 5–10 minutes)…"
    # shellcheck disable=SC1090
    source ~/et-env/bin/activate || die "~/et-env not found"
    ( cd $ET && python3 -m examples.models.llama.export_llama \
        --checkpoint $LLAMA_DIR/consolidated.00.pth --params $LLAMA_DIR/params.json \
        --use_kv_cache --use_sdpa_with_kv_cache --xnnpack \
        --quantization_mode 8da4w --embedding-quantize 4,32 \
        --max_seq_length 1024 --max_context_length 1024 \
        --output_name llama3_2_1b_instruct.pte ) > /tmp/llama_export.log 2>&1 \
        || { tail -20 /tmp/llama_export.log; die "Llama export failed (full log: /tmp/llama_export.log)"; }
    touch "$MARK"
    rm -f $ET/.llama_pushed
    ok "Llama exported"
else
    ok "Already exported"
fi

step "3/5  Phone connection"
adb start-server >/dev/null 2>&1
STATE=$(adb get-state 2>/dev/null || true)
[ "$STATE" = "device" ] || die "Phone not connected. Plug in USB, unlock the phone, accept 'Allow USB debugging', then run again."
ok "Phone connected: $(adb shell getprop ro.product.model | tr -d '\r')"

step "4/5  Copying Llama to the phone"
adb shell mkdir -p /data/local/tmp/llama
if [ ! -f $ET/.llama_pushed ] || ! adb shell ls /data/local/tmp/llama/llama3_2_1b_instruct.pte >/dev/null 2>&1; then
    adb push $ET/llama3_2_1b_instruct.pte /data/local/tmp/llama/ || die "push failed"
    adb push $LLAMA_DIR/tokenizer.model /data/local/tmp/llama/ || die "push failed"
    touch $ET/.llama_pushed
fi
ok "Llama on phone: $(adb shell ls -l /data/local/tmp/llama/llama3_2_1b_instruct.pte | awk '{print $5/1e6 " MB"}')"

step "5/5  Building and installing Uzhavan (first build 5–10 min)"
./gradlew :app:installDebug --console=plain -q || die "Build failed — copy the lines under 'What went wrong' and send them"
adb shell am force-stop com.cropadvisor
adb shell am start -n com.cropadvisor/.MainActivity >/dev/null
ok "Uzhavan is open on your phone 🎉"
