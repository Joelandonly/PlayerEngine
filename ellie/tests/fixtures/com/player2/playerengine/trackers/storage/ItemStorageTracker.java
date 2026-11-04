package com.player2.playerengine.trackers.storage;
import java.util.*;
/** Inventory-count boundary only; does not simulate storage or game item identity. */
public final class ItemStorageTracker {
    public final Map<String,Integer> counts = new HashMap<>();
    public boolean unavailable;
    public int getItemCountInventoryOnly(String[] matches) {
        if (unavailable) throw new IllegalStateException("inventory unavailable");
        return Arrays.stream(matches).mapToInt(id -> counts.getOrDefault(id,0)).sum();
    }
}
