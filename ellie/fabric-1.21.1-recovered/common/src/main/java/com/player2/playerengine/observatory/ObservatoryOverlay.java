package com.player2.playerengine.observatory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;

public final class ObservatoryOverlay implements ClientModInitializer {
   private static final String[] TABS = new String[]{"GOALS", "MATERIALS", "NAVIGATION", "BUILD", "TIMELINE"};
   private static volatile boolean open;
   private static volatile int tab;
   private static volatile int scroll;
   private static boolean previousF8;
   private static boolean previousF9;
   private static boolean previousF10;
   private static boolean previousUp;
   private static boolean previousDown;
   private static boolean inputFailed;
   private static boolean renderFailed;
   private static boolean keyBindingsReady;
   private static boolean hudReported;
   private static Object showKey;
   private static Object fallbackKey;
   private static Object tabKey;
   private static Object exportKey;
   private static Object upKey;
   private static Object downKey;
   private static Method wasPressed;
   private static Object client;
   private static Method fill;
   private static Method text;
   private static Object renderer;
   private static long window;
   private static Method glfw;

   public void onInitializeClient() {
      DistanceDiagnostics.announceBuild();

      try {
         Object var1 = getStaticField("net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback", "EVENT");
         Object var2 = getStaticField("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents", "END_CLIENT_TICK");
         register(var1, "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback", (var0, var1x, var2x) -> {
            if (var1x.getName().equals("onHudRender") && var2x != null && var2x.length > 0) {
               render(var2x[0]);
            }

            return null;
         });
         register(var2, "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick", (var0, var1x, var2x) -> {
            if (var1x.getName().equals("onEndTick")) {
               poll();
            }

            return null;
         });

         try {
            registerKeyBindings();
         } catch (Throwable var4) {
            keyBindingsReady = false;
            System.err.println("[EllieObservatory] Key bindings unavailable; falling back to direct input: " + var4);
            ObservatoryTelemetry.event("HUD_WARN", "Key binding registration failed: " + var4.getClass().getSimpleName());
         }

         ObservatoryTelemetry.event("HUD_INIT", "Client hooks registered; key bindings " + (keyBindingsReady ? "READY" : "FALLBACK") + "; open F8 or F7");
         System.out.println("[EllieObservatory] Client HUD ready, F8/F7 opens, logs: " + ObservatoryTelemetry.SNAPSHOT);
      } catch (Throwable var5) {
         System.err.println("[EllieObservatory] HUD registration unavailable: " + var5);
      }
   }

   private static Object getStaticField(String var0, String var1) throws Exception {
      return Class.forName(var0).getField(var1).get(null);
   }

   private static void register(Object var0, String var1, InvocationHandler var2) throws Exception {
      Class var3 = Class.forName(var1);
      Object var4 = Proxy.newProxyInstance(var3.getClassLoader(), new Class[]{var3}, (var1x, var2x, var3x) -> {
         if (var2x.getDeclaringClass() == Object.class) {
            if (var2x.getName().equals("hashCode")) {
               return System.identityHashCode(var1x);
            } else {
               return var2x.getName().equals("equals") ? var1x == var3x[0] : "EllieObservatory";
            }
         } else {
            return var2.invoke(var1x, var2x, var3x);
         }
      });
      Class var5 = Class.forName("net.fabricmc.fabric.api.event.Event");
      Method var6 = var5.getMethod("register", Object.class);
      var6.invoke(var0, var4);
   }

   private static Method method(Object var0, String var1, int var2) throws Exception {
      for (Method var6 : var0.getClass().getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == var2) {
            return var6;
         }
      }

