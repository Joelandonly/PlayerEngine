package com.player2.playerengine.reliability;

import static com.player2.playerengine.nativecommands.NativeAccess.call;
import java.lang.reflect.Array;

/**
 * Native CraftWithMatchingMaterialsTask calculated a deficit of planks, then
 * passed it to TaskCatalogue as an ABSOLUTE inventory target. With 92 held
 * planks and 92 additional needed it asked for a total of 92, finished
 * immediately, and tried the same child on the next tick forever.
 *
 * Both reviewed native shortfall handoffs must convert the missing quantity
 * into an absolute inventory target. This helper does not create items or
 * change crafting recipes.
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
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot verify native plank inventory before re-targeting",exception);
        }
    }

    /**
     * The SECOND native handoff occurs in CraftWithMatchingPlanksTask:
     * when a log can supply the last few planks, it requests exactly ONE
     * plank from TaskCatalogue even if 188 planks already exist. The child
     * treats an absolute target of 1 as complete, never consuming the log.
     *
     * Request the current amount plus one; crafting one log produces four
     * more planks and lets the parent proceed to the trapdoor recipe.
     */
    public static int absoluteTargetForItem(Object controller,Object item,int additional) {
        try {
            if (item==null) throw new IllegalArgumentException("Null resource item");
            Object matches=Array.newInstance(item.getClass(),1);
            Array.set(matches,0,item);
            Object tracker=call(controller,"getItemStorage");
            int held=((Number)call(tracker,"getItemCount",matches)).intValue();
            return absoluteCount(held,additional);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot verify specific plank inventory before re-targeting",exception);
        }
    }
}
