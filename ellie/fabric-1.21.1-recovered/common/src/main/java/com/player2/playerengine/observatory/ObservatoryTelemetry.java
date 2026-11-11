package com.player2.playerengine.observatory;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.schematic.ISchematic;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskChain;
import com.player2.playerengine.tasks.base.TaskRunner;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.state.BlockState;

public final class ObservatoryTelemetry {
   private static final Path DIR = Paths.get(System.getProperty("user.dir", "."), "logs");
   public static final Path EVENTS = DIR.resolve("ellie-observatory-events.jsonl");
   public static final Path SNAPSHOT = DIR.resolve("ellie-observatory-status.txt");
   private static final Map<String, String> fields = new ConcurrentHashMap<>();
   private static final Deque<String> timeline = new ArrayDeque<>();
   private static volatile long lastWrite;
   private static volatile long lastRunnerSample;
   private static volatile long lastBuilderSample;
   private static volatile String lastChain = "";
   private static volatile String lastPhase = "";
   private static volatile String lastTask = "";
   private static volatile int lastCompleted = -1;
   private static volatile boolean activeGoal;
   private static volatile Object lastGoalObject;
   private static final int HISTORY = 100;

   private ObservatoryTelemetry() {
   }

   private static String safe(Object var0) {
      if (var0 == null) {
         return "-";
      }

      String var1 = String.valueOf(var0).replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
      var1 = var1.replaceAll("(?i)(bearer\\s+[a-z0-9_.-]+|authkey\\[[^]]*\\]|token[=:]\\s*[^ ]+)", "[REDACTED]");
      if (var1.length() > 240) {
         var1 = var1.substring(0, 240) + "...";
      }

      return var1;
   }

   private static String json(String var0) {
      return "\"" + safe(var0).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
   }