      throw new NoSuchMethodException(var0.getClass() + "." + var1 + "/" + var2);
   }

   private static void setupRenderer() throws Exception {
      if (client == null || renderer == null) {
         Class var0 = Class.forName("net.minecraft.class_310");
         client = var0.getMethod("method_1551").invoke(null);
         renderer = var0.getField("field_1772").get(client);
      }
   }

   private static void setupFallbackInput() throws Exception {
      if (glfw == null) {
         setupRenderer();
         Object var0 = method(client, "method_22683", 0).invoke(client);
         window = ((Number)method(var0, "method_4490", 0).invoke(var0)).longValue();
         glfw = Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwGetKey", long.class, int.class);
      }
   }

   private static void registerKeyBindings() throws Exception {
      Class var0 = Class.forName("net.minecraft.class_304");
      Constructor var1 = var0.getConstructor(String.class, int.class, String.class);
      Method var2 = Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper").getMethod("registerKeyBinding", var0);
      wasPressed = var0.getMethod("method_1436");
      String var3 = "key.categories.ellie_observatory";
      showKey = var2.invoke(null, var1.newInstance("key.ellie_observatory.open", 297, var3));
      fallbackKey = var2.invoke(null, var1.newInstance("key.ellie_observatory.alternate", 296, var3));
      tabKey = var2.invoke(null, var1.newInstance("key.ellie_observatory.next_tab", 298, var3));
      exportKey = var2.invoke(null, var1.newInstance("key.ellie_observatory.export", 299, var3));
      upKey = var2.invoke(null, var1.newInstance("key.ellie_observatory.scroll_up", 266, var3));
      downKey = var2.invoke(null, var1.newInstance("key.ellie_observatory.scroll_down", 267, var3));
      keyBindingsReady = true;
   }

   private static boolean pressed(Object var0) throws Exception {
      return Boolean.TRUE.equals(wasPressed.invoke(var0));
   }

   private static boolean key(int var0) throws Exception {
      return ((Number)glfw.invoke(null, window, var0)).intValue() == 1;
   }

   private static void toggle() {
      open = !open;
      ObservatoryTelemetry.event("HUD_KEY", "Dashboard " + (open ? "OPEN" : "CLOSED") + ", tab " + TABS[tab]);
      System.out.println("[EllieObservatory] HUD toggled " + (open ? "ON" : "OFF"));
   }

   private static void poll() {
      DistanceDiagnostics.clientTick(client);
      if (!inputFailed) {
         try {
            if (keyBindingsReady) {
               if (pressed(showKey) || pressed(fallbackKey)) {
                  toggle();
               }

               if (open) {
                  if (pressed(tabKey)) {
                     tab = (tab + 1) % TABS.length;
                     scroll = 0;
                  }

                  if (pressed(upKey)) {
                     scroll = Math.max(0, scroll - 5);
                  }

                  if (pressed(downKey)) {
                     scroll += 5;
                  }

                  if (pressed(exportKey)) {
                     ObservatoryTelemetry.writeSnapshot();
                     ObservatoryTelemetry.event("EXPORT", "Manual diagnostic snapshot requested (F10)");
                  }
               }
            } else {
               setupFallbackInput();
               boolean var0 = key(297);
               boolean var1 = key(296);
               boolean var2 = key(298);
               boolean var3 = key(299);
               boolean var4 = key(266);
               boolean var5 = key(267);
               if ((var0 || var1) && !previousF8) {
                  toggle();
               }

               if (open) {
                  if (var2 && !previousF9) {
                     tab = (tab + 1) % TABS.length;
                     scroll = 0;
                  }

                  if (var4 && !previousUp) {
                     scroll = Math.max(0, scroll - 5);
                  }

                  if (var5 && !previousDown) {
                     scroll += 5;
                  }

                  if (var3 && !previousF10) {
                     ObservatoryTelemetry.writeSnapshot();
                     ObservatoryTelemetry.event("EXPORT", "Manual diagnostic snapshot requested (F10)");
                  }
               }

               previousF8 = var0 || var1;
               previousF9 = var2;
               previousF10 = var3;
               previousUp = var4;
               previousDown = var5;
            }
         } catch (Throwable var6) {
            inputFailed = true;
            System.err.println("[EllieObservatory] Input error; HUD unavailable until restart: " + var6);
            ObservatoryTelemetry.event("HUD_ERROR", "Input: " + var6.getClass().getSimpleName() + ": " + var6.getMessage());
         }
      }
   }

   private static void render(Object var0) {
      if (!renderFailed) {
         try {
            if (!hudReported) {
               hudReported = true;
               ObservatoryTelemetry.event("HUD_RENDER", "Render callback reached; dashboard open=" + open);
            }

            if (!open) {
               return;
            }

            setupRenderer();
            if (fill == null) {
               fill = method(var0, "method_25294", 5);
               text = method(var0, "method_51433", 6);
            }

            int var1 = 960;
            int var2 = 540;

            try {
               Object var3 = method(client, "method_22683", 0).invoke(client);
               var1 = ((Number)method(var3, "method_4486", 0).invoke(var3)).intValue();
               var2 = ((Number)method(var3, "method_4502", 0).invoke(var3)).intValue();
            } catch (Throwable var19) {
            }

            int var21 = Math.min(var1 - 20, 700);
            int var4 = Math.min(var2 - 22, 440);
            if (var21 < 260 || var4 < 120) {
               return;
            }

            byte var5 = 10;
            byte var6 = 10;
            rect(var0, var5, var6, var21, var4, -334752992);
            rect(var0, var5, var6, var21, 28, -14339000);
            label(var0, "ELLIE  /  OBSERVATORY  •  F8 Close", var5 + 12, var6 + 10, -1445638);
            String var7 = ObservatoryTelemetry.view().getOrDefault("phase", "No data");
            label(var0, shorten(var7, 38), var5 + var21 - 226, var6 + 10, -6892302);
            int var8 = var6 + 36;
            int var9 = var5 + 12;

            for (int var10 = 0; var10 < TABS.length; var10++) {
               String var11 = (var10 == tab ? "[" : " ") + TABS[var10] + (var10 == tab ? "]" : " ");
               label(var0, var11, var9, var8, var10 == tab ? -7478859 : -4601644);
               var9 += var11.length() * 6 + 9;
            }

            Map var22 = ObservatoryTelemetry.view();
            ArrayList var23 = new ArrayList();
            if (tab == 0) {
               add(var23, "LONG-TERM GOAL", (String)var22.get("goal"));
               add(var23, "LOCATION", (String)var22.get("site"));
               add(var23, "CURRENT PHASE", (String)var22.get("phase"));
               add(var23, "ACTIVE TASK CHAIN", (String)var22.get("chain"));
               add(var23, "TASK TREE", (String)var22.get("taskTree"));
               add(var23, "CURRENT ACTION", (String)var22.get("activity"));
               add(var23, "BLOCKER / FAILURE", (String)var22.get("blocker"));
               add(var23, "PROGRESS", (String)var22.get("progress"));
               var23.add("");
               var23.add("PLAN PHASES (observed, not an AI promise):");
               var23.add(" 1  Blueprint generation / validation");
               var23.add(" 2  Materials and scaffolding collection");
               var23.add(" 3  Foundation, Y-level stages, final doors");
               var23.add(" 4  Verify world blocks vs blueprint");
            } else if (tab == 1) {
               add(var23, "REQUESTED MATERIAL", (String)var22.get("material"));
               add(var23, "MATERIAL NEEDS", (String)var22.get("itemGoals"));
               add(var23, "COUNT AT START", (String)var22.get("materialOwnedAtStart"));
               add(var23, "LAST OBSERVED COUNT", (String)var22.get("materialCurrentCount"));
               add(var23, "CONSTRUCTION PROGRESS", (String)var22.get("progress"));
               var23.add("");
               var23.add("Counts are real task inventory snapshots.");
               var23.add("Shortages are recalculated by the builder.");
               var23.add("Unreserved inventory is not claimed as reserved.");
            } else if (tab == 2) {
               add(var23, "CURRENT CHAIN", (String)var22.get("chain"));
               add(var23, "TASK TREE", (String)var22.get("taskTree"));
               add(var23, "BUILD SITE", (String)var22.get("site"));
               add(var23, "TARGET", (String)var22.get("target"));
               add(var23, "IDLE TICKS", (String)var22.get("idleTicks"));
               add(var23, "PATH RESTARTS", (String)var22.get("retries"));
               add(var23, "INTERRUPTIONS", (String)var22.get("interruptions"));
               var23.add("");
               DistanceDiagnostics.appendNavigationLines(var23, var22);
               var23.add("Exact Baritone target and path nodes are NOT exposed");
               var23.add("by the current task interface. Not fabricated.");
            } else if (tab == 3) {
               add(var23, "GOAL", (String)var22.get("goal"));
               add(var23, "ANCHOR", (String)var22.get("site"));
               add(var23, "VERIFIED BLOCKS", (String)var22.get("progress"));
               add(var23, "ACTIVE Y LAYER", (String)var22.get("buildLayer"));
               add(var23, "LAYER PROGRESS", (String)var22.get("layerProgress"));
               add(var23, "BLUEPRINT POSITIONS", (String)var22.get("total"));
               add(var23, "PHASE", (String)var22.get("phase"));
               add(var23, "ACTION", (String)var22.get("activity"));
               add(var23, "MATERIAL SHORTAGES", (String)var22.get("itemGoals"));
               add(var23, "RESTARTS", (String)var22.get("retries"));
               add(var23, "INTERRUPTIONS", (String)var22.get("interruptions"));
               add(var23, "BLOCKER", (String)var22.get("blocker"));
               var23.add("");
               var23.add("Completion means block-by-block verification,");
               var23.add("not AI claiming that construction is finished.");
            } else {
               var23.addAll(ObservatoryTelemetry.history());
               if (var23.isEmpty()) {
                  var23.add("No events captured yet.");
               }
            }

            int var12 = var6 + 60;
            int var13 = Math.max(1, (var4 - 93) / 13);
            int var14 = Math.max(0, var23.size() - var13);
            if (scroll > var14) {
               scroll = var14;
            }

            int var15 = Math.max(20, (var21 - 25) / 6);

            for (int var16 = 0; var16 < var13 && var16 + scroll < var23.size(); var16++) {
               String var17 = (String)var23.get(var16 + scroll);
               int var18 = var17.startsWith("BLOCKER")
                  ? -18261
                  : (
                     !var17.contains("ERROR") && !var17.contains("FAILED")
                        ? (!var17.startsWith("LONG-TERM") && !var17.startsWith("REQUESTED") ? -2760727 : -8005187)
                        : -23910
                  );
               label(var0, shorten(var17, var15), var5 + 13, var12 + var16 * 13, var18);
            }

            rect(var0, var5 + 1, var6 + var4 - 26, var21 - 2, 25, -15129033);
            label(var0, "F9 Next tab  |  PgUp/PgDn Scroll  |  F10 Export  |  logs/ellie-observatory-*", var5 + 12, var6 + var4 - 17, -4667432);
         } catch (Throwable var20) {
            renderFailed = true;
            System.err.println("[EllieObservatory] HUD render problem: " + var20);
            ObservatoryTelemetry.event("HUD_ERROR", "Rendering: " + var20.getClass().getSimpleName() + ": " + var20.getMessage());
         }
      }
   }

   private static void add(List<String> var0, String var1, String var2) {
      var0.add(var1 + ":");

      String var3;
      for (var3 = var2 == null ? "Not reported yet" : var2; var3.length() > 106; var3 = var3.substring(106)) {
         var0.add("  " + var3.substring(0, 106));
      }

      var0.add("  " + var3);
      var0.add("");
   }

   private static String shorten(String var0, int var1) {
      return var0.length() > var1 ? var0.substring(0, Math.max(0, var1 - 3)) + "..." : var0;
   }

   private static void rect(Object var0, int var1, int var2, int var3, int var4, int var5) throws Exception {
      fill.invoke(var0, var1, var2, var1 + var3, var2 + var4, var5);
   }

   private static void label(Object var0, String var1, int var2, int var3, int var4) throws Exception {
      text.invoke(var0, renderer, var1, var2, var3, var4, false);
   }
}
