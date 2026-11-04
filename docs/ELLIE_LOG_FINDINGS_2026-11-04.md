# Findings from maintainer-supplied logs (2026-11-04)

Sources: the supplied `latest.log`, three archived game logs, Observatory status
and event exports. Raw logs stay outside the checkout. Version strings do not
identify a patched JAR uniquely; these sources do not provide an artifact checksum.

## Setup reported a narrower success than the requested starter base

At log time 22:17:47 in `2026-11-03-6.log.gz`, setup reports five optional items
skipped and says chest items were acquired but double chests were not placed.
The supervisor reports success. Earlier lines show bucket, chestplate, helmet,
boots and bread failures. This matches the old inventory-only contract; it does
not establish that a full kit or placed base was complete.

The maintainer now requires the full kit and placed workstations/storage. The new
preview changes that contract; the old success line is not acceptance evidence.

## Cobblestone changes falsely counted toward iron crafting

The status export shows an iron-chestplate objective with zero iron ingots while
cobblestone increases repeatedly, including 149 through 193. Each fingerprint
change is recorded as material progress. The old ledger also treats decreases as
progress, allowing unrelated gains or loss/regain cycles to reset a stall timer.

The regression excludes cobblestone from iron-equipment progress and accepts only
new material high-water marks. It does not prove that iron gathering/smelting works.

## Crafting approach and interaction reach disagree

In `latest.log`, log times 12:04:19 and 12:05:34 show iron leggings in
`MOVE_TO_TABLE`, targeting (7,59,8), while the NPC is near (7.48,56,8.24) and
(7.49,56,8.46), on ground. The task tree says “Getting within 3 blocks” and
“Completing goal.” A feet-cell navigation goal can be accepted while the separate
crafting reach/line-of-sight test is still false.

The preview tightens only the crafting macro's approach radius from three to one;
the native reach check and timeout remain. This is a candidate correction awaiting
live verification. Repeated GPS/local table selection also needs acceptance checks.

## Block-edge observation: ELLIE-023

The maintainer reports waiting with most of the visible body over the next block.
The event export includes a stall near fractional X=0.024, but recent stalls are
not all at edges. There is no proof of one universal cause. New diagnostics add
feet cell, the cell below it and collision bounds. The cell below feet is not
asserted to be the actual supporting collision shape.

## Distinct failures and limits

The latest furnace macro fails despite eight cobblestone, then retries and verifies
furnace acquisition. This is evidence of recovery in that run, not a universal fix.
Bucket/chestplate failures persist. The latest setup ends in `CANCELLED_OPERATOR`,
not completion. ChatGPT timeout/503 errors are a separate conversation path.

The logs also contain a recurring missing NPC skin asset and intermittent server
overload warnings. These are separate observations, not verified causes of the
crafting stall. The skin belongs to the companion integration; this preview does
not claim to fix it, and performance needs profiling rather than inferred causation.

Live checks still need to verify all required items and ten placed blocks, safe
site handling, no duplicate base on rerun, stop/resume/reload, and the observed
crafting approach. Local tests substitute game boundaries and do not replace that.

## Later run: setup mined its own foundation, then failed

The newest event export shows cobblestone placed at (6,62,-4) and (7,62,0),
then mined at those same coordinates during resource acquisition. Game-log times
14:08:47 and 14:09:20 (UTC 13:08:47 and 13:09:20) record two setup generations
failing with “Base site is occupied, obstructed or unsupported; no blocks will
be broken.” These are controlled task failures, not operator cancellation.
Restarting correctly skips existing bootstrap items but repeats the base failure.
This later evidence is separate from the earlier cancelled runs above.

The protected-layer correction reuses the shipped village resource-source guard
and revalidates preparation before station placement. It prevents acquisition from
using the active base footprint as a mine while allowing deliberate site excavation.
See `ELLIE_SETUP_LAYERS_HOTFIX.md`; complete in-game acceptance remains pending.
