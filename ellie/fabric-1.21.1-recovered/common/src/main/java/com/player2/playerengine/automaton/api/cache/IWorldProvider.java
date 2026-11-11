package com.player2.playerengine.automaton.api.cache;

import com.player2.playerengine.automaton.api.component.WorldComponentKey;
import com.player2.playerengine.automaton.cache.WorldProvider;

public interface IWorldProvider {
   WorldComponentKey<IWorldProvider> KEY = new WorldComponentKey<>(WorldProvider::new);

   IWorldData getCurrentWorld();
}
