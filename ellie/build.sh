#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
python3 - <<'PY'
import hashlib, json
from pathlib import Path
manifest = json.loads(Path('RELEASE_MANIFEST.json').read_text())
baseline = Path('baseline/playerengine-acquisition-safety.jar')
if hashlib.sha256(baseline.read_bytes()).hexdigest() != manifest['baseline_sha256']:
    raise SystemExit('Baseline checksum mismatch; refusing to patch an unknown JAR')
PY
rm -rf build/classes build/patches build/tools build/testclasses
mkdir -p build/{classes,patches,tools,testclasses} dist
javac --release 21 -cp baseline/playerengine-acquisition-safety.jar -d build/classes $(find src -name '*.java' | LC_ALL=C sort)
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchInterpreter.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchInterpreter baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchTaskRouting.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchTaskRouting baseline/playerengine-acquisition-safety.jar build/patches tools/supervised-commands.txt
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchCraftApproach.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchCraftApproach baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED -d build/tools tools/VerifyASM.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED -cp build/tools VerifyASM $(find build/classes build/patches -name '*.class' | LC_ALL=C sort)
javac --release 21 -cp build/classes -d build/testclasses tests/BridgeConfigTest.java
java -cp build/classes:build/testclasses BridgeConfigTest
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses $(find tests/fixtures -name '*.java' | LC_ALL=C sort) tests/SupervisorTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SupervisorTest
node --test tests/*.test.mjs
python3 tools/package.py
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -d build/tools tools/AuditTaskRouting.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp build/tools AuditTaskRouting dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-STARTER-BASE-PREVIEW.jar build/reports/task-routing.tsv
python3 tests/verify_task_routing.py
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/AuditCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools AuditCommands dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-STARTER-BASE-PREVIEW.jar build/reports/commands.tsv
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/CommandDispatchTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar CommandDispatchTest build/reports/commands.tsv
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/StarterBaseTest.java tests/SetupCompletionTest.java tests/SetupProgressTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar StarterBaseTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupCompletionTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupProgressTest
