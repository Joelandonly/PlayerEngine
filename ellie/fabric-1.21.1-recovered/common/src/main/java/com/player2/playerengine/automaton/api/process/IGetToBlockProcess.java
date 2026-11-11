package com.player2.playerengine.automaton.api.process;

import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;

public interface IGetToBlockProcess extends IBaritoneProcess {
   void getToBlock(BlockOptionalMeta var1);

   boolean blacklistClosest();
}
