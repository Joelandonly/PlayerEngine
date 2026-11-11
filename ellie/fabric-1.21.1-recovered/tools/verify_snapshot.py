#!/usr/bin/env python3
"""Validate SHA-256 of every archived source/build input; no network access."""
from pathlib import Path
import hashlib,json,sys
root=Path(__file__).resolve().parents[1]
man=json.loads((root/'SOURCE_SHA256.json').read_text(encoding='utf-8'))
failed=[]
for rel,expect in man['file_hashes'].items():
    p=root/rel
    if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=expect:failed.append(rel)
for rel in ['common/src/main/java/com/player2/playerengine/safezone/SafeZoneRegion.java','common/src/main/java/com/player2/playerengine/acquisition/ProjectMaterialPlanner.java','fabric/src/main/resources/fabric.mod.json']:
    if not (root/rel).is_file():failed.append(rel)
print('Source snapshot:',len(man['file_hashes']),'files checked; mismatches:',len(failed))
for rel in failed[:30]:print('FAIL:',rel)
sys.exit(1 if failed else 0)
