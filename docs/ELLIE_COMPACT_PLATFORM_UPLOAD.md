# Player2 default + conditional compact platform, 2026-11-04

- Player2 NPC interpreter restored by preserving the original `Player2APIService.class` byte-for-byte; no ChatGPT local sidecar needed.
- Setup changed from eight chests plus mandatory 9x7 deck to one table, one furnace, one chest.
- Land-first selection: use existing solid ground. Clear only necessary station cells.
- Restored fallback for water, holes and elevation: **eight or fewer load-bearing cobblestone blocks** (three under stations, five under service aisle) and a minimal dry clearance. Support jobs only when no workable natural site is found.
- Preserve the same support Y coordinate on retries; never schedule a clearance job at a foundation coordinate.
- Register a temporary Baritone `avoidBlockBreak` predicate for the platform's exact floor support coordinates in addition to the setup resource-source exclusion. Both are released on completion/stop.
- Verify real block state after native tasks, repair bounded damage rather than marking a missing platform complete.
- Persist natural/platform mode separately with V3 journal markers; old compact V2 and 9x7 journals are not blindly migrated.
- Simulated foundation/water/elevation scenarios pass; **real Minecraft Baritone navigation remains to be tested**.
