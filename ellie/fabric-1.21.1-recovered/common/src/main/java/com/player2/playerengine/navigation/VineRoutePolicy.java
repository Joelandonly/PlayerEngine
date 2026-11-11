package com.player2.playerengine.navigation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class VineRoutePolicy {
   private static final Map<Object, Boolean> REPORTED = Collections.synchronizedMap(new WeakHashMap<>());
   private static volatile Object VINE;

   private VineRoutePolicy() {
   }

   private static Object vine() throws Exception {
      Object var0 = VINE;
      if (var0 != null) {
         return var0;
      }

      var0 = Class.forName("net.minecraft.class_2246").getField("field_10597").get(null);
      VINE = var0;
      return var0;
   }

   private static Object call(Object var0, String var1, Object... var2) throws Exception {
      for (Method var6 : var0.getClass().getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == var2.length) {
            try {
               var6.setAccessible(true);
               return var6.invoke(var0, var2);
            } catch (IllegalArgumentException var8) {
            }
         }
      }

      throw new NoSuchMethodException(var1 + " in " + var0.getClass());
   }

   private static void announce(String var0, String var1) {
      try {
         Class var2 = Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry");
         var2.getMethod("event", String.class, String.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
         System.out.println("[EllieNav] " + var0 + ": " + var1);
      }
   }

   public static boolean install(Object var0) {
      if (var0 == null) {
         return false;
      }

      try {
         Object var12 = call(var0, "settings");
         Field var13 = var12.getClass().getField("blocksToAvoid");
         Object var3 = var13.get(var12);
         Object var4 = call(var3, "get");
         if (!(var4 instanceof List)) {
            return false;
         }

         Object var5 = vine();
         if (!((List)var4).contains(var5)) {
            ArrayList var6 = new ArrayList((List)var4);
            var6.add(var5);
            call(var3, "set", var6);
         }

         synchronized (REPORTED) {
            if (!REPORTED.containsKey(var0)) {
               REPORTED.put(var0, Boolean.TRUE);
               announce("VINE_POLICY", "Vines excluded from route cells; solid supports and ladders retained");
            }
         }

         return true;
      } catch (Exception var11) {
         Exception var1 = var11;
         synchronized (REPORTED) {
            if (!REPORTED.containsKey(var0)) {
               REPORTED.put(var0, Boolean.FALSE);
               announce("VINE_POLICY_FAILURE", var1.getClass().getSimpleName() + " " + var1.getMessage());
            }

            return false;
         }
      }
   }
}
