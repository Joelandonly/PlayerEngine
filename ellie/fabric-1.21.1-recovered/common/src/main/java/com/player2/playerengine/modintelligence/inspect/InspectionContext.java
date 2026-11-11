package com.player2.playerengine.modintelligence.inspect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.HolderLookup.Provider;

public final class InspectionContext {
   private final String minecraftVersion;
   private final String loader;
   private final int inspectorVersion;
   private final Provider registryAccess;

   public InspectionContext(String minecraftVersion, String loader, int inspectorVersion, Provider registryAccess) {
      this.minecraftVersion = minecraftVersion;
      this.loader = loader;
      this.inspectorVersion = inspectorVersion;
      this.registryAccess = registryAccess;
   }

   public String getMinecraftVersion() {
      return this.minecraftVersion;
   }

   public String getLoader() {
      return this.loader;
   }

   public int getInspectorVersion() {
      return this.inspectorVersion;
   }

   public Provider getRegistryAccess() {
      return this.registryAccess;
   }

   public String sourceModId(ResourceLocation id) {
      return id.getNamespace();
   }
}
