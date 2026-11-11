package com.player2.playerengine.navigation;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class NavStallRecovery {
   private static final Map<Object, NavStallRecovery.State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final int SAMPLE_INTERVAL = 20;
   private static final int STALL_TICKS = 160;
   private static final int ACTION_COOLDOWN = 160;
   private static final int AVOID_TTL = 600;
   private static final int RECOVERY_TASK_TTL = 160;
   private static final int[][] OFFSETS = new int[][]{{0, 0, 0}, {0, 1, 0}, {0, -1, 0}};

   private NavStallRecovery() {
   }

   private static Object invoke(Object var0, String var1, Object... var2) {
      if (var0 == null) {
         return null;
      }

      for (Method var6 : var0.getClass().getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == var2.length) {
            try {
               var6.setAccessible(true);
               return var6.invoke(var0, var2);
            } catch (IllegalArgumentException var8) {
            } catch (Throwable var9) {
               return null;
            }
         }
      }

      return null;
   }

   private static Object field(Object var0, String var1) {
      if (var0 == null) {
         return null;
      }

      Class var2 = var0.getClass();

      while (var2 != null) {
         try {
            Field var3 = var2.getDeclaredField(var1);
            var3.setAccessible(true);
            return var3.get(var0);
         } catch (NoSuchFieldException var4) {
            var2 = var2.getSuperclass();
         } catch (Throwable var5) {
            return null;
         }
      }

      return null;
   }

   private static int number(Object var0) {
      return var0 instanceof Number ? ((Number)var0).intValue() : 0;
   }

   private static NavStallRecovery.Point point(Object var0) {
      if (var0 == null) {
         return null;
      }

      Object var1 = field(var0, "x");
      Object var2 = field(var0, "y");
      Object var3 = field(var0, "z");
      if (var1 instanceof Number && var2 instanceof Number && var3 instanceof Number) {
         return new NavStallRecovery.Point(number(var1), number(var2), number(var3));
      }

      var1 = invoke(var0, "method_10263");
      var2 = invoke(var0, "method_10264");
      var3 = invoke(var0, "method_10260");
      return var1 instanceof Number && var2 instanceof Number && var3 instanceof Number
         ? new NavStallRecovery.Point(number(var1), number(var2), number(var3))
         : null;
   }

   private static String blockName(Object var0) {
      if (var0 == null) {
         return "unknown";
      }

      Object var1 = invoke(var0, "method_26204");
      String var2 = String.valueOf(var1 == null ? var0 : var1);
      int var3 = var2.indexOf("minecraft:");
      if (var3 < 0) {
         return var2.toLowerCase(Locale.ROOT);
      }

      String var4 = var2.substring(var3).toLowerCase(Locale.ROOT);

      int var5;
      for (var5 = 0; var5 < var4.length(); var5++) {
         char var6 = var4.charAt(var5);
         if (!Character.isLetterOrDigit(var6) && var6 != '_' && var6 != ':') {
            break;
         }
      }

      return var4.substring(0, var5);
   }

   private static Object blockPos(int var0, int var1, int var2) {
      try {
         return Class.forName("net.minecraft.class_2338").getConstructor(int.class, int.class, int.class).newInstance(var0, var1, var2);
      } catch (Throwable var4) {
         return null;
      }
   }

   private static Object stateAt(Object var0, NavStallRecovery.Point var1) {
      Object var2 = blockPos(var1.x, var1.y, var1.z);
      if (var2 == null) {
         return null;
      }

      Object var3 = invoke(var0, "method_8477", var2);
      return var3 instanceof Boolean && (Boolean)var3 ? invoke(var0, "method_8320", var2) : null;
   }

   private static boolean vine(String var0) {
      return var0.equals("minecraft:vine");
   }

   private static boolean leaves(String var0) {
      return var0.startsWith("minecraft:") && var0.endsWith("_leaves");
   }

   private static boolean nonSolid(String var0) {
      return var0.equals("minecraft:air")
         || var0.equals("minecraft:cave_air")
         || var0.equals("minecraft:void_air")
         || var0.equals("minecraft:water")
         || var0.equals("minecraft:lava")
         || vine(var0)
         || var0.equals("unknown");
   }

   private static void event(String var0, String var1) {
      try {
         Class var2 = Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry");
         var2.getMethod("event", String.class, String.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
         System.out.println("[EllieNav] " + var0 + ": " + var1);
      }
   }

   private static void snapshot(String var0, Object var1) {
      try {
         Class var2 = Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry");
         var2.getMethod("set", String.class, Object.class).invoke(null, var0, var1);
      } catch (Throwable var3) {
      }
   }

   private static List<NavStallRecovery.Point> plannedPositions(Object var0) {
      Object var1 = invoke(var0, "getCurrent");
      if (var1 == null) {
         return List.of();
      } else {
         Object var2 = invoke(var1, "getPath");
         if (!(invoke(var2, "positions") instanceof List var4)) {
            return List.of();
         } else {
            if (var4.isEmpty()) {
               return List.of();
            }

            int var5 = Math.max(0, number(invoke(var1, "getPosition")));
            ArrayList var6 = new ArrayList();

            for (int var7 = Math.max(0, var5); var7 < Math.min(var4.size(), var5 + 5); var7++) {
               NavStallRecovery.Point var8 = point(var4.get(var7));
               if (var8 != null) {
                  var6.add(var8);
               }
            }

            return var6;
         }
      }
   }

   private static NavStallRecovery.Point findObstacle(Object var0, NavStallRecovery.Point var1, List<NavStallRecovery.Point> var2, boolean var3) {
      if (var0 != null && var1 != null && !var2.isEmpty()) {
         NavStallRecovery.Point var4 = (NavStallRecovery.Point)var2.get(Math.min(2, var2.size() - 1));
         ArrayList<NavStallRecovery.Point> var5 = new ArrayList();

         for (NavStallRecovery.Point var7 : var2) {
            if (!(var7.distSq(var1) > 18.0)) {
               for (int[] var11 : OFFSETS) {
                  var5.add(new NavStallRecovery.Point(var7.x + var11[0], var7.y + var11[1], var7.z + var11[2]));
               }
            }
         }

         int var16 = Integer.compare(var4.x, var1.x);
         int var17 = Integer.compare(var4.z, var1.z);
         if (var16 != 0 || var17 != 0) {
            for (int var18 = 1; var18 <= 2; var18++) {
               for (int var20 = 0; var20 <= 1; var20++) {
                  var5.add(new NavStallRecovery.Point(var1.x + var16 * var18, var1.y + var20, var1.z + var17 * var18));
               }
            }
         }

         NavStallRecovery.Point var19 = null;
         double var21 = Double.POSITIVE_INFINITY;

         for (NavStallRecovery.Point var12 : var5) {
            if (!(var12.distSq(var1) > 12.0) && (var12.x != var1.x || var12.y != var1.y || var12.z != var1.z)) {
               String var13 = blockName(stateAt(var0, var12));
               if (vine(var13) || var3 && leaves(var13)) {
                  double var14 = var12.distSq(var1) + (leaves(var13) ? 4 : 0);
                  if (var14 < var21) {
                     var21 = var14;
                     var19 = var12;
                  }
               }
            }
         }

         return var19;
      } else {
         return null;
      }
   }

   private static Object worldOf(Object var0) {
      Object var1 = invoke(var0, "getEntityContext");
      return invoke(var1, "world");
   }

   private static boolean safeToBreak(Object var0, NavStallRecovery.Point var1, NavStallRecovery.Point var2) {
      if (var2 != null && !(var2.distSq(var1) > 12.0)) {
         String var3 = blockName(stateAt(var0, var1));
         String var4 = blockName(stateAt(var0, new NavStallRecovery.Point(var1.x, var1.y - 1, var1.z)));
         if (!vine(var3) && !nonSolid(var4)) {
            String var5 = blockName(stateAt(var0, var2));
            return vine(var5) || leaves(var5);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isBusyWithDestruction(Object var0) {
      Object var1 = invoke(var0, "getCurrentTaskChain");
      if (var1 == null) {
         return false;
      }

      String var2 = String.valueOf(invoke(var1, "getName"));
      if (!var2.contains("Mob Defense") && !var2.contains("MLG") && !var2.contains("Survival") && !var2.contains("Navigation Recovery")) {
         Object var3 = invoke(var1, "getTasks");
         if (var3 instanceof List) {
            for (Object var5 : (List)var3) {
               String var6 = String.valueOf(var5);
               if (var6.contains("<Killing")) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static boolean isMiningTarget(Object var0) {
      Object var1 = invoke(var0, "getCurrentTaskChain");
      Object var2 = invoke(var1, "getTasks");
      if (var2 instanceof List) {
         for (Object var4 : (List)var2) {
            String var5 = String.valueOf(var4);
            if (var5.contains("Destroying block") || var5.contains("Mining ")) {
               return true;
            }
         }
      }

      return false;
   }

   private static Object rememberedSetting(NavStallRecovery.State var0) {
      return var0.settingRef == null ? null : var0.settingRef.get();
   }

   private static NavRecoveryChain rememberedChain(NavStallRecovery.State var0) {
      return var0.recoveryChainRef == null ? null : var0.recoveryChainRef.get();
   }

   private static void restoreAvoid(NavStallRecovery.State var0) {
      Object var1 = rememberedSetting(var0);
      if (var1 != null) {
         invoke(var1, "set", var0.prior);
      }

      var0.settingRef = null;
      var0.prior = null;
   }

   private static boolean avoidObservedVines(
      Object var0, Object var1, NavStallRecovery.Point var2, List<NavStallRecovery.Point> var3, NavStallRecovery.State var4
   ) {
      NavStallRecovery.Point var5 = findObstacle(var1, var2, var3, false);
      if (var5 == null && vine(blockName(stateAt(var1, var2)))) {
         var5 = var2;
      }

      if (var5 == null) {
         return false;
      }

      Object var6 = stateAt(var1, var5);
      Object var7 = invoke(var6, "method_26204");
      if (var7 == null) {
         return false;
      }

      if (rememberedSetting(var4) != null) {
         var4.avoidUntil = var4.ticks + 600;
         return true;
      }

      Object var8 = invoke(var0, "settings");
      Object var9 = field(var8, "blocksToAvoid");
      Object var10 = invoke(var9, "get");
      if (!(var10 instanceof List)) {
         return false;
      }

      ArrayList var11 = new ArrayList((List)var10);
      if (!var11.contains(var7)) {
         var11.add(var7);
      }

      if (var11.size() == ((List)var10).size()) {
         return true;
      }

      invoke(var9, "set", var11);
      var4.settingRef = new WeakReference<>(var9);
      var4.prior = var10;
      var4.avoidUntil = var4.ticks + 600;
      return true;
   }

   private static boolean requestReplan(Object var0) {
      Object var1 = invoke(var0, "cancelSegmentIfSafe");
      return Boolean.TRUE.equals(var1);
   }

   private static void resetStable(NavStallRecovery.State var0) {
      var0.stallTicks = 0;
      var0.samePosAttempts = 0;
      var0.attempts = 0;
      var0.stalledAt = null;
   }

   public static void onTick(Object var0) {
      if (var0 != null) {
         NavStallRecovery.State var1;
         synchronized (STATES) {
            var1 = STATES.computeIfAbsent(var0, var0x -> new NavStallRecovery.State());
         }

         if (++var1.ticks % 20 == 0) {
            try {
               VineRoutePolicy.install(invoke(var0, "getBaritone"));
               Object var21 = invoke(var0, "getTaskRunner");
               if (!Boolean.TRUE.equals(invoke(var21, "isActive"))) {
                  if (var1.breakInProgress && rememberedChain(var1) != null) {
                     rememberedChain(var1).cancelRecovery();
                  }

                  var1.breakInProgress = false;
                  resetStable(var1);
                  restoreAvoid(var1);
                  return;
               }

               Object var3 = invoke(var0, "getBaritone");
               Object var4 = invoke(var3, "getPathingBehavior");
               Object var5 = invoke(var3, "getEntityContext");
               NavStallRecovery.Point var6 = point(invoke(var5, "feetPos"));
               if (var6 == null) {
                  return;
               }

               Object var7 = invoke(var4, "getGoal");
               Object var8 = invoke(var4, "getCurrent");
               List var9 = plannedPositions(var4);
               boolean var10 = var7 != null && (var8 != null || Boolean.TRUE.equals(invoke(var4, "isPathing")));
               if (var1.last == null) {
                  var1.last = var6;
                  return;
               }

               if (var6.distSq(var1.last) >= 1.0) {
                  var1.last = var6;
                  resetStable(var1);
                  if (var1.breakInProgress && var1.pendingBreak != null && var6.distSq(var1.pendingBreak) > 25.0 && rememberedChain(var1) != null) {
                     rememberedChain(var1).cancelRecovery();
                  }

                  if (rememberedSetting(var1) != null) {
                     restoreAvoid(var1);
                     event("PATH_RECOVERY", "Moving again @" + var6 + "; temporary vine avoidance cleared");
                  }

                  return;
               }

               if (rememberedSetting(var1) != null && var1.ticks >= var1.avoidUntil) {
                  restoreAvoid(var1);
               }

               if (var1.breakInProgress) {
                  if (var1.ticks >= var1.avoidUntil) {
                     if (rememberedChain(var1) != null) {
                        rememberedChain(var1).cancelRecovery();
                     }

                     var1.breakInProgress = false;
                     event("PATH_RECOVERY", "Break task expired @" + var1.pendingBreak);
                     var1.sinceAction = var1.ticks;
                  }

                  return;
               }

               if (!var10 || isBusyWithDestruction(var21)) {
                  var1.stallTicks = 0;
                  return;
               }

               var1.stallTicks += 20;
               if (var9.isEmpty()) {
                  if (var1.stallTicks >= 160 && var1.ticks - var1.sinceAction >= 160) {
                     var1.sinceAction = var1.ticks;
                     snapshot("navigation", "No active path @" + var6 + " goal=" + var7);
                     event("PATH_STALL", "feet=" + var6 + " goal=" + var7 + " reason=goal_with_no_active_path");
                     event("PATH_RECOVERY", "action=wait_for_route; no obstruction verified");
                  }

                  return;
               }

               int var11 = isMiningTarget(var21) ? 300 : 160;
               if (var1.stallTicks < var11 || var1.ticks - var1.sinceAction < 160) {
                  return;
               }

               Object var12 = worldOf(var3);
               NavStallRecovery.Point var13 = findObstacle(var12, var6, var9, true);
               String var14 = var13 == null ? "none" : blockName(stateAt(var12, var13));
               NavStallRecovery.Point var15 = (NavStallRecovery.Point)var9.get(0);
               snapshot("navigation", "Stalled @" + var6 + ", next=" + var15 + ", obstacle=" + var14);
               event(
                  "PATH_STALL",
                  "feet=" + var6 + " next=" + var15 + " goal=" + var7 + " stationaryTicks=" + var1.stallTicks + " obstacle=" + var14 + " candidate=" + var13
               );
               if (var1.stalledAt == null || var1.stalledAt.distSq(var6) > 1.0) {
                  var1.stalledAt = var6;
                  var1.samePosAttempts = 0;
               }

               var1.samePosAttempts++;
               var1.attempts++;
               var1.sinceAction = var1.ticks;
               boolean var16 = avoidObservedVines(var3, var12, var6, var9, var1);
               boolean var17 = requestReplan(var4);
               event("PATH_RECOVERY", "attempt=" + var1.samePosAttempts + " action=replan replanned=" + var17 + " avoidVines=" + var16 + " @" + var6);
               if (var1.samePosAttempts >= 2 && var1.samePosAttempts <= 3 && var13 != null && safeToBreak(var12, var6, var13) && var17) {
                  if (rememberedChain(var1) == null) {
                     Object var18 = newChain(var21);
                     if (var18 instanceof NavRecoveryChain) {
                        var1.recoveryChainRef = new WeakReference<>((NavRecoveryChain)var18);
                     }
                  }

                  if (rememberedChain(var1) != null && rememberedChain(var1).begin(var13.x, var13.y, var13.z)) {
                     var1.breakInProgress = true;
                     var1.pendingBreak = var13;
                     var1.avoidUntil = var1.ticks + 160;
                     event("PATH_RECOVERY", "action=clear_obstacle type=" + var14 + " pos=" + var13 + " using survival DestroyBlockTask");
                  }
               } else if (var1.samePosAttempts >= 4) {
                  event("PATH_RECOVERY", "action=bounded_stop @" + var6 + "; waiting for terrain/task change");
                  var1.sinceAction = var1.ticks + 600;
               }
            } catch (Throwable var20) {
               event("PATH_RECOVERY", "monitor error=" + var20.getClass().getSimpleName() + " " + var20.getMessage());
            }
         }
      }
   }

   private static Object newChain(Object var0) {
      try {
         return NavRecoveryChain.class.getConstructor(Class.forName("com.player2.playerengine.tasks.base.TaskRunner")).newInstance(var0);
      } catch (Throwable var2) {
         event("PATH_RECOVERY", "recovery chain unavailable: " + var2.getClass().getSimpleName());
         return null;
      }
   }

   public static void recoveryDone(Object var0, String var1) {
      NavStallRecovery.State var2;
      synchronized (STATES) {
         var2 = STATES.get(var0);
      }

      if (var2 != null) {
         var2.breakInProgress = false;
         var2.sinceAction = var2.ticks;
         var2.stallTicks = 0;
         event("PATH_RECOVERY", "action=clear_finished result=" + var1 + " block=" + var2.pendingBreak);
      }
   }

   private static final class Point {
      final int x;
      final int y;
      final int z;

      Point(int var1, int var2, int var3) {
         this.x = var1;
         this.y = var2;
         this.z = var3;
      }

      double distSq(NavStallRecovery.Point var1) {
         double var2 = this.x - var1.x;
         double var4 = this.y - var1.y;
         double var6 = this.z - var1.z;
         return var2 * var2 + var4 * var4 + var6 * var6;
      }

      @Override
      public String toString() {
         return this.x + "," + this.y + "," + this.z;
      }
   }

   private static final class State {
      int ticks;
      int stallTicks;
      int sinceAction;
      int attempts;
      int samePosAttempts;
      int avoidUntil;
      NavStallRecovery.Point last;
      NavStallRecovery.Point stalledAt;
      NavStallRecovery.Point pendingBreak;
      WeakReference<Object> settingRef;
      Object prior;
      WeakReference<NavRecoveryChain> recoveryChainRef;
      boolean breakInProgress;
   }
}
