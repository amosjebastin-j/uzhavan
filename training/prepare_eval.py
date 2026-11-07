# Laptop: puts real-field test photos on the phone for the in-app "Field-photo accuracy test".
# Uses the PlantDoc TEST split (real farm photos, never used for training).
# Usage (phone connected):  python3 ~/CropAdvisor/training/prepare_eval.py
# Your own photos: drop them into ~/crop/eval/<folder>/ (folder names are printed below) and run again.
import os, re, json, shutil, subprocess, collections

CROP = os.path.expanduser('~/crop')
PD = f'{CROP}/PlantDoc-Dataset'
OUT = f'{CROP}/eval'
PHONE = '/sdcard/Android/data/com.cropadvisor/files/eval'
EXT = ('.jpg', '.jpeg', '.png', '.bmp', '.webp')
OTHER = ('apple', 'grape', 'cherry', 'peach', 'orange', 'squash', 'strawberry', 'soybean', 'soyabean',
         'blueberry', 'raspberry')
labels = json.load(open(f'{CROP}/labels.json'))

def toks(s): return re.sub(r'[^a-z]+', ' ', s.lower()).split()
def key(s): return re.sub(r'[^a-z0-9]+', '_', s.lower()).strip('_')

def label_for(folder):                       # same mapping as train_v2.py
    name = toks(folder); n = ' '.join(name); has = lambda *w: any(x in n for x in w)
    if 'tomato' in n:
        for k, l in [('mosaic', 'Tomato_mosaic_virus'), ('yellow', 'Tomato_Yellow_Leaf_Curl_Virus'),
                     ('curl', 'Tomato_Yellow_Leaf_Curl_Virus'), ('spider', 'Spider_mites Two-spotted_spider_mite'),
                     ('mite', 'Spider_mites Two-spotted_spider_mite'), ('target', 'Target_Spot'),
                     ('septoria', 'Septoria_leaf_spot'), ('mold', 'Leaf_Mold'), ('bacterial', 'Bacterial_spot'),
                     ('early', 'Early_blight'), ('late', 'Late_blight')]:
            if k in n: return 'Tomato___' + l
        return 'Tomato___healthy' if has('healthy') or name in (['tomato', 'leaf'], ['tomato']) else None
    if 'potato' in n:
        if 'early' in n: return 'Potato___Early_blight'
        if 'late' in n: return 'Potato___Late_blight'
        return 'Potato___healthy' if has('healthy') or name in (['potato', 'leaf'], ['potato']) else None
    if 'pepper' in n or 'bell' in n:
        return 'Pepper,_bell___Bacterial_spot' if has('spot', 'bacterial') else 'Pepper,_bell___healthy'
    if 'corn' in n or 'maize' in n:
        if 'rust' in n: return 'Corn_(maize)___Common_rust_'
        if has('gray', 'grey', 'cercospora'): return 'Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot'
        if has('blight', 'northern'): return 'Corn_(maize)___Northern_Leaf_Blight'
        return None
    if any(c in n for c in OTHER): return 'Other___leaf'
    return None

if not os.path.isdir(PD):
    print('Downloading PlantDoc (real-field photos)…')
    subprocess.run(['git', 'clone', '--depth', '1', 'https://github.com/pratikkayal/PlantDoc-Dataset.git', PD], check=True)

count = collections.Counter()
test = f'{PD}/test'
for folder in sorted(os.listdir(test)):
    lab = label_for(folder)
    if lab not in labels: print(f'  skip  {folder}'); continue
    dst = f'{OUT}/{key(lab)}'; os.makedirs(dst, exist_ok=True)
    for i, f in enumerate(sorted(os.listdir(f'{test}/{folder}'))):
        if f.lower().endswith(EXT):
            shutil.copy(f'{test}/{folder}/{f}', f'{dst}/pd_{key(folder)}_{i}{os.path.splitext(f)[1].lower()}')
for d in sorted(os.listdir(OUT)):
    count[d] = len([f for f in os.listdir(f'{OUT}/{d}') if f.lower().endswith(EXT)])
    print(f'  {count[d]:4d}  {d}')
print(f'Total {sum(count.values())} photos in {OUT}')

subprocess.run(['adb', 'shell', 'rm', '-rf', PHONE], check=True)
subprocess.run(['adb', 'shell', 'mkdir', '-p', PHONE], check=True)
subprocess.run(['adb', 'push', f'{OUT}/.', PHONE], check=True)
# fallback copy the app can always read on Android 16 (Android/data can be hidden from the app)
subprocess.run(['adb', 'shell', 'rm -rf /data/local/tmp/eval && mkdir -p /data/local/tmp/eval'], check=True)
subprocess.run(['adb', 'push', f'{OUT}/.', '/data/local/tmp/eval'], check=True)
subprocess.run(['adb', 'shell', 'chmod -R a+rX /data/local/tmp/eval'], check=True)
print('\n✔ On the phone: Insights tab → "Run field-photo test".')
print(f'  Get the results after:  adb pull {os.path.dirname(PHONE)}/eval_results.csv {os.path.dirname(PHONE)}/eval_summary.txt ~/crop/')
