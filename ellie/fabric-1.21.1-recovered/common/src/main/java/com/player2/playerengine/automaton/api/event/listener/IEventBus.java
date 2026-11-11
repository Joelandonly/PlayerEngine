package com.player2.playerengine.automaton.api.event.listener;

public interface IEventBus extends IGameEventListener {
   void registerEventListener(IGameEventListener var1);
}
