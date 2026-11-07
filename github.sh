#!/usr/bin/env bash
# Laptop: uploads the project to GitHub.  Usage:  bash ~/CropAdvisor/github.sh YOUR_GITHUB_USERNAME
# Before: on github.com → "+" → New repository → name: uzhavan → Public → Create (leave it empty).
# When git asks for a password, paste your Personal Access Token (not your GitHub password).
set -e
U=${1:?give your GitHub username:  bash github.sh YOUR_USERNAME}
cd ~/CropAdvisor
mkdir -p docs
for f in eval_summary_v2.txt eval_results_v2.csv eval_summary_v3.txt eval_results_v3.csv; do
  [ -f ~/crop/$f ] && cp ~/crop/$f docs/
done
[ -d .git ] || git init -q -b main
git config user.name >/dev/null || git config user.name "$U"
git config user.email >/dev/null || git config user.email "$U@users.noreply.github.com"
git add -A
git commit -qm "Uzhavan: offline crop disease advisor (ExecuTorch, MobileNetV3 + Llama 3.2 1B)" || echo "(nothing new to commit)"
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$U/uzhavan.git"
git push -u origin main
echo "✔ https://github.com/$U/uzhavan"
