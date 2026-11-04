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
rm -rf build/classes build/patches build/tools build/testclasses build/native-testclasses
mkdir -p build/{classes,patches,tools,testclasses} dist
javac --release 21 -cp baseline/playerengine-acquisition-safety.jar -d build/classes $(find src -name '*.java' | LC_ALL=C sort)
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchNativeCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchNativeCommands baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchTaskRouting.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchTaskRouting baseline/playerengine-acquisition-safety.jar build/patches tools/supervised-commands.txt build/classes
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchCraftApproach.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchCraftApproach baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchCraftTableRecovery.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchCraftTableRecovery baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED -d build/tools tools/VerifyASM.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED -cp build/tools VerifyASM $(find build/classes build/patches -name '*.class' | LC_ALL=C sort)
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses $(find tests/fixtures -name '*.java' | LC_ALL=C sort) tests/SupervisorTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SupervisorTest
python3 tools/package.py
python3 tests/verify_player2_integrity.py
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -d build/tools tools/AuditTaskRouting.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp build/tools AuditTaskRouting dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-NATIVE-COMMANDS.jar build/reports/task-routing.tsv
python3 tests/verify_task_routing.py
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/AuditCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools AuditCommands dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-NATIVE-COMMANDS.jar build/reports/commands.tsv
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/CommandDispatchTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar CommandDispatchTest build/reports/commands.tsv
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/StarterBaseTest.java tests/SetupCompletionTest.java tests/SetupProgressTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar StarterBaseTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupCompletionTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupProgressTest
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupSiteRegressionTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupSiteRegressionTest
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupTerraformTest.java tests/SetupBuildZoneTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupTerraformTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupBuildZoneTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifySetupStartup.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifySetupStartup dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-NATIVE-COMMANDS.jar

# Isolated regression: avoid far-above workstations, recover from navigation stalls,
# and retain alternatives without destroying blocks or altering Player2.
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/fixtures/com/player2/playerengine/tasks/crafting/CraftingTableLocator.java tests/CraftTableRecoveryTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar CraftTableRecoveryTest

javac --release 21 -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/NativeCommandTextTest.java tests/NativePlannerTest.java
java -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar NativeCommandTextTest
java -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar NativePlannerTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyNativeCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyNativeCommands dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-NATIVE-COMMANDS.jar

mkdir -p build/native-testclasses
javac --release 21 -cp build/patches:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/native-testclasses $(find tests/nativefixtures -name '*.java' | LC_ALL=C sort) tests/NativeCommandExecutionTest.java
java -cp build/native-testclasses:build/patches:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar NativeCommandExecutionTest
