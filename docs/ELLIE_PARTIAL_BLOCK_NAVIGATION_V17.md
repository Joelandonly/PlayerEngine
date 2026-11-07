# Partial-block navigation v17

Baseline: owner-tested food reliability v16, revision
`41d57410d912eb77dca72291bd76167f77ad4a4d`. Build ID:
`20261107-partial-block-navigation-v17`. **Awaiting live Minecraft validation.**

## Behavior and scope

The native `EntityContext.feetPos()` previously queried chunk coordinates with
`(int)x << 4` and corrected only slab blocks. Navigation's logical feet could
remain inside the support cell when walking on lower stairs or partial blocks.
Wrong-height branches can then stop/restart a movement or repeatedly jump.

The replacement uses the existing non-loading `BlockStateInterface`. It derives
the logical foot cell from a collision face beneath Ellie's bounding box, including
while hopping over that support or straddling a block/chunk edge. Physical foot
height remains correct when already standing on a floor that route settings avoid.
It does not project feet onto a face above the
entity, an absent floor or unknown chunk data. Shapes receive `BSI.access`, not
the live world, for neighbor queries. No view is retained across world changes.

The native `canWalkOn` unsupported-floor fallback now recognizes a horizontal
collision patch around the node center within the block's height. This supports
ordinary partial floors without an ever-growing list of slab names. Existing
full-block, climbable, liquid and slab-setting branches remain native. Thin
overlays at or below 1/8 block keep the existing walk-through representation.
Carpets and shallow snow therefore do not become a second, higher floor node.

The native passability fluid branch previously treated solid waterlogged stairs
as open water. A collision check now rejects that body-space interpretation while
retaining walking on their top surface. Open water with no solid collision keeps
the native swimming behavior; tagged climbables retain native ladder/scaffolding
handling, including waterlogged variants.

The fallback rejects vertical-only or over-height shapes, unknown data, configured
avoided blocks, fluids, hazardous partial blocks and known unstable supports such
as big dripleaf and turtle eggs. This is not a promise that every object can be
used as flooring. Narrow rims, open vertical trapdoors, walls and fences can
remain unsuitable for the existing integer-cell navigator.

For a level traverse/diagonal, a verified partial floor can require a rise above
the NPC's actual automatic step height. The existing movement input state then
receives a jump only when it is already walking forward toward a known support,
the rise is within the normal jump envelope, and the movement is neither backing,
sneaking nor airborne. Missing bridge floors and higher-priority control retain
their inputs. Ascend cost also rejects rises over 1.25 blocks from low supports;
native stair movements may still approach the stair's upper step before jumping.

Only four native classes change: `EntityContext`, `MovementHelper`, `Movement`
and `MovementAscend`, plus one geometry helper and its box record. After removing
the reviewed hooks, native helper/movement/ascend method bodies must match the
pinned baseline. All 1,878 unrelated v16 classes must remain byte-identical.
The independent 32-class setup manifest is unchanged. `BasicSetupTask.java`, the
14 stages, food repairs, Player2 integration, storage, construction, bridging,
chunk ownership, observatory and descent safety remain protected.

## Validation

Run `bash ellie/build.sh` and `node --test tests/*.test.mjs` (Java 21 required).
The full historical suite remains mandatory, including the 14-stage setup,
food-chain failures, inventory, shared recovery, safe descents and water/thread
boundaries. The new checks are:

- `PartialBlockNavigationTest`: actual packaged feet, `canWalkOn`, `canWalkThrough`
  and geometry-helper bodies; explicit entity/settings/chunk boundaries. The
  pinned fixture comes from vanilla 1.21.1: 1,470 states in 66 representative
  block types, including slab variants, stair facings/halves/shapes, all eight
  snow layers, carpets, paths/farmland, beds, trapdoors, cakes, chests, workstations,
  vertical shapes and hazards. Vanilla collision boxes, land pathfindability and
  full-collision-cube flags are exported, rather than guessed in the fixture.
- Grounded quadrants, hop frames, positive/negative chunk coordinates and borders,
  missing chunks, a worker-thread query, world replacement, disabled bottom slabs,
  avoided floors, unsafe/gradual ascents, ordinary stair ascents and step inputs.
- Missing bridge floor, sneaking, backing, completed movement, airborne control,
  pause, distance and downward movement must leave the native input map intact.
- `VerifyPartialBlockNavigation`: packaged hook placement and native method-body
  comparisons, including unchanged traverse/diagonal/bridge implementation.
- `verify_partial_navigation_scope.py`: aggregate hash of every unrelated v16
  class, allowed class set, and no shipped game/test fixtures.

`tools/ExportPartialBlockShapes.java` records the fixture from a Mojang-mapped
vanilla 1.21.1 JAR and its matching version libraries after registry bootstrap.
It uses `EmptyBlockGetter` and the default collision context. The fixture is not
a real generated world, does not load datapack tags, and does not cover modded
collision callbacks or every vanilla block/material variant. Shapes shared by
wood/stone variants are represented by canonical examples.

The new tests do not execute Minecraft physics, A* journeys, actual placement,
NPC animation, server tick scheduling or Player2 communication. They cannot
establish that walking is smooth, or explain the earlier intermittent world
freeze. Existing stall diagnostics remain intact.

A before/after execution probe of the packaged feet method at a north-facing
lower stair (x=0.25, y=64.5, z=0.75) returns the incorrect logical Y=64 in v16
and the expected Y=65 in v17. This uses the same substituted entity/chunk boundary
as the geometry suite; it is not a live walk across the stair.

## Live acceptance

Fully restart Minecraft and replace the previous Ellie PlayerEngine JAR with the
Actions artifact containing `PartialBlockNavigation` and its revision. Keep the
separate Player2NPC dependency. Confirm build ID and revision in the startup log.

1. Use the same survival NPC on a short mixed route: full blocks, bottom/top/double
   slabs, straight/inner/outer stairs in several facings, carpet and snow depths
   1, 2, 3, 5 and 8. Include ascending and descending transitions, turns and a low
   ceiling. Repeat across x/z=15/16 and -1/0 boundaries.
2. Try placed beds, chests, farmland/path blocks, workstations and horizontal
   trapdoors. Open a trapdoor and verify that its vertical sheet is not planned
   as a floor. Test beside fences/walls and near a drop.
3. Repeat ordinary mining/get and outward bridging, a safe drop and a rejected
   lethal descent. Jumping for a small ledge must not become jumping into a void.
4. Run the unchanged 14-stage setup and the working v16 food scenario. Byte
   preservation protects their implementations, not every live consequence of
   a shared navigation change.

On failure, retain latest.log plus observatory events/status and the route's
block states/coordinates. Smooth movement and full survival completion remain
live acceptance criteria, not claims made from this build's automated checks.
