package com.player2.playerengine.memory;

import com.player2.playerengine.PlayerEngine;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

public final class MemoryStoreRegistry {
   private static final Map<MemoryScope, MemoryStore> STORES = new ConcurrentHashMap<>();

   private MemoryStoreRegistry() {
   }

   public static MemoryStore getOrLoad(MinecraftServer server, MemoryScope scope) {
      if (server != null && scope != null) {
         MemoryStore existing = STORES.get(scope);
         if (existing != null) {
            return existing;
         }

         try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            return STORES.computeIfAbsent(scope, s -> MemoryStore.loadForCompanion(worldRoot, s));
         } catch (RuntimeException e) {
            PlayerEngine.LOGGER.warn("Memory: store load failed for {} ({}).", scope, e.getClass().getSimpleName());
            return null;
         }
      } else {
         return null;
      }
   }

   public static MemoryStore peek(MemoryScope scope) {
      return scope == null ? null : STORES.get(scope);
   }

   public static void flushAllIfDirty(MinecraftServer server) {
      if (server != null && !STORES.isEmpty()) {
         long nowTick = currentTick(server);

         for (MemoryStore store : STORES.values()) {
            try {
               store.flushIfDirty(nowTick);
            } catch (RuntimeException e) {
               PlayerEngine.LOGGER.warn("Memory: tick-end flush failed ({}).", e.getClass().getSimpleName());
            }
         }
      }
   }

   public static void clearAll(MinecraftServer server) {
      long nowTick = server != null ? currentTick(server) : 0L;

      for (MemoryStore store : STORES.values()) {
         try {
            store.clear(nowTick);
         } catch (RuntimeException e) {
            PlayerEngine.LOGGER.warn("Memory: final flush failed ({}).", e.getClass().getSimpleName());
         }
      }

      STORES.clear();
   }

   private static long currentTick(MinecraftServer server) {
      try {
         return server.overworld() != null ? server.overworld().getGameTime() : 0L;
      } catch (RuntimeException e) {
         return 0L;
      }
   }
}
