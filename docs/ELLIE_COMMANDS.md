# Native command inventory and validation

The current build is based on the maintainer's Player2 crafting-recovery revision,
with compact three-station setup retained. It registers **46 NPC commands**.
All exact owner requests for these IDs now bypass model selection. Supported
local build/farm phrases also have a deterministic route. Ordinary conversation
can still use optional Player2 chat; the gameplay commands do not require a model.

`build_structure` selects only shipped local templates. `agentic` now uses existing
local drop/storage plans, including template chest labels. GPS description polish
no longer requests AI. The original `Player2APIService` remains byte-identical to
the baseline. See [native command changes](ELLIE_NATIVE_COMMANDS.md).

The permanent no-op `dummy` command and its never-finishing test task are removed
from the shipped JAR. Eight other command classes remain unregistered. Substantial
but unverified code, such as the speedrun, is not called impossible or enabled
without acceptance evidence.

Validation covers all 46 dispatcher lookups, eight unavailable IDs, all 46 exact
command parser entries, owner authorization, local planning, food-selection errors,
and the patched builder's orchestration/outcome handling. Physical game boundaries
are substituted. This does **not** certify all 46 gameplay handlers in Minecraft.
Run `cd ellie && bash build.sh` to regenerate the packaged command report.

## NPC commands: full registered list

These are internal bot command IDs, not 46 independent Minecraft slash commands.
Use your existing NPC command entry point and configured prefix (the examples in original
help use `@`). Natural-language aliases such as “Ellie, build a huge cobblestone ...” are handled by the local owner route for supported templates; they are not canonical IDs in this registry.
`<...>` means required; `[...]` means optional. Constructor literals and full original
help text are recorded in [the TSV inventory](ELLIE_COMMANDS.tsv). The syntax below is a
concise guide, not proof that each game operation succeeds.

| Command / syntax | Purpose | Evidence |
| --- | --- | --- |
| `agentic <goal text>` | Local bounded drop-pickup/storage planning | Registered; lookup passed; gameplay untested |
| `attack <player-or-mob> [count]` | Attack a named player or mob | Registered; lookup passed; gameplay untested |
| `audit_waypoint <x> <y> <z>` | Refresh a stored waypoint observation | Registered; lookup passed; gameplay untested |
| `bodylang <greeting / nod_head / shake_head / victory>` | Perform a gesture | Registered; lookup passed; gameplay untested |
| `build_structure <x> <y> <z> "search query" "description"` | Select a supported local blueprint; gather materials and physically construct it | Registered; lookup passed; gameplay untested |
| `chatclef <on / off>` | User-controlled AI bridge switch | Registered; lookup passed; gameplay untested |
| `compare_waypoint <x> <y> <z>` | Compare stored and observed waypoint contents | Registered; lookup passed; gameplay untested |
| `create_waypoint <x> <y> <z>` | Record a storage waypoint | Registered; lookup passed; gameplay untested |
| `delete_waypoint <x> <y> <z>` | Delete a waypoint record | Registered; lookup passed; gameplay untested |
| `deposit [items...]` | Deposit selected items or all nonessential items nearby | Registered; lookup passed; gameplay untested |
| `deposit_storage_slot <x> <y> <z> <botSlot> <count> [containerSlot]` | Transfer an exact NPC inventory slot into storage | Registered; lookup passed; gameplay untested |
| `deposit_to_storage <x> <y> <z> <items...>` | Deposit selected items at exact storage coordinates | Registered; lookup passed; gameplay untested |
| `eat_food [item]` | Eat carried suitable food; optional exact item | Registered; lookup passed; gameplay untested |
| `equip <items...>` | Equip armor/weapons | Registered; lookup passed; gameplay untested |
| `fish` | Fish; requires a fishing rod | Registered; lookup passed; gameplay untested |
| `follow [username]` | Follow owner or named player | Registered; lookup passed; gameplay untested |
| `food <food-units>` | Gather food by food value | Registered; lookup passed; gameplay untested |
| `get <items...>` | Gather/craft requested items | Registered; lookup passed; gameplay untested |
| `give [username] <item> [count]` | Give/drop items to a player; drop is an alias | Registered; lookup passed; gameplay untested |
| `goto <coordinates/target> [dimension]` | Travel to coordinates/dimension | Registered; lookup passed; gameplay untested |
| `harvest_farm [x y z]` | One finite pass harvesting fully grown crops on a recorded farm | Registered; lookup passed; gameplay untested |
| `hero` | Attack hostile mobs | Registered; lookup passed; gameplay untested |
| `idle` | Stand still | Registered; lookup passed; gameplay untested |
| `leaveboat` | Dismount | Registered; lookup passed; gameplay untested |
| `locate_storage` | Report nearby storage coordinates | Registered; lookup passed; gameplay untested |
| `locate_structure <stronghold / desert_temple>` | Locate stronghold/desert temple | Registered; lookup passed; gameplay untested |
| `locate_waypoints <query...>` | Query stored inventory/farm waypoints | Registered; lookup passed; gameplay untested |
| `meat <food-units>` | Gather meat by food value | Registered; lookup passed; gameplay untested |
| `mine <block> [count]` | Mine and collect a named block | Registered; lookup passed; gameplay untested |
| `pickup_drops` | Collect nearby dropped items | Registered; lookup passed; gameplay untested |
| `place_sign <x> <y> <z> <face> [item_id] [line instructions...]` | Place a sign on an anchor and write front/back lines | Registered; lookup passed; gameplay untested |
| `plant_farm <item=count[,item=count]> [x y z [policy]]` | Plant ordered crop groups on a recorded farm | Registered; lookup passed; gameplay untested |
| `read_signs [radius]` | Read nearby sign text and positions | Registered; lookup passed; gameplay untested |
| `reload_settings` | Reload bot settings | Registered; lookup passed; gameplay untested |
| `resetmemory` | User-only memory reset | Registered; lookup passed; gameplay untested |
| `scan [block]` | Locate nearest matching block | Registered; lookup passed; gameplay untested |
| `scan_storage <x> <y> <z> <light / deep / targeted> [items...]` | Inspect live storage at exact coordinates | Registered; lookup passed; gameplay untested |
| `set_attack_hostiles <on / off>` | Toggle automatic hostile attacks | Registered; lookup passed; gameplay untested |
| `set_follow_mode <mode>` | Change companion follow behavior | Registered; lookup passed; gameplay untested |
| `setup` | Full kit plus placed table, furnace and one chest | Registered; lookup passed; gameplay untested |
| `setup_farm [bot / here / owner / player] OR setup_farm <x> <y> <z>` | Prepare and record a 9x9 irrigated plot; does not plant it | Registered; lookup passed; gameplay untested |
| `smelt <item> [count]` | Deferred furnace/blast-furnace/smoker processing; configurable | Registered; lookup passed; gameplay untested |
| `smith <item> [count]` | Recipe-driven smithing upgrade; configurable | Registered; lookup passed; gameplay untested |
| `stop` | Stop automation | Registered; lookup passed; gameplay untested |
| `withdraw_from_storage <x> <y> <z> <items...>` | Withdraw selected items at exact coordinates | Registered; lookup passed; gameplay untested |
| `withdraw_storage_slot <x> <y> <z> <containerSlot> <count> [botSlot]` | Withdraw an exact container slot | Registered; lookup passed; gameplay untested |

