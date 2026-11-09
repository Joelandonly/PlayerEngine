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
    assert metadata['build_id']=='20261108-schematic-native-acquisition-v18.3'
    assert metadata['live_minecraft_validated'] is False
    assert metadata['baseline_sha256']==sha256((root/'baseline/playerengine-acquisition-safety.jar').read_bytes()).hexdigest()
    assert revision in z.read('META-INF/ELLIE_BUILD_REVISION.txt').decode()
    assert b'20261105-basic-batched-home-v9' in z.read('com/player2/playerengine/tasks/setup/EllieSetupTask.class'), 'The proven setup v9 implementation must remain untouched'
    expected={}
    for directory in ('classes','patches'):
        for p in (root/'build'/directory).rglob('*.class'):
            expected[p.relative_to(root/'build'/directory).as_posix()]=p.read_bytes()
    # The GitHub release build applies two reviewed v18.3 binary hotfixes plus the P0 overlay AFTER
    # historical compile/scope checks. Accept only the known-good replacement
    # for the schematic coordinator, never an arbitrary class mismatch.
    reviewed_hotfixes={
        'com/player2/playerengine/tasks/base/Task.class':
            '164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637',
        'com/player2/playerengine/PlayerEngineCommands.class':
            '53d73ad8d59c315928f8af5f5b20f6406738d074a26d0c65c606474cdb425b82',
        'com/player2/playerengine/nativecommands/NativeCommandText.class':
            'ff1557c2698978babd2dcf763b549a66c9e615e7bd381f47cd19035bf4f893e4',
        'com/player2/playerengine/schematic/SchematicProjectTask.class':
            '1418b5fd28ef64fffeaeabadb0058e992b63ea47b05db7587f1e96694111969f',
        'com/player2/playerengine/schematic/SchematicWorld.class':
            'ae14bf7a89033bdfdc202f1b19db12e42b536b3e34fab1a1f93d74d66e6080fc',
        'com/player2/playerengine/tasks/resources/CraftWithMatchingMaterialsTask.class':
            'f36e362ee7cd35d3e89b9122943fda5a5d4eaa1c759405c60f33165ce0f75c83',
        'com/player2/playerengine/tasks/resources/CraftWithMatchingPlanksTask.class':
            'c7c3f19f0a1758255e585626504552b66b0fe716ace1882f82a9c7f66f2d0a30',
    }
    for name,data in expected.items():
        packaged=z.read(name)
        if packaged==data:continue
        assert name in reviewed_hotfixes and sha256(packaged).hexdigest()==reviewed_hotfixes[name],name
    # If the package contains the storage helper, require the fixed Task body too.
    if 'com/player2/playerengine/schematic/SchematicStorageReconciler.class' in z.namelist():
        assert sha256(z.read('com/player2/playerengine/tasks/base/Task.class')).hexdigest()==(
            '164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637'
        ),'The packaged stop-loop fix is missing'
    assert z.testzip() is None
print('Verified packaged build identity:', metadata['build_id'], revision, 'JAR SHA-256',sha256(jar.read_bytes()).hexdigest())
