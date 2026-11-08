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
rm -rf build/schematic-testclasses build/schematic-game-testclasses build/schematic-fixtures build/classes build/patches build/tools build/testclasses build/native-testclasses build/shared-testclasses build/table-testclasses build/distance-testclasses build/game-hooks build/inventory-testclasses build/survival-testclasses build/material-testclasses build/legacy-testclasses build/descent-testclasses build/water-testclasses build/food-testclasses build/command-testclasses
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
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchAutonomy.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchAutonomy baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchReliability.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchReliability baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchCraftConvergence.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchCraftConvergence baseline/playerengine-acquisition-safety.jar build/patches
# Repair existing food/cooking workers and bind command completion to their exact executor receipt.
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchFoodReliability.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchFoodReliability baseline/playerengine-acquisition-safety.jar build/patches
# Independent server heartbeat / real entity age / chunk ticket diagnostics (read-only).
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchDistanceDiagnostics.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchDistanceDiagnostics baseline/playerengine-acquisition-safety.jar build/patches
# In the packaged ConversationManager only, bypass proximity for named authenticated owner commands.
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchRemoteOwnerCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchRemoteOwnerCommands baseline/playerengine-acquisition-safety.jar build/patches
# Repair the upstream false-full inventory check at its common source.
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchInventoryTracker.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchInventoryTracker baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchSafeDescents.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchSafeDescents baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchWaterEscapeGoal.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchWaterEscapeGoal baseline/playerengine-acquisition-safety.jar build/patches
# Shared partial-height collision support; keep native bridging and setup intact.
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchPartialBlockNavigation.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchPartialBlockNavigation baseline/playerengine-acquisition-safety.jar build/patches
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED -d build/tools tools/VerifyASM.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED -cp build/tools VerifyASM $(find build/classes build/patches -name '*.class' | LC_ALL=C sort)
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses $(find tests/fixtures -name '*.java' | LC_ALL=C sort) tests/SupervisorTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SupervisorTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/PatchSchematicProjects.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools PatchSchematicProjects baseline/playerengine-acquisition-safety.jar build/patches
python3 tools/package.py
python3 tests/verify_player2_integrity.py
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -d build/tools tools/AuditTaskRouting.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp build/tools AuditTaskRouting dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar build/reports/task-routing.tsv
python3 tests/verify_task_routing.py
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/AuditCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools AuditCommands dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar build/reports/commands.tsv
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/CommandDispatchTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar CommandDispatchTest build/reports/commands.tsv
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/StarterBaseTest.java tests/SetupCompletionTest.java tests/SetupProgressTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar StarterBaseTest
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SurfaceLightingTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SurfaceLightingTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupCompletionTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupProgressTest
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupSiteRegressionTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupSiteRegressionTest
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupTerraformTest.java tests/SetupBuildZoneTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupTerraformTest
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupBuildZoneTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifySetupStartup.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifySetupStartup dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

# Isolated regression: avoid far-above workstations, recover from navigation stalls,
# and retain alternatives without destroying blocks or altering Player2.
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/fixtures/com/player2/playerengine/tasks/crafting/CraftingTableLocator.java tests/CraftTableRecoveryTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar CraftTableRecoveryTest

javac --release 21 -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/NativeCommandTextTest.java tests/NativePlannerTest.java tests/TargetRecoveryPolicyTest.java
java -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar NativeCommandTextTest
java -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar NativePlannerTest
javac --release 21 -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/AutonomyIntegrationTest.java
java -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar AutonomyIntegrationTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyAutonomy.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyAutonomy dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyNativeCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyNativeCommands dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyRemoteOwnerCommands.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyRemoteOwnerCommands dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

