package com.player2.playerengine.tasks.construction.build_structure;

public final class DoorAcquisitionPolicy {
   private DoorAcquisitionPolicy() {
   }

   public static Object pickTask(Object var0, int var1, String var2, Object var3) {
      if ("door".equals(var2)) {
         try {
            ClassLoader var4 = var3.getClass().getClassLoader();
            Class var5 = Class.forName("net.minecraft.class_1792", false, var4);
            Class var6 = Class.forName("com.player2.playerengine.PlayerEngineController", false, var4);
            Class var7 = Class.forName("com.player2.playerengine.util.ItemTarget", false, var4);
            Object var8 = var7.getConstructor(var5, int.class).newInstance(var0, var1);
            Class var9 = Class.forName("com.player2.playerengine.tasks.crafting.CraftMacroTasks", false, var4);
            Object var10 = var9.getMethod("tryCreateMacroTask", var6, var7).invoke(null, var3, var8);
            if (var10 != null) {
               record("RESOURCE_CHOICE", "Door: crafting-first (verified recipe planner); avoid remote village salvage; target=" + var0 + " count=" + var1);
               return var10;
            }

            record("RESOURCE_CHOICE", "Door: no craft macro; trying existing acquisition tasks (may salvage; budget still bounded by builder watchdog)");
         } catch (Throwable var12) {
            record("RESOURCE_CHOICE", "Door: craft macro not available (" + var12.getClass().getSimpleName() + "); existing catalogue fallback");
         }
      }

      try {
         ClassLoader var13 = var3.getClass().getClassLoader();
         Class var14 = Class.forName("com.player2.playerengine.TaskCatalogue", false, var13);
         Class var15 = Class.forName("net.minecraft.class_1792", false, var13);
         Object var16 = var14.getMethod("getItemTask", var15, int.class).invoke(null, var0, var1);
         if ("door".equals(var2)) {
            record("RESOURCE_CHOICE", "Door: existing catalogue task=" + var16);
         }

         return var16;
      } catch (Throwable var11) {
         record("RESOURCE_CHOICE", "Task construction failed for " + var0 + ": " + var11);
         return null;
      }
   }

   private static void record(String var0, String var1) {
      try {
         Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry").getMethod("event", String.class, String.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
         System.out.println("[EllieDoor] " + var0 + ": " + var1);
      }
   }
}
