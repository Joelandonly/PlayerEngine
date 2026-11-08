#!/usr/bin/env python3
"""Verify final carpet overlay without relaxing prior v18.3/P0 class freezes."""
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile
import subprocess
ROOT=Path(__file__).resolve().parents[1]
JAR=ROOT/"dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
CARPET="com/player2/playerengine/schematic/SchematicWhiteCarpetAcquisition"
WORLD="com/player2/playerengine/schematic/SchematicWorld.class"
PREVIOUS={
 "com/player2/playerengine/tasks/base/Task.class":
 "164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637",
 "com/player2/playerengine/tasks/resources/CraftWithMatchingMaterialsTask.class":
 "f36e362ee7cd35d3e89b9122943fda5a5d4eaa1c759405c60f33165ce0f75c83",
 "com/player2/playerengine/tasks/resources/CraftWithMatchingPlanksTask.class":
 "c7c3f19f0a1758255e585626504552b66b0fe716ace1882f82a9c7f66f2d0a30",
 "com/player2/playerengine/schematic/SchematicProjectTask.class":
 "f605c5275c746f93e6c5fdaefd632c17f36b13fa2d0832426c5f366b71cf7c5f",
}
with ZipFile(JAR) as archive:
 names=archive.namelist()
 assert archive.testzip() is None,"corrupt final carpet JAR"
 assert len(set(names))==len(names),"duplicate class file in output"
 additions=[n for n in names if n.startswith(CARPET) and n.endswith(".class")]
 assert set(additions)=={
  CARPET+".class",
  CARPET+"$Stage.class",
 },additions
 assert len(names)==1942,"carpet patch added out-of-scope entries"
 assert sha256(archive.read(WORLD)).hexdigest()==(
  "3886e7780cf90bc563fbcb96feed0391e3218b29317ae39b991d19f4f28c6ced"
 ),"Unknown post-package schematic world class SHA"
 for name,expected in PREVIOUS.items():
  assert sha256(archive.read(name)).hexdigest()==expected,"prior P0 bytecode changed "+name
 assert b"20261105-basic-batched-home-v9" in archive.read(
  "com/player2/playerengine/tasks/setup/EllieSetupTask.class"
 ),"historical 14-stage setup changed"
 assert WORLD in names
actual=subprocess.run(["javap","-p","-c","-classpath",str(JAR),
 "com.player2.playerengine.schematic.SchematicWorld"],
 capture_output=True,text=True,check=True)
assert "SchematicWhiteCarpetAcquisition" in actual.stdout, "final native acquire does not call wool-first route"
helper=subprocess.run(["javap","-p","-c","-classpath",str(JAR),
 "com.player2.playerengine.schematic.SchematicWhiteCarpetAcquisition"],
 capture_output=True,text=True,check=True)
assert "CraftInInventoryTask" in helper.stdout and "nativeItem" in helper.stdout, "staged native craft path missing"
print("PASS verified final wool-first runtime hook, native staged recipe, and unchanged previous hotfixes")
print("PASS output class count",len(names),"helper classes",len(additions))
print("CARPET WORLD SHA256",sha256(ZipFile(JAR).read(WORLD)).hexdigest())
