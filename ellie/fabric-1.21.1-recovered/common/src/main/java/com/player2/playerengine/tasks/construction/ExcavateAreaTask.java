package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.nativecommands.NativeAccess;
import com.player2.playerengine.schematic.SchematicDepotGuard;
import com.player2.playerengine.schematic.SchematicPlan;
import com.player2.playerengine.schematic.SchematicWorld;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;

public final class ExcavateAreaTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
   private static final int MAX_VOLUME = 32768;
   private static final int SCAN_PER_TICK = 128;
   private static final int MAX_TRANSIENT_INTERRUPTS = 128;
   private static final int MAX_FINAL_RECHECKS = 2;
   // The Baritone builder can release its own schematic on successful completion.
   // Briefly settle and recheck before attempting a bounded replacement plan.
   private static final int RELEASE_SETTLE_TICKS = 10;
   private static final int MAX_LAYER_RETRIES = 3;
   private final int x1;
   private final int y1;
   private final int z1;
   private final int x2;
   private final int y2;
   private final int z2;
   private final int volume;
   private Object world;
   private Object builder;
   private Object ownedSchematic;
   private int scan;
   private int airAtStart;
   private int verifyScan;
   private int remaining;
   private int airCount;
   // Native clearArea has its own build ordering. Limit every clearArea plan to one
   // horizontal layer so the command controls height order (highest Y first).
   private boolean preflightComplete;
   private int nextLayerY;
   private int layerScan;
   private int layerAirAtStart;
   private int activeLayerY;
   private int builderReleasedTicks;
   private int layerRetries;
   private int finalScan;
   private int finalAir;
   private int finalRechecks;
   private String finalMismatch = "";
   private int verifiedAirLast = -1;
   private int noWorkTicks;
   private int buildTicks;
   private boolean building;
   private boolean complete;
   private boolean success;
   private boolean terminal;
   private boolean transientSuspended;
   private int acceptedInterrupts;
   private String reason = "Preflight pending";

   public ExcavateAreaTask(int var1, int var2, int var3, int var4, int var5, int var6) {
      int[] var7 = normalize(var1, var2, var3, var4, var5, var6);
      this.x1 = var7[0];
      this.y1 = var7[1];
      this.z1 = var7[2];
      this.x2 = var7[3];
      this.y2 = var7[4];
      this.z2 = var7[5];
      this.volume = var7[6];
   }

   public static int[] normalize(int var0, int var1, int var2, int var3, int var4, int var5) {
      int var6 = Math.min(var0, var3);
      int var7 = Math.min(var1, var4);
      int var8 = Math.min(var2, var5);
      int var9 = Math.max(var0, var3);
      int var10 = Math.max(var1, var4);
      int var11 = Math.max(var2, var5);
      if (var6 >= -29999000 && var9 <= 29999000 && var8 >= -29999000 && var11 <= 29999000 && var7 >= -64 && var10 <= 319) {
         long var12 = (long)var9 - var6 + 1L;
         long var14 = (long)var10 - var7 + 1L;
         long var16 = (long)var11 - var8 + 1L;
         long var18 = var12 * var14 * var16;
         if (var18 >= 1L && var18 <= 32768L) {
            return new int[]{var6, var7, var8, var9, var10, var11, (int)var18};
         } else {
            throw new IllegalArgumentException("Excavation would affect " + var18 + " blocks; limit is 32768 per command");
         }
      } else {
         throw new IllegalArgumentException("Excavation exceeds vanilla world bounds");
      }
   }

   private SchematicPlan.Pos cell(int var1) {
      int var2 = this.x2 - this.x1 + 1;
      int var3 = this.z2 - this.z1 + 1;
      int var4 = var1 % var2;
      int var5 = var1 / var2 % var3;
      int var6 = var1 / (var2 * var3);
      return new SchematicPlan.Pos(this.x1 + var4, this.y1 + var6, this.z1 + var5);
   }

   /** X then Z within a single Y; all generated plans have height exactly one. */
   private SchematicPlan.Pos layerCell(int y, int index) {
      int width = this.x2 - this.x1 + 1;
      return new SchematicPlan.Pos(this.x1 + index % width, y, this.z1 + index / width);
   }

   private int layerVolume() {
      return (this.x2 - this.x1 + 1) * (this.z2 - this.z1 + 1);
   }

   private void resetLayerScan() {
      this.layerScan = 0;
      this.layerAirAtStart = 0;
   }

   private void advanceToNextLayer() {
      this.nextLayerY--;
      this.builderReleasedTicks = 0;
      this.layerRetries = 0;
      this.resetLayerScan();
   }

   private void finishSuccess(String message) {
      this.terminal = this.success = this.complete = true;
      this.reason = message;
      this.stopOwnedBuilder();
      this.report("EXCAVATE_COMPLETED", this.reason);
   }

   private void report(String var1, String var2) {
      try {
         NativeAccess.callStatic(
            "com.player2.playerengine.observatory.ObservatoryTelemetry",
            "event",
            var1,
            "area=" + this.x1 + "," + this.y1 + "," + this.z1 + ".." + this.x2 + "," + this.y2 + "," + this.z2 + " " + var2
         );
      } catch (Exception | LinkageError var4) {
      }
   }

   @Override
   protected void onStart() {
      try {
         if (this.world != null) {
            this.worldCheck();
            return;
         }

         this.world = NativeAccess.call(this.controller, "getWorld");
         if (this.world == null || NativeAccess.call(this.controller, "getEntity") == null) {
            throw new IllegalStateException("NPC/world unavailable");
         }

         int var1 = ((Number)NativeAccess.call(this.world, "method_31607")).intValue();
         int var2 = ((Number)NativeAccess.call(this.world, "method_31600")).intValue();
         if (this.y1 < var1 || this.y2 >= var2) {
            throw new IllegalStateException("Area exceeds world height " + var1 + ".." + (var2 - 1));
         }

         Object var3 = NativeAccess.call(this.controller, "getBaritone");
         this.builder = NativeAccess.call(var3, "getBuilderProcess");
         if (Boolean.TRUE.equals(NativeAccess.call(this.builder, "isActive"))) {
            throw new IllegalStateException("Existing Baritone construction is active; do not hijack it");
         }

         this.report("EXCAVATE_PREFLIGHT", "volume=" + this.volume + " safety=fail_closed");
      } catch (Exception | LinkageError var4) {
         this.block("START", var4);
      }
   }

   private void worldCheck() throws Exception {
      if (this.world == null || NativeAccess.call(this.controller, "getWorld") != this.world || NativeAccess.call(this.controller, "getEntity") == null) {
         throw new IllegalStateException("NPC/world changed; excavation cancelled");
      }
   }

   private Object pos(SchematicPlan.Pos var1) throws Exception {
      return SchematicWorld.position(var1);
   }

   private boolean air(SchematicPlan.Pos var1) throws Exception {
      Object var2 = NativeAccess.call(this.world, "method_8320", this.pos(var1));
      return Boolean.TRUE.equals(NativeAccess.call(var2, "method_26215"));
   }

   private boolean safe(SchematicPlan.Pos var1) throws Exception {
      Object var2 = this.pos(var1);
      if (!Boolean.TRUE.equals(NativeAccess.call(this.world, "method_22340", var2))) {
         throw new IllegalStateException("Unloaded chunk at " + var1.key());
      } else if (SchematicDepotGuard.protects(this.controller, var2)) {
         throw new IllegalStateException("Protected schematic depot/support at " + var1.key());
      } else if (NativeAccess.call(this.world, "method_8321", var2) != null) {
         throw new IllegalStateException("Storage/block entity in excavation at " + var1.key());
      } else {
         Object var3 = NativeAccess.call(this.world, "method_8320", var2);
         Object var4 = NativeAccess.call(var3, "method_26204");
         Object var5 = Class.forName("net.minecraft.class_7923").getField("field_41175").get(null);
         String var6 = String.valueOf(NativeAccess.call(var5, "method_10221", var4));
         if (!var6.equals("minecraft:water")
            && !var6.equals("minecraft:lava")
            && !var6.equals("minecraft:bedrock")
            && !var6.equals("minecraft:end_portal")
            && !var6.equals("minecraft:end_portal_frame")
            && !var6.equals("minecraft:nether_portal")) {
            return Boolean.TRUE.equals(NativeAccess.call(var3, "method_26215"));
         } else {
            throw new IllegalStateException("Unsafe or unbreakable block " + var6 + " at " + var1.key());
         }
      }
   }

   private Task block(String var1, Throwable var2) {
      return this.block(var1, var2.getClass().getSimpleName() + ": " + var2.getMessage());
   }

   private Task block(String var1, String var2) {
      if (this.terminal) {
         return null;
      }

      this.terminal = true;
      this.success = false;
      this.reason = "BLOCKED_EXCAVATE_" + var1 + ": " + var2;
      this.report("EXCAVATE_BLOCKED", this.reason);
      this.fail(this.reason);
      this.stopOwnedBuilder();
      return null;
   }

   private void stopOwnedBuilder() {
      if (this.builder != null && this.ownedSchematic != null) {
         try {
            if (NativeAccess.get(this.builder, "schematic") == this.ownedSchematic || NativeAccess.get(this.builder, "realSchematic") == this.ownedSchematic) {
               NativeAccess.call(this.builder, "onLostControl");
            }
         } catch (Exception | LinkageError var2) {
         }
      }
   }

   @Override
   protected Task onTick() {
      if (this.terminal) {
         return null;
      }

      try {
         this.worldCheck();
         Task inventoryRecovery=com.player2.playerengine.agentic.AutoInventoryCleanup.beforeResourceTick(this);
         if(inventoryRecovery!=null){
            if(this.building){this.stopOwnedBuilder();this.transientSuspended=true;this.reconcileTransientResume(false);}
            return inventoryRecovery;
         }
         if (this.transientSuspended) {
            boolean retained = this.building
               && this.builder != null
               && this.ownedSchematic != null
               && (NativeAccess.get(this.builder, "schematic") == this.ownedSchematic
                   || NativeAccess.get(this.builder, "realSchematic") == this.ownedSchematic)
               && Boolean.TRUE.equals(NativeAccess.call(this.builder, "isActive"));
            this.reconcileTransientResume(retained);
         }

         // Preserve the old fail-closed full-volume preflight, including protected
         // schematic depots, fluids, unloaded chunks and block entities.
         if (!this.preflightComplete) {
            for (int visited = 0; this.scan < this.volume && visited < SCAN_PER_TICK; visited++) {
               if (this.safe(this.cell(this.scan))) {
                  this.airAtStart++;
               }
               this.scan++;
            }
            if (this.scan < this.volume) {
               return null;
            }
            if (this.airAtStart == this.volume) {
               this.finishSuccess("Already clear: verified " + this.volume + " air blocks");
               return null;
            }
            this.preflightComplete = true;
            this.nextLayerY = this.y2;
            this.remaining = this.volume - this.airAtStart;
            this.resetLayerScan();
            this.report("EXCAVATE_TOP_DOWN_READY", "highestY=" + this.y2 + " lowestY=" + this.y1 + " remaining=" + this.remaining);
         }

         if (!this.building) {
            if (this.nextLayerY < this.y1) {
               // Falling sand/gravel or changes above us can re-fill layers.
               // Never report a successful excavation without one final complete
               // verification of the entire requested volume.
               for (int visited = 0; this.finalScan < this.volume && visited < SCAN_PER_TICK; visited++) {
                  SchematicPlan.Pos pos = this.cell(this.finalScan);
                  if (this.safe(pos)) {
                     this.finalAir++;
                  } else if (this.finalMismatch.isEmpty()) {
                     this.finalMismatch = pos.key();
                  }
                  this.finalScan++;
               }
               if (this.finalScan < this.volume) {
                  return null;
               }
               if (this.finalAir == this.volume) {
                  this.remaining = 0;
                  this.finishSuccess("Verified top-down excavation: " + this.volume + " air blocks");
                  return null;
               }
               if (++this.finalRechecks > MAX_FINAL_RECHECKS) {
                  return this.block("REFILLED", "Final verification found " + (this.volume - this.finalAir)
                     + " non-air blocks; first=" + this.finalMismatch + " after " + MAX_FINAL_RECHECKS + " rechecks");
               }
               this.remaining = this.volume - this.finalAir;
               this.report("EXCAVATE_TOP_DOWN_RECHECK", "nonAir=" + this.remaining + " first=" + this.finalMismatch
                  + " pass=" + this.finalRechecks);
               this.nextLayerY = this.y2;
               this.resetLayerScan();
               this.finalScan = 0;
               this.finalAir = 0;
               this.finalMismatch = "";
               return null;
            }

            // Preflight each individual layer again before excavating it. This
            // avoids clearing a newly placed chest or protected depot while the
            // NPC is descending through a tall site.
            int layerSize = this.layerVolume();
            for (int visited = 0; this.layerScan < layerSize && visited < SCAN_PER_TICK; visited++) {
               if (this.safe(this.layerCell(this.nextLayerY, this.layerScan))) {
                  this.layerAirAtStart++;
               }
               this.layerScan++;
            }
            if (this.layerScan < layerSize) {
               return null;
            }
            if (this.layerAirAtStart == layerSize) {
               this.report("EXCAVATE_LAYER_CLEAR", "y=" + this.nextLayerY + " alreadyAir=true");
               this.advanceToNextLayer();
               return null;
            }
            if (Boolean.TRUE.equals(NativeAccess.call(this.builder, "isActive"))) {
               return this.block("BUILDER_OCCUPIED", "Another native construction is active before layer " + this.nextLayerY);
            }
            this.activeLayerY = this.nextLayerY;
            NativeAccess.call(
               this.builder,
               "clearArea",
               this.pos(new SchematicPlan.Pos(this.x1, this.activeLayerY, this.z1)),
               this.pos(new SchematicPlan.Pos(this.x2, this.activeLayerY, this.z2))
            );
            this.ownedSchematic = NativeAccess.get(this.builder, "schematic");
            if (this.ownedSchematic == null) {
               throw new IllegalStateException("Native clearArea did not create a layer builder plan");
            }
            this.building = true;
            this.builderReleasedTicks = 0;
            this.verifyScan = 0;
            this.airCount = 0;
            this.verifiedAirLast = -1;
            this.noWorkTicks = 0;
            this.reason = "Excavating top-down layer Y=" + this.activeLayerY;
            this.report("EXCAVATE_LAYER_STARTED", "y=" + this.activeLayerY + " nonAir=" + (layerSize - this.layerAirAtStart)
               + " order=top_down builder=native_clearArea");
            return null;
         }

         if (++this.buildTicks > 240000) {
            return this.block("WORK_BUDGET", "Total excavation work budget exceeded");
         }
         // IMPORTANT: the native builder calls onLostControl() by itself when
         // its layer plan completes. That clears BOTH schematic references.
         // Check the actual world before deciding anyone has stolen control.
         boolean ownsBuilder = this.ownedSchematic != null
            && (NativeAccess.get(this.builder, "schematic") == this.ownedSchematic
                || NativeAccess.get(this.builder, "realSchematic") == this.ownedSchematic);
         boolean builderActive = Boolean.TRUE.equals(NativeAccess.call(this.builder, "isActive"));

         int layerSize = this.layerVolume();
         for (int visited = 0; this.verifyScan < layerSize && visited < SCAN_PER_TICK; visited++) {
            if (this.air(this.layerCell(this.activeLayerY, this.verifyScan))) {
               this.airCount++;
            }
            this.verifyScan++;
         }
         if (this.verifyScan < layerSize) {
            return null;
         }

         int layerRemaining = layerSize - this.airCount;
         if (this.airCount > this.verifiedAirLast) {
            this.noWorkTicks = 0;
         } else {
            this.noWorkTicks += (layerSize + SCAN_PER_TICK - 1) / SCAN_PER_TICK;
         }
         this.verifiedAirLast = this.airCount;
         this.verifyScan = 0;
         this.airCount = 0;

         if (layerRemaining == 0) {
            int cleared = layerSize - this.layerAirAtStart;
            this.remaining = Math.max(0, this.remaining - cleared);
            this.report("EXCAVATE_LAYER_COMPLETED", "y=" + this.activeLayerY + " remainingEstimate=" + this.remaining);
            this.stopOwnedBuilder();
            this.ownedSchematic = null;
            this.building = false;
            this.advanceToNextLayer();
            return null;
         }
         // A different ACTIVE plan is a genuine takeover; never cancel or
         // overwrite somebody else's builder work.
         if (!ownsBuilder && builderActive) {
            return this.block("OWNERSHIP", "A different native builder is active at Y=" + this.activeLayerY
               + "; " + layerRemaining + " blocks are still solid");
         }
         if (!ownsBuilder || !builderActive) {
            // This is usually normal Baritone completion. Give block changes
            // time to settle before retrying only the unexcavated cells.
            if (this.builderReleasedTicks++ == 0) {
               this.report("EXCAVATE_BUILDER_RELEASED", "y=" + this.activeLayerY
                  + " remaining=" + layerRemaining + " active=" + builderActive);
            }
            if (this.builderReleasedTicks < RELEASE_SETTLE_TICKS) {
               return null;
            }
            if (this.layerRetries >= MAX_LAYER_RETRIES) {
               return this.block("INCOMPLETE_LAYER", "Native builder released layer Y=" + this.activeLayerY
                  + " but " + layerRemaining + " blocks remain after " + this.layerRetries + " bounded retries");
            }
            this.layerRetries++;
            this.report("EXCAVATE_LAYER_RETRY", "y=" + this.activeLayerY + " attempt=" + this.layerRetries
               + "/" + MAX_LAYER_RETRIES + " remaining=" + layerRemaining);
            this.building = false;
            this.ownedSchematic = null;
            this.builderReleasedTicks = 0;
            this.verifyScan = 0;
            this.airCount = 0;
            this.noWorkTicks = 0;
            this.verifiedAirLast = -1;
            this.resetLayerScan(); // recheck depot, fluids, loaded chunks before replanning
            return null;
         }
         this.builderReleasedTicks = 0;
         if (this.noWorkTicks > 2400) {
            return this.block("STALLED", "No verified clearing in 2,400 ticks at layer Y=" + this.activeLayerY
               + "; " + layerRemaining + " blocks remain in this layer");
         }
         if (this.buildTicks % 200 == 0) {
            this.report("EXCAVATE_PROGRESS", "layerY=" + this.activeLayerY
               + " layerRemaining=" + layerRemaining + " remainingEstimate=" + this.remaining);
         }
         return null;
      } catch (Exception | LinkageError ex) {
         return this.block("BOUNDARY", ex);
      }
   }

   private void reconcileTransientResume(boolean builderRetained) {
      if (!this.transientSuspended) {
         return;
      }
      this.transientSuspended = false;
      if (this.building && builderRetained) {
         this.report("EXCAVATE_RESUMED", "project=" + this.id() + " mode=layer_retained"
            + " layerY=" + this.activeLayerY + " interrupts=" + this.acceptedInterrupts);
         return;
      }
      // The native builder was released during interruption. Recheck the whole
      // volume and resume only the still-solid layers, always from top to bottom.
      // Never trust a partially completed old layer plan after ownership loss.
      this.building = false;
      this.ownedSchematic = null;
      this.builderReleasedTicks = 0;
      this.layerRetries = 0;
      this.preflightComplete = false;
      this.scan = 0;
      this.airAtStart = 0;
      this.verifyScan = 0;
      this.airCount = 0;
      this.verifiedAirLast = -1;
      this.nextLayerY = this.y2;
      this.resetLayerScan();
      this.finalScan = 0;
      this.finalAir = 0;
      this.finalMismatch = "";
      this.noWorkTicks = 0;
      this.report("EXCAVATE_RESUMED", "project=" + this.id()
         + " mode=full_safety_recheck_then_top_down interrupts=" + this.acceptedInterrupts
         + " workTicks=" + this.buildTicks);
   }

   @Override
   public boolean isFinished() {
      return this.terminal;
   }

   @Override
   public boolean isTerminal() {
      return this.terminal;
   }

   @Override
   public boolean isSuccessful() {
      return this.terminal && this.success;
   }

   @Override
   public String controlledReason() {
      return this.reason;
   }

   @Override
   public boolean prepareForTransientResume(TaskSuspensionCause var1) {
      return !this.terminal && (var1 == TaskSuspensionCause.HIGHER_PRIORITY_CHAIN || var1 == TaskSuspensionCause.GESTURE_OVERLAY);
   }

   @Override
   public void afterChildrenStopped() {
      if (!this.terminal) {
         if (!this.transientSuspended) {
            this.transientSuspended = true;
            this.acceptedInterrupts++;
            this.report(
               "EXCAVATE_SUSPENDED",
               "project="
                  + this.id()
                  + " stage="
                  + (this.building ? "BUILD" : "PREFLIGHT")
                  + " remaining="
                  + this.remaining
                  + " interrupts="
                  + this.acceptedInterrupts
                  + " budgetTicks="
                  + this.buildTicks
                  + " noWorkTicks="
                  + this.noWorkTicks
            );
         }

         this.stopOwnedBuilder();
         if (this.acceptedInterrupts > 128) {
            this.block("PREEMPTION_BUDGET", "Too many higher-priority interruptions: " + this.acceptedInterrupts);
         }
      }
   }

   @Override
   public void onTransientResumeAbandoned() {
      this.block("RESUME_ABANDONED", "Native coordinator abandoned transient excavation resume");
   }

   private String id() {
      return Integer.toHexString(System.identityHashCode(this));
   }

   @Override
   protected void onStop(Task var1) {
      this.stopOwnedBuilder();
      this.transientSuspended = false;
      if (!this.terminal) {
         this.terminal = true;
         this.success = false;
         this.reason = "BLOCKED_EXCAVATE_CANCELLED: explicit owner stop/replacement";
         this.report("EXCAVATE_CANCELLED", "project=" + this.id() + " " + this.reason);
      }
   }

   @Override
   protected boolean isEqual(Task var1) {
      return var1 instanceof ExcavateAreaTask var2
         && this.x1 == var2.x1
         && this.y1 == var2.y1
         && this.z1 == var2.z1
         && this.x2 == var2.x2
         && this.y2 == var2.y2
         && this.z2 == var2.z2;
   }

   @Override
   protected String toDebugString() {
      return "Excavate top-down "
         + this.x1
         + ","
         + this.y1
         + ","
         + this.z1
         + " to "
         + this.x2
         + ","
         + this.y2
         + ","
         + this.z2
         + " remaining="
         + this.remaining
         + " "
         + this.reason;
   }
}