mkdir -p build/native-testclasses
# The packaged controller hook references real Minecraft classes; the native fixtures have
# their own PlayerEngineController stub. Keep the verified packaged class untouched.
mkdir -p build/game-hooks/com/player2/playerengine
cp build/patches/com/player2/playerengine/PlayerEngineController.class build/game-hooks/com/player2/playerengine/PlayerEngineController.class
rm build/patches/com/player2/playerengine/PlayerEngineController.class
javac --release 21 -cp build/patches:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/native-testclasses $(find tests/nativefixtures -name '*.java' | LC_ALL=C sort) tests/NativeCommandExecutionTest.java tests/ProductionAndNpcUseTest.java
java -cp build/native-testclasses:build/patches:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar NativeCommandExecutionTest
javac --release 21 -cp build/testclasses:build/classes:build/native-testclasses:baseline/playerengine-acquisition-safety.jar -d build/native-testclasses tests/RemoteOwnerCommandGateTest.java
java -cp build/testclasses:build/classes:build/native-testclasses:baseline/playerengine-acquisition-safety.jar RemoteOwnerCommandGateTest
java -cp build/native-testclasses:build/patches:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar ProductionAndNpcUseTest
java -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar TargetRecoveryPolicyTest

mkdir -p build/shared-testclasses
javac --release 21 -cp build/patches:build/native-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/shared-testclasses $(find tests/sharedfixtures -name '*.java' | LC_ALL=C sort) tests/SharedRecoveryRuntimeTest.java tests/WorkshopPlacementTest.java
java -cp build/shared-testclasses:build/patches:build/native-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar com.player2.playerengine.reliability.SharedRecoveryRuntimeTest
java -cp build/shared-testclasses:build/patches:build/native-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar com.player2.playerengine.reliability.WorkshopPlacementTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyRecovery.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyRecovery dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

# Execute the packaged table item/tool decisions; isolate game boundary fixtures.
mkdir -p build/table-testclasses
javac --release 21 -cp build/native-testclasses:build/testclasses:baseline/playerengine-acquisition-safety.jar -d build/table-testclasses $(find tests/tablefixtures -name '*.java' | LC_ALL=C sort)
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/table-testclasses:build/native-testclasses:build/testclasses:baseline/playerengine-acquisition-safety.jar -d build/tools tools/VerifyTableOutput.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools:build/table-testclasses:build/native-testclasses:build/testclasses:baseline/playerengine-acquisition-safety.jar VerifyTableOutput dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

javac --release 21 -cp build/testclasses:build/classes:build/native-testclasses:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupNativeAcquisitionTest.java
java -cp build/testclasses:build/classes:build/native-testclasses:baseline/playerengine-acquisition-safety.jar SetupNativeAcquisitionTest

# Deterministic village regression: real structure parser, footprint and protected source boundaries.
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/CompactVillageBlueprintTest.java
java -cp build/native-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar CompactVillageBlueprintTest

# Independent, read-only server-clock diagnostics regression and packaged hook audit.
mkdir -p build/distance-testclasses
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/distance-testclasses tests/DistanceDiagnosticsTest.java
java -cp build/distance-testclasses:build/classes:baseline/playerengine-acquisition-safety.jar DistanceDiagnosticsTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyDistanceDiagnostics.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyDistanceDiagnostics dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

# Fresh inventory, agentic quote, early axe, and chunk watchdog regression.
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/distance-testclasses tests/InventorySpaceTest.java $(find tests/chunkfixtures -name '*.java') tests/ChunkTicketWatchdogTest.java
java -cp build/distance-testclasses:build/classes:baseline/playerengine-acquisition-safety.jar InventorySpaceTest
java -cp build/distance-testclasses:build/patches:build/classes:baseline/playerengine-acquisition-safety.jar ChunkTicketWatchdogTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyInventoryTracker.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyInventoryTracker dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

# Alternating-target history regression.
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/distance-testclasses tests/TargetOscillationGuardTest.java
java -cp build/distance-testclasses:build/classes:baseline/playerengine-acquisition-safety.jar TargetOscillationGuardTest

# Full setup orchestration, including workshop and light verification, with explicit game boundaries.
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupChainTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupChainTest

cp build/game-hooks/com/player2/playerengine/PlayerEngineController.class build/patches/com/player2/playerengine/PlayerEngineController.class
python3 tests/verify_build_revision.py

# Execute the packaged canonical inventory tracker, not merely its hook audit.
mkdir -p build/inventory-testclasses
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/inventory-testclasses tests/PackagedInventorySlotTest.java
java -cp build/inventory-testclasses:build/testclasses:build/native-testclasses:build/patches:build/classes:baseline/playerengine-acquisition-safety.jar PackagedInventorySlotTest

