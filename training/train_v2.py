# Uzhavan vision model v2 — trained for REAL FIELD photos, not just lab photos.
# Kaggle: Settings → GPU T4 x1, Internet ON. Paste this whole file into ONE cell and run.
# It downloads PlantVillage, Rice and PlantDoc by itself — no "Add Input" needed.
import os, re, json, random, time, collections, zlib
import torch, torch.nn as nn, torchvision as tv
from torchvision import transforms as T
from PIL import Image, ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True
torch.backends.cudnn.benchmark = True
random.seed(0); torch.manual_seed(0)

LABELS = ['Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot', 'Corn_(maize)___Common_rust_',
          'Corn_(maize)___Northern_Leaf_Blight', 'Corn_(maize)___healthy', 'Pepper,_bell___Bacterial_spot',
          'Pepper,_bell___healthy', 'Potato___Early_blight', 'Potato___Late_blight', 'Potato___healthy',
          'Tomato___Bacterial_spot', 'Tomato___Early_blight', 'Tomato___Late_blight', 'Tomato___Leaf_Mold',
          'Tomato___Septoria_leaf_spot', 'Tomato___Spider_mites Two-spotted_spider_mite', 'Tomato___Target_Spot',
          'Tomato___Tomato_Yellow_Leaf_Curl_Virus', 'Tomato___Tomato_mosaic_virus', 'Tomato___healthy',
          'Rice___BrownSpot', 'Rice___Healthy', 'Rice___Hispa', 'Rice___LeafBlast',
          'Other___leaf', 'Other___background']
EXT = ('.jpg', '.jpeg', '.png', '.bmp', '.webp')
OTHER_CROPS = ('apple', 'grape', 'cherry', 'peach', 'orange', 'squash', 'strawberry', 'soybean', 'soyabean',
               'blueberry', 'raspberry')

def toks(s): return re.sub(r'[^a-z]+', ' ', s.lower()).split()

def label_for(path):
    full = toks(path); name = toks(os.path.basename(path)); n = ' '.join(name)
    if 'grayscale' in full or 'segmented' in full: return None
    if 'background' in n: return 'Other___background'
    if any(w in full for w in ('augmentation', 'augmented')): return None          # duplicates of PlantVillage
    has = lambda *w: any(x in n for x in w)
    if 'tomato' in n:
        for k, l in [('mosaic', 'Tomato_mosaic_virus'), ('yellow', 'Tomato_Yellow_Leaf_Curl_Virus'),
                     ('curl', 'Tomato_Yellow_Leaf_Curl_Virus'), ('spider', 'Spider_mites Two-spotted_spider_mite'),
                     ('mite', 'Spider_mites Two-spotted_spider_mite'), ('target', 'Target_Spot'),
                     ('septoria', 'Septoria_leaf_spot'), ('mold', 'Leaf_Mold'), ('mould', 'Leaf_Mold'),
                     ('bacterial', 'Bacterial_spot'), ('early', 'Early_blight'), ('late', 'Late_blight')]:
            if k in n: return 'Tomato___' + l
        return 'Tomato___healthy' if (has('healthy') or name in (['tomato', 'leaf'], ['tomato'])) else None
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
        return 'Corn_(maize)___healthy' if has('healthy') else None
    if 'rice' in full or 'labelledrice' in full:
        if 'brown' in n: return 'Rice___BrownSpot'
        if 'hispa' in n: return 'Rice___Hispa'
        if 'blast' in n: return 'Rice___LeafBlast'
        if 'healthy' in n: return 'Rice___Healthy'
        return None
    if any(c in n for c in OTHER_CROPS): return 'Other___leaf'
    return None

