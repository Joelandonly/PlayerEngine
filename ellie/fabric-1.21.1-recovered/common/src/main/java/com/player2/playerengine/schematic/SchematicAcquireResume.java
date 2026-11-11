package com.player2.playerengine.schematic;

import com.player2.playerengine.nativecommands.NativeAccess;
import com.player2.playerengine.tasks.base.Task;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SchematicAcquireResume {
   private static final Map<SchematicProjectTask, SchematicAcquireResume.Lease> PENDING = Collections.synchronizedMap(new IdentityHashMap<>());

   private static String id(Object var0) {
      return Integer.toHexString(System.identityHashCode(var0));
   }

   private SchematicAcquireResume() {
   }

   public static boolean hold(SchematicProjectTask var0) {
      SchematicProjectTask.Environment var1 = null;

      try {
         Object var8 = NativeAccess.get(var0, "action");
         if (var8 != null && "ACQUIRE".equals(String.valueOf(NativeAccess.get(var8, "kind")))) {
            var1 = (SchematicProjectTask.Environment)NativeAccess.get(var0, "environment");
            if (var1 == null) {
               return false;
            }

            String var3 = (String)NativeAccess.get(var8, "item");
            int var4 = ((Number)NativeAccess.get(var8, "target")).intValue();
            if (var3 != null && var4 >= 1) {
               if (var1.builderActive()) {
                  var1.pauseBuilder();
               }

               int var5 = var1.inventory().getOrDefault(var3, 0);
               PENDING.put(var0, new SchematicAcquireResume.Lease(var8, var3, var4, var5));
               var1.report(
                  "SCHEMATIC_ACQUIRE_SUSPENDED",
                  "project="
                     + id(var0)
                     + " action="
                     + id(var8)
                     + " item="
                     + var3
                     + " absoluteTarget="
                     + var4
                     + " carriedBefore="
                     + var5
                     + " remaining="
                     + Math.max(0, var4 - var5)
                     + " actionTicks="
                     + NativeAccess.get(var8, "ticks")
                     + " idle="
                     + NativeAccess.get(var8, "idle")
                     + " reason=transient_native_child_stop watchdog_preserved=true"
               );
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } catch (Exception | LinkageError var7) {
         Throwable var2 = var7;

         try {
            if (var1 != null) {
               var1.report("SCHEMATIC_ACQUIRE_SUSPEND_FAILED", var2.getClass().getSimpleName() + ": " + var2.getMessage());
            }
         } catch (Exception var6) {
         }

         PENDING.remove(var0);
         return false;
      }
   }

   public static void beforePoll(SchematicProjectTask var0) throws Exception {
      SchematicAcquireResume.Lease var1 = PENDING.get(var0);
      if (var1 != null) {
         Object var2 = NativeAccess.get(var0, "action");
         if (var2 == var1.action() && "ACQUIRE".equals(String.valueOf(NativeAccess.get(var2, "kind")))) {
            String var3 = (String)NativeAccess.get(var2, "item");
            int var4 = ((Number)NativeAccess.get(var2, "target")).intValue();
            if (var3 != null && var4 >= 1 && var4 == var1.target() && var3.equals(var1.item())) {
               SchematicProjectTask.Environment var5 = (SchematicProjectTask.Environment)NativeAccess.get(var0, "environment");
               if (var5 == null) {
                  throw new IllegalStateException("Transient schematic world unavailable");
               }

               int var6 = var5.inventory().getOrDefault(var3, 0);
               if (var6 >= var4) {
                  PENDING.remove(var0);
                  var5.report(
                     "SCHEMATIC_ACQUIRE_RESUMED",
                     "project="
                        + id(var0)
                        + " action="
                        + id(var2)
                        + " item="
                        + var3
                        + " absoluteTarget="
                        + var4
                        + " carriedBefore="
                        + var1.held()
                        + " carriedAfter="
                        + var6
                        + " remaining=0 next=VERIFY already_satisfied=true no_native_restart_needed=true"
                  );
               } else {
                  Task var7 = var5.acquire(var3, var4);
                  if (var7 == null) {
                     throw new IllegalStateException("Native acquisition unavailable: " + var3);
                  }

                  NativeAccess.set(var7, "controller", NativeAccess.get(var0, "controller"));
                  NativeAccess.set(var2, "task", var7);
                  PENDING.remove(var0);
                  var5.report(
                     "SCHEMATIC_ACQUIRE_RESUMED",
                     "project="
                        + id(var0)
                        + " action="
                        + id(var2)
                        + " item="
                        + var3
                        + " absoluteTarget="
                        + var4
                        + " carriedBefore="
                        + var1.held()
                        + " carriedAfter="
                        + var6
                        + " remaining="
                        + Math.max(0, var4 - var6)
                        + " next=ACQUIRE native_child_recreated=true no_depot_roundtrip=true actionTicks="
                        + NativeAccess.get(var2, "ticks")
                        + " idle="
                        + NativeAccess.get(var2, "idle")
                        + " watchdog_preserved=true"
                  );
               }
            } else {
               throw new IllegalStateException("Transient resource acquisition identity/target changed");
            }
         } else {
            PENDING.remove(var0);
         }
      }
   }

   public static void forget(SchematicProjectTask var0) {
      PENDING.remove(var0);
   }

   public static boolean pending(SchematicProjectTask var0) {
      return PENDING.containsKey(var0);
   }

   private record Lease(Object action, String item, int target, int held) {
   }
}
