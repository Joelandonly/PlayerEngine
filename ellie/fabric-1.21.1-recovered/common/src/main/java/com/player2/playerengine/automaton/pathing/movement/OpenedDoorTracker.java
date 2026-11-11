package com.player2.playerengine.automaton.pathing.movement;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;

public final class OpenedDoorTracker {
   private static final int MAX_TRACKED = 64;
   private static final Map<IBaritone, OpenedDoorTracker> INSTANCES = new WeakHashMap<>();
   private final Map<OpenedDoorTracker.TrackedDoor, Boolean> doors = new LinkedHashMap<>();

   public static OpenedDoorTracker forBaritone(IBaritone baritone) {
      synchronized (INSTANCES) {
         return INSTANCES.computeIfAbsent(baritone, b -> new OpenedDoorTracker());
      }
   }

   public void recordOpened(ServerLevel level, BlockPos pos) {
      OpenedDoorTracker.TrackedDoor key = new OpenedDoorTracker.TrackedDoor(level.dimension(), pos.immutable());
      if (!this.doors.containsKey(key)) {
         if (this.doors.size() >= 64) {
            Iterator<OpenedDoorTracker.TrackedDoor> it = this.doors.keySet().iterator();
            if (it.hasNext()) {
               it.next();
               it.remove();
            }
         }

         this.doors.put(key, Boolean.TRUE);
      }
   }

   public void closeClearedDoors(IEntityContext ctx, Set<BlockPos> occupiedOrNeeded) {
      if (!this.doors.isEmpty()) {
         ServerLevel level = ctx.world();
         ResourceKey<Level> here = level.dimension();
         Iterator<Entry<OpenedDoorTracker.TrackedDoor, Boolean>> it = this.doors.entrySet().iterator();

         while (it.hasNext()) {
            OpenedDoorTracker.TrackedDoor door = it.next().getKey();
            if (!door.dimension.equals(here)) {
               it.remove();
            } else {
               BlockPos pos = door.pos;
               if (!occupiedOrNeeded.contains(pos) && !occupiedOrNeeded.contains(pos.above()) && !occupiedOrNeeded.contains(pos.below())) {
                  if (!level.hasChunkAt(pos)) {
                     it.remove();
                  } else {
                     BlockState state = level.getBlockState(pos);
                     if (!(state.getBlock() instanceof DoorBlock door1 && DoorBlock.isWoodenDoor(state))) {
                        it.remove();
                     } else if (!(Boolean)state.getValue(DoorBlock.OPEN)) {
                        it.remove();
                     } else if ((Boolean)state.getValue(DoorBlock.POWERED)) {
                        it.remove();
                     } else if (!entityStandingIn(level, pos)) {
                        door1.setOpen(ctx.entity(), level, state, pos, false);
                        it.remove();
                     }
                  }
               }
            }
         }
      }
   }

   public void clear() {
      this.doors.clear();
   }

   private static boolean entityStandingIn(ServerLevel level, BlockPos pos) {
      AABB box = new AABB(pos).expandTowards(0.0, 1.0, 0.0);
      Iterator var3 = level.getEntities((Entity)null, box, ex -> ex.isAlive() && !ex.isSpectator()).iterator();
      if (var3.hasNext()) {
         Entity e = (Entity)var3.next();
         return true;
      } else {
         return false;
      }
   }

   private static final class TrackedDoor {
      private final ResourceKey<Level> dimension;
      private final BlockPos pos;

      private TrackedDoor(ResourceKey<Level> dimension, BlockPos pos) {
         this.dimension = dimension;
         this.pos = pos;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof OpenedDoorTracker.TrackedDoor other) ? false : this.dimension.equals(other.dimension) && this.pos.equals(other.pos);
         }
      }

      @Override
      public int hashCode() {
         return 31 * this.dimension.hashCode() + this.pos.hashCode();
      }
   }
}