# ---------------- download datasets (no "Add Input" clicks needed) ----------------
DATA = '/kaggle/working/data'; os.makedirs(DATA, exist_ok=True)
import kagglehub, subprocess
for name, slug in [('plantvillage', 'abdallahalidev/plantvillage-dataset'),
                   ('rice', 'minhhuy2810/rice-diseases-image-dataset')]:
    try:
        p = kagglehub.dataset_download(slug); link = os.path.join(DATA, name)
        if not os.path.exists(link): os.symlink(p, link)
        print('✔', name, '→', p)
    except Exception as e: print('✘', name, e)
if not os.path.exists(f'{DATA}/plantdoc'):   # real-field photos, from the PlantDoc GitHub repo
    r = subprocess.run(['git', 'clone', '--depth', '1', 'https://github.com/pratikkayal/PlantDoc-Dataset.git',
                        f'{DATA}/plantdoc'], capture_output=True, text=True)
    print('✔ plantdoc' if r.returncode == 0 else '✘ plantdoc ' + r.stderr[-300:])

# ---------------- collect images ----------------
items, seen, used_dirs = [], set(), collections.defaultdict(int)
for root, dirs, files in [w for top in ('/kaggle/input', DATA) for w in os.walk(top, followlinks=True)]:
    imgs = [f for f in files if f.lower().endswith(EXT)]
    if not imgs: continue
    lab = label_for(root)
    if lab is None: continue
    field = 'plantdoc' in root.lower().replace('-', '').replace('_', '')
    split_hint = 'test' if field and 'test' in toks(root) else None
    for f in imgs:
        p = os.path.join(root, f)
        key = (f.lower(), os.path.getsize(p))
        if key in seen: continue
        seen.add(key); items.append((p, LABELS.index(lab), field, split_hint)); used_dirs[(lab, root)] += 1

train, val, test_lab, test_field = [], [], [], []
for it in items:
    p, y, field, hint = it
    if hint == 'test': test_field.append(it); continue
    h = zlib.crc32(p.encode()) % 100
    if field: (val if h < 10 else train).append(it)          # PlantDoc train split: 10% val
    else: (val if h < 8 else test_lab if h < 16 else train).append(it)

cnt = collections.Counter(LABELS[y] for _, y, _, _ in train)
print(f'images: train {len(train)}  val {len(val)}  lab-test {len(test_lab)}  FIELD-test {len(test_field)}')
for l in LABELS: print(f'  {cnt.get(l, 0):6d}  {l}')
missing = [l for l in LABELS if cnt.get(l, 0) == 0]
if missing: print('⚠ no images for:', missing)
if not test_field: print('⚠ PlantDoc not found — add it as input for real-field training and the field accuracy number')
LABELS_USED = [l for l in LABELS if cnt.get(l, 0) > 0]
remap = {LABELS.index(l): i for i, l in enumerate(LABELS_USED)}
fix = lambda L: [(p, remap[y], f, h) for p, y, f, h in L if y in remap]
train, val, test_lab, test_field = map(fix, (train, val, test_lab, test_field))

# ---------------- data ----------------
MEAN, STD = [0.485, 0.456, 0.406], [0.229, 0.224, 0.225]
tf_train = T.Compose([
    T.RandomResizedCrop(224, scale=(0.2, 1.0), ratio=(0.7, 1.4)),
    T.RandomHorizontalFlip(), T.RandomVerticalFlip(0.2), T.RandomRotation(30),
    T.ColorJitter(0.45, 0.45, 0.35, 0.04),
    T.RandomApply([T.GaussianBlur(5, (0.1, 2.0))], p=0.3),
    T.RandomAdjustSharpness(2, p=0.2),
    T.ToTensor(), T.Normalize(MEAN, STD), T.RandomErasing(p=0.3, scale=(0.02, 0.15)),
])
tf_eval = T.Compose([T.Resize(256), T.CenterCrop(224), T.ToTensor(), T.Normalize(MEAN, STD)])

class DS(torch.utils.data.Dataset):
    def __init__(s, items, tf): s.items, s.tf = items, tf
    def __len__(s): return len(s.items)
    def __getitem__(s, i):
        p, y, _, _ = s.items[i]
        try: img = Image.open(p).convert('RGB')
        except Exception: img = Image.new('RGB', (256, 256))
        img.thumbnail((600, 600))
        return s.tf(img), y

