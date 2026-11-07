# Schematic native task bindings v18.2

Build ID: `20261107-schematic-native-bindings-v18.2`. Live gameplay remains
unverified for this revision.

## Supplied failure

The new run used v18.1, revision `9b4a674`, and
`ellie schematic_prepare 123.litematic -436 79 96`. The 20×133×20 file imported
with 2,130 targets, and site clearance passed. Ellie acquired/crafted a chest.
The first depot foundation at `-439 79 93` failed to create `PlaceBlockTask`.
An alternate depot chest was successfully placed at `-439 79 95`, verified in
the world and consumed from inventory. Travel to it then failed with
`NoSuchMethodException: GetToBlockTask constructor`. The controller kept ticking;
the inventory had 17 empty main slots. This is a task-construction failure.

Both errors have one shared cause. `FoodAcquisition.construct` recognized boxed
integers but rejected boxed booleans for primitive boolean parameters. Both
constructors exist in the pinned engine. The same error would also prevent
later trapdoor, double-slab and water interactions.

## Repair and preservation

The shared resolver now matches all eight primitive wrappers, preserves actual
argument values and assignable reference/array types, permits null references
and rejects null primitive arguments. A missing signature includes the supplied
argument types. A constructor's own exception still propagates; it does not
silently select another action.

Only **FoodAcquisition.class** changes in production. All other **1,901 classes**
and every other resource are frozen against the delivered v18.1 JAR, apart from
the two build identity records. The 32 protected setup hashes, native builder,
navigation, fall protection, storage workers, commands and Player2 integration
remain unchanged. There is no new controller or source migration.

## Validation

- The new real-game test fails against the previous packaged v18.1 JAR with the
  exact `PlaceBlockTask` constructor error from this run.
- The repaired packaged resolver passes 11 checks for primitive/reference
  arguments, null handling, wrong types/arity and constructor exceptions.
- A separate test bootstraps actual Minecraft 1.21.1 registries and runs 20
  checks against actual packaged native constructor bodies: foundation, chest,
  travel, counted acquisition, exact deposit/withdrawal, trapdoor, double slab
  and water interaction. It checks target positions, materials, counts and
  distinct false/true flags. No replacement Minecraft or worker classes are
  present on this test's classpath.
- GitHub Actions downloads hash-verified official game libraries and pinned
  Fabric mappings/remapper for that test. It gates artifact publication. The
  game dependencies are test-only and are not bundled in Ellie.
- The complete existing Java and Node regression suites, schematic coordinator
  success/failure scenarios, parser fixtures, world-boundary tests, packaged
  hook checks, build identity checks and preservation hashes are also required.

The real-game test constructs tasks and boots registries; it does **not** tick
an NPC or load a survival world. Existing coordinator tests still substitute
game actions. Neither proves the complete mob farm can be gathered and built.

## Live check

Replace the previous Ellie PlayerEngine JAR, keeping only one Ellie/PlayerEngine
JAR installed. Confirm startup shows `20261107-schematic-native-bindings-v18.2`
and the delivered commit. Rerun the same file and coordinates:

```
ellie schematic_prepare 123.litematic -436 79 96
```

Saved depots and current stock are rechecked. Verify Ellie walks to the existing
project chest, transfers counted materials, expands storage if required, and
reports preparation complete only after actual stock verification. An uneven
depot site should now construct its foundation task instead of rejecting the
signature. Navigation and physical placement can still fail for separate live
reasons, which must remain bounded and report specific blockers.

Before the tall farm, use the included small `smoke.litematic` at a clear site
to exercise construction, trapdoor state and water. The full farm's tall access,
placement, depot UI, material gathering and fluid settling still need live tests.
