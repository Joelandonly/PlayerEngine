"""Package the current Ellie build without changing the imported provenance."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from hashlib import sha256
import json

ROOT = Path(__file__).resolve().parent
JAR_NAME = 'PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar'
JAR = ROOT / 'dist' / JAR_NAME
OUT = ROOT / 'dist' / 'PlayerEngine-Ellie-Player2-Shared-Recovery.zip'
BASE = ROOT / 'baseline' / 'playerengine-acquisition-safety.jar'
original = json.loads((ROOT / 'RELEASE_MANIFEST.json').read_text())
if sha256(BASE.read_bytes()).hexdigest() != original['baseline_sha256']:
    raise SystemExit('Baseline checksum mismatch')
files = {}
for directory in ['src', 'tools', 'tests', 'baseline', 'provenance', 'sidecar', 'legacy-chatgpt']:
    for p in sorted((ROOT / directory).rglob('*')):
        if p.is_file() and p.suffix not in ('.jar', '.class', '.pyc'):
            files[p.relative_to(ROOT).as_posix()] = p.read_bytes()
for name in ['README.md', 'UPSTREAM_README.md', 'RELEASE_NOTES.md', 'HISTORICAL_RELEASE_NOTES.md',
             'build.sh', 'make_release.py']:
    files[name] = (ROOT / name).read_bytes()
# Include current guidance in the portable bundle; preserve imported docs as history.
docs_root = ROOT.parent / 'docs' if (ROOT.parent / 'docs').is_dir() else ROOT / 'docs'
if docs_root.is_dir():
    for p in sorted(docs_root.iterdir()):
        if p.is_file() and p.suffix in ('.md', '.tsv'):
            data = p.read_bytes()
            if p.suffix == '.md': data = data.decode().replace('../ellie/', '../').encode()
            files['docs/' + p.name] = data
files['README.md'] = files['README.md'].decode().replace('../docs/', 'docs/').encode()
for name in ['LICENSE', 'DEVELOPMENT_LOG.md']:
    candidate = ROOT.parent / name if (ROOT.parent / name).is_file() else ROOT / name
    if candidate.is_file(): files[name] = candidate.read_bytes()
manifest = {
    **original,
    'jar_sha256': sha256(JAR.read_bytes()).hexdigest(),
    'source_upload_jar_sha256': original.get('source_upload_jar_sha256', original['jar_sha256']),
    'target': 'Minecraft 1.21.1 Fabric, PlayerEngine 1.4.0, Java 21, native Player2',
    'build_kind': 'binary-overlay; complete Minecraft 1.21.1 source unavailable',
    'release': 'Ellie non-loading water escape v15 — CI candidate; gameplay UNVERIFIED',
    'craft_recovery_upload_jar_sha256': 'f36e4210a31024c113f7be6d2afa97385c878a15b1ab1d9f590a4ea745980b9f',
    'changed_existing_classes': [
        'com/player2/playerengine/supervisor/CoreGoalSupervisor.class',
        'com/player2/playerengine/supervisor/CoreGoalSupervisor$Slot.class',
        'com/player2/playerengine/supervisor/GoalLifecycle.class',
        'com/player2/playerengine/supervisor/GoalLifecycle$Snapshot.class',
        'com/player2/playerengine/supervisor/GoalLifecycle$Status.class',
    ],
    'tests': 'Run bash build.sh: supervisor, native commands, setup/base, production/NPC-use, shared recovery policy/runtime, packaged bytecode and Player2 integrity checks; live Minecraft requires separate validation',
}
with ZipFile(BASE) as base_archive, ZipFile(JAR) as jar_archive:
    previous={n:base_archive.read(n) for n in base_archive.namelist() if not n.endswith('/')}
    current={n:jar_archive.read(n) for n in jar_archive.namelist() if not n.endswith('/')}
manifest['changed_existing_classes']=sorted(n for n in previous.keys() & current.keys() if n.endswith('.class') and previous[n]!=current[n])
manifest['new_classes']=sorted(n for n in current.keys()-previous.keys() if n.endswith('.class'))
manifest['removed_classes']=sorted(n for n in previous.keys()-current.keys() if n.endswith('.class'))
with ZipFile(JAR) as packaged:
    manifest.update(json.loads(packaged.read('META-INF/ELLIE_BUILD.json')))
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
    assert not any(n.endswith(('.jar','.class')) for n in z.namelist()), 'source bundle must contain no binaries'
print('ZIP:', OUT, 'bytes', OUT.stat().st_size, 'SHA256:', sha256(OUT.read_bytes()).hexdigest())
