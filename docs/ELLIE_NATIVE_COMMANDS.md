# Native commands on the crafting-recovery revision

> **Historical 46-command native routing revision (not the latest inventory).**
> The draft PR #29 stack has 49 registered NPC IDs, including two schematic commands and `excavate`; the current owner-facing `setup` is 14-stage basic HOME v9, not the older survival setup described by this historical baseline. See [current command inventory](ELLIE_COMMANDS.md) and [current backlog](ELLIE_BACKLOG_CURRENT_20261109.md). Retain the details below as revision-specific history.


This revision starts from the uploaded `ELLIE-PLAYER2-CRAFT-RECOVERY.jar`, whose
source rebuilt to exactly SHA-256
`f36e4210a31024c113f7be6d2afa97385c878a15b1ab1d9f590a4ea745980b9f`.
It retains compact setup, conditional platform support, survival kit, table rejection,
blueprint layers/protection and the original Player2 API implementation.

## Changes

| Path | Current behavior |
| --- | --- |
| Exact owner command | All 46 canonical IDs bypass model selection; arguments keep case/quotes. The original authenticated owner gate remains. |
| `build_structure` | Uses shipped starter shelter, cabin, lodge, stone cottage, workshop, storage shed, greenhouse or 47x47 fortified-village templates. Unsupported requests fail locally; no generated-code/model retry. Physical layer builder is retained byte-for-byte. |
| `agentic` | Existing finite local drop-pickup/storage/deposit plans only. Unsupported goals return guidance to direct commands. No API service or model settings required to plan. |
| Chest labels and GPS | Existing deterministic text templates; model text/polish settings cannot trigger inference. |
| `eat_food [item]` | No argument selects carried suitable food; absent/invalid food or full hunger reports an error instead of a silent no-op/equip loop. |
| `scan [block]` | Default `dirt` and provided block IDs normalize to lowercase for registry lookup. |
| Completion reports | A fresh failed/blocked/cancelled supervisor receipt cannot be reported as verified completion. |
| Command sequences | Quoted semicolons remain part of the argument, including sign text. |
| `dummy` | Removed with its empty, never-finishing task. It was already unregistered. |

The eight remaining unregistered classes are `cycle`, `gamer`, `inventory`, `list`,
`pause`, `unpause`, `stash`, and `status`. They have substantial or recoverable
implementations; “not enabled/tested” is not evidence of zero chance of working.
The native chat route explicitly refuses unavailable commands, including
`beat_minecraft`, without asking a model to invent an action. No speedrun-completion
claim is made.

## Examples

```text
Ellie, setup
Ellie, get cobblestone 64
Ellie, mine iron_ore 8
Ellie, eat_food
Ellie, setup_farm
Ellie, plant_farm wheat_seeds=32
Ellie, make a farm here
Ellie, build a huge cobblestone village
Ellie, build_structure 100 64 100 "small shelter" "small cobblestone shelter"
Ellie, stop!
```

The convenience build phrase derives a nearby anchor; explicit `build_structure`
coordinates provide precise placement. Both use the same local selector and native
physical builder. “Make a farm here” uses ordinary nearby safe-site selection;
`setup_farm bot` requests the exact NPC anchor, with the existing safety gates.
General conversation remains optional Player2 chat. A finite local command grammar
does not translate every arbitrary sentence into a supported task.

## Validation and remaining work

The build checks all command metadata/registration, real dispatcher lookups,
exact parsing, owner routing, local plans, no-model entry points, food selection and
errors, and actual patched build orchestration with a substituted physical child.
The child-constructor signature is matched to the real packaged binary. Physical
builder and companion API compatibility are checked separately. Existing setup,
crafting-recovery, supervisor, ASM and packaging checks remain required.

This is not a live pass of every Minecraft action. Navigation deadlocks, resource
availability, complete farms/builds and the speedrun need in-world reproduction.
The subsequent shared-recovery build implements a common task gate, interaction
approaches, conservative obstruction handling, temporary rejection, look diagnostics
and critical-air override during user work. It also adds worksite/bench memory and
fixes the supplied NPC axe crash and furnace handoff. See
[shared recovery and live limits](ELLIE_SHARED_RECOVERY.md). These remain candidates
for live Minecraft acceptance, not a claim that every terrain/tool combination works.
