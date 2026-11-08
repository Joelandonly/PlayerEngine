#!/usr/bin/env python3
"""Reproducibly install the wool-first route after ALL prior P0 checks.

The prior P0 fix and 14-stage setup stay byte-identical. Only the production
SchematicWorld.acquire class changes; additional classes must come from the
tracked carpet-src file and have a strict class-name prefix.
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
WORLD="com/player2/playerengine/schematic/SchematicWorld.class"
INPUT_SHA="ae14bf7a89033bdfdc202f1b19db12e42b536b3e34fab1a1f93d74d66e6080fc"
CARPET_PREFIX="com/player2/playerengine/schematic/SchematicWhiteCarpetAcquisition"
EXPORTS=[
 "--add-exports","java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
 "--add-exports","java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
]
def digest(blob:bytes)->str:return sha256(blob).hexdigest()
def run(*command):subprocess.run([str(x) for x in command],check=True,cwd=ROOT)

def main():
 source=ROOT/"tools/carpet-src/com/player2/playerengine/schematic/SchematicWhiteCarpetAcquisition.java"
 if not source.is_file():raise FileNotFoundError(source)
 with ZipFile(JAR) as archive:
  names=archive.namelist()
  if len(names)!=1940 or len(names)!=len(set(names)):
   raise RuntimeError("Expected exactly the verified P0 release JAR")
  before=archive.read(WORLD)
  if digest(before)!=INPUT_SHA:raise RuntimeError("Pinned SchematicWorld input SHA changed")
  if any(name.startswith(CARPET_PREFIX) for name in names):
   raise RuntimeError("Carpet route already installed")

 with TemporaryDirectory(prefix="ellie-white-carpet-") as root:
  temp=Path(root);compiled=temp/"classes";compiled.mkdir()
  patch=temp/"patch";patch.mkdir()
  original=temp/"SchematicWorld.class";original.write_bytes(before)
  patched=temp/"SchematicWorld-patched.class"
  run("javac","--release","21","-cp",JAR,"-d",compiled,source)
  additions={p.relative_to(compiled).as_posix():p.read_bytes()
      for p in compiled.rglob("*.class")}
  if (len(additions)<2 or len(additions)>3 or
      "com/player2/playerengine/schematic/SchematicWhiteCarpetAcquisition.class" not in additions or
      not all(n.startswith(CARPET_PREFIX) for n in additions)):
   raise RuntimeError(f"Unexpected generated helper classes {sorted(additions)}")

  run("javac",*EXPORTS,"-d",patch,ROOT/"tools/PatchSchematicCarpetRoute.java")
  run("java",*EXPORTS,"-cp",patch,"PatchSchematicCarpetRoute",original,patched)
  patch_bytes=patched.read_bytes()
  if patch_bytes==before:raise RuntimeError("Wool-first override produced no code change")

  temporary=JAR.with_suffix(".carpet-pending.jar")
  try:
   with ZipFile(JAR) as previous,ZipFile(temporary,"w") as output:
    for item in previous.infolist():
     data=previous.read(item)
     output.writestr(copy(item),patch_bytes if item.filename==WORLD else data)
    for name in sorted(additions):output.writestr(name,additions[name])
   with ZipFile(JAR) as previous,ZipFile(temporary) as updated:
    if updated.testzip() is not None:raise RuntimeError("Corrupted carpet release JAR")
    changed={n for n in previous.namelist() if previous.read(n)!=updated.read(n)}
    if changed!={WORLD}:raise RuntimeError(f"Other production classes changed: {sorted(changed)}")
    if set(updated.namelist())!=set(previous.namelist())|set(additions):
     raise RuntimeError("Unreviewed archive entry differences")
   temporary.replace(JAR)
  finally:temporary.unlink(missing_ok=True)

 print("PASS: White carpet route patches ONE existing production class; no other P0 or setup modifications")
 print("CARPET CLASS SHA256 "+digest(patch_bytes))
 print("CARPET HELPER CLASSES "+", ".join(sorted(additions)))
 print("CARPET FINAL JAR SHA256 "+digest(JAR.read_bytes()))
if __name__=="__main__":main()
