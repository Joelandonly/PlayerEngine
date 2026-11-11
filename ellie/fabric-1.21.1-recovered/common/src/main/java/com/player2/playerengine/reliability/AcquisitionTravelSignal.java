package com.player2.playerengine.reliability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

public final class AcquisitionTravelSignal {
   private static final WeakHashMap<Object, TravelProgressPolicy> POLICIES = new WeakHashMap<>();

   private AcquisitionTravelSignal() {
   }

   public static boolean shouldCredit(Object var0, Object var1, int var2) {
      if (var0 != null && var1 != null) {
         try {
            Object var3 = invoke(var1, "getBaritone");
            Object var4 = invoke(var3, "getPathingBehavior");
            boolean var5 = Boolean.TRUE.equals(invoke(var4, "isPathing"));
            Object var6 = var5 ? invoke(var4, "getCurrent") : null;
            int var7 = var6 == null ? -1 : ((Number)invoke(var6, "getPosition")).intValue();
            Object var8 = var6 == null ? null : invoke(var6, "getPath");
            Object var9 = var8 == null ? null : invoke(var8, "getDest");
            String var10 = var9 == null ? null : coord(var9, "x") + ":" + coord(var9, "y") + ":" + coord(var9, "z");
            Object var11 = invoke(var1, "getPlayer");
            Object var12 = invoke(var11, "method_19538");
            double var13 = coord(var12, "field_1352");
            double var15 = coord(var12, "field_1351");
            double var17 = coord(var12, "field_1350");
            double var19 = var9 == null ? Double.NaN : coord(var9, "x") + 0.5 - var13;
            double var21 = var9 == null ? Double.NaN : coord(var9, "y") - var15;
            double var23 = var9 == null ? Double.NaN : coord(var9, "z") + 0.5 - var17;
            double var25 = var9 == null ? Double.NaN : Math.sqrt(var19 * var19 + var21 * var21 + var23 * var23);
            synchronized (POLICIES) {
               TravelProgressPolicy var28 = POLICIES.computeIfAbsent(var0, var0x -> new TravelProgressPolicy());
               return var28.update(var2, var5, var7, var10, var13, var15, var17, var25);
            }
         } catch (ReflectiveOperationException | RuntimeException var31) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static Object invoke(Object var0, String var1) throws ReflectiveOperationException {
      if (var0 == null) {
         throw new NoSuchMethodException(var1 + " null receiver");
      }

      Method var2 = var0.getClass().getMethod(var1);
      var2.trySetAccessible();
      return var2.invoke(var0);
   }

   private static double coord(Object var0, String var1) throws ReflectiveOperationException {
      Field var2 = var0.getClass().getField(var1);
      return ((Number)var2.get(var0)).doubleValue();
   }
}
