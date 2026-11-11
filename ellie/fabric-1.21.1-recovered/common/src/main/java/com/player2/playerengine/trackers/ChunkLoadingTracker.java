package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.observatory.ChunkTicketWatchdog;
import com.player2.playerengine.util.ChunkController;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public class ChunkLoadingTracker {
   private List<ChunkPos> chunks = new ArrayList<>();
   private int ticks = 20;
   private long playerLastSeen = -1L;
   private LivingEntity entity;

   public ChunkLoadingTracker(PlayerEngineController controller) {
      this.entity = controller.getEntity();
   }

   public boolean tick() {
      this.ticks--;
      if (this.ticks > 0) {
         return false;
      }

      this.ticks = 20;
      List players = this.entity.level().getEntitiesOfClass(Player.class, this.entity.getBoundingBox().inflate(48.0, 48.0, 48.0));
      if (!players.isEmpty()) {
         this.playerLastSeen = System.currentTimeMillis();
      }

      double x = this.entity.getX() / 16.0;
      double z = this.entity.getZ() / 16.0;
      List<ChunkPos> list = new ArrayList<>();
      list.add(new ChunkPos(Mth.floor(x), Mth.floor(z)));
      list.add(new ChunkPos(Mth.ceil(x), Mth.ceil(z)));
      list.add(new ChunkPos(Mth.floor(x), Mth.ceil(z)));
      list.add(new ChunkPos(Mth.ceil(x), Mth.floor(z)));

      for (ChunkPos chunk : list) {
         if (!this.chunks.contains(chunk)) {
            ChunkController.instance.load((ServerLevel)this.entity.level(), this.entity.getUUID(), chunk.x, chunk.z);
         }

         this.chunks.remove(chunk);
      }

      for (ChunkPos chunk : this.chunks) {
         ChunkController.instance.unload((ServerLevel)this.entity.level(), this.entity.getUUID(), chunk.x, chunk.z);
      }

      this.chunks = list;
      return false;
   }

   public void reset() {
      ChunkTicketWatchdog.reset(this);
      if (this.entity.level() instanceof ServerLevel) {
         ChunkController.instance
            .unload(
               (ServerLevel)this.entity.level(), this.entity.getUUID(), this.entity.chunkPosition().x, this.entity.chunkPosition().z
            );
         this.chunks.clear();
         this.playerLastSeen = 0L;
      }
   }
}
