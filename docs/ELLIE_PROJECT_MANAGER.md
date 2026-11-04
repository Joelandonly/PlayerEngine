# In-game project supervisor

This records the supervisor reliability pass. The subsequent starter-base preview
adds explicit setup and crafting changes, documented in [ELLIE_SETUP.md](ELLIE_SETUP.md);
its allowed task changes are separately verified during packaging.

This work concerns `CoreGoalSupervisor` and its handoff to the native
`TaskStepExecutorAdapter`, not the ChatGPT interpreter or an operating-system
process manager. The existing native runner remains the single execution engine.

## Recovered source and fixes

`ellie/src/com/player2/playerengine/supervisor/` restores the baseline's public
supervisor/lifecycle API as readable source. `GoalLifecycle` was recovered with
CFR 0.152; `CoreGoalSupervisor` was reconstructed against the pinned JAR's method
signatures. The full matching mod source is still not available.

The original JAR failed eight of the initial twelve regression scenarios:

- Synchronous completion borrowed an old same-ID receipt and could report success
  for a rejected submission. Completion now waits for its own returned receipt.
- Stale callbacks still invoked the old continuation. They can no longer advance
  a superseded project; duplicate callbacks are also ignored.
- A typed task claiming success could override a running, blocked, cancelled or
  rolled-back execution. Success now requires a successful executor receipt and,
  for typed tasks, a terminal successful outcome.
- Invalid task submissions could take ownership away from valid work. Task,
  policy and identity are validated before beginning a generation.
- A null terminal lifecycle status was accepted. It is now rejected without
  changing the active lifecycle.

The regression suite now has 15 passing scenarios, using the actual baseline
`StepExecution` state machine. It substitutes only controller/task boundaries and
logging; it does not simulate Minecraft, output inventories, or task internals.

## Tool routing

The baseline has many command handlers that bypass the supervisor through
`runUserTask`. `ProjectTaskGateway` now routes 20 reviewed command classes / 22
call sites into the supervisor. The explicit list is
[`supervised-commands.txt`](../ellie/tools/supervised-commands.txt): attack, waypoint
operations, storage transfers, eating, equipment, fishing, food, giving, goto,
structure location, meat gathering, signs, storage scans and stashing.

Previously tracked operations continue to use `runUserTaskTracked`. This is not
a claim that every JAR feature now has a persistent project adapter. Gesture,
game-mode policy, unpause, idle, recovery and survival paths retain their original
semantics. Replacing those blindly can disrupt user control or survival priority.

Build-time verification checks the **packaged JAR**: the reviewed command call
sites reach the gateway, no reviewed call site retains the legacy route, and
native chain/task, controller, adapter, gesture and resume classes stay byte-identical.
`build/reports/task-routing.tsv` lists observed static routes for further review;
the number of static call sites is not a count of supported tools or runtime tests.

## Validation

Run `cd ellie && bash build.sh` with Java 21, Node 22+ and Python 3. It runs:

- 15 supervisor scenarios, plus the bridge configuration/fail-closed JVM test;
- all 24 Node tests;
- ASM verification of generated and patched classes;
- exact permitted-class inventory and unchanged-baseline-entry checks;
- routing verification against packaged bytecode.

`python3 make_release.py` produces a distinct supervisor preview ZIP and JAR.
The original `RELEASE_MANIFEST.json` remains provenance for the uploaded artifact;
the ZIP contains a generated manifest describing the current output. Test fixtures
are never included as runtime classes.

## Remaining acceptance gates

This is a reliability preview, not a flawless-project-manager claim. No live
Minecraft session was run. Before using it on an important world, verify:

1. A building project submits, acquires materials, executes and verifies its final
   blocks; receipts alone do not establish correct world output.
2. Storage/equipment/goto tools report the correct result through the supervisor.
3. Replacing a project cannot let an old callback start another task.
4. User stop, pause/resume and world exit preserve cancellation and ownership.
5. Combat and food emergencies can interrupt work safely, then resume only valid work.
6. Load/reload revalidates world state. Journaling a status is not durable restoration
   of an arbitrary task graph; this patch does not implement such restoration.

Broader remaining work: resumable compound project plans, per-step inventory/world
verification, bounded progress/stall recovery, explicit resource/tool adapters for
all remaining project features, and the existing navigation/crafting/furnace issues.
These require task-specific changes and live acceptance checks. Keep the stable
backlog IDs and validation matrix; do not close them based on this harness.
