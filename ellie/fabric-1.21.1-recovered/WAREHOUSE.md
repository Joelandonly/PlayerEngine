# Ellie warehouse candidate — Minecraft 1.21.1 Fabric

Baseline: `5c58ea10e288cb6e3e67079cfbc3d1332b6f9bab`,
`feature/ellie-waterfill-access-and-reach-20261111`.
Development branch: `feature/ellie-storage-warehouse-recovery`.

This implementation requires a successful clean Fabric build and disposable-world
acceptance before installation in an important world. See the delivery's validation
report for what actually ran. Fixture tests are not Minecraft gameplay tests.

## Commands

Send exact owner commands through Ellie's normal authenticated chat route:

- `ellie_storage init here`: authorize an initial four-double-chest warehouse at
  Ellie's feet. The site is checked before it is saved. You can choose another
  location with the same command until site work or registration starts.
- `ellie_storage resume`: retry the authorized plan after fixing supplies/access
  or after cancellation/restart. Actual blocks and protection are checked again.
  After a journal write fault, `resume` reloads the last committed receipts.
- `ellie_storage expand [count]`: add the requested number of double chests in
  four-chest modules after the current warehouse is READY. The default adds
  four. For example, `expand 8` adds eight double chests across two modules.
  Maximum 32 doubles total.
- `ellie_storage status`: live verified capacity, occupied/free slots, requested
  capacity, dimension, construction state and reserve threshold. Unverified or
  unloaded containers do not count as usable capacity.
- `ellie_storage list`: live stock and category labels for verified containers.
- `ellie_storage organize`: bounded consolidation into earlier matching chests;
  at most 64 stack transfers per pass. Withdrawn items survive cancellation.
- `ellie_storage off` / `ellie_storage on`: disable/re-enable warehouse use;
  no chests, contents or legacy registrations are deleted.
- `ellie_storage reserve 4`: set the extra room Ellie aims to free on a storage
  trip, between one and the size of her main inventory. She starts a trip when
  the active task needs more output slots than are free, with the trigger capped
  at the configured reserve for long jobs.
- `ellie_storage register x y z`: explicitly authorize an existing double chest
  occupying an exact planned pair. Both halves must be accessible and unprotected.
- `ellie_storage approve_floor`: explicitly approve existing cobblestone floor
  tiles in the planned footprint. Does not approve containers or change blocks.

Construction, organization and registration require no unrelated running/suspended
supervisor goal; stop that goal explicitly if you want to replace it. Status and
list work while construction runs. Warehouse mutations require authenticated owner
routing, so a model-selected command cannot grant itself unlimited construction.

## Layout and terrain

Each module is eight blocks east-west by seven north-south. Four double chests
occupy x=1..2 and x=5..6 at z=1 and z=5. Chest bases are one block above the
origin; the module lays a raised cobblestone floor at origin Y. A three-wide central
aisle, side aisles and end crosswalks connect all pairs. Expansion extends south
by seven blocks without moving existing chests. The west edge at the origin is
an entrance with a one-block ascent from existing ground.

Choose dry ground with an accessible west entrance. Ellie can clear survival-breakable
blocks in the warehouse footprint, including player-placed blocks. Safe Zones
block clearance and construction. Fluids, unbreakable blocks and unsupported
foundation gaps still prevent a build. Each support column can fill up to 12
blocks below the raised floor.

Ellie uses the shared native acquisition planner to prepare missing tools and
gather materials. Each Overworld module uses 56 cobblestone for its floor, extra
cobblestone for support gaps, and eight single-chest items. Nether bunkers can
use blackstone and nether brick blocks in the same build.
The village's mining exclusion protects the footprint during construction.
Ellie automatically continues after brief gear or gesture interruptions. After
a deliberate stop or restart, `resume` revalidates the recorded site.

Existing chests are never silently adopted. To reuse an existing matching layout,
explicitly approve its cobblestone floor, register each planned double chest, then
resume. A changed chest footprint, lid obstruction or protected half invalidates
allocation. Actual navigation is bounded by the existing 115-block/45-second
container transfer limits; inaccessible candidates are excluded for the current
recovery pass and another candidate is tried.

## Inventory recovery and authorization

Resource tasks (including CraftMacroResourceTask), excavation, dropped-item pickup
and loose-item gathering check main inventory capacity before production. The
cleanup manager uses remaining task output and compatible partial stacks to
estimate the number of new slots needed. Producers with no declared output need
room for the next stack. On a storage trip Ellie frees the required room plus the
configured reserve, as far as available storage and eligible surplus permit.
After verified transfers the original task resumes. Loose-item gathering excludes
storage time and unloaded items from its collection accounting.

