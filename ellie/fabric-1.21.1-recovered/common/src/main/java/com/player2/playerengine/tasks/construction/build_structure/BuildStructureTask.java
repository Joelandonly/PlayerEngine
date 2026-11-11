package com.player2.playerengine.tasks.construction.build_structure;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.automaton.api.process.IBuilderProcess;
import com.player2.playerengine.automaton.api.schematic.ISchematic;
import com.player2.playerengine.nativecommands.NativeBuildSupport;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.LLMCompleter;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.Prompts;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.time.TimerGame;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.sandrohc.schematic4j.SchematicLoader;
import net.sandrohc.schematic4j.exception.ParsingException;
import net.sandrohc.schematic4j.schematic.Schematic;
import net.sandrohc.schematic4j.schematic.types.SchematicBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BuildStructureTask extends Task implements TransientlyResumableTask, TrackedTaskOutcomeProvider {
   private static final int maxNumErrors = 2;
   private static Logger LOGGER = LogManager.getLogger();
   private boolean isDone;
   private BlockPos buildPosition;
   private String schematicQuery;
   private String description;
   private PlayerEngineController mod;
   private Player2APIService service;
   private int numErrors;
   private Task actuallyRunningTask;
   private ConversationHistory history;
   private LLMCompleter completer;
   private boolean nativeResumePending;
   private String nativeFailure;

   public BuildStructureTask(BlockPos position, String schematicQuery, String description, PlayerEngineController mod) {
      this.buildPosition = position;
      this.schematicQuery = schematicQuery;
      this.description = description;
      this.mod = mod;
   }

   @Override
   protected void onStart() {
      NativeBuildSupport.start(this);
   }

   @Override
   protected Task onTick() {
      return NativeBuildSupport.tick(this);
   }

   @Override
   public boolean isFinished() {
      return this.isDone;
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof BuildStructureTask o) ? false : o.description == this.description;
   }

   @Override
   protected void onStop(Task next) {
      NativeBuildSupport.stop(this);
   }

   @Override
   protected String toDebugString() {
      return "BuildingStructure(pos=" + this.buildPosition.toShortString() + ", description=" + this.description + ")";
   }

   @Override
   public boolean prepareForTransientResume(TaskSuspensionCause var1) {
      if (var1 != TaskSuspensionCause.HIGHER_PRIORITY_CHAIN && var1 != TaskSuspensionCause.GESTURE_OVERLAY) {
         NativeBuildSupport.suspended(this, false);
         return false;
      } else {
         NativeBuildSupport.suspended(this, true);
         return true;
      }
   }

   @Override
   public void onTransientResumeAbandoned() {
      this.onStop(null);
   }

   @Override
   public boolean isTerminal() {
      return this.isDone;
   }

   @Override
   public boolean isSuccessful() {
      return NativeBuildSupport.successful(this);
   }

   @Override
   public String controlledReason() {
      return NativeBuildSupport.reason(this);
   }

   private class BuildFromCode extends Task {
      private static final int MAX_OPERATIONS = 4096;
      private static final int MAX_UNIQUE_BLOCKS = 1024;
      private static final int MAX_AXIS = 48;
      private static final int MAX_ANCHOR_DISTANCE = 48;
      private static final int MAX_IDLE_TICKS = 2000;
      private static final int MAX_RUN_TICKS = 18000;
      private static final int MAX_MATERIAL_TICKS = 36000;
      String code;
      Optional<Optional<String>> result = Optional.empty();
      final BuildStructureTask this$0;
      private transient ExecutorService parserWorker;
      private transient Future<List<StructureFromCode.SetBlockCommand>> parseFuture;
      private volatile List<StructureFromCode.SetBlockCommand> parsed;
      private BuildStructureTask.BuildFromCode.PhysicalSchematic schematic;
      private boolean startedBuilder;
      private int ticks;
      private int ticksSinceProgress;
      private int previousCompleted = -1;
      private int resourcesAttempts;
      private Task gathering;
      private Item currentResource;
      private int resourceStartCount;
      private IBuilderProcess builder;
      private boolean finished;
      private int buildRestarts;
      private int builderInactiveTicks;
      private int temporaryInterruptions;
      private int lastGatheringCount = -1;
      private int gatheringStallTicks;
      private int gatheringNoProgressRetries;
      private Item lastGatheringMaterial;
      private int scaffoldRetries;
      private boolean scaffoldReady;
      private Task scaffoldTask;
      private int scaffoldStallTicks;
      private int activeLayer;
      private int layerDamageCount;
      private int previousScopedCompleted = -1;
      private int lastLoggedLayer = -1;
      private int prepStage;
      private Task prepTask;
      private Item prepItem;
      private String prepLabel;
      private int prepTarget;
      private int prepStartCount;
      private int prepLastCount;
      private int prepStallTicks;
      private int prepRetries;
      private boolean prepAxeChecked;
      private boolean prepTableNeeded;
      private boolean prepPlanLogged;
      private final Map<Item, Integer> reservedDoorCounts = new LinkedHashMap<>();

      BuildFromCode(BuildStructureTask nullx, String nullxx) {
         this.this$0 = nullx;
         this.code = nullxx;
      }

      @Override
      protected void onStart() {
         try {
            if (this.finished) {
               return;
            }

            if (this.schematic != null) {
               this.setDebugState("Resuming saved in-memory blueprint after interruption (" + this.temporaryInterruptions + ")");
               return;
            }

            if (this.parseFuture != null && !this.parseFuture.isCancelled() && !this.parseFuture.isDone()) {
               this.setDebugState("Resuming existing blueprint parser");
               return;
            }

            if (this.parseFuture != null && this.parseFuture.isDone() && !this.parseFuture.isCancelled()) {
               return;
            }

            if (this.parserWorker != null) {
               this.parserWorker.shutdownNow();
            }

            this.parseFuture = null;
            if (this.code == null || this.code.length() > 240000) {
               throw new IllegalArgumentException("Blueprint program is empty or too large");
            }

            this.parserWorker = Executors.newSingleThreadExecutor(var0 -> {
               Thread var1 = new Thread(var0, "PlayerEngine-physical-blueprint");
               var1.setDaemon(true);
               return var1;
            });
            this.parseFuture = this.parserWorker.submit(() -> {
               StructureFromCode.Program var1 = StructureFromCode.compile(this.code);
               StructureFromCode.Runner var2x = new StructureFromCode.Runner(var1);
               ArrayList var3 = new ArrayList();

               Optional var4;
               while ((var4 = var2x.next()).isPresent()) {
                  if (var3.size() >= 6144) {
                     throw new IllegalArgumentException("Blueprint exceeds 4096 operations");
                  }

                  var3.add((StructureFromCode.SetBlockCommand)var4.get());
               }

               if (var3.isEmpty()) {
                  throw new IllegalArgumentException("Generated blueprint contained zero block operations");
               } else {
                  return var3;
               }
            });
            this.setDebugState("Parsing physical construction blueprint");
         } catch (Exception var2) {
            this.failed("Blueprint initialization: " + var2.getMessage());
         }
      }

      private PlayerEngineController controller() {
         PlayerEngineController var1 = this.this$0.controller;
         if (var1 == null) {
            var1 = this.controller;
         }

         if (var1 == null) {
            throw new IllegalStateException("No NPC controller for construction");
         } else {
            return var1;
         }
      }

      @Override
      protected Task onTick() {
         ObservatoryTelemetry.builderTick(this);
         if (this.finished) {
            return null;
         }

         this.ticks++;

         PlayerEngineController var1;
         Task var10000;
         try {
            if (this.ticks > 54000) {
               this.failed("Construction timed out without verified completion");
               return null;
            }

            var1 = this.controller();
            VillageSiteGuard.update(this, var1);
            if (this.schematic == null) {
               if (this.parseFuture == null) {
                  this.failed("Blueprint parser never started");
                  return null;
               }

               if (!this.parseFuture.isDone()) {
                  if (this.ticks > 1600) {
                     this.failed("Blueprint parsing timed out");
                  }

                  return null;
               }

               try {
                  this.parsed = this.parseFuture.get();
               } catch (Exception var13) {
                  Throwable var3 = var13.getCause() == null ? var13 : var13.getCause();
                  this.failed("Invalid structure blueprint: " + var3.getMessage());
                  return null;
               } finally {
                  this.parserWorker.shutdownNow();
                  this.parserWorker = null;
               }

               BlockPos var2 = this.getBuildAnchor();
               this.schematic = BuildStructureTask.BuildFromCode.PhysicalSchematic.fromCommands(
                  GenericWoodResolver.resolve(this.optimizeAffordableWoodenHouse(this.parsed), this.this$0), var2
               );
               VillageSiteGuard.update(this, var1);
               this.schematic.checkProtectedSite(var1);
               this.activeLayer = 0;
               this.layerDamageCount = 0;
               this.previousScopedCompleted = -1;
               this.lastLoggedLayer = -1;
               this.parsed = null;
               this.prepStage = 0;
               this.prepTask = null;
               this.prepAxeChecked = false;
               this.prepPlanLogged = false;
               this.reservedDoorCounts.clear();
               this.builder = var1.getBaritone().getBuilderProcess();
               this.setDebugState("Blueprint ready, checking " + this.schematic.blocks.size() + " positions");
            }

            int var16 = this.schematic.countCompleted(var1);
            if (var16 == this.schematic.blocks.size()) {
               if (this.startedBuilder && this.builder.isActive()) {
                  this.builder.onLostControl();
               }

               this.success();
               return null;
            }

            if (this.prepStage < 3) {
               return this.materialPreflight(var1);
            }

            int var17 = this.schematic.countScopedPlanned(this.activeLayer);

            int var4;
            for (var4 = this.schematic.countScopedCompleted(var1, this.activeLayer);
               var4 == var17 && this.activeLayer <= this.schematic.sy;
               var4 = this.schematic.countScopedCompleted(var1, this.activeLayer)
            ) {
               if (this.startedBuilder && this.builder != null && this.builder.isActive()) {
                  this.builder.onLostControl();
               }

               this.startedBuilder = false;
               this.builderInactiveTicks = 0;
               this.ticksSinceProgress = 0;
               if (this.activeLayer == this.schematic.sy) {
                  if (this.schematic.countCompleted(var1) == this.schematic.blocks.size()) {
                     this.success();
                     return null;
                  }

                  this.failed("All construction stages were complete but final block verification failed");
                  return null;
               }

               ObservatoryTelemetry.event(
                  "LAYER",
                  "Completed structural layer Y="
                     + (this.schematic.origin.getY() + this.activeLayer)
                     + " (verified "
                     + var16
                     + "/"
                     + this.schematic.blocks.size()
                     + ")"
               );
               this.activeLayer++;
               this.layerDamageCount = 0;
               this.previousScopedCompleted = -1;
               var17 = this.schematic.countScopedPlanned(this.activeLayer);
            }

            if (this.lastLoggedLayer != this.activeLayer) {
               this.lastLoggedLayer = this.activeLayer;
               ObservatoryTelemetry.event(
                  "LAYER",
                  (
                        this.activeLayer == this.schematic.sy
                           ? "Final stage: doors / retained-layer repairs"
                           : "Starting Y=" + (this.schematic.origin.getY() + this.activeLayer)
                     )
                     + ", verified "
                     + var4
                     + "/"
                     + var17
               );
            }

            ObservatoryTelemetry.set(
               "buildLayer",
               this.activeLayer == this.schematic.sy
                  ? "Final doors"
                  : "Y=" + (this.schematic.origin.getY() + this.activeLayer) + " / " + (this.schematic.origin.getY() + this.schematic.sy - 1)
            );
            ObservatoryTelemetry.set("layerProgress", var4 + "/" + var17);
            if (this.startedBuilder && this.previousScopedCompleted >= 0 && var4 < this.previousScopedCompleted) {
               this.layerDamageCount++;
               ObservatoryTelemetry.event(
                  "LAYER_CHURN",
                  "Scoped verification fell "
                     + this.previousScopedCompleted
                     + "->"
                     + var4
                     + " at stage "
                     + this.activeLayer
                     + " (incident "
                     + this.layerDamageCount
                     + ")"
               );
               if (this.layerDamageCount >= 6) {
                  if (this.builder.isActive()) {
                     this.builder.onLostControl();
                  }

                  this.failed(
                     "Repeated break/replace churn at Y-stage "
                        + this.activeLayer
                        + " ("
                        + this.layerDamageCount
                        + " regressions); stopped to protect construction"
                  );
                  return null;
               }

               if (this.layerDamageCount % 2 == 0) {
                  if (this.builder.isActive()) {
                     this.builder.onLostControl();
                  }

                  this.startedBuilder = false;
                  this.builderInactiveTicks = 0;
                  this.ticksSinceProgress = 0;
                  this.buildRestarts++;
                  ObservatoryTelemetry.event("LAYER_REPLAN", "Rebuilding stage with earlier layers in scope");
               }
            }

            this.previousScopedCompleted = var4;
            if (var16 > this.previousCompleted) {
               this.previousCompleted = var16;
               this.ticksSinceProgress = 0;
            } else if (this.startedBuilder) {
               this.ticksSinceProgress++;
            }

            VillageProactiveRestock.beforeBuilderTick(this, var1);
            VillageWorkRecovery.onTick(this, var1);
            if (this.startedBuilder) {
               this.setDebugState(
                  "Placing Y-layer "
                     + this.activeLayer
                     + "/"
                     + this.schematic.sy
                     + " physically: "
                     + var4
                     + "/"
                     + var17
                     + " (overall "
                     + var16
                     + "/"
                     + this.schematic.blocks.size()
                     + ")"
               );
               if (!this.builder.isActive()) {
                  if (++this.builderInactiveTicks <= 40) {
                     return null;
                  }

                  this.startedBuilder = false;
                  this.builderInactiveTicks = 0;
                  this.ticksSinceProgress = 0;
                  if (++this.buildRestarts > 12) {
                     this.failed("Builder restarted too often, " + (this.schematic.blocks.size() - var16) + " blocks still missing");
                  }

                  return null;
               }

               this.builderInactiveTicks = 0;
               if (this.ticksSinceProgress > 2000) {
                  this.builder.onLostControl();
                  this.startedBuilder = false;
                  this.ticksSinceProgress = 0;
                  if (++this.buildRestarts > 12) {
                     this.failed("Baritone placement/navigation stalled at " + var16 + "/" + this.schematic.blocks.size());
                  }
               }

               return null;
            }

            if (this.ticks > 36000) {
               this.failed("Gathering materials took too long");
               return null;
            }

            if (this.gathering != null && !this.gathering.isFinished()) {
               int var5 = var1.getItemStorage().getItemCountInventoryOnly(this.currentResource);
               if (var5 > this.lastGatheringCount) {
                  this.lastGatheringCount = var5;
                  this.gatheringStallTicks = 0;
               } else if (++this.gatheringStallTicks > 2400) {
                  String var23 = "Material subtask stalled for 120 seconds: " + this.currentResource + " (owned=" + var5 + ", task=" + this.gathering + ")";
                  ObservatoryTelemetry.event("CRAFT_BLOCKER", var23 + "; " + this.recipeDependencyStatus(var1));
                  if (++this.gatheringNoProgressRetries >= 2) {
                     this.failed(var23 + "; " + this.recipeDependencyStatus(var1));
                     return null;
                  }

                  this.gathering.stop();
                  this.gathering = null;
                  this.gatheringStallTicks = 0;
                  ObservatoryTelemetry.event("CRAFT_RETRY", "Refreshing stalled material task (retry " + this.gatheringNoProgressRetries + "/2)");
                  return null;
               }
            }

            if (this.gathering != null && this.gathering.isFinished()) {
               int var18 = var1.getItemStorage().getItemCountInventoryOnly(this.currentResource);
               if (var18 <= this.resourceStartCount) {
                  String var22 = "Finished material task without obtaining " + this.currentResource + " (" + var18 + "/" + this.resourceStartCount + ")";
                  ObservatoryTelemetry.event("CRAFT_BLOCKER", var22 + "; " + this.recipeDependencyStatus(var1));
                  this.gathering = null;
                  this.gatheringStallTicks = 0;
                  if (++this.gatheringNoProgressRetries >= 2) {
                     this.failed(var22 + "; " + this.recipeDependencyStatus(var1));
                     return null;
                  }

                  ObservatoryTelemetry.event("CRAFT_RETRY", "Re-requesting material after incomplete task (" + this.gatheringNoProgressRetries + "/2)");
                  return null;
               }

               this.gatheringNoProgressRetries = 0;
               this.gathering = null;
               this.currentResource = null;
               this.gatheringStallTicks = 0;
               this.lastGatheringCount = -1;
            }

            if (this.gathering != null) {
               this.setDebugState("Gathering construction materials (" + var16 + "/" + this.schematic.blocks.size() + " already placed)");
               return (Task)WaterBridgeCoordinator.beforeGatherReturn(this.gathering, this, var1);
            }

            Iterator var19 = this.schematic.requiredInventory(var1).entrySet().iterator();

            Entry var6;
            Item var7;
            int var8;
            do {
               if (!var19.hasNext()) {
                  if (!this.scaffoldReady) {
                     ResourceLocation var20 = ResourceLocation.fromNamespaceAndPath("minecraft", "dirt");
                     Block var21 = (Block)BuiltInRegistries.BLOCK.get(var20);
                     var7 = ItemHelper.blocksToItems(new Block[]{var21})[0];
                     var8 = var1.getItemStorage().getItemCountInventoryOnly(var7);
                     if (var8 >= 12) {
                        this.scaffoldReady = true;
                        this.scaffoldStallTicks = 0;
                        this.scaffoldTask = null;
                     } else if (this.scaffoldTask != null && !this.scaffoldTask.isFinished() && ++this.scaffoldStallTicks > 600) {
                        this.scaffoldReady = true;
                        this.scaffoldTask = null;
                        System.err.println("[PlayerEngine physical builder] Scaffold gathering timed out; continuing with available materials");
                     } else if (this.scaffoldTask != null && this.scaffoldTask.isFinished()) {
                        if (var8 > 0) {
                           this.scaffoldReady = true;
                           this.scaffoldTask = null;
                        } else if (++this.scaffoldRetries >= 2) {
                           this.scaffoldReady = true;
                           this.scaffoldTask = null;
                           System.err.println("[PlayerEngine physical builder] Scaffold gathering unavailable; continuing without reserve");
                        } else {
                           this.scaffoldTask = null;
                        }
                     } else if (this.scaffoldTask == null) {
                        if (TaskCatalogue.taskExists(var7)) {
                           this.scaffoldTask = TaskCatalogue.getItemTask(var7, 12);
                           this.scaffoldStallTicks = 0;
                        } else {
                           this.scaffoldReady = true;
                        }
                     }

                     if (!this.scaffoldReady && this.scaffoldTask != null) {
                        this.setDebugState("Collecting temporary navigation scaffold blocks (" + var8 + "/12)");
                        return this.scaffoldTask;
                     }
                  }

                  this.schematic.checkProtectedSite(var1);
                  this.builder.build("PlayerEngine structure layer " + this.activeLayer, this.schematic.layerView(this.activeLayer), this.schematic.origin);
                  this.startedBuilder = true;
                  this.builderInactiveTicks = 0;
                  this.ticksSinceProgress = 0;
                  this.setDebugState(
                     "Started physical Y-layer "
                        + this.activeLayer
                        + "/"
                        + this.schematic.sy
                        + ": "
                        + var4
                        + "/"
                        + var17
                        + " (overall "
                        + var16
                        + "/"
                        + this.schematic.blocks.size()
                        + ")"
                  );
                  return null;
               }

               var6 = (Entry)var19.next();
               var7 = (Item)var6.getKey();
               var8 = Math.min(256, (Integer)var6.getValue());
            } while (
               var1.getItemStorage().getItemCountInventoryOnly(var7)
                  >= VillageMaterialBudget.refillThreshold(var8, (Integer)var6.getValue(), this.schematic.blocks.size())
            );

            if (!TaskCatalogue.taskExists(var7)) {
               this.failed("Cannot gather required structure material " + var7 + " (need " + var8 + ")");
               return null;
            }

            if (++this.resourcesAttempts > 128) {
               this.failed("Too many material gathering operations");
               return null;
            }

            if (this.lastGatheringMaterial != var7) {
               this.gatheringNoProgressRetries = 0;
               this.lastGatheringMaterial = var7;
            }

            this.currentResource = var7;
            this.resourceStartCount = var1.getItemStorage().getItemCountInventoryOnly(var7);
            this.lastGatheringCount = this.resourceStartCount;
            this.gatheringStallTicks = 0;
            int var9 = Math.min(var8, this.resourceStartCount + 64);
            this.gathering = TaskCatalogue.getItemTask(var7, var9);
            if (this.gathering == null) {
               this.failed("Missing gathering task for material " + var7);
               return null;
            }

            this.setDebugState("Gathering " + var7 + " (" + this.resourceStartCount + " / " + var8 + ")");
            var10000 = this.gathering;
         } catch (Throwable var15) {
            this.failed("Physical construction error: " + var15.getClass().getSimpleName() + ": " + var15.getMessage());
            return null;
         }

         return (Task)WaterBridgeCoordinator.beforeGatherReturn(var10000, this, var1);
      }

      private Task materialPreflight(PlayerEngineController var1) {
         if (!this.prepPlanLogged) {
            this.prepPlanLogged = true;
            Map var2 = this.schematic.requiredDoorInventory(var1);
            this.prepTableNeeded = !var2.isEmpty() || this.shouldUpgradeAxe();
            ObservatoryTelemetry.event(
               "CRAFT_PLAN", "Construction: workbench=" + this.prepTableNeeded + ", doors=" + var2 + ", optionalAxe=" + this.shouldUpgradeAxe()
            );
         }

         if (this.prepTask != null) {
            int var6 = var1.getItemStorage().getItemCountInventoryOnly(this.prepItem);
            if (var6 > this.prepLastCount) {
               this.prepLastCount = var6;
               this.prepStallTicks = 0;
               ObservatoryTelemetry.event("CRAFT_PROGRESS", this.prepLabel + " " + var6 + "/" + this.prepTarget);
            } else {
               this.prepStallTicks++;
            }

            int var3 = "axe".equals(this.prepLabel) ? 1600 : ("workbench".equals(this.prepLabel) ? 2400 : 4800);
            if (var6 >= this.prepTarget) {
               ObservatoryTelemetry.event("CRAFT_READY", this.prepLabel + " ready: " + var6 + "/" + this.prepTarget);
               if ("door".equals(this.prepLabel)) {
                  this.reservedDoorCounts.put(this.prepItem, this.prepTarget);
               }

               this.prepTask = null;
               this.prepRetries = 0;
               this.prepStallTicks = 0;
            } else {
               if (!this.prepTask.isFinished() && this.prepStallTicks <= var3) {
                  this.setDebugState("Preparing " + this.prepLabel + " early: " + var6 + "/" + this.prepTarget);
                  ObservatoryTelemetry.set("craftDependencies", this.recipeDependencyStatus(var1));
                  return (Task)WaterBridgeCoordinator.beforeGatherReturn(this.prepTask, this, var1);
               }

               String var4 = "Crafting "
                  + this.prepLabel
                  + " cannot progress ("
                  + var6
                  + "/"
                  + this.prepTarget
                  + ", elapsed "
                  + this.prepStallTicks / 20
                  + "s, task="
                  + this.prepTask
                  + ")";
               this.prepTask = null;
               this.prepStallTicks = 0;
               ObservatoryTelemetry.event("CRAFT_BLOCKER", var4);
               if ("axe".equals(this.prepLabel)) {
                  ObservatoryTelemetry.event("AXE_UPGRADE", "Skipped optional axe; using bare hands or existing tools");
                  this.prepStage = 3;
                  return null;
               }

               if (++this.prepRetries >= 2) {
                  this.failed(var4 + "; " + this.recipeDependencyStatus(var1));
                  return null;
               }

               ObservatoryTelemetry.event("CRAFT_RETRY", "Retry " + this.prepRetries + "/2: " + this.prepLabel);
            }
         }

         if (this.prepStage == 0) {
            if (!this.prepTableNeeded) {
               this.prepStage = 1;
               return null;
            } else {
               Item var9 = this.itemFromBlock("crafting_table");
               int var12 = var1.getItemStorage().getItemCountInventoryOnly(var9);
               if (var12 >= 1) {
                  ObservatoryTelemetry.event("CRAFT_READY", "Existing crafting table reserved for 3x3 recipes");
                  this.prepStage = 1;
                  return null;
               } else {
                  return this.startPreparation(var1, var9, 1, "workbench") ? (Task)WaterBridgeCoordinator.beforeGatherReturn(this.prepTask, this, var1) : null;
               }
            }
         } else if (this.prepStage == 1) {
            for (Entry var11 : this.schematic.requiredDoorInventory(var1).entrySet()) {
               Item var13 = (Item)var11.getKey();
               int var5 = Math.min(64, (Integer)var11.getValue());
               if (var1.getItemStorage().getItemCountInventoryOnly(var13) < var5) {
                  if (this.startPreparation(var1, var13, var5, "door")) {
                     return (Task)WaterBridgeCoordinator.beforeGatherReturn(this.prepTask, this, var1);
                  }

                  return null;
               }

               this.reservedDoorCounts.put(var13, var5);
            }

            this.prepStage = 2;
            this.prepRetries = 0;
            ObservatoryTelemetry.event("CRAFT_RESERVE", "Door item(s) prepared before physical construction: " + this.reservedDoorCounts);
            return null;
         } else {
            if (this.prepStage == 2) {
               if (!this.shouldUpgradeAxe()) {
                  this.prepStage = 3;
                  ObservatoryTelemetry.event("AXE_UPGRADE", "Not needed for this structure size");
                  return null;
               }

               if (!this.prepAxeChecked) {
                  this.prepAxeChecked = true;
                  Item[] var7 = TaskCatalogue.getItemMatches("wooden_axe");
                  if (var7 != null && var7.length > 0 && var7[0] != null) {
                     Item var10 = var7[0];
                     if (var1.getItemStorage().getItemCountInventoryOnly(var10) > 0) {
                        ObservatoryTelemetry.event("AXE_UPGRADE", "Already has a wooden axe");
                     } else if (TaskCatalogue.taskExists(var10) && this.startPreparation(var1, var10, 1, "axe")) {
                        return (Task)WaterBridgeCoordinator.beforeGatherReturn(this.prepTask, this, var1);
                     }
                  } else {
                     ObservatoryTelemetry.event("AXE_UPGRADE", "No axe recipe registered; continuing without optional tool");
                  }
               }

               this.prepStage = 3;
            }

            ObservatoryTelemetry.set("craftDependencies", "Ready: preflight finished; doors reserved for final layer");
            return null;
         }
      }

      private boolean startPreparation(PlayerEngineController var1, Item var2, int var3, String var4) {
         if (!TaskCatalogue.taskExists(var2)) {
            String var7 = "No registered resource task for pre-crafting " + var4 + ": " + var2;
            ObservatoryTelemetry.event("CRAFT_BLOCKER", var7);
            if ("axe".equals(var4)) {
               this.prepStage = 3;
               return false;
            } else {
               this.failed(var7);
               return false;
            }
         } else {
            this.prepItem = var2;
            this.prepLabel = var4;
            this.prepTarget = var3;
            this.prepStartCount = var1.getItemStorage().getItemCountInventoryOnly(var2);
            this.prepLastCount = this.prepStartCount;
            this.prepStallTicks = 0;
            this.prepTask = (ResourceTask)DoorAcquisitionPolicy.pickTask(var2, var3, var4, var1);
            if (this.prepTask == null) {
               String var6 = "Cannot create registered resource task for " + var4;
               ObservatoryTelemetry.event("CRAFT_BLOCKER", var6);
               if ("axe".equals(var4)) {
                  this.prepStage = 3;
                  return false;
               } else {
                  this.failed(var6);
                  return false;
               }
            } else {
               String var5 = "Pre-crafting " + var4 + " (" + this.prepStartCount + "/" + var3 + ")";
               ObservatoryTelemetry.event("CRAFT_REQUEST", var5 + "; " + this.recipeDependencyStatus(var1));
               ObservatoryTelemetry.set("craftDependencies", this.recipeDependencyStatus(var1));
               this.setDebugState(var5);
               return true;
            }
         }
      }

      private Item itemFromBlock(String var1) {
         Block var2 = (Block)BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", var1));
         Item[] var3 = ItemHelper.blocksToItems(new Block[]{var2});
         if (var3.length != 0 && var3[0] != null) {
            return var3[0];
         } else {
            throw new IllegalArgumentException("No item for minecraft:" + var1);
         }
      }

      private boolean shouldUpgradeAxe() {
         if (this.schematic != null && this.schematic.sx * this.schematic.sz >= 81) {
            int var1 = 0;

            for (Item var3 : this.schematic.materialFor.values()) {
               if (var3 != null && String.valueOf(var3).contains("planks")) {
                  var1++;
               }
            }

            return var1 >= 160;
         } else {
            return false;
         }
      }

      private List<StructureFromCode.SetBlockCommand> optimizeAffordableWoodenHouse(List<StructureFromCode.SetBlockCommand> var1) {
         try {
            Field var2 = BuildStructureTask.class.getDeclaredField("description");
            var2.setAccessible(true);
            Object var3 = var2.get(this.this$0);
            String var4 = var3 == null ? "" : var3.toString().toLowerCase(Locale.ROOT);
            if (var4.contains("wood") && (var4.contains("small") || var4.contains("compact")) && !var4.contains("stone") && !var4.contains("cobblestone")) {
               int var5 = 0;

               for (StructureFromCode.SetBlockCommand var7 : var1) {
                  if ("stone".equals(var7.blockName) || "minecraft:stone".equals(var7.blockName)) {
                     var5++;
                  }
               }

               if (var5 < 16) {
                  return var1;
               }

               ArrayList var11 = new ArrayList(var1.size());

               for (StructureFromCode.SetBlockCommand var8 : var1) {
                  String var9 = !"stone".equals(var8.blockName) && !"minecraft:stone".equals(var8.blockName) ? var8.blockName : "cobblestone";
                  var11.add(new StructureFromCode.SetBlockCommand(var8.x, var8.y, var8.z, var9));
               }

               System.out
                  .println(
                     "[PlayerEngine physical builder] Starter wooden house: substituted " + var5 + " smelted stone foundation blocks with survival cobblestone"
                  );
               return var11;
            } else {
               return var1;
            }
         } catch (ReflectiveOperationException var10) {
            return var1;
         }
      }

      private BlockPos getBuildAnchor() throws Exception {
         Field var1 = BuildStructureTask.class.getDeclaredField("buildPosition");
         var1.setAccessible(true);
         return (BlockPos)var1.get(this.this$0);
      }

      private void success() {
         this.finished = true;
         this.result = Optional.of(Optional.empty());
         this.setDebugState("Verified all structure blocks placed");
      }

      private void failed(String var1) {
         this.finished = true;
         this.result = Optional.of(Optional.of(var1));
         this.setDebugState("Construction FAILED: " + var1);

         try {
            Field var2 = BuildStructureTask.class.getDeclaredField("numErrors");
            var2.setAccessible(true);
            var2.setInt(this.this$0, 3);
         } catch (ReflectiveOperationException var3) {
            System.err.println("[PlayerEngine physical builder] Cannot mark construction failed: " + var3);
         }

         System.err.println("[PlayerEngine physical builder] " + var1);
      }

      @Override
      protected void onStop(Task var1) {
         if (this.startedBuilder && this.builder != null && this.builder.isActive()) {
            this.builder.onLostControl();
         }

         this.startedBuilder = false;
         this.builderInactiveTicks = 0;
         this.ticksSinceProgress = 0;
         if (!this.finished) {
            this.temporaryInterruptions++;
            this.setDebugState("Construction suspended, blueprint retained (" + this.temporaryInterruptions + ")");
         }

         VillageSiteGuard.clearForJob(this);
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      protected boolean isEqual(Task var1) {
         return this == var1;
      }

      @Override
      protected String toDebugString() {
         return "Physical structure: " + (this.schematic == null ? "planning" : this.previousCompleted + "/" + this.schematic.blocks.size());
      }

      private String recipeDependencyStatus(PlayerEngineController var1) {
         return GenericWoodResolver.dependencyStatus(this, var1);
      }

      static final class PhysicalSchematic implements ISchematic {
         final BlockPos origin;
         final int sx;
         final int sy;
         final int sz;
         final Map<Long, BlockState> blocks;
         final Map<Long, Item> materialFor;
         final Set<Long> doorKeys = new HashSet<>();

         private boolean selectedAt(long var1, int var3) {
            int var4 = (int)(var1 >>> 16 & 65535L);
            return this.doorKeys.contains(var1) ? var3 >= this.sy : var4 <= var3;
         }

         ISchematic layerView(int var1) {
            return new BuildStructureTask.BuildFromCode.PhysicalSchematic.LayerView(this, var1);
         }

         int countScopedPlanned(int var1) {
            int var2 = 0;

            for (long var4 : this.blocks.keySet()) {
               if (this.selectedAt(var4, var1)) {
                  var2++;
               }
            }

            return var2;
         }

         int countScopedCompleted(PlayerEngineController var1, int var2) {
            int var3 = 0;

            for (Entry var5 : this.blocks.entrySet()) {
               long var6 = (Long)var5.getKey();
               if (this.selectedAt(var6, var2)) {
                  int var8 = (int)(var6 >> 32);
                  int var9 = (int)(var6 >> 16 & 65535L);
                  int var10 = (int)(var6 & 65535L);
                  BlockPos var11 = new BlockPos(this.origin.getX() + var8, this.origin.getY() + var9, this.origin.getZ() + var10);
                  if (this.isCompleteAt(var1, var6, var11, var1.getWorld().getBlockState(var11))) {
                     var3++;
                  }
               }
            }

            return var3;
         }

         Map<Item, Integer> requiredDoorInventory(PlayerEngineController var1) {
            LinkedHashMap<Item, Integer> var2 = new LinkedHashMap<>();

            for (long var4 : this.doorKeys) {
               Item var6 = this.materialFor.get(var4);
               if (var6 != null) {
                  int var7 = (int)(var4 >> 32);
                  int var8 = (int)(var4 >> 16 & 65535L);
                  int var9 = (int)(var4 & 65535L);
                  BlockPos var10 = new BlockPos(this.origin.getX() + var7, this.origin.getY() + var8, this.origin.getZ() + var9);
                  if (!this.isCompleteAt(var1, var4, var10, var1.getWorld().getBlockState(var10))) {
                     var2.merge(var6, 1, Integer::sum);
                  }
               }
            }

            return var2;
         }

         Map<Item, Integer> requiredInventoryScoped(PlayerEngineController var1, int var2) {
            LinkedHashMap<Item, Integer> var3 = new LinkedHashMap<>();

            for (Entry var5 : this.materialFor.entrySet()) {
               long var6 = (Long)var5.getKey();
               Item var8 = (Item)var5.getValue();
               if (var8 != null && this.selectedAt(var6, var2)) {
                  int var9 = (int)(var6 >> 32);
                  int var10 = (int)(var6 >> 16 & 65535L);
                  int var11 = (int)(var6 & 65535L);
                  BlockPos var12 = new BlockPos(this.origin.getX() + var9, this.origin.getY() + var10, this.origin.getZ() + var11);
                  if (!this.isCompleteAt(var1, var6, var12, var1.getWorld().getBlockState(var12))) {
                     var3.merge(var8, 1, Integer::sum);
                  }
               }
            }

            return var3;
         }

         PhysicalSchematic(BlockPos var1, int var2, int var3, int var4, Map<Long, BlockState> var5, Map<Long, Item> var6) {
            this.origin = var1;
            this.sx = var2;
            this.sy = var3;
            this.sz = var4;
            this.blocks = var5;
            this.materialFor = var6;

            for (Entry var8 : var5.entrySet()) {
               String var9 = String.valueOf(BuiltInRegistries.BLOCK.getKey(((BlockState)var8.getValue()).getBlock()));
               if (isDoorName(var9)) {
                  this.doorKeys.add((Long)var8.getKey());
               }
            }
         }

         private static boolean isDoorName(String var0) {
            return var0 != null && var0.endsWith("_door") && !var0.endsWith("_trapdoor");
         }

         static long key(int var0, int var1, int var2) {
            return (long)var0 << 32 | (long)var1 << 16 | var2;
         }

         static BuildStructureTask.BuildFromCode.PhysicalSchematic fromCommands(List<StructureFromCode.SetBlockCommand> var0, BlockPos var1) {
            LinkedHashMap var2 = new LinkedHashMap();

            for (StructureFromCode.SetBlockCommand var4 : var0) {
               var2.put(var4.x + ":" + var4.y + ":" + var4.z, var4.blockName);
            }

            ArrayList var27 = new ArrayList();

            for (StructureFromCode.SetBlockCommand var5 : var0) {
               String var6 = var5.blockName;
               if (var6 != null && (var6.endsWith("_bed") || var6.endsWith("_hanging_sign") || var6.endsWith("_tall_grass"))) {
                  throw new IllegalArgumentException("Unsupported multi-block or orientation-dependent material: " + var6);
               }

               if (isDoorName(var6)) {
                  String var7 = (String)var2.get(var5.x + ":" + (var5.y - 1) + ":" + var5.z);
                  String var8 = (String)var2.get(var5.x + ":" + (var5.y + 1) + ":" + var5.z);
                  if (var6.equals(var7)) {
                     continue;
                  }

                  if (!var6.equals(var8)) {
                     throw new IllegalArgumentException("Door must have adjacent lower and upper cells: " + var6);
                  }
               }

               var27.add(var5);
            }

            var0 = var27;
            LinkedHashMap var29 = new LinkedHashMap();
            LinkedHashMap var30 = new LinkedHashMap();
            LinkedHashMap var31 = new LinkedHashMap();
            int var32 = Integer.MAX_VALUE;
            int var33 = Integer.MAX_VALUE;
            int var9 = Integer.MAX_VALUE;
            int var10 = Integer.MIN_VALUE;
            int var11 = Integer.MIN_VALUE;
            int var12 = Integer.MIN_VALUE;
            int var13 = var1.getX();
            int var14 = var1.getY();
            int var15 = var1.getZ();

            for (StructureFromCode.SetBlockCommand var17 : var0) {
               if (Math.abs(var17.x - var13) <= 48 && Math.abs(var17.y - var14) <= 32 && Math.abs(var17.z - var15) <= 48) {
                  String var18 = var17.blockName;
                  if (var18 != null && var18.matches("(?:minecraft:)?[a-z0-9_]{1,60}")) {
                     String var19 = var18.startsWith("minecraft:") ? var18.substring(10) : var18;
                     Block var20 = (Block)var29.get(var19);
                     if (var20 == null) {
                        ResourceLocation var21 = ResourceLocation.fromNamespaceAndPath("minecraft", var19);
                        var20 = (Block)BuiltInRegistries.BLOCK.get(var21);
                        if (var20 == null || !var21.equals(BuiltInRegistries.BLOCK.getKey(var20))) {
                           throw new IllegalArgumentException("Unknown block: " + var18);
                        }

                        var29.put(var19, var20);
                     }

                     Item var39 = var19.equals("air") ? null : ItemHelper.blocksToItems(new Block[]{var20})[0];
                     if (!var19.equals("air") && var39 == null) {
                        throw new IllegalArgumentException("Block cannot be held as item: " + var18);
                     }

                     String var22 = var17.x + ":" + var17.y + ":" + var17.z;
                     var30.put(var22, var20.defaultBlockState());
                     var31.put(var22, var39);
                     var32 = Math.min(var32, var17.x);
                     var33 = Math.min(var33, var17.y);
                     var9 = Math.min(var9, var17.z);
                     var10 = Math.max(var10, var17.x);
                     var11 = Math.max(var11, var17.y);
                     var12 = Math.max(var12, var17.z);
                     if (var30.size() > 6144) {
                        throw new IllegalArgumentException("More than 1024 unique structure blocks");
                     }
                     continue;
                  }

                  throw new IllegalArgumentException("Invalid block identifier: " + var18);
               }

               throw new IllegalArgumentException("Generated block outside bounded construction region: " + var17);
            }

            int var34 = var10 - var32 + 1;
            int var35 = var11 - var33 + 1;
            int var36 = var12 - var9 + 1;
            if (var34 <= 48 && var35 <= 32 && var36 <= 48 && (long)var34 * var35 * var36 <= 200000L) {
               LinkedHashMap var37 = new LinkedHashMap();
               LinkedHashMap var38 = new LinkedHashMap();

               for (StructureFromCode.SetBlockCommand var41 : var0) {
                  String var23 = var41.x + ":" + var41.y + ":" + var41.z;
                  long var24 = key(var41.x - var32, var41.y - var33, var41.z - var9);
                  var37.put(var24, (BlockState)var30.get(var23));
                  var38.put(var24, (Item)var31.get(var23));
               }

               return new BuildStructureTask.BuildFromCode.PhysicalSchematic(new BlockPos(var32, var33, var9), var34, var35, var36, var37, var38);
            } else {
               throw new IllegalArgumentException("Structure bounds exceed safety limit");
            }
         }

         @Override
         public int widthX() {
            return this.sx;
         }

         @Override
         public int heightY() {
            return this.sy;
         }

         @Override
         public int lengthZ() {
            return this.sz;
         }

         @Override
         public boolean inSchematic(int var1, int var2, int var3, BlockState var4) {
            return this.blocks.containsKey(key(var1, var2, var3));
         }

         @Override
         public BlockState desiredState(int var1, int var2, int var3, BlockState var4, List<BlockState> var5) {
            BlockState var6 = this.blocks.get(key(var1, var2, var3));
            if (var6 == null) {
               return var4;
            } else {
               return var6.getBlock().equals(var4.getBlock()) ? var4 : var6;
            }
         }

         int countCompleted(PlayerEngineController var1) {
            int var2 = 0;

            for (Entry var4 : this.blocks.entrySet()) {
               long var5 = (Long)var4.getKey();
               int var7 = (int)(var5 >> 32);
               int var8 = (int)(var5 >> 16 & 65535L);
               int var9 = (int)(var5 & 65535L);
               BlockPos var10 = new BlockPos(this.origin.getX() + var7, this.origin.getY() + var8, this.origin.getZ() + var9);
               BlockState var11 = var1.getWorld().getBlockState(var10);
               if (this.isCompleteAt(var1, var5, var10, var11)) {
                  var2++;
               }
            }

            return var2;
         }

         private boolean isCompleteAt(PlayerEngineController var1, long var2, BlockPos var4, BlockState var5) {
            BlockState var6 = this.blocks.get(var2);
            if (!var5.getBlock().equals(var6.getBlock())) {
               return VillageProtectedUtility.accept(
                  this.blocks.size(),
                  String.valueOf(BuiltInRegistries.BLOCK.getKey(var5.getBlock())),
                  this.isDoorAt(var2),
                  var4.getX(),
                  var4.getY(),
                  var4.getZ()
               );
            } else if (this.isDoorAt(var2)) {
               BlockPos var7 = new BlockPos(var4.getX(), var4.getY() + 1, var4.getZ());
               return var1.getWorld().getBlockState(var7).getBlock().equals(var6.getBlock());
            } else {
               return true;
            }
         }

         private boolean isDoorAt(long var1) {
            return this.doorKeys.contains(var1);
         }

         void checkProtectedSite(PlayerEngineController var1) {
            for (Entry var3 : this.blocks.entrySet()) {
               long var4 = (Long)var3.getKey();
               int var6 = (int)(var4 >> 32);
               int var7 = (int)(var4 >> 16 & 65535L);
               int var8 = (int)(var4 & 65535L);
               BlockPos var9 = new BlockPos(this.origin.getX() + var6, this.origin.getY() + var7, this.origin.getZ() + var8);
               BlockState var10 = var1.getWorld().getBlockState(var9);
               if (!this.isCompleteAt(var1, var4, var9, var10)) {
                  String var11 = String.valueOf(BuiltInRegistries.BLOCK.getKey(var10.getBlock()));
                  if (isProtectedBlockName(var11)) {
                     throw new IllegalArgumentException(
                        "Refusing to overwrite protected block " + var11 + " at " + var9.getX() + "," + var9.getY() + "," + var9.getZ()
                     );
                  }

                  if (this.isDoorAt(var4)) {
                     BlockPos var12 = new BlockPos(var9.getX(), var9.getY() + 1, var9.getZ());
                     BlockState var13 = var1.getWorld().getBlockState(var12);
                     String var14 = String.valueOf(BuiltInRegistries.BLOCK.getKey(var13.getBlock()));
                     if (isProtectedBlockName(var14)) {
                        throw new IllegalArgumentException(
                           "Refusing to overwrite protected upper door cell "
                              + var14
                              + " at "
                              + var12.getX()
                              + ","
                              + var12.getY()
                              + ","
                              + var12.getZ()
                        );
                     }
                  }
               }
            }
         }

         private static boolean isProtectedBlockName(String var0) {
            return var0.equals("minecraft:bedrock")
               || var0.equals("minecraft:chest")
               || var0.equals("minecraft:trapped_chest")
               || var0.equals("minecraft:barrel")
               || var0.equals("minecraft:furnace")
               || var0.equals("minecraft:blast_furnace")
               || var0.equals("minecraft:smoker")
               || var0.equals("minecraft:spawner")
               || var0.equals("minecraft:end_portal_frame")
               || var0.endsWith("_shulker_box")
               || var0.endsWith("_bed")
               || var0.endsWith("_banner")
               || var0.endsWith("_sign")
               || var0.endsWith("_hanging_sign")
               || var0.equals("minecraft:enchanting_table")
               || var0.equals("minecraft:beacon")
               || var0.equals("minecraft:brewing_stand")
               || var0.equals("minecraft:anvil")
               || var0.equals("minecraft:chipped_anvil")
               || var0.equals("minecraft:damaged_anvil")
               || var0.equals("minecraft:hopper")
               || var0.equals("minecraft:dispenser")
               || var0.equals("minecraft:dropper")
               || var0.equals("minecraft:crafting_table")
               || var0.equals("minecraft:lectern")
               || var0.equals("minecraft:jukebox")
               || var0.equals("minecraft:respawn_anchor")
               || var0.equals("minecraft:ender_chest");
         }

         Map<Item, Integer> requiredInventory(PlayerEngineController var1) {
            LinkedHashMap<Item, Integer> var2 = new LinkedHashMap<>();

            for (Entry var4 : this.materialFor.entrySet()) {
               Item var5 = (Item)var4.getValue();
               if (var5 != null) {
                  long var6 = (Long)var4.getKey();
                  int var8 = (int)(var6 >> 32);
                  int var9 = (int)(var6 >> 16 & 65535L);
                  int var10 = (int)(var6 & 65535L);
                  BlockPos var11 = new BlockPos(this.origin.getX() + var8, this.origin.getY() + var9, this.origin.getZ() + var10);
                  BlockState var12 = var1.getWorld().getBlockState(var11);
                  boolean var13 = this.isCompleteAt(var1, var6, var11, var12);
                  if (!var13) {
                     var2.merge(var5, 1, Integer::sum);
                  }
               }
            }

            return var2;
         }

         static final class LayerView implements ISchematic {
            final BuildStructureTask.BuildFromCode.PhysicalSchematic base;
            final int stage;

            LayerView(BuildStructureTask.BuildFromCode.PhysicalSchematic var1, int var2) {
               this.base = var1;
               this.stage = var2;
            }

            @Override
            public int widthX() {
               return this.base.sx;
            }

            @Override
            public int heightY() {
               return this.base.sy;
            }

            @Override
            public int lengthZ() {
               return this.base.sz;
            }

            @Override
            public boolean inSchematic(int var1, int var2, int var3, BlockState var4) {
               long var5 = BuildStructureTask.BuildFromCode.PhysicalSchematic.key(var1, var2, var3);
               return this.base.blocks.containsKey(var5) && this.base.selectedAt(var5, this.stage);
            }

            @Override
            public BlockState desiredState(int var1, int var2, int var3, BlockState var4, List<BlockState> var5) {
               long var6 = BuildStructureTask.BuildFromCode.PhysicalSchematic.key(var1, var2, var3);
               return !this.base.selectedAt(var6, this.stage) ? var4 : this.base.desiredState(var1, var2, var3, var4, var5);
            }
         }
      }
   }

   private class BuildSchematicFromDescriptionTask extends Task {
      private Schematic schematic = null;
      private boolean finished = false;
      private BlockPos origin;
      private TimerGame blockPlaceTimer = new TimerGame(0.1F);
      private int blockPlaceProgress;

      public boolean hasSchematic() {
         return this.schematic != null;
      }

      @Override
      protected void onStart() {
         this.finished = false;
         this.schematic = null;
         this.blockPlaceProgress = 0;
         this.origin = BuildStructureTask.this.buildPosition;
         String query = BuildStructureTask.this.schematicQuery;
         BuildStructureTask.LOGGER.info("Searching Schematic: {}", query);
         List<JsonObject> schematics = BuildStructureTask.this.service.searchSchematics(query);
         BuildStructureTask.LOGGER.info("Got {} results", schematics.size());
         if (schematics.size() == 0) {
            this.finished = true;
         } else {
            ConversationHistory selectPromptHistory = new ConversationHistory(Prompts.getSelectSchematicPrompt());
            StringBuilder result = new StringBuilder("{");
            result.append(String.join(",\n", schematics.stream().map(s -> s.toString()).toList()));
            result.append("\n}");
            selectPromptHistory.addUserMessage(result.toString(), BuildStructureTask.this.service);
            BuildStructureTask.LOGGER.info("Querying LLM to pick best schematic...");
            BuildStructureTask.this.completer
               .processToString(
                  BuildStructureTask.this.service,
                  selectPromptHistory,
                  schematicID -> {
                     BuildStructureTask.LOGGER.info("LLM Picked best schematic id: {}", schematicID);
                     String b64String = BuildStructureTask.this.service.getSchematicBinary(schematicID);
                     BuildStructureTask.LOGGER.debug("Got schematic b64: {}", b64String);
                     if (b64String != null && b64String.length() != 0) {
                        ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(b64String));

                        try {
                           this.schematic = SchematicLoader.load(input);
                           BuildStructureTask.LOGGER
                              .info("Loaded schematic successfully! {}x{}x{}", this.schematic.width(), this.schematic.height(), this.schematic.length());
                        } catch (ParsingException | IOException e) {
                           e.printStackTrace();
                           this.finished = true;
                        }
                     } else {
                        this.finished = true;
                     }
                  },
                  errStr -> {
                     BuildStructureTask.LOGGER.info("LLM Transport Error={}", errStr);
                     this.finished = true;
                  },
                  false,
                  AiTaskClass.PLANNING
               );
         }
      }

      private static <T extends Enum<T> & StringRepresentable & Comparable<T>> BlockState setEnumProp(EnumProperty<T> enumProp, BlockState blockState, String value) {
         for (T possible : enumProp.getPossibleValues()) {
            T possibleEnum = possible;
            if (possibleEnum.name().toLowerCase().equals(value)) {
               return (BlockState)blockState.setValue(enumProp, possibleEnum);
            }
         }

         BuildStructureTask.LOGGER
            .warn(
               "Unable to find enum prop that matched the value \"{}\" to any of following enums: [{}]",
               value,
               String.join(", ", enumProp.getPossibleValues().stream().map(x -> "\"" + x.toString() + "\"").toList())
            );
         return blockState;
      }

      @Override
      protected Task onTick() {
         if (this.schematic == null) {
            return null;
         }

         if (!this.blockPlaceTimer.elapsed()) {
            return null;
         }

         for (boolean foundInvalidBlock = false; !foundInvalidBlock; this.blockPlaceProgress++) {
            int xs = this.schematic.width();
            int zs = this.schematic.length();
            int ys = this.schematic.height();
            int xx = this.blockPlaceProgress % xs;
            int zz = this.blockPlaceProgress / xs % zs;
            int yy = this.blockPlaceProgress / (zs * xs);
            if (this.blockPlaceProgress > xs * zs * ys) {
               this.finished = true;
               return null;
            }

            SchematicBlock desiredSchematicState = this.schematic.block(xx, yy, zz);
            String blockName = desiredSchematicState.block();

            while (blockName.startsWith("minecraft:")) {
               blockName = blockName.substring("minecraft:".length());
            }

            ResourceLocation desiredSchematicId = ResourceLocation.fromNamespaceAndPath("minecraft", blockName);
            Block desiredSchematicBlock = (Block)BuiltInRegistries.BLOCK.get(desiredSchematicId);
            BlockPos worldPos = this.origin.offset(xx - xs / 2, yy, zz - zs / 2);
            BlockState currentState = BuildStructureTask.this.mod.getWorld().getBlockState(worldPos);
            BlockState desiredState = desiredSchematicBlock.defaultBlockState();
            Map<String, Property<?>> props = new HashMap<>();

            for (Property<?> p : desiredState.getProperties()) {
               props.put(p.getName().toLowerCase(), p);
            }

            for (Entry<String, String> propEntry : desiredSchematicState.states().entrySet()) {
               String propKey = propEntry.getKey().toLowerCase();
               String propValue = propEntry.getValue();
               Property<?> prop = props.get(propKey);
               if (prop == null) {
                  BuildStructureTask.LOGGER
                     .warn("Failed to find prop with name {} for block {}. Ignoring this prop.", propKey, desiredSchematicBlock.getName());
               } else if (prop instanceof BooleanProperty booleanProp) {
                  desiredState = desiredState.setValue(booleanProp, Boolean.parseBoolean(propValue));
               } else if (prop instanceof IntegerProperty integerProp) {
                  desiredState = desiredState.setValue(integerProp, Integer.parseInt(propValue));
               } else if (prop instanceof EnumProperty<?> enumProp) {
                  desiredState = setEnumProp(enumProp, desiredState, propValue);
               } else {
                  BuildStructureTask.LOGGER
                     .warn(
                        "Did not account for this type of property: {}, {} when setting {}={}. Add support for this! Ignoring this state for now.",
                        prop.getClass().getSimpleName(),
                        prop.toString(),
                        propKey,
                        propValue
                     );
               }
            }

            if (!currentState.getBlock().getName().equals(desiredSchematicBlock.getName())) {
               BuildStructureTask.LOGGER
                  .info("ASDF REPLACING BLOCK({}): {} -> {} ({})", worldPos, currentState.getBlock().getName(), desiredState.toString());
               // Legacy direct world-write: not routed through NPC interaction manager.
               if(com.player2.playerengine.safezone.SafeZoneService.deny(BuildStructureTask.this.mod.getWorld(),worldPos,"legacy-build")) {
                  this.setDebugState("BLOCKED_SAFEZONE at "+worldPos);
                  return null; // no false completion, no editing protected area
               }
               BuildStructureTask.this.mod.getWorld().setBlock(worldPos, desiredState, 3);
               float targetMaxBuildTime = 180.0F;
               int blocksToPlace = xs * ys * zs;
               float blockPlaceDelay = Math.min(blocksToPlace > 0 ? targetMaxBuildTime / blocksToPlace : 0.3F, 0.3F);
               this.blockPlaceTimer.setInterval(blockPlaceDelay);
               this.blockPlaceTimer.reset();
               foundInvalidBlock = true;
            } else {
               BuildStructureTask.LOGGER.info("ASDF gucci {}", worldPos);
            }
         }

         return null;
      }

      @Override
      protected void onStop(Task var1) {
      }

      @Override
      protected boolean isEqual(Task var1) {
         return var1 instanceof BuildStructureTask.BuildSchematicFromDescriptionTask;
      }

      @Override
      protected String toDebugString() {
         return String.format(
            "Building structure at (%s) from schematic search: (%s)", BuildStructureTask.this.buildPosition.toShortString(), BuildStructureTask.this.description
         );
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }
   }

   private class RequestLLMCode extends Task {
      Optional<Either<String, String>> llmResult = Optional.empty();

      @Override
      protected boolean isEqual(Task var1) {
         return var1 instanceof BuildStructureTask.RequestLLMCode && ((BuildStructureTask.RequestLLMCode)var1).llmResult == this.llmResult;
      }

      @Override
      protected void onStart() {
         String var1 = FastStarterBlueprint.maybeCode(BuildStructureTask.this);
         if (var1 != null) {
            this.llmResult = Optional.of(Either.left(var1));
         } else {
            BuildStructureTask.this.completer.processToString(BuildStructureTask.this.service, BuildStructureTask.this.history, codeResult -> {
               BuildStructureTask.LOGGER.info("LLM generated code={}", codeResult);
               this.llmResult = Optional.of(Either.left(codeResult));
            }, errStr -> {
               BuildStructureTask.LOGGER.info("LLM Transport Error={}", errStr);
               this.llmResult = Optional.of(Either.right(errStr));
            }, false, AiTaskClass.PLANNING);
         }
      }

      @Override
      protected void onStop(Task var1) {
      }

      @Override
      protected Task onTick() {
         return null;
      }

      @Override
      protected String toDebugString() {
         return String.format(
            "Thinking about how to build structure at (%s) with description (%s)",
            BuildStructureTask.this.buildPosition.toShortString(),
            BuildStructureTask.this.description
         );
      }

      @Override
      public boolean isFinished() {
         return this.llmResult.isPresent();
      }
   }
}
