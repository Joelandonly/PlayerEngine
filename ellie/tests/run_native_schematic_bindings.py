"""Test packaged native constructors with real, hash-verified Minecraft 1.21.1 classes.

Downloads are test dependencies only. No game libraries enter the Ellie artifact.
This bootstraps registries, not a world: it cannot prove navigation or placement.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
META_SHA1 = "82ba6501da174a665c1787775f946487422f2432"
MAPPINGS_SHA256 = "e8bd6a4c39e235ee2dc73063558610775c72a2572fa0c6b32915b149074012a6"
REMAPPER_SHA256 = "428ef53500d45ba6656930ca851cb6b9a3cc91a66aa904946d6a51ac2bdc7a98"


def digest(path, algorithm="sha256"):
    with path.open("rb") as source:
        return hashlib.file_digest(source, algorithm).hexdigest()


def download(url, path, expected, algorithm="sha256"):
    if path.is_file() and digest(path, algorithm) == expected:
        return path
    if not url.startswith("https://"):
        raise ValueError("Test dependencies must use HTTPS")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".download")
    try:
        with urllib.request.urlopen(url, timeout=60) as response, temporary.open("wb") as out:
            while chunk := response.read(1024 * 1024):
                out.write(chunk)
        if digest(temporary, algorithm) != expected:
            raise ValueError(f"Dependency integrity failure: {path.name}")
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
    return path


def run(*args, timeout=180):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT, timeout=timeout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=ROOT / "build/native-game-runtime")
    args = parser.parse_args()
    cache = args.cache.resolve()
    metadata = download(
        f"https://piston-meta.mojang.com/v1/packages/{META_SHA1}/1.21.1.json",
        cache / "1.21.1.json", META_SHA1, "sha1")
    info = json.loads(metadata.read_text())
    assert info["id"] == "1.21.1"
    client_info = info["downloads"]["client"]
    client = download(client_info["url"], cache / "client.jar", client_info["sha1"], "sha1")
    mappings = download(
        "https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1-v2.jar",
        cache / "intermediary-1.21.1-v2.jar", MAPPINGS_SHA256)
    remapper = download(
        "https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.11.1/tiny-remapper-0.11.1-fat.jar",
        cache / "tiny-remapper-0.11.1-fat.jar", REMAPPER_SHA256)
    # Registry bootstrap needs Java artifacts, not platform-specific native classifiers.
    artifacts = [lib["downloads"]["artifact"] for lib in info["libraries"]
                 if len(lib["name"].split(":")) == 3 and "artifact" in lib.get("downloads", {})]

    def library(artifact):
        return download(artifact["url"], cache / "libraries" / artifact["path"], artifact["sha1"], "sha1")

    with ThreadPoolExecutor(max_workers=4) as executor:
        libraries = list(executor.map(library, artifacts))
    remapped = cache / "minecraft-intermediary.jar"
    receipt = cache / "remap-receipt.json"
    inputs = {"metadata_sha1": META_SHA1, "client_sha1": client_info["sha1"],
              "mappings_sha256": MAPPINGS_SHA256, "remapper_sha256": REMAPPER_SHA256}
    valid = False
    if receipt.is_file() and remapped.is_file():
        saved = json.loads(receipt.read_text())
        valid = saved.get("inputs") == inputs and saved.get("output_sha256") == digest(remapped)
    if not valid:
        with ZipFile(mappings) as jar:
            (cache / "intermediary.tiny").write_bytes(jar.read("mappings/mappings.tiny"))
        remapped.unlink(missing_ok=True)
        run("java", "-Xmx1G", "-jar", remapper, client, remapped,
            cache / "intermediary.tiny", "official", "intermediary", *libraries)
        receipt.write_text(json.dumps({"inputs": inputs, "output_sha256": digest(remapped)}, indent=2) + "\n")
    packaged = ROOT / "dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar"
    classes = ROOT / "build/native-bindings-testclasses"
    classes.mkdir(parents=True, exist_ok=True)
    run("javac", "--release", "21", "-cp", packaged, "-d", classes,
        ROOT / "tests/NativeSchematicBindingsTest.java")
    print(f"Testing packaged native actions with Minecraft 1.21.1 and {len(libraries)} pinned Java libraries", flush=True)
    run("java", "-cp", os.pathsep.join(map(str, [classes, packaged, remapped, *libraries])),
        "NativeSchematicBindingsTest")
    # New command must link to actual 1.21.1 intermediary Baritone/world
    # signatures, not just a stub. No NPC/world execution is simulated.
    run("javac", "--release", "21", "-cp", packaged, "-d", classes,
        ROOT / "tests/NativeExcavateBindingsTest.java")
    run("java", "-Xverify:all", "-cp",
        os.pathsep.join(map(str, [classes, packaged, remapped, *libraries])),
        "NativeExcavateBindingsTest")


if __name__ == "__main__":
    main()
