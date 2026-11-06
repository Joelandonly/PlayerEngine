# Food reliability v16

Build identity: `20261106-food-reliability-v16`. Minecraft 1.21.1 Fabric,
PlayerEngine 1.4.0, Java 21. **Awaiting live Minecraft validation.**

This is the first patch in the agreed repair order: cooking fuel, food quantity
accounting, survival supervision, and truthful command completion. It does not
attempt a full upstream migration or replace the task runner.

## Behavior

- An issued fuel task remains the active cooking child until it obtains usable
  fuel, actually fails, or consumes 600 active worker ticks. Failed coal/plank
  collection switches to another existing wood/log acquisition task. At most
  three strategies run. A food job remembers failed fuel families across later
  cooking batches instead of repeating their timeout after every animal.
- A hunt's target is current raw inventory plus one, rather than an absolute
  one already held. A cooking batch targets current cooked inventory plus the
  carried raw input. Fuel planning uses remaining output, not the final total.
- Food retains its cooking child after input leaves inventory and enters the
  furnace/smoker, through collection of the actual cooked output. It reuses an
  existing furnace, otherwise retaining the existing smoker obtain/place path.
- The food workers stop with a specific BLOCKED reason after a failed child or
  2,400 active ticks without a new inventory high-water mark. Successful food
  progress can continue; switching targets alone earns no progress.
- The existing FoodChain keeps emergency eating. Its collector yields to an
  explicit owner food/meat command. A failed automatic attempt is suppressed
  until food/fuel changes or relocation provides a genuinely different resource
  opportunity. Unrelated ore gains do not rearm it. If unresolved hunger is
  critical and no food is carried, the owner job receives BLOCKED_SURVIVAL_FOOD.
- `food`/`meat` quantities are additional **hunger points**, not item counts.
  `meat` starts from cooked meat only; bread no longer inflates that goal.
  Nonpositive/oversized quantities reject before an action starts. Exact item
  quantities still use `get <food_item> <count>`.

## Completion contract

Every synchronous command submission is associated with its own receipt. A
failed task cannot become ordinary success through a callback that simply calls
`finish()`. Valid partial notes remain notes. An early native leaf callback is
deferred until its executor settles, preserving commands such as `place_sign`
without prematurely finishing a still-running job.

The adapter reads existing outcome APIs for storage scans/transfers, smelting,
smithing and deposition instead of treating every terminal flag as success.
Sign failures retain their specific reason; giving sets a successful terminal
only on its existing completed-delivery path. Stale-generation callbacks cannot
continue a superseded job. No new queue, planner, thread or movement controller
is introduced.

## Protected scope

`tests/proven_setup_sha256.json` is unchanged. All 32 setup classes must match
the owner-confirmed `6290ef647051bc4f1be1898b795c031d80295252` implementation.
`BasicSetupTask.java` and the 14-stage plan are unchanged. Existing command IDs,
Player2 API implementation, outward bridging, fall checks, chunk ownership,
observatory and water-escape safeguards are retained. The packaging allowlist
and native hook checks reject unexpected changes and fixture leakage.

## Regression evidence and limits

The local full build passed, including 55 packaged food-chain checks, 17 command
receipt/lifecycle checks, 14 packaged terminal-mapper checks and 42 packaged hook
checks. All 24 historical sidecar tests passed. A direct comparison also found
all 406 setup/navigation classes byte-identical to v15; the independent protected
setup manifest still verifies its 32 owner-confirmed classes.

- The full `bash ellie/build.sh` suite remains mandatory, including setup,
  recovery, native command routing, packaged class verification, inventory,
  descent/water-thread boundaries, and the server-stall diagnostic tests.
- `PackagedFoodLoopTest` executes the **actual packaged** food, smoker, furnace,
  fuel planner and helper method bodies. Its class loader substitutes game types
  and the combat/acquisition/navigation/world boundaries using a pinned vanilla
  symbol snapshot. The fixture task driver preserves child equality/retention,
  while world ticks model input, fuel and output consumption. It checks repeated
  kills, delayed fuel, missing fuel, wood alternatives, existing furnace/smoker,
  missing/obstructed stations, existing raw/cooked food, absent animals, scope
  cleanup, automatic-priority suppression and explicit owner food work.
- The repeated-hunt assertion fails against v15: `meat goal must hunt beyond
  the first animal`. The changed workers pass it. This is a behavioral
  before/after result at the substituted game boundary, not live Minecraft.
- `CommandCompletionTest` executes the actual patched Command lifecycle and
  supervisor with real StepExecution receipts. It checks sync/async/nested
  completion, stale callbacks, native early callbacks, failure notes and command
  instance reuse. Native task execution is substituted.
- `PackagedCompletionMapTest` executes the packaged terminal mapper and record;
  unrelated adapter methods are stripped only in its test class loader. Worker
  outcome objects are boundaries. Failed storage/smelting/sign results and valid
  partial outcomes must map truthfully.
- `VerifyFoodReliability` inspects the shipped action/receipt hooks; it does not
  substitute for executing the behavioral tests.
- The 24 historical sidecar tests remain required, though that sidecar is not
  shipped in the native Player2 mod.

The tests do not reproduce real Minecraft pathfinding, entity combat, physical
pickup, placement clicks, modded recipes, API authorization, chunk transitions
or the intermittent server freeze. The live acceptance run is still necessary.
Later audit repairs for slabs, deposits, pickups, navigation deadlines, fishing,
acquisition resolution and kill attribution remain separate work.

## Live acceptance

Fully restart Minecraft and replace the previous **PlayerEngine Ellie JAR** with
the Actions JAR whose filename includes `FoodReliability` and the revision.
Keep the separate Player2NPC dependency. The startup log must show this build ID
and the artifact's source revision; do not identify it only by the unchanged
Minecraft mod version string. The artifact supplies SHA256SUMS and embeds
`META-INF/ELLIE_BUILD.json` / `ELLIE_BUILD_REVISION.txt`.

1. In the same survival scenario, run `ellie food 30` with no fuel and a nearby
   usable station. Verify several hunts, actual fuel acquisition and cooking.
2. Repeat with one raw chicken and several cooked chickens already carried.
   Newly requested work must still increase the correct inventory total.
3. Repeat with an existing furnace, then with no station, then an obstructed
   station. Expect actual reuse/placement or a specific BLOCKED reason.
4. Test inaccessible coal with available wood, and then no reachable fuel at
   all. Expect a different fuel strategy or bounded BLOCKED_FUEL, never an
   endless repeated action or fabricated cooked output.
5. While hungry, start another owner job. Verify that food failure is visible;
   it must not silently monopolize execution. Supply food/fuel and retry.
6. Run the protected fresh-world setup and verify its 14 stages and home
   stations. Byte preservation is a strong safeguard, not proof that shared
   worker changes cannot affect live behavior.

Use the existing latest.log and observatory events/status for failures. If a
server freeze recurs, retain `logs/ellie-server-stall.txt` when generated; the
exact previous freeze cause remains unconfirmed.
