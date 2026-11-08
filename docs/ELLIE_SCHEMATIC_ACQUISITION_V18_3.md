# Schematic acquisition and material progress v18.3

Build ID: `20261108-schematic-native-acquisition-v18.3`.
Live survival execution remains unverified for this revision.

## Supplied run and cause

The supplied v18.2 run reached project storage and deposited supplies. It then
requested 64 cobblestone slabs through `CraftMacroResourceTask`, which collected
cobblestone but stopped with `no further progress toward the craft`. The owner's
separate `agentic get cobblestone_slab 64` chose native `CraftInTableTask` and
succeeded. On restarting preparation, 66 slabs were recognised and deposited in
two verified transfers (64 and 2). The next 64-slab request was a further batch
toward the blueprint's 404 slabs, not lost memory of the first batch.

There were two acquisition routes: schematic preparation preferred the dynamic
macro, while agentic resource requests used the native catalogue directly. There
was also a shared macro convergence fault: after more than 64 resolver calls,
the brief transition from completed ingredient collection to crafting could
trigger the attempt/no-progress guard before the next phase ran.

## Repair

- Both agentic resource tasks and schematic preparation now call the same native
  catalogue lookup. The schematic remains the owning task, with its own verified
  inventory/storage/world postconditions; no nested agentic command or replacement
  supervisor is created. Catalogue misses retain the existing dynamic recipe
  fallback. An exact `white_carpet` catalogue entry is absent in this pinned
  engine, so its dynamic fallback remains necessary.
- The shared macro grants one convergence handoff when a collection episode has
  no external ingredients left and the resolver still requires crafting. This
  re-arms convergence accounting for the next phase. It grants at most eight
  handoffs per macro instance; repeated flat collection and phase cycling remain
  bounded. UNOBTAINABLE results are handled first, and no output or completion
  is fabricated. Original table, collection and failed-craft guards remain.
- Preparation reports the required amount, verified chest stock, actual carried
  amount, remaining amount to gather and current inventory target. For the run:
  `required=404 stored=66 carried=0 remainingToGather=338 batchTarget=64`.
  A separate `schematicMaterials` observatory field keeps that information visible.
  Stock is explicitly labelled `lastVerifiedChestScan` while travelling/gathering;
  logging does not force-load or read distant chest chunks. Decisions and transfer
  verification still rescan the actual chest slots.
- Action progress starts from the actual starting inventory, so already-owned
  equipment is not credited as a new gain on the first action tick.

## Validation and scope

The complete Java and Node suites are required. Added coverage includes:

- An extracted **actual packaged convergence method**, with explicit substituted
  resolver/world/action signals, reproduces the prior release's false failure
  after a long gather. The repaired method passes collection handoff, subsequent
  crafting, repeated empty collection, stalled table, unavailable ingredients,
  unsatisfiable inventory craft and repeated-phase bounds.
- Every original macro method and field is compared with a receipt from the
  delivered v18.2 JAR after removing the one reviewed convergence hook. This
  preserves all existing recipe execution and failed-action logic.
- The full production schematic coordinator prepares 404 slabs from 66 already
  carried, respects six-output recipe batches, stages exactly the required stock,
  retains surplus and owner gear, resumes after partial stocking, and reports a
  failed later batch without losing the 66 already stored. Native actions/world
  in those coordinator scenarios are substitutes.
- Real Minecraft 1.21.1 registries and actual packaged native task constructors
  verify the native catalogue choices for project materials, identical delegation
  through the agentic factory, quantity validation and explicit catalogue misses.
  Packaged wiring checks require native lookup to precede dynamic macro creation.

Only five production class files may differ from v18.2: the shared agentic
resource integration, schematic coordinator, schematic world adapter and its
depot inner class, and the shared crafting macro. All other **1,897 classes** and
resources are frozen against delivered v18.2, apart from build identity. The
depot inner class's native instructions are unchanged; its source line numbers
move with the adapter edit. All **32 protected setup classes** retain their
proven hashes. Navigation, bridging, descent protection, native storage workers,
Player2 integration and the working 14-stage setup are preserved.

## Live check

Install only the new Ellie/PlayerEngine JAR and confirm the startup build ID and
delivered commit. Rerun the same file and origin:

```
ellie schematic_prepare 123.litematic -436 79 96
```

The next slab acquisition should name `route=native_catalogue` and
`worker=CraftInTableTask`. Inspect carried and chest quantities while subsequent
batches are crafted/deposited. Previously staged slabs must count toward the
total; any new blocker must retain its specific reason and stored quantity.

These automated checks do not tick a live NPC or execute the real survival
recipe/UI/pathing chain. Live material collection, table access, chest transfers,
dynamic white-carpet production and complete preparation/construction still
require Minecraft testing. Test the included small smoke schematic before the
tall farm. A successful build or mocked coordinator does not prove gameplay.
