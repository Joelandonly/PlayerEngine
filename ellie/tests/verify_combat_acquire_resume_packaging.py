#!/usr/bin/env python3
"""Inspect the FINAL GitHub JAR for exactly the approved transient resume hook."""
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile
import subprocess
ROOT=Path(__file__).resolve().parents[1]
JAR=ROOT/"dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
PROJECT="com/player2/playerengine/schematic/SchematicProjectTask.class"
HELPER="com/player2/playerengine/schematic/SchematicAcquireResume"
PREVIOUS={
 "com/player2/playerengine/tasks/base/Task.class":
  "164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637",
 "com/player2/playerengine/schematic/SchematicWorld.class":
  "ae14bf7a89033bdfdc202f1b19db12e42b536b3e34fab1a1f93d74d66e6080fc",
 "com/player2/playerengine/tasks/resources/CraftWithMatchingMaterialsTask.class":
  "f36e362ee7cd35d3e89b9122943fda5a5d4eaa1c759405c60f33165ce0f75c83",
 "com/player2/playerengine/tasks/resources/CraftWithMatchingPlanksTask.class":
  "c7c3f19f0a1758255e585626504552b66b0fe716ace1882f82a9c7f66f2d0a30",
}
with ZipFile(JAR) as z:
 names=z.namelist()
 assert len(names)==1942 and len(set(names))==len(names),"Unexpected released class set"
 assert z.testzip() is None,"Corrupt packaged JAR"
 assert {HELPER+".class",HELPER+"$Lease.class"}<=set(names)
 for name,digest in PREVIOUS.items():
  assert sha256(z.read(name)).hexdigest()==digest, "Older P0/safety bytecode unexpectedly changed: "+name
 assert b"20261105-basic-batched-home-v9" in z.read(
  "com/player2/playerengine/tasks/setup/EllieSetupTask.class"),"14-stage setup changed"
 assert sha256(z.read(PROJECT)).hexdigest()!=(
  "f605c5275c746f93e6c5fdaefd632c17f36b13fa2d0832426c5f366b71cf7c5f"
 ),"Coordinator did not change"
for name,functions in [
 ("com.player2.playerengine.schematic.SchematicProjectTask",
  ["SchematicAcquireResume.hold","SchematicAcquireResume.beforePoll","SchematicAcquireResume.forget"]),
 ("com.player2.playerengine.schematic.SchematicAcquireResume",
  ["SCHEMATIC_ACQUIRE_SUSPENDED","SCHEMATIC_ACQUIRE_RESUMED"]),
]:
 proc=subprocess.run(["javap","-p","-c","-v","-classpath",str(JAR),name],
  capture_output=True,text=True,check=True)
 for fn in functions:
  assert fn in proc.stdout, "Missing packaged native hook/event: "+fn
print("PASS: production SchematicProjectTask transient ACQUIRE hooks, exact scope and prior P0 bytecode preserved")
