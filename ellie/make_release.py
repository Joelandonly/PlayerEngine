"""Package the current Ellie build without changing the imported provenance."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
from hashlib import sha256
import json

ROOT = Path(__file__).resolve().parent
JAR_NAME = 'PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-STARTER-BASE-PREVIEW.jar'
JAR = ROOT / 'dist' / JAR_NAME
OUT = ROOT / 'dist' / 'PlayerEngine-Ellie-Starter-Base.zip'
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
    'jar_sha256': sha256(files[JAR_NAME]).hexdigest(),
    'source_upload_jar_sha256': original.get('source_upload_jar_sha256', original['jar_sha256']),
    'build_kind': 'binary-overlay; complete Minecraft 1.21.1 source unavailable',
    'release': 'Ellie full starter kit and placed workstation/storage base preview',
    'changed_existing_classes': [
        'com/player2/playerengine/player2api/Player2APIService.class',
        'com/player2/playerengine/supervisor/CoreGoalSupervisor.class',
        'com/player2/playerengine/supervisor/CoreGoalSupervisor$Slot.class',
        'com/player2/playerengine/supervisor/GoalLifecycle.class',
        'com/player2/playerengine/supervisor/GoalLifecycle$Snapshot.class',
        'com/player2/playerengine/supervisor/GoalLifecycle$Status.class',
    ],
    'tests': 'Run bash build.sh: bridge, supervisor, dispatcher, setup/base and Node tests; live Minecraft requires separate validation',
}
manifest['changed_existing_classes'] += [
    'com/player2/playerengine/commands/' + name + '.class'
    for name in (ROOT / 'tools/supervised-commands.txt').read_text().splitlines()
]
manifest['new_classes'] = ['com/player2/playerengine/interpreter/LocalChatGPTBridge.class',
                           'com/player2/playerengine/supervisor/ProjectTaskGateway.class']
manifest['changed_existing_classes'] += [
    'com/player2/playerengine/commands/EllieSetupCommand.class',
    'com/player2/playerengine/tasks/setup/EllieSetupTask.class',
    'com/player2/playerengine/tasks/setup/EllieSetupTask$Step.class',
    'com/player2/playerengine/tasks/setup/CoreAcquisitionProgress.class',
    'com/player2/playerengine/tasks/setup/CoreAcquisitionProgress$Sample.class',
    'com/player2/playerengine/tasks/crafting/CraftMacroResourceTask.class',
]
manifest['new_classes'] += ['com/player2/playerengine/tasks/setup/'+name+'.class' for name in [
    'StarterBaseLayout','StarterBaseLayout$Pos','StarterBaseLayout$Placement','StarterBaseLayout$Probe',
    'StarterBaseTask','StarterBaseTask$Environment','StarterBaseWorld']]
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
