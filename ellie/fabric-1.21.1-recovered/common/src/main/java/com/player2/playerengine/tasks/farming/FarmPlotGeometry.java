package com.player2.playerengine.tasks.farming;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

public final class FarmPlotGeometry {
   private FarmPlotGeometry() {
   }

   public static List<BlockPos> allCells(BlockPos center) {
      Objects.requireNonNull(center, "center");
      ArrayList<BlockPos> cells = new ArrayList<>(81);

      for (int dz = -4; dz <= 4; dz++) {
         boolean reverse = (dz + 4 & 1) != 0;
         if (reverse) {
            for (int dx = 4; dx >= -4; dx--) {
               cells.add(center.offset(dx, 0, dz).immutable());
            }
         } else {
            for (int dx = -4; dx <= 4; dx++) {
               cells.add(center.offset(dx, 0, dz).immutable());
            }
         }
      }

      return List.copyOf(cells);
   }

   public static List<BlockPos> soilCells(BlockPos center) {
      ArrayList<BlockPos> cells = new ArrayList<>(80);

      for (BlockPos cell : allCells(center)) {
         if (!cell.equals(center)) {
            cells.add(cell);
         }
      }

      return List.copyOf(cells);
   }

   public static boolean contains(BlockPos center, BlockPos position) {
      Objects.requireNonNull(center, "center");
      Objects.requireNonNull(position, "position");
      return position.getY() == center.getY()
         && Math.abs(position.getX() - center.getX()) <= 4
         && Math.abs(position.getZ() - center.getZ()) <= 4;
   }

   public static List<BlockPos> autoCandidateCenters(BlockPos anchor) {
      Objects.requireNonNull(anchor, "anchor");
      int radius = 48;
      long radiusSquared = (long)radius * radius;
      LinkedHashSet<BlockPos> sampled = new LinkedHashSet<>();

      for (int dz = -8; dz <= 8; dz++) {
         for (int dx = -8; dx <= 8; dx++) {
            if ((long)dx * dx + (long)dz * dz <= 64L) {
               sampled.add(anchor.offset(dx, 0, dz).immutable());
            }
         }
      }

      for (int dz = -45; dz <= 45; dz += 5) {
         for (int dx = -45; dx <= 45; dx += 5) {
            if ((long)dx * dx + (long)dz * dz <= radiusSquared) {
               sampled.add(anchor.offset(dx, 0, dz).immutable());
            }
         }
      }

      sampled.add(anchor.offset(radius, 0, 0).immutable());
      sampled.add(anchor.offset(-radius, 0, 0).immutable());
      sampled.add(anchor.offset(0, 0, radius).immutable());
      sampled.add(anchor.offset(0, 0, -radius).immutable());
      ArrayList<BlockPos> candidates = new ArrayList<>(sampled);
      candidates.sort(candidateComparator(anchor));
      if (candidates.size() > 512) {
         throw new IllegalStateException("farm candidate sampling exceeded the fixed cap");
      } else {
         return List.copyOf(candidates);
      }
   }

   public static List<BlockPos> scanEnvelope(BlockPos center) {
      Objects.requireNonNull(center, "center");
      int radius = 5;
      ArrayList<BlockPos> positions = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1) * 5);

      for (int dy = -1; dy <= 3; dy++) {
         for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
               positions.add(center.offset(dx, dy, dz).immutable());
            }
         }
      }

      return List.copyOf(positions);
   }

   public static List<BlockPos> stanceCandidates(BlockPos center, BlockPos actionTarget) {
      return stanceCandidates(center, actionTarget, 1, true);
   }

   public static List<BlockPos> clearStanceCandidates(BlockPos center, BlockPos actionTarget) {
      ArrayList<BlockPos> candidates = new ArrayList<>(stanceCandidates(center, actionTarget));
      candidates.addAll(stanceCandidates(center, actionTarget, 2, false));
      return List.copyOf(candidates);
   }

   private static List<BlockPos> stanceCandidates(BlockPos center, BlockPos actionTarget, int feetOffset, boolean excludeCenterSupport) {
      Objects.requireNonNull(center, "center");
      Objects.requireNonNull(actionTarget, "actionTarget");
      BlockPos surface = new BlockPos(actionTarget.getX(), center.getY(), actionTarget.getZ());
      ArrayList<BlockPos> inside = new ArrayList<>(4);
      ArrayList<BlockPos> outside = new ArrayList<>(4);

      for (Direction direction : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
         BlockPos horizontalSupport = surface.relative(direction);
         if (!excludeCenterSupport || !horizontalSupport.equals(center)) {
            BlockPos feet = horizontalSupport.above(feetOffset).immutable();
            if (contains(center, horizontalSupport)) {
               inside.add(feet);
            } else {
               outside.add(feet);
            }
         }
      }

      inside.addAll(outside);
      return List.copyOf(inside);
   }

   private static Comparator<BlockPos> candidateComparator(BlockPos anchor) {
      return Comparator.<BlockPos>comparingLong(position -> horizontalDistanceSquared(anchor, position))
         .thenComparingInt(Vec3i::getX)
         .thenComparingInt(Vec3i::getY)
         .thenComparingInt(Vec3i::getZ);
   }

   public static long horizontalDistanceSquared(BlockPos first, BlockPos second) {
      long dx = (long)first.getX() - second.getX();
      long dz = (long)first.getZ() - second.getZ();
      return dx * dx + dz * dz;
   }
}
