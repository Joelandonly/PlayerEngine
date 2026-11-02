package adris.altoclef.tasks.construction.build_structure;

import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone Java smoke tests (no Minecraft classes or external test runner). */
public final class ConstructionMaterialPlannerTest {
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        Map<String, Integer> needed = new LinkedHashMap<>();
        needed.put("planks", 64);
        needed.put("cobblestone", 32);
        needed.put("torch", 4);
        Map<String, Integer> have = Map.of("planks", 20, "cobblestone", 32, "torch", 1);

        Map<String, Integer> missing = ConstructionMaterialPlanner.missing(
                needed, item -> have.getOrDefault(item, 0));
        check(missing.size() == 2, "only short materials are requested");
        check(missing.get("planks") == 44, "partial inventory is subtracted");
        check(missing.get("torch") == 3, "multiple types are handled");
        check(!missing.containsKey("cobblestone"), "completed requirements skipped");

        check(ConstructionMaterialPlanner.missing(needed, item -> 1000).isEmpty(),
                "fully supplied construction needs nothing");
        check(ConstructionMaterialPlanner.missing(Map.of("planks", 5), item -> -2)
                        .get("planks") == 5, "negative inventory observation is clamped");
        try {
            ConstructionMaterialPlanner.missing(Map.of("planks", -1), item -> 0);
            throw new AssertionError("negative material requirement must fail");
        } catch (IllegalArgumentException expected) {
            // pass
        }
        System.out.println("ConstructionMaterialPlannerTest: PASS");
    }
}
