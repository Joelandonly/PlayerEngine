"""Freeze all v18.2 entries outside the reviewed acquisition/progress/convergence patch."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
receipt = json.loads((root / 'tests/schematic_acquisition_scope.json').read_text())
with ZipFile(root / 'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar') as jar:
    names = sorted(n for n in jar.namelist() if n not in receipt['allowed_changes'])
    digest = hashlib.sha256()
    for name in names:
        digest.update(name.encode() + b'\0' + jar.read(name))
    assert len(names) == receipt['unchanged_entries'], 'Unexpected JAR entry set change'
    assert sum(n.endswith('.class') for n in names) == receipt['unchanged_classes']
    assert digest.hexdigest() == receipt['unchanged_sha256'], 'An unrelated v18.2 entry changed'
    assert set(receipt['allowed_changes']) <= set(jar.namelist())
print(f"Schematic acquisition scope: {len(names)} entries including {receipt['unchanged_classes']} classes byte-identical to delivered v18.2; only five reviewed class files and build identity may change")
