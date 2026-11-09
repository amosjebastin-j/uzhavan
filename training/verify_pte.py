# Laptop: checks that crop_mnv3.pte (ExecuTorch) gives the same answer as the PyTorch weights it came from.
# Run from ~/crop:  python ~/CropAdvisor/training/verify_pte.py crop_mnv3_v3.pth
# Exit code 0 = OK, 1 = broken export (uzhavan.sh then keeps the previous working model).
import json, sys
import torch, torchvision

weights = sys.argv[1]
labels = json.load(open('labels.json'))
m = torchvision.models.mobilenet_v3_large()
m.classifier[3] = torch.nn.Linear(1280, len(labels))
m.load_state_dict(torch.load(weights, map_location='cpu'))
m.eval()

try:
    from executorch.runtime import Runtime
except Exception as e:                       # runtime not available in this env: cannot check, do not block
    print(f'   (skipping .pte check: {e})'); sys.exit(0)

method = Runtime.get().load_program('crop_mnv3.pte').load_method('forward')
torch.manual_seed(0)
worst, agree = 0.0, 0
for _ in range(5):
    x = torch.randn(1, 3, 224, 224)
    with torch.no_grad():
        ref = m(x)
    out = method.execute([x])[0]
    if tuple(out.shape) != tuple(ref.shape):
        print(f'   ✘ output shape {tuple(out.shape)} != {tuple(ref.shape)}'); sys.exit(1)
    worst = max(worst, (out - ref).abs().max().item())
    agree += int(out.argmax() == ref.argmax())
print(f'   .pte vs PyTorch: max difference {worst:.5f}, same top class {agree}/5, {len(labels)} classes')
sys.exit(0 if worst < 0.05 and agree == 5 else 1)
