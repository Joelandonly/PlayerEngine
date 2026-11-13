package com.player2.playerengine.tasks.construction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;

/** Plans a single support column without changing the world. */
public final class GroundedFoundation {
   public static final int MAX_DEPTH = 12;

   public enum Cell { SOLID, DRY_AIR, BLOCKED, UNREADABLE }

   public record Column(List<BlockPos> placements, BlockPos obstruction, Cell failure) {
      public boolean ready() {
         return this.failure == null;
      }
   }

   private GroundedFoundation() {}

   public static Column plan(BlockPos top, Function<BlockPos, Cell> inspect) {
      List<BlockPos> gap = new ArrayList<>();
      for (int depth = 0; depth <= MAX_DEPTH; depth++) {
         BlockPos position = top.above(-depth);
         Cell cell = inspect.apply(position);
         if (cell == Cell.SOLID) {
            Collections.reverse(gap);
            return new Column(List.copyOf(gap), null, null);
         }
         if (cell != Cell.DRY_AIR) return new Column(List.of(), position, cell);
         gap.add(position);
      }
      return new Column(List.of(), top, Cell.DRY_AIR);
   }
}
