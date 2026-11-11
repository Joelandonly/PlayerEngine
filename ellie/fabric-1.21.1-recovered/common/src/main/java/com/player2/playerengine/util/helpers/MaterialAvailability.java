package com.player2.playerengine.util.helpers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.elliegps.EllieGPSCountingService;
import com.player2.playerengine.agentic.elliegps.EllieGPSCountingServiceStub;
import com.player2.playerengine.agentic.elliegps.EllieGPSWaypointCountingService;
import com.player2.playerengine.agentic.elliegps.WaypointCountResult;
import com.player2.playerengine.agentic.elliegps.WaypointSearchDegradationReporter;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.ItemTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

public final class MaterialAvailability {
   private static volatile EllieGPSCountingService waypointSource = EllieGPSCountingServiceStub.INSTANCE;
   private static final int YIELD_PER_BLOCK = 1;

   public static void setWaypointSource(EllieGPSCountingService service) {
      waypointSource = service != null ? service : EllieGPSCountingServiceStub.INSTANCE;
   }

   private MaterialAvailability() {
   }

   public static MaterialAvailability.Breakdown count(
      PlayerEngineController mod, ItemTarget target, Vec3 origin, double dropRadius, double localSourceBlockRadius
   ) {
      int inventory = mod.getItemStorage().getItemCountInventoryOnly(target.getMatches());
      int nearbyDrops = countNearbyDrops(mod, target, origin, dropRadius);
      int mineableYield = countMineableYield(mod, target, origin, localSourceBlockRadius);
      EllieGPSCountingService ws = waypointSource;
      boolean ellieEnabled = mod.getModSettings().getEllieGpsEnabled();
      int waypointTerm;
      if (!ellieEnabled) {
         waypointTerm = 0;
      } else {
         if (ws instanceof EllieGPSWaypointCountingService real) {
            ServerLevel world = mod.getWorld();
            String dimensionId = world != null ? world.dimension().location().toString() : null;
            WaypointCountResult waypointResult = real.estimateNearbyWaypointItemsChecked(target, origin, localSourceBlockRadius, dimensionId);
            waypointTerm = waypointResult.count();
            WaypointSearchDegradationReporter.reportOnce(mod, waypointResult.status());
            MaterialAvailability.Breakdown bd = new MaterialAvailability.Breakdown(inventory, nearbyDrops, 0, mineableYield, waypointTerm);
            Debug.logInternal(
               "EllieGPS Breakdown [dim=%s]: inv=%d drops=%d mineable=%d waypoint=%d | sufficiency=%d sourceCapacity=%d",
               dimensionId,
               bd.inventory(),
               bd.nearbyDrops(),
               bd.mineableYield(),
               bd.waypointTerm(),
               bd.sufficiencyCount(),
               bd.localSourceCapacity()
            );
            return bd;
         }

         waypointTerm = ws.estimateNearbyWaypointItems(target, origin, localSourceBlockRadius);
      }

      return new MaterialAvailability.Breakdown(inventory, nearbyDrops, 0, mineableYield, waypointTerm);
   }

   public static boolean targetsMetSufficiency(PlayerEngineController mod, Vec3 origin, double dropRadius, ItemTarget... targets) {
      for (ItemTarget target : targets) {
         if (!ItemTarget.nullOrEmpty(target)) {
            int inventory = mod.getItemStorage().getItemCountInventoryOnly(target.getMatches());
            int nearbyDrops = countNearbyDrops(mod, target, origin, dropRadius);
            if (inventory + nearbyDrops < target.getTargetCount()) {
               return false;
            }
         }
      }

      return true;
   }

   public static boolean localSourceCanCoverRemainder(
      PlayerEngineController mod, ItemTarget target, Vec3 origin, double dropRadius, double localSourceBlockRadius
   ) {
      if (ItemTarget.nullOrEmpty(target)) {
         return true;
      }

      MaterialAvailability.Breakdown breakdown = count(mod, target, origin, dropRadius, localSourceBlockRadius);
      int remainder = target.getTargetCount() - breakdown.sufficiencyCount();
      return remainder <= 0 ? true : breakdown.localSourceCapacity() >= remainder;
   }

   public static boolean reachableLocalSourceCoversNetDemand(
      PlayerEngineController mod, ItemTarget target, Vec3 origin, double dropRadius, double localSourceBlockRadius
   ) {
      if (!ItemTarget.nullOrEmpty(target) && target.getTargetCount() > 0) {
         MaterialAvailability.Breakdown breakdown = count(mod, target, origin, dropRadius, localSourceBlockRadius);
         int reachableExternalCapacity = breakdown.nearbyDrops() + breakdown.inFlightDrops() + breakdown.localSourceCapacity();
         return reachableExternalCapacity >= target.getTargetCount();
      } else {
         return false;
      }
   }

   private static int countNearbyDrops(PlayerEngineController mod, ItemTarget target, Vec3 origin, double dropRadius) {
      Predicate<ItemEntity> pred = entityx -> !entityx.isRemoved() && target.matches(entityx.getItem().getItem());
      List<ItemEntity> drops = mod.getEntityTracker().getItemDropsWithin(origin, dropRadius, pred);
      int total = 0;

      for (ItemEntity entity : drops) {
         total += entity.getItem().getCount();
      }

      return total;
   }

   private static int countMineableYield(PlayerEngineController mod, ItemTarget target, Vec3 origin, double localSourceBlockRadius) {
      List<Block> blocks = new ArrayList<>();

      for (Block block : AutomaticMiningSourcePolicy.blocksFor(target.getMatches())) {
         if (!blocks.contains(block)) {
            blocks.add(block);
         }
      }

      if (blocks.isEmpty()) {
         return 0;
      }

      double radiusSq = localSourceBlockRadius * localSourceBlockRadius;
      int reachableBlocks = 0;

      for (BlockPos pos : mod.getBlockScanner().getKnownLocations(blocks.toArray(new Block[0]))) {
         if (!(Vec3.atCenterOf(pos).distanceToSqr(origin) > radiusSq)
            && WorldHelper.canReach(mod, pos)
            && (!mod.getChunkTracker().isChunkLoaded(pos) || blocks.contains(mod.getWorld().getBlockState(pos).getBlock()))) {
            reachableBlocks++;
         }
      }

      return reachableBlocks * 1;
   }

   public record Breakdown(int inventory, int nearbyDrops, int inFlightDrops, int mineableYield, int waypointTerm) {
      public int sufficiencyCount() {
         return this.inventory + this.nearbyDrops + this.inFlightDrops;
      }

      public int localSourceCapacity() {
         return this.mineableYield + this.waypointTerm;
      }
   }
}
