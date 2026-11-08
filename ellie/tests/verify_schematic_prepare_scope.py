"""Freeze every v18 class except the preparation repairs and shared constructor resolver."""
import hashlib,json
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
receipt=json.loads((root/'tests/schematic_prepare_scope.json').read_text())
with ZipFile(root/'dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar') as jar:
    added=set(receipt['new_classes']);allowed=set(receipt['allowed_existing_changes'])
    classes=sorted(n for n in jar.namelist() if n.endswith('.class') and n not in allowed|added)
    digest=hashlib.sha256()
    for n in classes:digest.update(n.encode()+b'\0'+jar.read(n))
    assert len(classes)==receipt['unchanged_classes'], 'Unexpected class set change'
    assert digest.hexdigest()==receipt['unchanged_sha256'], 'An unrelated v18 class changed'
    assert added<=set(jar.namelist()), 'Schematic helper missing'
    assert not any(n.startswith(('net/minecraft/','PartialBlockNavigationTest','PartialNative')) for n in jar.namelist()), 'Test types leaked into JAR'
print(f"Schematic preparation hotfix scope: all {len(classes)} unrelated classes byte-identical to v18 {receipt['source_revision']}; preparation repairs and constructor resolver and reviewed acquisition/convergence repair only")
