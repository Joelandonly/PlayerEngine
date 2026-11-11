package com.player2.playerengine.reliability;

import com.player2.playerengine.nativecommands.NativeAccess;
import java.lang.reflect.Array;

public final class PlankTargetReconciliation {
   private PlankTargetReconciliation() {
   }

   public static int absoluteCount(int var0, int var1) {
      if (var0 >= 0 && var1 >= 1) {
         return Math.addExact(var0, var1);
      } else {
         throw new IllegalArgumentException("Invalid held/shortfall counts: " + var0 + "/" + var1);
      }
   }

   public static int absoluteTarget(Object var0, Object var1, int var2) {
      try {
         Object var3 = NativeAccess.call(var0, "getItemStorage");
         Object var4 = NativeAccess.call(var1, "getMatches");
         int var5 = ((Number)NativeAccess.call(var3, "getItemCount", var4)).intValue();
         return absoluteCount(var5, var2);
      } catch (Exception var6) {
         throw new IllegalStateException("Cannot verify native plank inventory before re-targeting", var6);
      }
   }

   public static int absoluteTargetForItem(Object var0, Object var1, int var2) {
      try {
         if (var1 == null) {
            throw new IllegalArgumentException("Null resource item");
         }

         Object var3 = Array.newInstance(var1.getClass(), 1);
         Array.set(var3, 0, var1);
         Object var4 = NativeAccess.call(var0, "getItemStorage");
         int var5 = ((Number)NativeAccess.call(var4, "getItemCount", var3)).intValue();
         return absoluteCount(var5, var2);
      } catch (Exception var6) {
         throw new IllegalStateException("Cannot verify specific plank inventory before re-targeting", var6);
      }
   }
}