## Present but unavailable through default NPC registration

| ID | Intended purpose | Finding |
| --- | --- | --- |
| `cycle` | One cycles the dragon B) | Class exists; not registered; lookup rejected |
| `gamer` | Beats the game (Miran version) | Class exists; not registered; lookup rejected |
| `inventory` | Prints the bot's inventory OR returns how many of an item the bot has | Class exists; not registered; lookup rejected |
| `list` | List all obtainable items | Class exists; not registered; lookup rejected |
| `pause` | Pauses the bot after the task thats running (Still in development!) | Class exists; not registered; lookup rejected |
| `stash` | Store an item in a chest/container stash. Will deposit ALL non-equipped items if item list is empty. | Class exists; not registered; lookup rejected |
| `status` | Get status of currently executing step or last completed step | Class exists; not registered; lookup rejected |
| `unpause` | UnPauses the bot (Still in development!) | Class exists; not registered; lookup rejected |

`gamer` is the original “beat Minecraft” command. Its handler constructs `BeatMinecraftTask`,
which contains substantial speedrun logic; it is not simply an empty placeholder. But no
`new GamerCommand()` is registered in the default NPC command initializer, and no
`beat_minecraft` alias is defined there. Therefore normal NPC command dispatch cannot
start it in this artifact. Enabling registration alone would not prove the old speedrun
works in 1.21.1; it needs an isolated-world acceptance test before being advertised.

`pause` / `unpause` / `status` here refer to NPC commands. They must not be confused with
similarly named low-level Automatone controls or the server's chain-status diagnostics.

## Building and farm examples to validate in Minecraft

- `setup`: full starter gear/supplies plus three placed stations; no farm or house.
- `setup_farm`: choose a nearby safe plot or resume an eligible unfinished farm.
- `setup_farm bot`: exact block beneath the NPC; `owner` uses the owner, not gaze.
- `plant_farm wheat_seeds=32,carrot=16`: plant a recorded farm; setup alone does not plant.
- `harvest_farm`: harvest mature crops once; no perpetual farming guarantee.
- `get cobblestone 64`: acquire cobblestone, not build a cobblestone structure.
- `build_structure 100 64 100 "cobblestone shelter" "5 by 5 shelter with walls and roof"`:
  selects a matching local blueprint when supported, otherwise reports an unsupported local template without AI fallback. Use disposable coordinates.

