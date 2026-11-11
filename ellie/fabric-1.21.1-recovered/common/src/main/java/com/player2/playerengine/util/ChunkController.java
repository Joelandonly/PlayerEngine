package com.player2.playerengine.util;

import com.player2.playerengine.observatory.ChunkTicketWatchdog;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public class ChunkController {
   public static ChunkController instance = new ChunkController();
   private HashMap<Long, List<UUID>> loaded = new HashMap<>();

   public ChunkController() {
      instance = this;
   }

   public void clear() {
      ChunkTicketWatchdog.clear();
   }

   public void unload(ServerLevel world, UUID id, int xChunk, int zChunk) {
      ChunkTicketWatchdog.unload(world, id, xChunk, zChunk);
   }

   public void load(ServerLevel world, UUID id, int xChunk, int zChunk) {
      ChunkTicketWatchdog.load(world, id, xChunk, zChunk);
   }

   public int size() {
      return ChunkTicketWatchdog.size();
   }
}
