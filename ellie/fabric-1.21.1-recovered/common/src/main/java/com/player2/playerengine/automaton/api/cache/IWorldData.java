package com.player2.playerengine.automaton.api.cache;

public interface IWorldData {
   ICachedWorld getCachedWorld();

   IWaypointCollection getWaypoints();

   IContainerMemory getContainerMemory();

   void addBlockPosToCache(int var1, int var2);
}
