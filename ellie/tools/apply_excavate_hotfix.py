#!/usr/bin/env python3
"""Install standalone, tightly scoped explicit excavation command after PR28.

Changes exactly PlayerEngineCommands and NativeCommandText; adds exactly
three tracked native command/task/route classes. Does not rewrite the builder,
mob defense, existing inventory behavior, the schematic coordinator or setup.
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
COMMANDS="com/player2/playerengine/PlayerEngineCommands.class"
ROUTE="com/player2/playerengine/nativecommands/NativeCommandText.class"
PROJECT="com/player2/playerengine/schematic/SchematicProjectTask.class"
PROJECT_SHA="1418b5fd28ef64fffeaeabadb0058e992b63ea47b05db7587f1e96694111969f"
NEW={
 "com/player2/playerengine/commands/ExcavateAreaCommand.class",
 "com/player2/playerengine/tasks/construction/ExcavateAreaTask.class",
 "com/player2/playerengine/nativecommands/ExcavationCommandRoute.class",
}
EXPORTS=[
 "--add-exports","java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
 "--add-exports","java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
]
def sha(value:bytes)->str:return sha256(value).hexdigest()
def run(*command):subprocess.run([str(x) for x in command],check=True,cwd=ROOT)
def main():
 with ZipFile(JAR) as z:
  names=z.namelist()
  if len(names)!=1942 or len(set(names))!=len(names) or NEW & set(names):
   raise RuntimeError("Expected exact verified PR28 artifact without excavation")
  if sha(z.read(PROJECT))!=PROJECT_SHA:raise RuntimeError("Missing current Mob Defense resume patch")
  original={COMMANDS:z.read(COMMANDS), ROUTE:z.read(ROUTE)}

 with TemporaryDirectory(prefix="ellie-excavate-") as directory:
  temp=Path(directory);tools=temp/"asm";tools.mkdir()
  compiled=temp/"compiled";compiled.mkdir()
  source=list((ROOT/"tools/excavate-src").rglob("*.java"))
  if len(source)!=3:raise RuntimeError("Unexpected tracked excavation source set")
  run("javac","--release","21","-cp",JAR,"-d",compiled,*source)
  additions={p.relative_to(compiled).as_posix():p.read_bytes() for p in compiled.rglob("*.class")}
  if set(additions)!=NEW:raise RuntimeError(f"Unexpected excavation classes {set(additions)^NEW}")
  run("javac",*EXPORTS,"-d",tools,ROOT/"tools/PatchExcavationCommand.java")
  inputs={}
  outputs={}
  for n,data in original.items():
   path=temp/n;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);inputs[n]=path
   outputs[n]=temp/("patched-"+n.split("/")[-1])
  run("java",*EXPORTS,"-cp",tools,"PatchExcavationCommand",
      inputs[COMMANDS],outputs[COMMANDS],inputs[ROUTE],outputs[ROUTE])
  modified={n:p.read_bytes() for n,p in outputs.items()}
  if any(modified[n]==original[n] for n in original):raise RuntimeError("Excavation registration/parser overlay did not modify both expected classes")

  tmp=JAR.with_suffix(".excavate-temporary.jar")
  try:
   with ZipFile(JAR) as old,ZipFile(tmp,"w") as new:
    for entry in old.infolist():new.writestr(copy(entry),modified.get(entry.filename,old.read(entry)))
    for n in sorted(additions):new.writestr(n,additions[n])
   with ZipFile(JAR) as old,ZipFile(tmp) as new:
    if new.testzip() is not None:raise RuntimeError("Corrupt excavation JAR")
    different={name for name in old.namelist() if old.read(name)!=new.read(name)}
    if different!={COMMANDS,ROUTE}:raise RuntimeError(f"Unexpected production byte changes {different}")
    if set(new.namelist())!=set(old.namelist())|NEW or len(new.namelist())!=1945:
     raise RuntimeError("Unexpected entry set")
   tmp.replace(JAR)
  finally:tmp.unlink(missing_ok=True)
 print("PASS: excavate overlay changes two existing command/router classes, adds three tracked classes")
 for n,data in modified.items():print("EXCAVATE CLASS SHA256 "+n+" "+sha(data))
 print("EXCAVATE FINAL JAR SHA256 "+sha(JAR.read_bytes()))
if __name__=="__main__":main()
