package com.player2.playerengine.modintelligence.inspect;

import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import net.minecraft.resources.ResourceLocation;

public interface CapabilityInspector<T> {
   CapabilityMap inspect(InspectionContext var1, ResourceLocation var2, T var3);
}
