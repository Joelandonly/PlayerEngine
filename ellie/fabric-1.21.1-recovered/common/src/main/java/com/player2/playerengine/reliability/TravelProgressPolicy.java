package com.player2.playerengine.reliability;

public final class TravelProgressPolicy {
   public static final int MAX_UNCHANGED_MATERIAL_TICKS = 4800;
   private int ticks;
   private int lastHeld = Integer.MIN_VALUE;
   private String destination;
   private int lastPathIndex = -1;
   private double lastDistance = Double.NaN;
   private double lastX = Double.NaN;
   private double lastY = Double.NaN;
   private double lastZ = Double.NaN;
   private int travelCreditCount;

   public boolean update(int var1, boolean var2, int var3, String var4, double var5, double var7, double var9, double var11) {
      if (var1 > this.lastHeld && this.lastHeld != Integer.MIN_VALUE) {
         this.ticks = 0;
         this.destination = null;
         this.lastPathIndex = -1;
         this.lastDistance = Double.NaN;
      }

      if (var1 != this.lastHeld) {
         this.lastHeld = var1;
      }

      this.ticks++;
      if (var2 && var4 != null) {
         boolean var13 = var4.equals(this.destination);
         boolean var14 = Double.isFinite(this.lastX)
            && Double.isFinite(var5)
            && (var5 - this.lastX) * (var5 - this.lastX) + (var7 - this.lastY) * (var7 - this.lastY) + (var9 - this.lastZ) * (var9 - this.lastZ) >= 0.36;
         boolean var15 = var13 && var14 && Double.isFinite(var11) && Double.isFinite(this.lastDistance) && var11 <= this.lastDistance - 0.35;
         boolean var16 = var13 && var3 > this.lastPathIndex;
         boolean var17 = this.ticks < 4800 && (var15 || var16);
         this.destination = var4;
         this.lastPathIndex = var3;
         this.lastDistance = var11;
         this.lastX = var5;
         this.lastY = var7;
         this.lastZ = var9;
         if (var17) {
            this.travelCreditCount++;
         }

         return var17;
      } else {
         this.destination = null;
         this.lastPathIndex = -1;
         this.lastDistance = Double.NaN;
         return false;
      }
   }

   public int getTicks() {
      return this.ticks;
   }

   public int getTravelCreditCount() {
      return this.travelCreditCount;
   }
}
