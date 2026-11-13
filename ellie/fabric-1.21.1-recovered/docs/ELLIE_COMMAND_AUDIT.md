# Ellie command audit

Reviewed November 12, 2026 against the recovered Minecraft 1.21.1 Fabric source
on `feature/ellie-storage-warehouse-recovery`. The review covers all 56 routes in
the offline command deck. A source check establishes registration, syntax and
intent. Live outcomes are recorded separately below.

The Minecraft command menu registers the same 56 routes under `/ellie`. It
offers suggestions for subcommands and arguments while the existing native
route validates and dispatches the full input. Commands target the nearest
loaded companion owned by the player in the current dimension.

## Build and terrain

| Command | Decision | Source finding |
| --- | --- | --- |
| `setup` | Updated deck | The active starter plan gathers wood and cobblestone, makes wooden and stone tools and 64 torches, and verifies the home bench, chest, and furnace. |
| `build_structure` | Keep | Selects a shipped local blueprint. The compact village is a repeatable baseline for physical construction. |
| `setup_farm` | Keep | Selects or resumes a site and records a fixed irrigated 9×9 farm. Explicit coordinates are supported. |
| `plant_farm` | Keep | Plants ordered `item=count` groups on a recorded farm with a crop layout policy. |
| `harvest_farm` | Keep | Performs a finite pass over ripe crops on a recorded farm. |
| `excavate` | Keep | Edits an inclusive cuboid through the builder with protection and inventory checks. |
| `safezone` | Keep | Authenticated owner route for recording, listing, checking, and removing protected cuboids. |
| `schematic_prepare` | Follow up | Reads a schematic and prepares its exact material bill. Test acquisition when the required wood is locally scarce. |
| `schematic_build` | Follow up | Runs the imported project with exact block IDs and physical verification. A future palette option needs the same mapping in preparation and construction. |
| `waterfill` | Keep | Authenticated, bounded water-source replacement around the owner. Partial results report inaccessible cells. |

## Gather, craft and travel

| Command | Decision | Source finding |
| --- | --- | --- |
| `acquire_block` | Keep | Uses the block knowledge and acquisition routes for a specified block. Useful when the request starts with a block ID. |
| `agentic` | Fixed outcome and deck | Uses the local resource and storage planner. Its completion now uses the supervisor’s terminal outcome, including operator cancellation. |
| `block_info` | Keep | Looks up block properties and acquisition routes without acting on them. |
| `eat_food` | Keep | Consumes carried food, optionally of a specified type. |
| `fish` | Keep | Runs the fishing task and requires a rod. |
| `food` | Keep | Acquires food measured in hunger points. `get` remains the exact item-count route. |
| `get` | Keep | Item-oriented native acquisition. The direct route supports unambiguous item/count reorderings. |
| `meat` | Keep | Acquires cooked-meat hunger points through hunting and cooking. |
| `mine` | Keep | Targets blocks and their drops, using the tool tier required by the block. |
| `pickup_drops` | Keep | Collects nearby loose drops through the shared recovery path. |
| `scan` | Keep, review naming | Quick nearest-block lookup with a dirt default. `locate_block` has a separate loaded-chunk underground search and radius. |
| `smelt` | Keep | Processes a specified input with a recipe, fuel, and workstation. |
| `smith` | Keep | Resolves a registered result and gathers its template, base, and addition. |
| `follow` | Keep | Follows the owner or a named player. |
| `goto` | Keep | Supports XYZ, XZ, Y and dimension targets. Full XYZ is the clearest form for examples. |
| `leaveboat` | Keep | Dismounts the current vehicle. Useful when travel leaves Ellie mounted. |
| `locate_block` | Keep | Searches loaded chunks without generating terrain. Returns coordinates for later travel or mining. |
| `locate_structure` | Keep | Currently supports strongholds and desert temples. |

## Storage and GPS

