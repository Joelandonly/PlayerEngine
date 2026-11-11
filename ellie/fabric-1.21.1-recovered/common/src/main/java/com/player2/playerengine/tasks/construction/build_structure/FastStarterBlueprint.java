package com.player2.playerengine.tasks.construction.build_structure;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class FastStarterBlueprint {
   private FastStarterBlueprint() {
   }

   public static String maybeCode(BuildStructureTask var0) {
      try {
         Field var1 = BuildStructureTask.class.getDeclaredField("description");
         Field var2 = BuildStructureTask.class.getDeclaredField("schematicQuery");
         Field var3 = BuildStructureTask.class.getDeclaredField("buildPosition");
         var1.setAccessible(true);
         var2.setAccessible(true);
         var3.setAccessible(true);
         Object var4 = var3.get(var0);
         if (var4 == null) {
            return null;
         }

         Method var5 = var4.getClass().getMethod("method_10263");
         Method var6 = var4.getClass().getMethod("method_10264");
         Method var7 = var4.getClass().getMethod("method_10260");
         return BlueprintLibrary.choose(
            (String)var1.get(var0), (String)var2.get(var0), (Integer)var5.invoke(var4), (Integer)var6.invoke(var4), (Integer)var7.invoke(var4)
         );
      } catch (ReflectiveOperationException | RuntimeException var8) {
         System.err.println("[EllieBlueprint] Local selection failed, retaining original AI planner: " + var8);
         return null;
      }
   }

   public static String buildAt(int var0, int var1, int var2) {
      return BlueprintLibrary.buildAt(var0, var1, var2);
   }
}
