package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.util.ItemTarget;
import net.minecraft.world.phys.Vec3;

public final class EllieGPSCountingServiceStub implements EllieGPSCountingService {
   public static final EllieGPSCountingServiceStub INSTANCE = new EllieGPSCountingServiceStub();

   @Override
   public int estimateNearbyWaypointItems(ItemTarget target, Vec3 origin, double radiusBlocks) {
      return 0;
   }
}
