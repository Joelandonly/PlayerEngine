#!/usr/bin/env python3
"""Produce the scoped P0 runtime overlay after the verified v18.3 hotfix.

Only three existing classes may change:
- CraftWithMatchingMaterialsTask: missing -> absolute plank target.
- SchematicWorld: register persistent world-scoped depot protection.
- SchematicProjectTask: keep reserved-source protection after cancellation.

No task runner, 14-stage setup, command routing or old hotfix classes are
replaced outside those precise reviewed boundaries.
"""
from __future__ import annotations

from copy import copy
from hashlib import sha256
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JAR=ROOT/"dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
CLASSES={
 "com/player2/playerengine/tasks/resources/CraftWithMatchingMaterialsTask.class":
 "73da8c1d78e3df47a3be0f31a5fa70e6876f72a6915174408234412aee8a3880",
 "com/player2/playerengine/schematic/SchematicWorld.class":
 "1898a8ee739974b552e1c4dca6d482a893b4b253bd5ceb8e0462d1955fe02f95",
 "com/player2/playerengine/schematic/SchematicProjectTask.class":
 "d56fe212cec636b67af2c93dc81e4e5c07a9066ef739204dec7181ca4be69956",
}
NEW_CLASSES={
 "com/player2/playerengine/reliability/PlankTargetReconciliation.class",
 "com/player2/playerengine/schematic/SchematicDepotGuard.class",
 "com/player2/playerengine/schematic/SchematicDepotGuard$Lease.class",
}
EXPORTS=[
 "--add-exports","java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
 "--add-exports","java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
]
def sha(data:bytes)->str:return sha256(data).hexdigest()
def run(*cmd):subprocess.run([str(a) for a in cmd],check=True,cwd=ROOT)

def main():
 with ZipFile(JAR) as z:
  names=z.namelist()
  assert len(names)==1937 and len(names)==len(set(names)), "Expected exact post-v18-hotfix JAR"
  assert not set(names)&NEW_CLASSES, "P0 helpers already packaged"
  for n,h in CLASSES.items():
   actual=sha(z.read(n))
   if actual!=h:raise RuntimeError(f"Unknown pinned v18.3 class {n}: {actual}")
 with TemporaryDirectory(prefix="ellie-p0-") as scratch:
  temp=Path(scratch);tools=temp/"tools";tools.mkdir();newclasses=temp/"new";newclasses.mkdir()
  run("javac",*EXPORTS,"-d",tools,ROOT/"tools/PatchMatchingPlankTarget.java",ROOT/"tools/PatchSchematicDepotGuard.java")
  sources=list((ROOT/"tools/p0-src").rglob("*.java"))
  if len(sources)!=2:raise RuntimeError(f"Unexpected P0 source count: {sources}")
  run("javac","--release","21","-cp",JAR,"-d",newclasses,*sources)
  compiled={f.relative_to(newclasses).as_posix():f.read_bytes() for f in newclasses.rglob("*.class")}
  if set(compiled)!=NEW_CLASSES:raise RuntimeError(f"Unknown helper class delta {set(compiled)^NEW_CLASSES}")
  existing={}
  with ZipFile(JAR) as z:
   for name in CLASSES:
    path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(name))
   native=next(n for n in CLASSES if n.endswith("CraftWithMatchingMaterialsTask.class"))
   world=next(n for n in CLASSES if n.endswith("SchematicWorld.class"))
   project=next(n for n in CLASSES if n.endswith("SchematicProjectTask.class"))
   craft=temp/"patched-craft.class";outworld=temp/"patched-world.class";outproject=temp/"patched-project.class"
   run("java",*EXPORTS,"-cp",tools,"PatchMatchingPlankTarget",temp/native,craft)
   run("java",*EXPORTS,"-cp",tools,"PatchSchematicDepotGuard",temp/world,outworld,temp/project,outproject)
   existing={native:craft.read_bytes(),world:outworld.read_bytes(),project:outproject.read_bytes()}
  tempjar=JAR.with_suffix(".p0-temporary.jar")
  try:
   with ZipFile(JAR) as original,ZipFile(tempjar,"w") as output:
    for entry in original.infolist():
     data=original.read(entry)
     output.writestr(copy(entry),existing.get(entry.filename,data))
    for name in sorted(compiled):output.writestr(name,compiled[name])
   with ZipFile(JAR) as previous,ZipFile(tempjar) as final:
    assert final.testzip() is None,"Corrupt final JAR"
    changed={n for n in previous.namelist() if previous.read(n)!=final.read(n)}
    if changed!=set(CLASSES):raise RuntimeError(f"Out-of-scope production changes: {changed^set(CLASSES)}")
    assert set(final.namelist())==set(previous.namelist())|NEW_CLASSES
    assert len(final.namelist())==1940
   tempjar.replace(JAR)
  finally:tempjar.unlink(missing_ok=True)
 print("PASS: P0 exact three-class patch; original 1,934 other entries preserved")
 for name in existing:print("P0 CLASS SHA256 "+name+" "+sha(existing[name]))
 print("P0 FINAL JAR SHA256 "+sha(JAR.read_bytes()))

if __name__=="__main__":main()
