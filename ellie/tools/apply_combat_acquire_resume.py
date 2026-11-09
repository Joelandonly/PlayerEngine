#!/usr/bin/env python3
"""Overlay the one reviewed schematic ACQUIRE-resume fix after PR26 verification.

Only the already-fixed SchematicProjectTask.class is modified; all 1,939
other existing JAR entries remain byte-identical. Strict SHA prevents the
patcher from accepting an unknown binary or an accidentally merged PR27.
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
PROJECT="com/player2/playerengine/schematic/SchematicProjectTask.class"
INPUT_SHA="f605c5275c746f93e6c5fdaefd632c17f36b13fa2d0832426c5f366b71cf7c5f"
HELPER="com/player2/playerengine/schematic/SchematicAcquireResume"
NEW_CLASSES={HELPER+".class",HELPER+"$Lease.class"}
EXPORTS=[
 "--add-exports","java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
 "--add-exports","java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
]
def digest(blob:bytes)->str:return sha256(blob).hexdigest()
def run(*command):subprocess.run([str(x) for x in command],cwd=ROOT,check=True)
def main():
 with ZipFile(JAR) as original:
  names=original.namelist()
  if len(names)!=1940 or len(names)!=len(set(names)) or (NEW_CLASSES & set(names)):
   raise RuntimeError("Expected exact PR26 release archive with no previous acquire overlay")
  old=original.read(PROJECT)
  if digest(old)!=INPUT_SHA:
   raise RuntimeError(f"Unknown production SchematicProjectTask input: {digest(old)}")

 with TemporaryDirectory(prefix="ellie-combat-resume-") as d:
  temp=Path(d);tools=temp/"tools";tools.mkdir()
  classes=temp/"classes";classes.mkdir()
  source=ROOT/"tools/combat-resume-src/com/player2/playerengine/schematic/SchematicAcquireResume.java"
  run("javac","--release","21","-cp",JAR,"-d",classes,source)
  additions={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob("*.class")}
  if set(additions)!=NEW_CLASSES:
   raise RuntimeError(f"Unexpected runtime helper class set: {set(additions)^NEW_CLASSES}")
  run("javac",*EXPORTS,"-d",tools,ROOT/"tools/PatchCombatAcquireResume.java")
  before=temp/"before.class";before.write_bytes(old)
  after=temp/"after.class"
  run("java",*EXPORTS,"-cp",tools,"PatchCombatAcquireResume",before,after)
  replaced=after.read_bytes()
  if replaced==old:raise RuntimeError("No native coordinator change emitted")

  temporary=JAR.with_suffix(".resume-temporary.jar")
  try:
   with ZipFile(JAR) as z,ZipFile(temporary,"w") as dest:
    for entry in z.infolist():
     dest.writestr(copy(entry),replaced if entry.filename==PROJECT else z.read(entry))
    for name in sorted(additions):dest.writestr(name,additions[name])
   with ZipFile(JAR) as previous, ZipFile(temporary) as actual:
    if actual.testzip() is not None:raise RuntimeError("Corrupted release ZIP")
    diffs={n for n in previous.namelist() if previous.read(n)!=actual.read(n)}
    if diffs!={PROJECT}:raise RuntimeError(f"Unapproved binary changes: {diffs}")
    if set(actual.namelist())!=set(previous.namelist())|NEW_CLASSES:
     raise RuntimeError("Unexpected additional/missing classes")
    if len(actual.namelist())!=1942:raise RuntimeError("Incorrect final class entry count")
   temporary.replace(JAR)
  finally:temporary.unlink(missing_ok=True)

 print("PASS: combat resume patch changes one existing production class; all prior hotfixes and 14-stage setup untouched")
 print("COMBAT RESUME PROJECT SHA256 "+digest(replaced))
 print("COMBAT RESUME FINAL JAR SHA256 "+digest(JAR.read_bytes()))
if __name__=="__main__":main()
