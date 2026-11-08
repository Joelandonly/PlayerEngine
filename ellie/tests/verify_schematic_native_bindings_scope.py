"""Freeze every v18.1 JAR entry except the shared resolver and build metadata."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
receipt = json.loads((root / 'tests/schematic_native_bindings_scope.json').read_text())
with ZipFile(root / 'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar') as jar:
    allowed = set(receipt['allowed_changes'])
    names = sorted(n for n in jar.namelist() if n not in allowed)
    digest = hashlib.sha256()
    for name in names:
        digest.update(name.encode() + b'\0' + jar.read(name))
    assert len(names) == receipt['unchanged_entries'], 'Unexpected JAR entry set change'
    assert sum(n.endswith('.class') for n in names) == receipt['unchanged_classes']
    assert digest.hexdigest() == receipt['unchanged_sha256'], 'An unrelated v18.1 entry changed'
    assert allowed <= set(jar.namelist())
print(f"Native bindings hotfix scope: {len(names)} entries, including {receipt['unchanged_classes']} classes, byte-identical to v18.1; reviewed changes are listed in the receipt; v18.3 separately freezes other v18.2 entries")
