# Warehouse implementation validation — November 12, 2026

Status: buildable local candidate; Minecraft gameplay acceptance remains open.

Baseline verified directly against GitHub:
`feature/ellie-waterfill-access-and-reach-20261111` at
`5c58ea10e288cb6e3e67079cfbc3d1332b6f9bab`.
Development branch: `feature/ellie-storage-warehouse-20261112`.
GitHub status: **LOCAL ONLY**. No new commit, push, merge or environment publication.
All storage implementation changes in this delivery have NOT been uploaded.

Launcher: `Ellie_Storage_Warehouse_Apply_Build_Verify_20261112.cmd`.

## Implemented

Shared inventory pressure gate, safe pause and bounded unload/retry while retaining
resource goals; live multi-container capacity and component-compatible stacking;
separate authorized warehouse registry with recorded double-chest halves; atomic
world/companion metadata; dimension checks; task/material reservations; deterministic
raised-floor four-double-chest modules; explicit four-chest expansion up to 32;
Safe Zone/project/terrain/access preflight; bounded acquisition and placement;
construction receipts and explicit resume; matching-stack consolidation; category
labels; bounded telemetry; compatibility with separately registered legacy cleanup
storage; deposit outcome accounting and stale open-container identity checks.

Automatic sign placement/editing is not implemented. Optional signs use the
existing owner `place_sign` command and categories shown by `ellie_storage list`.
Organization consolidates matching items; it does not perform a complete category
sort. Unloaded, too-distant, protected or inaccessible storage is not assumed usable.
Items outside the conservative surplus policy can require owner intervention.

## Executed validation

- Actual Java 21 / Gradle 8.14 `:fabric:build`: **PASS**, including source compilation,
  Architectury transformation, shadow packaging and remapped Fabric JAR production.
  Build completed with 23 deprecation warnings and no compilation errors.
- Actual implementation compiled against deterministic in-memory server fixtures:
  **42 checks PASS**. The real recovery, layout, ledger, manager, builder, organizer,
  reservations and bounded transfer classes are exercised.
- PowerShell parsing: all four `.ps1` scripts pass parsing under PowerShell 7.4.6.
- Delivery scripts executed under PowerShell on Linux: baseline worktree patching,
  repeated application, source hash verification, JAR verification and logic test
  compilation/execution. See `reports/delivery-tests.log`.
- `git diff --check`: **PASS**.

No Minecraft client/server gameplay occurred. In particular, successful fixtures
and compilation do not establish real NPC navigation, physical placement, recipe
execution, owner-chat UI behavior, recovery across a running Minecraft restart,
or compatibility with every installed mod. The full Windows `.cmd` launcher has
not been run on Windows; its platform-independent PowerShell verification paths
have run here. Windows native folder selection, known-folder lookup and installed
Gradle/JDK discovery remain Windows acceptance steps.

## Requested scenarios and evidence

| Scenario | Result and limit |
|---|---|
| 36/36 inventory | Fixture passes: unload and retain original resource goal |
| 33/36 inventory | Fixture passes: proactive recovery and four free slots |
| Replacement pickaxe with full inventory | Fixture passes recovery before a pickaxe resource request; actual recipe execution unverified |
| Unload then resume mining | Resource-parent resumption passes fixture; real mining/navigation unverified |
| First chest full / second available | Live allocator fixture passes |
| All warehouse chests full | Fixture safely pauses, retains items and reports reason |
| Four-to-eight doubles | Planner and builder fixtures pass; existing stored items retained |
| Connected paths / access | Layout flood-fill and builder fixtures pass; real walking/placement unverified |
| Restart persistence | Atomic file reload passes; actual Minecraft restart unverified |
| Safe Zones / protected containers | Builder rejection and double-half protection fixtures pass |
| Cancellation / interrupted construction | Transfer/organizer cancellation and builder receipt/resume fixtures pass |
| Missing wood / failed crafting / unreachable resources | No-progress acquisition and unsupported construction fixtures pass; real crafting/resource navigation unverified |
| Dimension mismatch | Fixture blocks travel/deposit |
| Project supplies / task reservations | Fixtures retain project materials, active reservations and gear |
| Container replaced while open | Fixture fails transfer without item loss |

## Outputs

Cloud build: `ellie/fabric-1.21.1-recovered/fabric/build/libs/playerengine-recovered-fabric-1.21.1-1.4.0-recovered-WIP.jar`.
Delivery copy: `candidate/Ellie_Storage_Warehouse_1.21.1_CANDIDATE.jar` inside ZIP.
Windows launcher output: `Downloads/Ellie_Storage_Warehouse_<timestamp>/Ellie_Storage_Warehouse_1.21.1_CANDIDATE.jar`.

Reports and source/JAR verification are included in the ZIP. Test this candidate
in a disposable matching Minecraft 1.21.1 Fabric world before using an important save.

## Changed files

The exact before/after SHA-256 hashes and full source files are in `manifest.json`
and `payload/`; `warehouse.patch` is a reviewable Git patch. Changed files:

- `ellie/fabric-1.21.1-recovered/WAREHOUSE.md`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/PlayerEngineCommands.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/agentic/AutoInventoryCleanup.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/agentic/InventorySpace.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/agentic/MaterialReservationService.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/agentic/StorageIntelligence.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/commands/EllieStorageCommand.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/commands/base/CommandExecutor.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/nativecommands/NativeCommandText.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/player2api/SetupDirectRoute.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/tasks/ResourceTask.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/tasks/agentic/GatherLooseItemsTask.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/tasks/base/Task.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/tasks/construction/ExcavateAreaTask.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/tasks/container/BoundedContainerTransferTask.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/tasks/movement/PickupDroppedItemTask.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/warehouse/WarehouseBuildTask.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/warehouse/WarehouseLayout.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/warehouse/WarehouseLedger.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/warehouse/WarehouseManager.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/warehouse/WarehouseOrganizeTask.java`
- `ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine/warehouse/WarehousePolicy.java`
- `ellie/fabric-1.21.1-recovered/tools/warehouse-delivery/Apply-WarehousePatch.ps1`
- `ellie/fabric-1.21.1-recovered/tools/warehouse-delivery/Ellie_Storage_Warehouse_Apply_Build_Verify_20261112.cmd`
- `ellie/fabric-1.21.1-recovered/tools/warehouse-delivery/Ellie_Storage_Warehouse_Apply_Build_Verify_20261112.ps1`
- `ellie/fabric-1.21.1-recovered/tools/warehouse-delivery/Verify-Logic.ps1`
- `ellie/fabric-1.21.1-recovered/tools/warehouse-delivery/Verify-Warehouse.ps1`
- `ellie/fabric-1.21.1-recovered/tools/warehouse-delivery/package.py`
- `ellie/fabric-1.21.1-recovered/tools/warehouse-tests/run.py`