There is no canonical `build a huge cobblestone` command in this NPC registry. The local exact-owner route recognizes supported build phrases and starts the
corresponding local blueprint through `build_structure`. Very large builds, material availability, placement verification, interruption and
resumption all need live tests; do not infer support from a command description.

## Low-level original Automatone commands

This is a separate registry reached through the `/playerengine <command...>` fallback
in `DefaultCommands`, subject to server permissions and an available automaton NPC.
These IDs are not interchangeable with the NPC commands above: for example, low-level
`build` loads a schematic, while NPC `build_structure` selects local blueprints.
Static registration/constructor inspection only; no in-world execution test was run.

| Command class | IDs / aliases |
| --- | --- |
| AxisCommand | `axis`, `highway` |
| BlacklistCommand | `blacklist` |
| BuildCommand | `build` |
| ChestsCommand | `chests` |
| ClickCommand | `click` |
| ComeCommand | `come` |
| ETACommand | `eta` |
| ExploreCommand | `explore` |
| ExploreFilterCommand | `explorefilter` |
| FindCommand | `find` |
| FishCommand | `fish` |
| FollowCommand | `follow` |
| ForceCancelCommand | `forcecancel` |
| GcCommand | `gc` |
| GoalCommand | `goal` |
| GotoCommand | `goto` |
| HelpCommand | `help`, `?` |
| InvertCommand | `invert` |
| MineCommand | `mine` |
| PathCommand | `path` |
| ProcCommand | `proc` |
| ReloadAllCommand | `reloadall` |
| RenderCommand | `render` |
| RepackCommand | `repack`, `rescan` |
| SaveAllCommand | `saveall` |
| SchematicaCommand | `schematica` |
| SelCommand | `sel`, `selection`, `s` |
| SetCommand | `set`, `setting`, `settings` |
| SurfaceCommand | `surface`, `top` |
| ThisWayCommand | `thisway`, `forward` |
| TunnelCommand | `tunnel` |
| VersionCommand | `version` |
| WaypointsCommand | `waypoints`, `waypoint`, `wp` |

Additional registered aliases: `modified`, `mod`, `com/player2/playerengine/automaton`,
`modifiedsettings` → `set modified`; `reset` → `set reset`; `sethome` → `waypoints save home`;
`home` → `waypoints goto home`. Execution controls supply `pause`/`p`, `resume`/`r`, `paused`,
and `cancel`/`c`/`stop` through `ExecutionControlCommands.registerCommands` invoked by
`BaritoneCommandManager`. Settings can also be queried/toggled by name; that set is dynamic.
The `damn` and `orderpizza` strings are easter eggs in the player-command wrapper, not tasks.

## Server administration and diagnostics

The JAR also declares `/playerengine` routes for `help`, `relog`, `summon`, `queue clear`,
`rag` (retrieve/reload/audit tail/reset-learned/inspect), `routing probe`, `capability`
(status/query/inspect/rebuild/enrich), `resolve`, `memory status`, `player2` configuration,
`budget` settings and `chain status`. These are administrative/diagnostic command families,
not NPC project tasks; they may require operator permission or account services.

Help text advertises `tpto`, and a builder method exists, but the inspected
`MCCommands.register` chain does not call `registerTpto`. Its presence in help is therefore
not evidence of a working route in this JAR. External extensions can register their own
routes; test the actual installed combination before concluding teleport is unavailable.

## Live test record needed per game command

Record the exact JAR hash, Player2NPC version, entered text/prefix, accepted/rejected response,
`CORE_GOAL_SUBMIT` receipt where applicable, task tree/progress, actual inventory or block
changes, and final result. Check stop/replacement while running. For farms, verify irrigated
plot, planted crop counts and mature-only harvest separately. For builds, verify every final
block and interruption/reload behavior. No command in this report is marked LIVE / PASS.

## Local fortified-village template

The earlier audit found `FastStarterBlueprint.maybeCode` before model requests.
The current native orchestrator calls that local selector directly and never starts
the LLM request task. `BlueprintLibrary.choose` recognizes village/settlement/fortified-town
requests and supplies a fixed 47x47 fortified-village plan. Thus the earlier blanket
statement that building always needs AI was too broad. Unsupported requests now fail locally without model-generated code.

Tested directly from the hotfix JAR: local generation and the actual
`StructureFromCode` compiler/runner produced 5,407 operations: 3,533 cobblestone,
378 dirt and 1,496 air/clearing cells. This fits the builder's actual 6,144-operation
and 240,000-character limits (the generated program is 180,038 characters).
The template includes eight buildings, four corner towers, perimeter walls, roads
and a central feature. It is a mixed-material template, not arbitrary town planning.
There is no separate registered `build_village` ID; it is a `build_structure` template.
Actual survival acquisition, construction, interruption and final world verification
were not run. These parser checks do not establish that the village completes in-game.
