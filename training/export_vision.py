# Laptop: converts the Kaggle-trained weights into crop_mnv3.pte for the phone (ExecuTorch + XNNPACK).
# Usage:  source ~/et-env/bin/activate && cd ~/crop && python ~/CropAdvisor/training/export_vision.py
import json, os, torch, torchvision
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
from executorch.exir import to_edge_transform_and_lower

labels = json.load(open('labels.json'))
m = torchvision.models.mobilenet_v3_large()
m.classifier[3] = torch.nn.Linear(1280, len(labels))
w = 'crop_mnv3_v3.pth' if os.path.exists('crop_mnv3_v3.pth') else 'crop_mnv3_v2.pth'   # newest weights win
print('weights:', w)
m.load_state_dict(torch.load(w, map_location='cpu'))
m.eval()
ep = torch.export.export(m, (torch.randn(1, 3, 224, 224),))
prog = to_edge_transform_and_lower(ep, partitioner=[XnnpackPartitioner()]).to_executorch()
open('crop_mnv3.pte', 'wb').write(prog.buffer)
print(f'✔ crop_mnv3.pte written ({len(prog.buffer) / 1e6:.1f} MB, {len(labels)} classes)')
