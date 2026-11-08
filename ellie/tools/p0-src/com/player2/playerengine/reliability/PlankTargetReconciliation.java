package com.player2.playerengine.reliability;

import static com.player2.playerengine.nativecommands.NativeAccess.call;

/**
 * Native CraftWithMatchingMaterialsTask calculated a deficit of planks, then
 * passed it to TaskCatalogue as an ABSOLUTE inventory target. With 92 held
 * planks and 92 additional needed it asked for a total of 92, finished
 * immediately, and tried the same child on the next tick forever.
 *
 * This is intentionally limited to the one reviewed getAllSameResourcesTask
 * call site. It neither creates items nor changes any crafting recipe.
 */
public final class PlankTargetReconciliation {
    private PlankTargetReconciliation() {}

    /** Pure, checked conversion of a missing quantity into an absolute target. */
    public static int absoluteCount(int held, int additional) {
        if (held < 0 || additional < 1) throw new IllegalArgumentException(
            "Invalid held/shortfall counts: "+held+"/"+additional);
        return Math.addExact(held,additional);
    }

    /**
     * Called only by the pinned native matching-plank acquisition task.
     * Use its OWN inventory tracker and item match set, not an unrelated
     * schematic copy of inventory state.
     */
    public static int absoluteTarget(Object controller,Object sameResourceTarget,int shortfall) {
        try {
            Object tracker=call(controller,"getItemStorage");
            Object matches=call(sameResourceTarget,"getMatches");
            int held=((Number)call(tracker,"getItemCount",matches)).intValue();
            return absoluteCount(held,shortfall);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot verify native plank inventory before re-targeting",exception);
        }
    }
}
