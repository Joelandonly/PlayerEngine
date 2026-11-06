"""Freeze the owner-confirmed setup implementation while patching shared movement."""
from pathlib import Path
from zipfile import ZipFile
from hashlib import sha256
import json

root = Path(__file__).resolve().parents[1]
receipt = json.loads((root / 'tests/proven_setup_sha256.json').read_text())
with ZipFile(root / 'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar') as jar:
    actual = {n for n in jar.namelist() if n.startswith('com/player2/playerengine/tasks/setup/') and n.endswith('.class')}
    assert actual == set(receipt['classes']), 'Protected setup class set changed'
    for name, digest in receipt['classes'].items():
        assert sha256(jar.read(name)).hexdigest() == digest, f'Proven setup changed: {name}'
    assert not any(n.startswith(('net/minecraft/', 'ellie/descent/')) for n in jar.namelist()), 'Test fixtures leaked into JAR'
print(f"Descent scope: all {len(actual)} setup classes byte-identical to proven {receipt['source_revision']}; no game fixtures shipped")
