package com.player2.playerengine.automaton.api.event.listener;

import com.player2.playerengine.automaton.api.event.events.BlockInteractEvent;
import com.player2.playerengine.automaton.api.event.events.PathEvent;

public interface AbstractGameEventListener extends IGameEventListener {
   @Override
   default void onTickServer() {
   }

   @Override
   default void onBlockInteract(BlockInteractEvent event) {
   }

   @Override
   default void onPathEvent(PathEvent event) {
   }
}
