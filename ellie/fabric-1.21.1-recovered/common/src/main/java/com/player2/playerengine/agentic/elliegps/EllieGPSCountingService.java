package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.util.ItemTarget;
import net.minecraft.world.phys.Vec3;

public interface EllieGPSCountingService {
   int estimateNearbyWaypointItems(ItemTarget var1, Vec3 var2, double var3);
}
