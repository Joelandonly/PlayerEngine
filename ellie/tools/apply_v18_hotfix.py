#!/usr/bin/env python3
"""Rebuild the two reviewed v18.3 binary hotfixes as part of the normal Ellie release.

Run AFTER all historical scope checks. The current project is an overlay of a
pinned binary, not a matching full-source engine. This step ensures future JARs
actually include both the tested lifecycle and chest-state changes.
"""
from __future__ import annotations

import hashlib
import subprocess
import tempfile
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
TASK = "com/player2/playerengine/tasks/base/Task.class"
PROJECT = "com/player2/playerengine/schematic/SchematicProjectTask.class"
HELPER = "com/player2/playerengine/schematic/SchematicStorageReconciler"
EXPECTED_V18_3 = {
    TASK: "c55c7dd863bd50b9726d4f664f2dd88d299b0207523465f6107ebbfbaa82ff38",
    PROJECT: "29c08c659f6aff6cea6ca57b91f5b99ec6c1c2f0f60e5507432ead0b73b48691",
}
EXPECTED_STOP_PATCH = "164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637"
EXPORTS = [
    "--add-exports", "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
    "--add-exports", "java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
]


def run(*args: str):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main():
    with ZipFile(JAR) as src:
        names = src.namelist()
        assert len(names) == len(set(names)), "Existing JAR contains duplicate entries"
        assert len(names) == 1934, f"Unexpected v18.3 entry count {len(names)}"
        for name, expected in EXPECTED_V18_3.items():
            found = sha(src.read(name))
            if found != expected:
                raise RuntimeError(f"Refusing to apply hotfix to unknown {name}: {found}")

    with tempfile.TemporaryDirectory(prefix="ellie-v18.3-hotfix-") as directory:
        temp = Path(directory)
        tools = temp / "tools"
        tools.mkdir()
        run("javac", *EXPORTS, "-d", tools,
            ROOT / "tools/StopLifecyclePatch.java",
            ROOT / "tools/StorageRecoveryBytecodePatch.java")
        classes = temp / "compiled"
        classes.mkdir()
        run("javac", "--release", "21", "-d", classes,
            ROOT / "tools/hotfix-src/com/player2/playerengine/schematic/SchematicStorageReconciler.java")

        with ZipFile(JAR) as source:
            for name in [TASK, PROJECT]:
                location = temp / name
                location.parent.mkdir(parents=True, exist_ok=True)
                location.write_bytes(source.read(name))
        out_task = temp / "patched-task.class"
        out_project = temp / "patched-schematic.class"
        run("java", *EXPORTS, "-cp", tools, "StopLifecyclePatch", temp / TASK, out_task)
        run("java", *EXPORTS, "-cp", tools, "StorageRecoveryBytecodePatch", temp / PROJECT, out_project)
        modified = {TASK: out_task.read_bytes(), PROJECT: out_project.read_bytes()}
        if sha(modified[TASK]) != EXPECTED_STOP_PATCH:
            raise RuntimeError("Packaged Task.class does not match the previously validated stop-loop fix")
        new = {
            name.relative_to(classes).as_posix(): name.read_bytes()
            for name in classes.rglob("*.class")
        }
        wanted_new = {HELPER + suffix for suffix in [".class", "$State.class", "$Snapshot.class"]}
        if set(new) != wanted_new:
            raise RuntimeError(f"Unreviewed helper classes: {set(new) ^ wanted_new}")
        with ZipFile(JAR) as original:
            if wanted_new & set(original.namelist()):
                raise RuntimeError("Hotfix helper already present in original archive")
            temporary = JAR.with_suffix(".hotfix-temp.jar")
            try:
                with ZipFile(temporary, "w") as target:
                    for entry in original.infolist():
                        target.writestr(entry, modified.get(entry.filename, original.read(entry)))
                    for name in sorted(new):
                        target.writestr(name, new[name])
                with ZipFile(temporary) as patched:
                    assert patched.testzip() is None
                    assert set(patched.namelist()) == set(original.namelist()) | wanted_new
                    changed = {n for n in original.namelist() if original.read(n) != patched.read(n)}
                    if changed != {TASK, PROJECT}:
                        raise RuntimeError(f"Unexpected binary changes: {changed}")
                temporary.replace(JAR)
            finally:
                temporary.unlink(missing_ok=True)
        print("PASS: v18.3 stop+storage hotfix integrated; exactly two existing class entries changed; three helper classes added")
        print("PASS: 14-stage setup, Player2 and all other 1,932 original entries byte-identical")
        print("Final patched JAR SHA256: " + sha(JAR.read_bytes()))


if __name__ == "__main__":
    main()