# balanced sampling: rare classes and real-field photos are drawn more often
c2 = collections.Counter(y for _, y, _, _ in train)
w = [(1.0 / c2[y]) ** 0.6 * (4.0 if f else 1.0) for _, y, f, _ in train]
EPOCH = 24000
sampler = torch.utils.data.WeightedRandomSampler(w, EPOCH, replacement=True)
dl = lambda L, tf, **k: torch.utils.data.DataLoader(DS(L, tf), batch_size=96, num_workers=4, pin_memory=True,
                                                    persistent_workers=True, **k)
train_dl = dl(train, tf_train, sampler=sampler)
val_dl = dl(val, tf_eval)

# ---------------- model ----------------
dev = 'cuda'
model = tv.models.mobilenet_v3_large(weights='DEFAULT')
model.classifier[3] = nn.Linear(1280, len(LABELS_USED))
model.to(dev).to(memory_format=torch.channels_last)
EPOCHS = 14
opt = torch.optim.AdamW(model.parameters(), lr=1.5e-3, weight_decay=0.05)
sched = torch.optim.lr_scheduler.OneCycleLR(opt, 1.5e-3, total_steps=EPOCHS * len(train_dl), pct_start=0.15)
crit = nn.CrossEntropyLoss(label_smoothing=0.1)
scaler = torch.cuda.amp.GradScaler()

@torch.no_grad()
def evaluate(loader):
    model.eval(); ok = n = 0; per = collections.defaultdict(lambda: [0, 0])
    for x, y in loader:
        x = x.to(dev, non_blocking=True).to(memory_format=torch.channels_last); y = y.to(dev)
        with torch.autocast('cuda', dtype=torch.float16):
            p = (model(x).softmax(1) + model(torch.flip(x, [3])).softmax(1)).argmax(1)
        ok += (p == y).sum().item(); n += len(y)
        for a, b in zip(y.tolist(), p.tolist()): per[a][0] += a == b; per[a][1] += 1
    return ok / max(n, 1), per

best = 0
for ep in range(EPOCHS):
    model.train(); t = time.time(); tot = 0
    for x, y in train_dl:
        x = x.to(dev, non_blocking=True).to(memory_format=torch.channels_last); y = y.to(dev)
        with torch.autocast('cuda', dtype=torch.float16): loss = crit(model(x), y)
        opt.zero_grad(set_to_none=True); scaler.scale(loss).backward(); scaler.step(opt); scaler.update(); sched.step()
        tot += loss.item()
    acc, _ = evaluate(val_dl)
    print(f'epoch {ep + 1}/{EPOCHS}  loss {tot / len(train_dl):.3f}  val {acc * 100:.2f}%  ({time.time() - t:.0f}s)')
    if acc > best: best = acc; torch.save(model.state_dict(), '/kaggle/working/crop_mnv3_v2.pth')

# ---------------- final report ----------------
model.load_state_dict(torch.load('/kaggle/working/crop_mnv3_v2.pth'))
metrics = {'val': best}
for name, L in [('lab_test', test_lab), ('FIELD_test', test_field)]:
    if not L: continue
    acc, per = evaluate(dl(L, tf_eval))
    metrics[name] = acc
    print(f'\n{name}: {acc * 100:.2f}%  ({len(L)} images)')
    for y, (a, b) in sorted(per.items()): print(f'   {a / b * 100:5.1f}%  {b:5d}  {LABELS_USED[y]}')
json.dump(LABELS_USED, open('/kaggle/working/labels.json', 'w'))
json.dump(metrics, open('/kaggle/working/metrics.json', 'w'), indent=1)
print('\n✔ Done. Download from Output: crop_mnv3_v2.pth, labels.json, metrics.json')
