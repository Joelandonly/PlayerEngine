# Pre-commit descent safety v13

Build ID: `20261106-safe-descents-v13`. Minecraft gameplay **UNVERIFIED**.
This patch starts from v12 `45b1a02e28ef44ce7585f36bf8047e9820f0ba5b`.
It does not change `BasicSetupTask.java`, setup stages, commands, chunk ownership,
emergency clutch behavior or the shared recovery controller.

## Behavior

The reported run bridges outward toward deep ore, then falls from the cliff.
Native movement planning already has fall settings, including a bucket allowance;
the logs do not establish which native decision produced that particular jump.
This patch adds an independent veto at two shared boundaries:

1. Immediately after native `Moves.apply`, before A* accepts the candidate cost.
2. At the start of `Movement.update`, before preparation can break blocks or
   issue movement inputs. It rechecks the loaded world and actual entity height.

Same-height and upward movements bypass the new world inspection entirely.
Outward bridging, its block placement, native staircase construction, ascent,
and the native movement bodies remain intact. Gradual descents and dry drops
with a verified landing no more than three blocks below the start are allowed.
The landing check uses collision height, so a bottom slab can increase a
nominal three-block drop to 3.5 and cause rejection. A one-block downward step
into a shaft is rejected if there is no safe landing below the excavated cell.
Unloaded/unknown landings, lava and other identified hazardous surfaces fail closed.

Existing source-water landings and continuous ladder/vine/scaffolding routes
can protect a descent; a ladder only at the bottom of a large gap cannot.
**A filled bucket in inventory grants no planning allowance.** Reliable NPC
deployment has not been separately established. Native emergency clutch logic
is unchanged, and this gate leaves already-airborne movements to that logic.
It prevents committing a grounded movement rather than attempting to rescue it.

A rejected live movement clears held native keys and returns `UNREACHABLE`.
The unchanged native path executor cancels/replans; A* filters the unsafe edge
on subsequent searches. Existing shared resource recovery/alternate-target
selection and its bounded job-wide no-output limit remain responsible for
exhausted resource searches. There is no additional movement/recovery controller.
`descentSafety` and rate-limited `DESCENT_REJECTED` / `DESCENT_EDGE_REJECTED`
events include source, destination and the specific reason. Candidate rejection
is visible even when planning finds no executable movement. Reflection/ABI failures reject downward
edges and emit `DESCENT_FILTER_UNAVAILABLE` rather than silently permitting a fall.

## Automated validation and limits

`bash ellie/build.sh` includes all existing regressions and new checks:

- Deterministic geometry: outward bridges over void, ascent, 1/2/3-block drops,
  deep dry drops, shafts, slab tops, hazard/unknown landings, source/flowing water,
  continuous/gapped climbables and 130 individual safe staircase steps.
- Real packaged helper with substituted world, entity, collision and inventory
  boundaries: bucket possession cannot unlock deep falls; removed support is
  vetoed; already-airborne input ownership remains unchanged.
- A small substituted graph search chooses a bridge/staircase route over a
  fatal shortcut and terminates without a route if only the shortcut exists.
  This is **not** execution of the full native A* search or Minecraft physics.
- Packaged ASM: one filter immediately after native candidate generation;
  pre-input gate first in `Movement.update`; native `UNREACHABLE` cancellation.
  The actual packaged gate is executed with native interface calls and its
  original movement body replaced by fixtures/sentinel. Rejected moves never
  enter that body; bridge/ascent/safe-drop moves do.
- All native concrete movement/bridge implementations, `Moves` and
  `PathExecutor` remain byte-identical to the pinned binary. All 32 setup
  classes must match SHA-256 receipts from owner-confirmed revision
  `6290ef647051bc4f1be1898b795c031d80295252` exactly.

These tests establish decision ordering and controlled scenarios, not live
survival success. Before accepting this build, repeat the same cliff/ore task
without a bucket, confirm outward blocks still get placed, and observe Ellie
finding stairs/another route or reporting a bounded failure rather than jumping.
Repeat with a bucket (no speculative deep drop), existing water, safe drops,
normal downward mining and gradual stairs. Remove a landing after planning and
check the pre-commit rejection. Confirm setup still completes and ordinary
mining/building, remote owner commands and independent chunk execution still work.
The original cliff seed and full NPC movement/placement physics require live testing.
