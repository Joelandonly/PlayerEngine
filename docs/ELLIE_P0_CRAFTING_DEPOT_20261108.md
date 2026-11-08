# Ellie P0: plank shortfall loop and schematic depot protection (2026-11-08)

**Base:** merged v18.3 + lifecycle/storage hotfixes, commit `56af538c12b58f2a90ea3087a8fae837743952e4`.

## Live incident reproduced from observatory/logs

At 15:08:45, `schematic_prepare 123.litematic` started 64 oak trapdoors.
At 15:08:57, the native task was repeatedly `Crafting 92 planks`.
It already held 92 oak planks and two logs; the correct bill for 64 trapdoors
is 192 planks (6 planks -> 2 trapdoors). The native
`CraftWithMatchingMaterialsTask.getAllSameResourcesTask()` computes the
**remaining** 92 plank-equivalents, then constructs an `ItemTarget` with
the **absolute** target count 92. The child task considers that already met
and returns without producing anything. This repeats indefinitely.

The fix converts the missing amount to the absolute target using the native
tracker, so 92 held + 92 missing = 184. It does not grant items, change
recipes, or replace the native acquisition catalogue.

When the owner tried `ellie agentic deposit`, the supervisor legitimately
replaced the active schematic task. The schematic-specific `breakGuard`
was removed by `SchematicWorld.close()`. Three previously usable
project chests were subsequently observed as `not_chest`, and inventory
became full with three chests and staged materials. This strongly suggests
navigation mined the unprotected project storage.

The depot fix maintains a **separate, world-scoped, controller-owned**
break-avoidance callback and reserved-resource check for all saved or newly
allocated project depots. Task cancellation still performs native cleanup,
but no longer drops protection of stored physical project supplies.
One callback per controller/world avoids accumulating duplicate guards.

## Exact binary changes

`ellie/tools/apply_p0_hotfix.py` runs after the verified prior hotfixes.
It pins the input hashes and permits exactly three production class edits:

- `CraftWithMatchingMaterialsTask.class`
- `SchematicWorld.class`
- `SchematicProjectTask.class`

It adds exactly three compiled helper class files, from tracked sources in
`ellie/tools/p0-src/`. All other original JAR entries, including the
14-stage setup, native safety chains, Player2 integration and previous
storage/stop patches, must remain byte-identical.

`ellie/tests/P0ReliabilityRegression.java` checks the real helper classes:
count conversion, runtime tracker reads, retained guard after simulated
stop, per-world release and new depot updates. The existing native test
suite and `tests/verify_p0_packaging.py` check final packaged call sites.

## Important limits

- **Not yet validated in live Minecraft.** Native crafting actions, path
  obstacles, chest-block break decisions and full survival-world behavior
  still require a controlled reproducible live run.
- The durable break guard is established once a schematic's persisted
  depots are loaded by `SchematicWorld.initialize()`. Before the first
  project initialization in a newly launched game session, previous
  project depots are not independently restored into the pathing guard.
  Improving that boot-time protection requires scoped journal discovery.
- A new explicit `agentic deposit` request can still supersede the
  schematic task, by design of the native command adapter. The P0 patch
  prevents the resulting depot destruction, but does **not** silently
  queue or resume the old project. That ownership contract should be
  addressed separately, without inventing a second task scheduler.

## In-game acceptance

1. Fresh world or backed-up test world; install ONLY the new CI-built JAR.
2. Start the same 123.litematic preparation and observe oak-trapdoor
   acquisition progressing beyond the prior 92-plank loop.
3. Confirm project depot chests remain placed when the owner explicitly
   interrupts the schematic with `ellie agentic deposit`.
4. Verify the bot does not mine the chest or its supporting block while
   pathfinding, and new `not_chest` invalidations are investigated.
5. Verify restart rechecks real chest contents without fake completed work.
6. Separately validate the existing 14-stage setup, inventory handling,
   safety chains, and a longer run beyond the early schematic materials.

The GitHub artifact produced by CI remains marked `UNVERIFIED` until
these live tests pass.