   public static synchronized void event(String var0, String var1) {
      String var2 = Instant.now().toString();
      String var3 = var2 + " | " + safe(var0) + " | " + safe(var1);
      timeline.addLast(var3);

      while (timeline.size() > 100) {
         timeline.removeFirst();
      }

      try {
         Files.createDirectories(DIR);
         if (Files.exists(EVENTS) && Files.size(EVENTS) > 2000000L) {
            Files.move(EVENTS, DIR.resolve("ellie-observatory-events.previous.jsonl"), StandardCopyOption.REPLACE_EXISTING);
         }

         String var4 = "{\"time\":" + json(var2) + ",\"type\":" + json(var0) + ",\"detail\":" + json(var1) + "}\n";
         Files.writeString(EVENTS, var4, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (Exception var5) {
         System.err.println("[EllieObservatory] Cannot append event: " + safe(var5.getMessage()));
      }

      System.out.println("[EllieObservatory] " + safe(var0) + ": " + safe(var1));
   }

   public static void set(String var0, Object var1) {
      fields.put(var0, safe(var1));
   }

   public static Map<String, String> view() {
      return new TreeMap<>(fields);
   }

   public static synchronized List<String> history() {
      return new ArrayList<>(timeline);
   }

   public static void goalStart(Object var0) {
      try {
         String var1 = safe(read(var0, "description"));
         String var2 = safe(read(var0, "buildPosition"));
         if (!activeGoal || lastGoalObject != var0 || !var1.equals(fields.get("goal")) || !var2.equals(fields.get("site"))) {
            lastGoalObject = var0;
            activeGoal = true;
            fields.put("goal", var1);
            fields.put("site", var2);
            lastCompleted = -1;
            fields.put("progress", "0/?");
            fields.put("material", "Not calculated yet");
            fields.put("target", "Waiting for blueprint");
            fields.put("blocker", "None reported");
            event("GOAL", "Build: " + var1 + " at " + var2);
         }

         fields.put("phase", "Generating/validating blueprint");
      } catch (Throwable var3) {
      }
   }

   public static void taskState(Object var0, Object var1) {
      try {
         String var2 = var0.getClass().getSimpleName();
         String var3 = safe(var1);
         if (var2.contains("BuildFromCode") || var2.contains("BuildStructure")) {
            fields.put("activity", var3);
            String var4 = var3.startsWith("Gathering") || var3.startsWith("Collecting")
               ? "Gathering materials"
               : (
                  var3.startsWith("Placing") || var3.startsWith("Started physical")
                     ? "Placing blocks"
                     : (
                        !var3.startsWith("Parsing") && !var3.startsWith("Blueprint")
                           ? (
                              var3.startsWith("Construction suspended")
                                 ? "Suspended"
                                 : (
                                    var3.startsWith("Construction FAILED")
                                       ? "Failed"
                                       : (var3.startsWith("Verified all") ? "Completed" : fields.getOrDefault("phase", "Building"))
                                 )
                           )
                           : "Blueprint preparation"
                     )
               );
            fields.put("phase", var4);
            if (var4.equals("Failed")) {
               fields.put("blocker", var3);
            }

            if (var4.equals("Completed")) {
               fields.put("blocker", "None");
               fields.put("itemGoals", "Materials ready");
               fields.put("craftDependencies", "Completed: all construction cells verified");
               fields.put("target", "All structure positions verified");
               fields.put("material", "No active gather request");
               fields.put("layerProgress", "Complete");
               fields.put("progress", fields.getOrDefault("total", "?") + "/" + fields.getOrDefault("total", "?"));
            }

            if (!var4.equals(lastPhase)) {
               lastPhase = var4;
               event(var4.equals("Failed") ? "ERROR" : (var4.equals("Suspended") ? "SUSPEND" : "PHASE"), var4 + ": " + var3);
            }
         }
      } catch (Throwable var5) {
      }
   }

   public static void taskFailure(Object var0, Object var1) {
      try {
         String var2 = safe(var0.getClass().getSimpleName()) + ": " + safe(var1);
         fields.put("blocker", var2);
         event("ERROR", var2);
      } catch (Throwable var3) {
      }
   }

   public static void onRunnerTick(TaskRunner var0) {
      long var1 = System.currentTimeMillis();
      if (var1 - lastRunnerSample >= 1000L) {
         lastRunnerSample = var1;

         try {
            TaskChain var3 = var0.getCurrentTaskChain();
            String var4 = var3 == null ? "None" : safe(var3.getName());
            fields.put("chain", var4);
            if (!var4.equals(lastChain)) {
               lastChain = var4;
               event("CHAIN", var4);
            }

            if (var3 != null) {
               List<Task> var5 = var3.getTasks();
               ArrayList var6 = new ArrayList();
               if (var5 != null) {
                  for (Task var8 : var5) {
                     if (var8 != null) {
                        var6.add(safe(var8.toString()));
                     }

                     if (var6.size() >= 8) {
                        break;
                     }
                  }
               }

               String var10 = String.join(" > ", var6);
               fields.put("taskTree", var10.isEmpty() ? "No active nested tasks" : var10);
               String var11 = var6.isEmpty() ? "Idle" : (String)var6.get(var6.size() - 1);
               if (!var11.equals(lastTask)) {
                  lastTask = var11;
                  event("TASK", var11);
               }
            } else {
               fields.put("taskTree", "No active task chain");
            }
         } catch (Throwable var9) {
         }

         if (var1 - lastWrite >= 1000L) {
            writeSnapshot();
         }
      }
   }

   public static void builderTick(Object var0) {
      long var1 = System.currentTimeMillis();
      if (var1 - lastBuilderSample >= 1000L) {
         lastBuilderSample = var1;

         try {
            Object var3 = read(var0, "schematic");
            if (var3 == null) {
               fields.put("progress", "Planning");
               return;
            }

            Object var4 = read(var3, "blocks");
            int var5 = var4 instanceof Map ? ((Map)var4).size() : 0;
            int var6 = number(read(var0, "previousCompleted"));
            if (var6 < 0) {
               var6 = 0;
            }

            fields.put("progress", var6 + "/" + var5);
            fields.put("total", String.valueOf(var5));
            int var7 = number(read(var0, "activeLayer"));
            int var8 = number(read(var3, "sy"));
            Object var9 = read(var3, "origin");
            int var10 = number(methodNamed(var9, "method_10264", 0).invoke(var9));
            fields.put("buildLayer", var7 >= var8 ? "Final doors" : "Y=" + (var10 + var7) + " of " + (var10 + var8 - 1));

            try {
               Method var11 = var3.getClass().getDeclaredMethod("countScopedPlanned", int.class);
               Method var12 = var3.getClass().getDeclaredMethod("countScopedCompleted", PlayerEngineController.class, int.class);
               var11.setAccessible(true);
               var12.setAccessible(true);
               Object var13 = read(var0, "this$0");
               Object var14 = read(var13, "controller");
               fields.put("layerProgress", var12.invoke(var3, var14, var7) + "/" + var11.invoke(var3, var7));
            } catch (Throwable var21) {
            }

            fields.put("retries", safe(read(var0, "buildRestarts")));
            fields.put("interruptions", safe(read(var0, "temporaryInterruptions")));
            fields.put("idleTicks", safe(read(var0, "ticksSinceProgress")));
            fields.put("site", safe(read(var3, "origin")));
            Object var24 = read(var0, "currentResource");
            fields.put("material", var24 == null ? "No active gather request" : safe(var24));
            if (var24 != null) {
               fields.put("materialOwnedAtStart", safe(read(var0, "resourceStartCount")));
               fields.put("materialCurrentCount", safe(read(var0, "lastGatheringCount")));
            }

            String var25 = "";

            try {
               Object var26 = read(var0, "this$0");
               Object var27 = read(var26, "controller");
               if (var27 == null) {
                  var27 = read(var26, "mod");
               }

               int var15 = number(read(var0, "activeLayer"));
               Method var16 = var3.getClass().getDeclaredMethod("requiredInventoryScoped", PlayerEngineController.class, int.class);
               var16.setAccessible(true);
               Object var17 = var16.invoke(var3, var27, var15);
               if (var17 instanceof Map) {
                  ArrayList var18 = new ArrayList();

                  for (Entry<?, ?> var20 : ((Map<?, ?>)var17).entrySet()) {
                     var18.add(safe(var20.getKey()) + " : " + safe(var20.getValue()) + " needed");
                     if (var18.size() >= 12) {
                        break;
                     }
                  }

                  var25 = var18.isEmpty() ? "Materials ready" : "Shortages: " + String.join("; ", var18);
               }
            } catch (Throwable var22) {
               var25 = "Materials unavailable (inspector)";
            }

            fields.put("itemGoals", var25);
            if (var6 != lastCompleted) {
               if (lastCompleted >= 0) {
                  event("BUILD", "Verified blocks " + var6 + "/" + var5);
               }

               lastCompleted = var6;
            }

            if (!"Completed".equals(fields.get("phase"))) {
               fields.put("target", findFirstMissing(var0, var3));
            }
         } catch (Throwable var23) {
         }
      }
   }

   private static String findFirstMissing(Object var0, Object var1) {
      try {
         Object var2 = read(var0, "this$0");
         Object var3 = read(var2, "controller");
         if (var3 == null) {
            var3 = read(var2, "mod");
         }

         if (var3 == null) {
            return "Not available (controller not yet attached)";
         }

         Object var4 = methodNamed(var3, "getWorld", 0).invoke(var3);
         Object var5 = read(var1, "origin");
         int var6 = number(methodNamed(var5, "method_10263", 0).invoke(var5));
         int var7 = number(methodNamed(var5, "method_10264", 0).invoke(var5));
         int var8 = number(methodNamed(var5, "method_10260", 0).invoke(var5));
         Constructor var9 = var5.getClass().getConstructor(int.class, int.class, int.class);
         Object var10 = read(var1, "blocks");
         if (!(var10 instanceof Map)) {
            return "Blueprint unavailable";
         }

         int var11 = number(read(var0, "activeLayer"));
         Method var12 = var1.getClass().getDeclaredMethod("layerView", int.class);
         var12.setAccessible(true);
         ISchematic var13 = (ISchematic)var12.invoke(var1, var11);

         for (Entry<?, ?> var15 : ((Map<?, ?>)var10).entrySet()) {
            long var16 = ((Number)var15.getKey()).longValue();
            int var18 = (int)(var16 >> 32);
            int var19 = (int)(var16 >> 16 & 65535L);
            int var20 = (int)(var16 & 65535L);
            Object var21 = var9.newInstance(var6 + var18, var7 + var19, var8 + var20);
            Object var22 = methodNamed(var4, "method_8320", 1).invoke(var4, var21);
            if (var13.inSchematic(var18, var19, var20, (BlockState)var22)) {
               Object var23 = var15.getValue();
               Object var24 = methodNamed(var22, "method_26204", 0).invoke(var22);
               Object var25 = methodNamed(var23, "method_26204", 0).invoke(var23);
               if (!Objects.equals(var24, var25)) {
                  return "NEXT MISSING: "
                     + safe(var25)
                     + " @ ("
                     + (var6 + var18)
                     + ","
                     + (var7 + var19)
                     + ","
                     + (var8 + var20)
                     + ") [found "
                     + safe(var24)
                     + "]";
               }
            }
         }

         return "All planned block types match (door upper halves checked separately by builder)";
      } catch (Throwable var26) {
         return "Next missing block unavailable: " + safe(var26.getClass().getSimpleName());
      }
   }

   private static Method methodNamed(Object var0, String var1, int var2) throws Exception {
      for (Method var6 : var0.getClass().getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == var2) {
            return var6;
         }
      }

      throw new NoSuchMethodException(var1);
   }

   private static int number(Object var0) {
      return var0 instanceof Number ? ((Number)var0).intValue() : 0;
   }

   private static Object read(Object var0, String var1) throws Exception {
      if (var0 == null) {
         return null;
      }

      for (Class var2 = var0.getClass(); var2 != null; var2 = var2.getSuperclass()) {
         try {
            Field var3 = var2.getDeclaredField(var1);
            var3.setAccessible(true);
            return var3.get(var0);
         } catch (NoSuchFieldException var4) {
         }
      }

      return null;
   }

   public static synchronized void writeSnapshot() {
      lastWrite = System.currentTimeMillis();

      try {
         Files.createDirectories(DIR);
         StringBuilder var0 = new StringBuilder("ELLIE OBSERVATORY - LIVE SNAPSHOT\n");
         var0.append("timestamp: ").append(Instant.now()).append("\n");

         for (Entry var2 : new TreeMap<>(fields).entrySet()) {
            var0.append((String)var2.getKey()).append(": ").append(safe(var2.getValue())).append("\n");
         }

         var0.append("\nRecent events:\n");

         for (String var7 : timeline) {
            var0.append(var7).append('\n');
         }

         Path var6 = SNAPSHOT.resolveSibling(SNAPSHOT.getFileName() + ".tmp");
         Files.writeString(var6, var0, StandardCharsets.UTF_8);

         try {
            Files.move(var6, SNAPSHOT, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var3) {
            Files.move(var6, SNAPSHOT, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Exception var4) {
         System.err.println("[EllieObservatory] Cannot save snapshot: " + safe(var4.getMessage()));
      }
   }

   static {
      fields.put("version", "1");
      fields.put("scope", "Integrated server (single-player)");
      fields.put("phase", "Waiting for task telemetry");
   }
}
