"""Verify shipped (post-overlay) class bytes, not only the source or intermediate JAR."""
import hashlib
import subprocess
from pathlib import Path
from zipfile import ZipFile

jar = Path(__file__).resolve().parents[1] / "dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
expected = {
    "com/player2/playerengine/tasks/base/Task.class":
        "164d10015b94591bd5cf4ecd833bc1212e5761f58ea628ba531c1e591c2a5637",
    "com/player2/playerengine/schematic/SchematicProjectTask.class":
        "d56fe212cec636b67af2c93dc81e4e5c07a9066ef739204dec7181ca4be69956",
}
with ZipFile(jar) as z:
    assert z.testzip() is None, "Corrupted final ZIP/JAR"
    names = z.namelist()
    assert len(names) == 1937 and len(names) == len(set(names)), "Unexpected patch scope"
    for name, digest in expected.items():
        actual = hashlib.sha256(z.read(name)).hexdigest()
        assert actual == digest, f"Packaged method drift: {name}: {actual}"
    helpers = [n for n in names if n.startswith("com/player2/playerengine/schematic/SchematicStorageReconciler")]
    assert sorted(helpers) == sorted([
        "com/player2/playerengine/schematic/SchematicStorageReconciler.class",
        "com/player2/playerengine/schematic/SchematicStorageReconciler$Snapshot.class",
        "com/player2/playerengine/schematic/SchematicStorageReconciler$State.class",
    ]), helpers
result = subprocess.run(
    ["javap", "-p", "-c", "-classpath", str(jar),
     "com.player2.playerengine.schematic.SchematicProjectTask"],
    capture_output=True, text=True, check=True,
)
assert "SchematicStorageReconciler.interceptBlock" in result.stdout
assert "SchematicStorageReconciler.recordStart" in result.stdout
print("PASS packaged v18.3 lifecycle and schematic hotfixes are byte-identical to tested class versions")
print("PASS three helper classes present, two runtime call sites verified, 1937 entries")
