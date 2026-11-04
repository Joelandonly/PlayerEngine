"""Package the current Ellie build without changing the imported provenance."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from hashlib import sha256
import json

ROOT = Path(__file__).resolve().parent
JAR_NAME = 'PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-CHATGPT-INTERPRETER-PREVIEW.jar'
JAR = ROOT / 'dist' / JAR_NAME
OUT = ROOT / 'dist' / 'PlayerEngine-Ellie-ChatGPT-Interpreter.zip'
BASE = ROOT / 'baseline' / 'playerengine-acquisition-safety.jar'
original = json.loads((ROOT / 'RELEASE_MANIFEST.json').read_text())
if sha256(BASE.read_bytes()).hexdigest() != original['baseline_sha256']:
    raise SystemExit('Baseline checksum mismatch')
files = {JAR_NAME: JAR.read_bytes()}
for directory in ['src', 'tools', 'sidecar', 'tests', 'baseline']:
    for p in sorted((ROOT / directory).rglob('*')):
        if p.is_file():
            files[p.relative_to(ROOT).as_posix()] = p.read_bytes()
for name in ['README.md', 'UPSTREAM_README.md', 'RELEASE_NOTES.md',
             'Start-Ellie-ChatGPT.ps1', 'build.sh', 'make_release.py']:
    files[name] = (ROOT / name).read_bytes()
manifest = {
    **original,
    'jar_sha256': sha256(files[JAR_NAME]).hexdigest(),
    'source_upload_jar_sha256': original['jar_sha256'],
    'build_kind': 'binary-overlay; complete Minecraft 1.21.1 source unavailable',
    'tests': 'Run bash build.sh; account inference and live Minecraft require separate validation',
}
files['RELEASE_MANIFEST.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
with ZipFile(OUT, 'w', ZIP_DEFLATED, compresslevel=6) as z:
    for name, data in sorted(files.items()):
        info = ZipInfo(name, date_time=(2026, 11, 3, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        z.writestr(info, data, ZIP_DEFLATED, compresslevel=6)
with ZipFile(OUT) as z:
    assert z.testzip() is None
    assert len(z.namelist()) == len(set(z.namelist()))
    assert z.read(JAR_NAME) == JAR.read_bytes()
print('ZIP:', OUT, 'bytes', OUT.stat().st_size, 'SHA256:', sha256(OUT.read_bytes()).hexdigest())