# Retained historical bridge is tested separately and never added to packaged class outputs.
mkdir -p build/legacy-testclasses
javac --release 21 -d build/legacy-testclasses legacy-chatgpt/src/com/player2/playerengine/interpreter/LocalChatGPTBridge.java tests/BridgeConfigTest.java
java -cp build/legacy-testclasses BridgeConfigTest

# Production setup entry plus survival assessment, food/fuel, cover and final verification.
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupShelterTest.java tests/SurvivalSetupTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupShelterTest
mkdir -p build/survival-testclasses
javac --release 21 -d build/survival-testclasses tests/survivalfixtures/com/player2/playerengine/tasks/deferred/DeferredJobStore.java
java -cp build/survival-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SurvivalSetupTest

javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/SetupStorageScopeTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SetupStorageScopeTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifySurvivalSetup.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifySurvivalSetup dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

# Natural terrain can be prepared; platforms use actual mixed full-cube supplies.
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/TerraformPlatformTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar TerraformPlatformTest

mkdir -p build/material-testclasses
javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/material-testclasses tests/materialfixtures/net/minecraft/class_2346.java tests/StructuralMaterialInventoryTest.java
java -cp build/material-testclasses:build/native-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar StructuralMaterialInventoryTest

javac --release 21 -cp build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/ShelterCheckpointTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar com.player2.playerengine.tasks.setup.ShelterCheckpointTest

javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/WorkshopOutputHandoffTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar WorkshopOutputHandoffTest

# New owner-facing basic setup, including batched material bills and native HOME workstation contract.
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/testclasses tests/BasicSetupTaskTest.java
java -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar BasicSetupTaskTest

# Shared pre-commit descent guard: deterministic terrain, packaged gate and bridge-body preservation.
mkdir -p build/descent-testclasses
javac --release 21 -cp build/testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar -d build/descent-testclasses tests/nativefixtures/net/minecraft/class_2338.java tests/descentfixtures/net/minecraft/class_2350.java tests/SafeDescentTest.java tests/SafeDescentThreadTest.java
java -cp build/descent-testclasses:build/testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar ellie.descent.SafeDescentTest
java -cp build/descent-testclasses:build/testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar ellie.descent.SafeDescentThreadTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/descent-testclasses:build/testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar -d build/tools tools/NativeMethodShape.java tools/VerifySafeDescents.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools:build/descent-testclasses:build/testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar ellie.descent.VerifySafeDescents dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar baseline/playerengine-acquisition-safety.jar
python3 tests/verify_descent_scope.py

# Execute the actual packaged water escape goal against isolated non-loading world/cache boundaries.
mkdir -p build/water-testclasses
javac --release 21 -cp dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar -d build/water-testclasses $(find tests/waterfixtures -name '*.java' | LC_ALL=C sort) tests/WaterEscapeGoalTest.java
java -cp build/water-testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar WaterEscapeGoalTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyWaterEscapeGoal.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyWaterEscapeGoal dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar baseline/playerengine-acquisition-safety.jar
javac --release 21 -cp dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar -d build/distance-testclasses tests/ServerStallCaptureTest.java
java -cp build/distance-testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar com.player2.playerengine.observatory.ServerStallCaptureTest

# Execute the packaged food/cooking workers across full success and failure chains.
mkdir -p build/food-testclasses build/command-testclasses
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED -d build/food-testclasses tests/PackagedFoodLoopTest.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED -cp build/food-testclasses PackagedFoodLoopTest dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar tests/food-game-symbols.properties
javac --release 21 -cp build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar -d build/command-testclasses tests/CommandCompletionTest.java
java -cp build/command-testclasses:build/testclasses:build/classes:build/patches:baseline/playerengine-acquisition-safety.jar CommandCompletionTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/command-testclasses tests/PackagedCompletionMapTest.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/command-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar PackagedCompletionMapTest dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/VerifyFoodReliability.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyFoodReliability dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar

# Actual packaged shared geometry boundaries, using a pinned vanilla collision snapshot.
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED -cp build/classes -d build/testclasses tests/PartialBlockNavigationTest.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED -cp build/testclasses:build/classes PartialBlockNavigationTest dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar tests/partial-game-symbols.properties tests/partial-block-shapes-1.21.1.tsv
python3 tests/verify_partial_navigation_scope.py
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/NativeMethodShape.java tools/VerifyPartialBlockNavigation.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyPartialBlockNavigation dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar baseline/playerengine-acquisition-safety.jar

# Execute the project coordinator, real bundled file parser, and immutable native layer adapter.
mkdir -p build/schematic-testclasses build/schematic-game-testclasses build/schematic-fixtures
python3 tests/make_schematic_fixtures.py build/schematic-fixtures
python3 - <<'PY'
from zipfile import ZipFile
from pathlib import Path
with ZipFile('baseline/playerengine-acquisition-safety.jar') as z:
    Path('build/schematic4j.jar').write_bytes(z.read('META-INF/jars/schematic4j-1.1.0.jar'))
PY
javac --release 21 -cp build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/schematic-testclasses $(find tests/schematicfixtures -name '*.java' | LC_ALL=C sort) tests/SchematicProjectTest.java tests/SchematicFilesTest.java
java -cp build/schematic-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SchematicProjectTest
java -cp build/schematic-testclasses:build/classes:build/schematic4j.jar SchematicFilesTest build/schematic-fixtures
javac --release 21 -cp build/schematic-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar -d build/schematic-game-testclasses $(find tests/schematicgamefixtures -name '*.java' | LC_ALL=C sort) tests/nativefixtures/net/minecraft/class_2338.java tests/SchematicBuildSupportTest.java tests/SchematicWorldSlotsTest.java tests/SchematicPreparationClearanceTest.java
java -cp build/schematic-game-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SchematicBuildSupportTest
java -cp build/schematic-game-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SchematicWorldSlotsTest
java -cp build/schematic-game-testclasses:build/schematic-testclasses:build/testclasses:build/classes:baseline/playerengine-acquisition-safety.jar SchematicPreparationClearanceTest
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools -d build/tools tools/VerifySchematicProjects.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifySchematicProjects dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar baseline/playerengine-acquisition-safety.jar
python3 tests/verify_schematic_scope.py

python3 tests/verify_schematic_prepare_scope.py

# Shared reflection boundary: primitive flags, reference/null handling and native failure propagation.
mkdir -p build/native-constructor-testclasses
javac --release 21 -cp dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar -d build/native-constructor-testclasses tests/NativeConstructorTest.java
java -cp build/native-constructor-testclasses:dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar NativeConstructorTest
python3 tests/verify_schematic_native_bindings_scope.py

mkdir -p build/macro-convergence-testclasses
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED -cp build/testclasses:baseline/playerengine-acquisition-safety.jar -d build/macro-convergence-testclasses tests/MacroConvergenceTest.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED -cp build/macro-convergence-testclasses:build/testclasses:baseline/playerengine-acquisition-safety.jar MacroConvergenceTest dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -d build/tools tools/NativeMethodShape.java tools/VerifyCraftConvergence.java
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED -cp build/tools VerifyCraftConvergence dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar tests/craft-convergence-native-methods.properties
python3 tests/verify_schematic_acquisition_scope.py

# v18.3 LIVE HOTFIX SYNCHRONIZATION: the historical v18.3 checks above intentionally
# verify the unmodified build. Only now overlay the reviewed lifecycle and chest
# reconciliation bytecode; this final JAR is what GitHub Actions must publish.
python3 tools/apply_v18_hotfix.py
mkdir -p build/storage-recovery-testclasses
javac --release 21 -cp dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar -d build/storage-recovery-testclasses tests/StorageRecoveryRegression.java
java -Xverify:all -cp dist/PlayerEngine-Fabric-1.21.1-1.4.0-ELLIE-PLAYER2-SHARED-RECOVERY.jar:build/storage-recovery-testclasses StorageRecoveryRegression
python3 tests/verify_v18_hotfix_packaging.py