| Command | Decision | Source finding |
| --- | --- | --- |
| `audit_waypoint` | Keep | Refreshes a recorded waypoint from live observations. |
| `cleanup_storage` | Keep | Gives players a simple, single-container overflow option before warehouse setup. |
| `compare_waypoint` | Keep | Reports drift without accepting the new inventory as the saved baseline. |
| `create_waypoint` | Keep | Records a searchable location from a live container. |
| `delete_waypoint` | Keep | Deletes a GPS record while leaving the container and contents in place. |
| `deposit` | Keep | Broad manual deposit into nearby storage. Useful for deliberate inventory clearing. |
| `deposit_storage_slot` | Keep | Moves a specified source slot, useful for variants that share an item ID. |
| `deposit_to_storage` | Updated deck | Checks explicit counts and capacity before transfer and reports the observed quantities, including partial outcomes. |
| `ellie_storage` | Keep, updated deck | Manages registered warehouse chests and shared inventory recovery. Construction and expansion to twelve double chests have live evidence. |
| `locate_storage` | Keep | Finds nearby physical containers for immediate use. |
| `locate_waypoints` | Keep | Searches saved GPS records, including farm and stale records. |
| `scan_storage` | Keep | Read-only live inspection with totals, numbered slots, or targeted counts. |
| `withdraw_from_storage` | Updated deck | Explicit counts get preflight checks. An omitted count requests what Ellie can carry, and the receipt reports what moved. |
| `withdraw_storage_slot` | Keep | Moves an exact slot variant, optionally into a chosen inventory slot. |

## Combat, interaction and controls

| Command | Decision | Source finding |
| --- | --- | --- |
| `attack` | Keep | Direct attack order against a named target. |
| `bodylang` | Keep | Four explicit gestures. These can briefly suspend compatible work. |
| `chatclef` | Keep | Owner control over the AI bridge. The authenticated native route is separate. |
| `equip` | Keep | Equips carried armor or melee weapons. |
| `give` | Keep | Gives a carried item to a player. `drop` is an alias for the same route. |
| `hero` | Updated help and deck | Guards reachable hostile threats within 16 blocks of the owner and stays near the owner when idle. |
| `idle` | Keep | Runs the dedicated idle task to stand still. |
| `place_sign` | Keep | Places and writes a sign against an explicit anchor and face. |
| `read_signs` | Keep | Reports nearby sign contents and positions. |
| `reload_settings` | Keep | Reloads configured bot settings and allow/block lists. |
| `resetmemory` | Keep | Explicit owner memory reset. Its irreversible effect merits the existing caution on the card. |
| `set_attack_hostiles` | Keep | Changes automatic hostile attacks. |
| `set_follow_mode` | Keep | Sets follow posture independently of direct attacks and owner guarding. |
| `stop` | Keep | Cancels current work. The owner route now presents verified cancellation as stopped. |

## Workflow evidence and next test

- Observatory logged the warehouse reaching `READY` with 12 double chests, and
  mining and crafting continuing after inventory recovery. The owner confirmed
  the warehouse works in-game.
- The logged `agentic` peony run was cancelled by the operator. Its supervisor
  receipt was `CANCELLED_OPERATOR`, followed by the command message
  `Step failed: get_resource`. The correction follows the supervisor outcome
  and preserves normal failure reporting for a genuine block or failure.
- The imported schematic bill uses exact palette IDs. Substituting local wood
  requires a consistent decision across the material bill, placement, and final
  verification. The local import check found 852 dark oak trapdoors and 8 dark
  oak fences in `creep.litematic`, and 64 oak trapdoors in `123.litematic`. The
  next live test is a small wooden `.litematic` in a disposable world with a
  different wood species nearby.
- No command is marked for removal. The three main overlaps serve different
  intents: quick lookup versus underground search, explicit storage versus
  automatic overflow, and direct acquisition versus a bounded local goal.

## Verification

- Fabric 1.21.1 compiled and produced a remapped JAR.
- 271 offline deck examples resolved to their expected native command.
- 82 Brigadier checks covered registration, non-player source rejection,
  command names, and representative argument completions.
- The resource parser, supervisor cancellation, and all 65 warehouse fixture
  checks passed. The slash menu still needs a live Minecraft playtest.