Automatic unloading can store ordinary surplus blocks, common drops, excess
sticks, and unequipped lower-tier tools when a more durable tool of the same kind
is available. Food, modified stacks, designated important items, protected gear,
active recipe ingredients, project materials, and reserved items stay carried.
A completely full inventory with no safe deposit pauses with a reason. Bootstrap construction
may require the owner to free slots or designate a separate legacy cleanup chest
before the first warehouse chest exists.

Warehouse metadata is atomic, versioned and scoped to the saved world and
companion; the location carries an explicit dimension. One warehouse per companion
is supported. Dimension mismatch pauses use rather than switching dimensions.
Observed stock persists for diagnostics, but allocation always reads loaded live
containers and component-compatible partial stacks. GPS sightings are not stock
or ownership authorization. Legacy cleanup chests are never imported; a configured
warehouse has priority, including when disabled or unavailable, preventing a
surprising fallback to legacy cleanup. Without a warehouse, the explicitly
registered legacy cleanup chest remains compatible. Resource withdrawals also
require an approved setup chest, warehouse chest or legacy registration.

## Organization and signs

Allocation prefers matching item stock; list displays approximate content categories.
Organization consolidates matching stacks between approved pairs without cycling.
It does not attempt a complete category sort of every chest. Existing `place_sign`
can place optional owner-selected labels using the categories in `ellie_storage list`.
Automatic sign creation/editing is not implemented. Missing signs never block storage.

## Persistence and diagnostics

Metadata: `<DeferredJobStore.worldRoot>/ellie-warehouses/<companion-id-hash>.properties`.
Keep this file with the world backup. Failed journal writes fail closed for further
warehouse access. Recorded contents are observational; live inventories remain
Minecraft's authority. Placement intent receipts allow interrupted cells to be
reconciled without clearing or rebuilding completed structures.

Observatory events include WAREHOUSE_INITIALIZED, WAREHOUSE_EXPANSION_STARTED,
WAREHOUSE_CHEST_ADDED, WAREHOUSE_CAPACITY_FULL, WAREHOUSE_PATH_UNREACHABLE,
WAREHOUSE_BUILD_COMPLETED, WAREHOUSE_ORGANIZE_RESULT, INVENTORY_RECOVERY_STARTED,
INVENTORY_RECOVERY_TRANSFER, INVENTORY_RECOVERY_COMPLETED and INVENTORY_RECOVERY_BLOCKED.
Repeated identical blocked reasons are suppressed; retry checks are throttled.

## Validation

Run `python tools/warehouse-tests/run.py` with Java 21 available. It compiles the
actual recovery, ledger, layout, allocation, organization and bounded transfer
classes against in-memory server fixtures. It tests inventory pressure, transfer
accounting, retries, reservations, dimensions, cancellation, layout and persistence.
It does not validate Minecraft placement, navigation, crafting or restart lifecycle.

Build the actual project from this directory with Gradle 8.14 and Java 21:
`gradle --no-daemon --console=plain :fabric:build`.
The Windows delivery automates the exact baseline, isolated worktree, idempotent
patching, tests, build exit checks, source/JAR verification and Downloads copy.
No game or mod installation is performed automatically.

## Windows delivery

Extract the entire ZIP, then double-click
`Ellie_Storage_Warehouse_Apply_Build_Verify_20261112.cmd`.
The matching `.ps1` and three verification/application scripts must remain beside
it, along with `manifest.json`, `payload/` and `fixtures/`. No manual PowerShell
commands are required. The launcher checks `C:\EllieSrc\PlayerEngine` and
`C:\EllieSrc` for the existing repository, or opens a folder chooser. It requires
Java 21 and uses exactly
`C:\EllieSrc\.phase3-tools\gradle-8.14\bin\gradle.bat`.

It fetches the development baseline branch, verifies that the expected baseline
is its ancestor, and creates a detached worktree at the exact baseline under
`%LOCALAPPDATA%\EllieWarehouse\worktrees\warehouse_<timestamp>`. Source hashes
are checked before any patch copy. Identical patches are idempotent; unfamiliar
local edits are rejected. The original checkout and its JARs are not patched.

A successful build/verification produces
`Downloads\Ellie_Storage_Warehouse_<timestamp>\Ellie_Storage_Warehouse_1.21.1_CANDIDATE.jar`,
a SHA-256 receipt, `WORKTREE.txt` and full `logs/`. Downloads relocation is read
from Windows' known-folder registry value. A failed command returns a nonzero
exit code, keeps logs and does not copy a JAR as a verified delivery. The ZIP also
includes the cloud-built candidate JAR under `candidate/` when a successful
Fabric build was available. Neither path installs a mod, launches Minecraft,
pushes commits or merges branches.
