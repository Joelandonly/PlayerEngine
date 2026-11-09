#!/usr/bin/env python3
"""Verify exact new excavation handler, strict entry set and preserved PR28 bytes."""
from pathlib import Path
from hashlib import sha256
from zipfile import ZipFile
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JAR=ROOT/"dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
CMD="com/player2/playerengine/PlayerEngineCommands.class"
ROUTE="com/player2/playerengine/nativecommands/NativeCommandText.class"
NEW={
 "com/player2/playerengine/commands/ExcavateAreaCommand.class",
 "com/player2/playerengine/tasks/construction/ExcavateAreaTask.class",
 "com/player2/playerengine/nativecommands/ExcavationCommandRoute.class",
}
FROZEN={
 "com/player2/playerengine/tasks/base/Task.class":
 "164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637",
 "com/player2/playerengine/schematic/SchematicProjectTask.class":
 "1418b5fd28ef64fffeaeabadb0058e992b63ea47b05db7587f1e96694111969f",
 "com/player2/playerengine/schematic/SchematicWorld.class":
 "ae14bf7a89033bdfdc202f1b19db12e42b536b3e34fab1a1f93d74d66e6080fc",
 "com/player2/playerengine/tasks/resources/CraftWithMatchingMaterialsTask.class":
 "f36e362ee7cd35d3e89b9122943fda5a5d4eaa1c759405c60f33165ce0f75c83",
 "com/player2/playerengine/tasks/resources/CraftWithMatchingPlanksTask.class":
 "c7c3f19f0a1758255e585626504552b66b0fe716ace1882f82a9c7f66f2d0a30",
}
with ZipFile(JAR) as z:
 names=z.namelist()
 assert z.testzip() is None
 assert len(names)==1945 and len(set(names))==len(names),"unexpected excavation JAR entry set"
 assert NEW <= set(names)
 assert not any(n.startswith("com/player2/playerengine/schematic/SchematicWhiteCarpetAcquisition")
                for n in names),"withdrawn sheep workaround leaked into release"
 for name,want in FROZEN.items():
  got=sha256(z.read(name)).hexdigest()
  assert got==want, "Prior tested native binary changed: "+name
 assert b"20261105-basic-batched-home-v9" in z.read(
   "com/player2/playerengine/tasks/setup/EllieSetupTask.class")
for klass,required in [
 ("com.player2.playerengine.PlayerEngineCommands",["ExcavateAreaCommand"]),
 ("com.player2.playerengine.nativecommands.NativeCommandText",["ExcavationCommandRoute.exact"]),
 ("com.player2.playerengine.tasks.construction.ExcavateAreaTask",
  ["clearArea","method_22340","method_8321","SchematicDepotGuard.protects"]),
]:
 p=subprocess.run(["javap","-p","-c","-v","-classpath",str(JAR),klass],
                  capture_output=True,text=True,check=True)
 for method in required:assert method in p.stdout,"Missing packaged excavate function "+method
print("PASS: registered excavate command, protected native clearArea and untouched combat/setup/P0 fixes")
