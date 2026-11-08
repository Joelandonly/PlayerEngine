#!/usr/bin/env python3
"""Inspect production bytecode after BOTH P0 overlays; no tests-only substitute."""
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JAR=ROOT/"dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
EXPECTED_NEW={
 "com/player2/playerengine/reliability/PlankTargetReconciliation.class",
 "com/player2/playerengine/schematic/SchematicDepotGuard.class",
 "com/player2/playerengine/schematic/SchematicDepotGuard$Lease.class",
}
with ZipFile(JAR) as z:
 names=z.namelist()
 assert z.testzip() is None,"Corrupt output JAR"
 assert len(names)==1940 and len(names)==len(set(names)),"Expected precisely three new P0 helper classes"
 assert EXPECTED_NEW <= set(names), "Missing P0 helper"
 assert sha256(z.read("com/player2/playerengine/tasks/base/Task.class")).hexdigest()==(
  "164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637"
 ),"Previous stop reentrancy fix was lost"
 assert b"20261105-basic-batched-home-v9" in z.read(
  "com/player2/playerengine/tasks/setup/EllieSetupTask.class"
 ),"Historical setup modified"
for classname,hook in [
 ("com.player2.playerengine.tasks.resources.CraftWithMatchingMaterialsTask",
  "PlankTargetReconciliation.absoluteTarget"),
 ("com.player2.playerengine.schematic.SchematicWorld","SchematicDepotGuard.retain"),
 ("com.player2.playerengine.schematic.SchematicProjectTask","SchematicDepotGuard.protects"),
]:
 proc=subprocess.run(["javap","-p","-c","-classpath",str(JAR),classname],
  capture_output=True,text=True,check=True)
 assert hook in proc.stdout, f"Missing production hook {hook} in {classname}"
print("PASS P0 final packaged bytecode: native plank target, durable chest registration and reserved-resource hook")
print("PASS 1,940 JAR entries; stop-loop and 14-stage setup preserved")
