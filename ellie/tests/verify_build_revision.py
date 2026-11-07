"""Verify the actual install JAR's identity, source revision and overlay contents."""
from pathlib import Path
from zipfile import ZipFile
from hashlib import sha256
import json, os, subprocess

root=Path(__file__).resolve().parents[1]
jar=root/'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar'
revision=os.environ.get('ELLIE_REVISION') or subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
actual_head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
assert revision==actual_head,'Build must check out the exact recorded source revision'
with ZipFile(jar) as z:
    metadata=json.loads(z.read('META-INF/ELLIE_BUILD.json'))
    assert metadata['source_revision']==revision, metadata
    digest=sha256()
    for p in sorted([root/'build.sh',root/'tools/package.py',*list((root/'src').rglob('*.java')),*list((root/'tools').glob('*.java')),*list((root/'tests').rglob('*'))]):
        if p.is_file():digest.update(p.relative_to(root).as_posix().encode()+b'\0'+p.read_bytes())
    assert metadata['source_sha256']==digest.hexdigest(),'JAR sources differ from current build inputs'
    assert metadata['build_id']=='20261107-partial-block-navigation-v17'
    assert metadata['live_minecraft_validated'] is False
    assert metadata['baseline_sha256']==sha256((root/'baseline/playerengine-acquisition-safety.jar').read_bytes()).hexdigest()
    assert revision in z.read('META-INF/ELLIE_BUILD_REVISION.txt').decode()
    assert b'20261105-basic-batched-home-v9' in z.read('com/player2/playerengine/tasks/setup/EllieSetupTask.class'), 'The proven setup v9 implementation must remain untouched'
    expected={}
    for directory in ('classes','patches'):
        for p in (root/'build'/directory).rglob('*.class'):
            expected[p.relative_to(root/'build'/directory).as_posix()]=p.read_bytes()
    for name,data in expected.items():assert z.read(name)==data,name
    assert z.testzip() is None
print('Verified packaged build identity:', metadata['build_id'], revision, 'JAR SHA-256',sha256(jar.read_bytes()).hexdigest())
