package com.player2.playerengine.blockknowledge;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.acquisition.NativeAcquisitionPlanner;
import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;

/** Compatibility bridge: block_info/acquire_block now use the same native policy as
 * get, agentic get and schematic preparation. */
public final class KnowledgeAcquisition {
    private KnowledgeAcquisition() {}
    public static Optional<Task> task(PlayerEngineController mod,String wanted,int absoluteTarget) {
        BlockKnowledge.Entry entry=BlockKnowledge.byItem(wanted);
        if(entry==null || !entry.obtainable() || entry.itemForm()==null) return Optional.empty();
        return NativeAcquisitionPlanner.task(mod,entry.itemForm(),absoluteTarget,null);
    }
}
