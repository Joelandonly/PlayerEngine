package adris.altoclef.tasks.construction.build_structure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Computes the additional inventory quantities needed to construct the
 * remaining blueprint blocks. Pure Java so it can be tested without Minecraft.
 */
public final class ConstructionMaterialPlanner {
    private ConstructionMaterialPlanner() {}

    public static <T> Map<T, Integer> missing(Map<T, Integer> remainingBlocks,
                                               ToIntFunction<T> inventoryCount) {
        Objects.requireNonNull(remainingBlocks, "remainingBlocks");
        Objects.requireNonNull(inventoryCount, "inventoryCount");
        Map<T, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<T, Integer> entry : remainingBlocks.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid construction material requirement");
            int owned = Math.max(0, inventoryCount.applyAsInt(entry.getKey()));
            int deficit = Math.max(0, entry.getValue() - owned);
            if (deficit > 0) missing.put(entry.getKey(), deficit);
        }
        return missing;
    }
}
