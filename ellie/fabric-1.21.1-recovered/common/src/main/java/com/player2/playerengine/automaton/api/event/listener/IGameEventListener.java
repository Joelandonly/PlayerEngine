package com.player2.playerengine.automaton.api.event.listener;

import com.player2.playerengine.automaton.api.event.events.BlockInteractEvent;
import com.player2.playerengine.automaton.api.event.events.PathEvent;

public interface IGameEventListener {
   void onTickServer();

   void onBlockInteract(BlockInteractEvent var1);

   void onPathEvent(PathEvent var1);
}
