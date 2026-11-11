package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.AgenticExecutionContext;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.AgenticStorageTarget;
import com.player2.playerengine.agentic.steps.LabelChestParams;
import com.player2.playerengine.agentic.steps.SignLabelText;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceSignTask;
import com.player2.playerengine.tasks.crafting.CraftMacroPhase;
import com.player2.playerengine.tasks.crafting.CraftMacroResourceTask;
import com.player2.playerengine.tasks.crafting.CraftMacroTasks;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.sign.SignScanSupport;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SignItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;

public final class LabelChestTask extends Task implements DescribesProgress {
   private static final Direction[] HORIZONTAL = new Direction[]{
      Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
   };
   private final LabelChestParams params;
   private final AgenticExecutionContext context;
   private LabelChestTask.Phase phase = LabelChestTask.Phase.READING_TARGET;
   private boolean finished;
   private long startMs;
   private AgenticStorageTarget target;
   private Item signItem;
   private BlockPos anchor;
   private Direction faceTowardAir;
   private PlaceSignTask placeChild;
   private Task craftChild;
   private long lastProgressMs;
   private CraftMacroPhase lastObservedMacroPhase;
   private int lastObservedHeld = -1;
   private boolean placeOutcomeRecorded;
   private String lastMessage = "";

   public LabelChestTask(LabelChestParams params, AgenticExecutionContext context) {
      this.params = params;
      this.context = context;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   @Override
   public String describeProgress() {
      String anchorStr = this.anchor != null
         ? String.format(Locale.ROOT, "%d %d %d", this.anchor.getX(), this.anchor.getY(), this.anchor.getZ())
         : "none";
      return String.format(
         Locale.ROOT,
         "phase=%s anchor=%s face=%s%s",
         this.phase.name().toLowerCase(Locale.ROOT),
         anchorStr,
         this.faceTowardAir != null ? this.faceTowardAir.getName() : "none",
         this.lastMessage.isEmpty() ? "" : " — " + this.lastMessage
      );
   }

   @Override
   protected void onStart() {
      this.startMs = System.currentTimeMillis();
      this.lastProgressMs = this.startMs;
      this.phase = LabelChestTask.Phase.READING_TARGET;
      if (!this.context.settings().isAgenticEnableLabelChest()) {
         this.finishWithWarning("label_disabled");
      } else {
         this.updateProgress();
         this.report("labeling chest", false);
      }
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      boolean macroMakingProgress = this.phase == LabelChestTask.Phase.CRAFTING_SIGN
         && this.craftChild instanceof CraftMacroResourceTask macro
         && macro.getPhase() != CraftMacroPhase.DONE
         && this.detectAndRefreshMacroProgress(macro);
      if (!macroMakingProgress) {
         double idleSec = (System.currentTimeMillis() - this.lastProgressMs) / 1000.0;
         if (idleSec >= this.params.timeoutSeconds()) {
            this.finishWithWarning(this.phase == LabelChestTask.Phase.CRAFTING_SIGN ? "sign_craft_failed: no craft progress, timed out" : "label_timeout");
            return null;
         }
      }
      return switch (this.phase) {
         case READING_TARGET -> this.tickReadingTarget();
         case CHECK_SIGN_ITEM -> this.tickCheckSignItem();
         case CRAFTING_SIGN -> this.tickCraftingSign();
         case SELECT_ANCHOR -> this.tickSelectAnchor();
         case PLACING -> this.tickPlacing();
         case DONE -> null;
      };
   }

   private Task tickReadingTarget() {
      Optional<AgenticStorageTarget> maybe = this.context.memory().storageTarget();
      if (maybe.isEmpty()) {
         this.finishWithWarning("no_storage_target_for_label");
         return null;
      } else {
         this.target = maybe.get();
         this.phase = LabelChestTask.Phase.CHECK_SIGN_ITEM;
         this.updateProgress();
         return null;
      }
   }

   private Task tickCheckSignItem() {
      Item resolved;
      if (!this.params.signItemId().isBlank()) {
         Item byId = resolveSignItemById(this.params.signItemId());
         resolved = byId instanceof SignItem && inventoryContainsItem(this.controller, byId) ? byId : findFirstSignItemInInventory(this.controller);
      } else {
         resolved = findFirstSignItemInInventory(this.controller);
      }

      if (resolved != Items.AIR && resolved instanceof SignItem) {
         this.signItem = resolved;
         this.phase = LabelChestTask.Phase.SELECT_ANCHOR;
         this.updateProgress();
         return null;
      } else {
         this.phase = LabelChestTask.Phase.CRAFTING_SIGN;
         this.updateProgress();
         return null;
      }
   }

   private Task tickCraftingSign() {
      Item held = findFirstSignItemInInventory(this.controller);
      if (held instanceof SignItem) {
         this.signItem = held;
         this.stopCraftChild();
         this.phase = LabelChestTask.Phase.SELECT_ANCHOR;
         this.updateProgress();
         return null;
      }

      if (this.craftChild != null) {
         if (this.craftChild.isActive() && !this.craftChild.stopped() && !this.isSignCraftChildComplete()) {
            return this.craftChild;
         }

         String reason = this.craftChild instanceof CraftMacroResourceTask macro && macro.getFailureReason() != null && !macro.getFailureReason().isBlank()
            ? macro.getFailureReason()
            : "craft did not produce a sign";
         this.finishWithWarning("sign_craft_failed: " + reason);
         return null;
      } else {
         Task macro = CraftMacroTasks.tryCreateMacroTask(this.controller, new ItemTarget("sign", 1), this.context != null ? this.context.reservations() : null);
         if (macro == null) {
            this.finishWithWarning("sign_craft_unavailable");
            return null;
         } else {
            this.craftChild = macro;
            this.updateProgress();
            this.report("crafting a sign to label the chest", false);
            return this.craftChild;
         }
      }
   }

   private boolean isSignCraftChildComplete() {
      return this.craftChild instanceof CraftMacroResourceTask macro ? macro.getPhase() == CraftMacroPhase.DONE : this.craftChild.isFinished();
   }

   private boolean detectAndRefreshMacroProgress(CraftMacroResourceTask macro) {
      long now = System.currentTimeMillis();
      boolean advanced = false;
      CraftMacroPhase macroPhase = macro.getPhase();
      if (macroPhase != this.lastObservedMacroPhase) {
         this.lastObservedMacroPhase = macroPhase;
         advanced = true;
      }

      int held = this.signCraftMaterialHeld();
      if (held > this.lastObservedHeld) {
         advanced = true;
      }

      if (held != this.lastObservedHeld) {
         this.lastObservedHeld = held;
      }

      if (advanced) {
         this.lastProgressMs = now;
      }

      return (now - this.lastProgressMs) / 1000.0 < this.params.timeoutSeconds();
   }

   private int signCraftMaterialHeld() {
      ItemStorageTracker storage = this.controller.getItemStorage();
      return storage.getItemCount(ItemHelper.LOG)
         + storage.getItemCount(ItemHelper.PLANKS)
         + storage.getItemCount(Items.STICK)
         + storage.getItemCount(Items.CRAFTING_TABLE)
         + storage.getItemCount(ItemHelper.WOOD_SIGN);
   }

   private Task tickSelectAnchor() {
      BlockPos chest = this.target.pos();
      ServerLevel level = this.controller.getWorld();
      int minY = level.getMinBuildHeight();
      int maxY = level.getMaxBuildHeight() - 1;
      BlockState chestState = level.getBlockState(chest);
      if (!chestState.isAir()) {
         for (Direction face : HORIZONTAL) {
            BlockPos signCell = SignScanSupport.expectedSignBlockPos(chest, face);
            if (signCell.getY() >= minY && signCell.getY() <= maxY && this.isPlaceableSignCell(signCell)) {
               this.anchor = chest;
               this.faceTowardAir = face;
               this.phase = LabelChestTask.Phase.PLACING;
               this.updateProgress();
               return null;
            }
         }
      }

      for (Direction face : HORIZONTAL) {
         BlockPos neighbor = chest.relative(face);
         if (WorldHelper.isSolidBlock(this.controller, neighbor)) {
            BlockPos signCell = neighbor.above();
            if (signCell.getY() >= minY && signCell.getY() <= maxY && this.isPlaceableSignCell(signCell)) {
               this.anchor = neighbor;
               this.faceTowardAir = Direction.UP;
               this.phase = LabelChestTask.Phase.PLACING;
               this.updateProgress();
               return null;
            }
         }
      }

      this.finishWithWarning("no_label_anchor");
      return null;
   }

   private boolean isPlaceableSignCell(BlockPos signCell) {
      return WorldHelper.isAir(this.controller, signCell) && WorldHelper.canReach(this.controller, signCell) && WorldHelper.canPlace(this.controller, signCell);
   }

   private Task tickPlacing() {
      if (this.placeChild != null) {
         if (this.placeChild.isActive() && !this.placeChild.stopped() && !this.placeChild.isFinished()) {
            return this.placeChild;
         }

         if (!this.placeOutcomeRecorded) {
            this.finishWithWarning("label_skipped: place_no_callback");
         }

         return null;
      } else {
         Component[] front = SignLabelText.frontLines(this.controller, this.buildLabelLines());
         Component[] back = SignLabelText.emptyBack();
         BlockPos placedAt = SignScanSupport.expectedSignBlockPos(this.anchor, this.faceTowardAir);
         this.placeChild = new PlaceSignTask(
            this.anchor,
            this.faceTowardAir,
            this.signItem,
            front,
            back,
            () -> this.finishWithSuccess("labeled at " + formatPos(placedAt)),
            code -> this.finishWithWarning("label_skipped: " + code)
         );
         this.updateProgress();
         return this.placeChild;
      }
   }

   private String[] buildLabelLines() {
      List<String> explicit = this.params.lines();
      if (explicit != null && !explicit.isEmpty()) {
         String[] out = new String[4];

         for (int i = 0; i < 4; i++) {
            out[i] = i < explicit.size() && explicit.get(i) != null ? explicit.get(i) : "";
         }

         return out;
      } else {
         String[] template = this.deterministicTemplate();
         if (this.params.autoLabel() && this.context.settings().isAgenticLabelUseModelText()) {
            String[] model = this.tryModelLabel(template);
            if (model != null) {
               return model;
            }
         }

         return template;
      }
   }

   private String[] deterministicTemplate() {
      BlockPos pos = this.target.pos();
      String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
      return new String[]{"Storage", this.contentCategorySummary(), coords, ""};
   }

   private String contentCategorySummary() {
      String blockId = this.target.blockId();
      if (blockId != null && !blockId.isBlank()) {
         int colon = blockId.indexOf(58);
         String name = colon >= 0 ? blockId.substring(colon + 1) : blockId;
         return name.replace('_', ' ');
      } else {
         return "Items";
      }
   }

   private String[] tryModelLabel(String[] fallbackTemplate) {
      return null;
   }

   private void finishWithSuccess(String message) {
      if (!this.finished) {
         this.placeOutcomeRecorded = true;
         this.phase = LabelChestTask.Phase.DONE;
         this.finished = true;
         this.lastMessage = message;
         this.stopChild();
         this.updateProgress();
         this.report("labeled chest", true);
         this.controller.log("[Agentic] label_chest: " + message);
      }
   }

   private void finishWithWarning(String reason) {
      if (!this.finished) {
         this.placeOutcomeRecorded = true;
         this.phase = LabelChestTask.Phase.DONE;
         this.finished = true;
         this.lastMessage = reason;
         this.stopChild();
         this.updateProgress();
         if (this.context != null && this.context.runState() != null) {
            this.context.runState().setLabelDegraded(AgenticRunRegistry.DegradationLevel.SKIPPED, reason);
         }

         this.report("skipped chest label: " + describeLabelDegradation(reason), true);
         this.controller.log("[Agentic] label_chest (best-effort, still ok): " + reason);
      }
   }

   private static String describeLabelDegradation(String reason) {
      if (reason.startsWith("sign_craft_failed: ")) {
         return "tried to craft a sign but couldn't (" + reason.substring("sign_craft_failed: ".length()) + ")";
      }

      return switch (reason) {
         case "label_disabled" -> "labeling disabled";
         case "label_timeout" -> "timed out";
         case "no_sign_item" -> "no sign item";
         case "sign_craft_unavailable" -> "no sign item and sign crafting is disabled";
         case "no_label_anchor" -> "no spot for a sign";
         case "no_storage_target_for_label" -> "no chest to label";
         case "interrupted" -> "interrupted";
         default -> reason;
      };
   }

   private void stopChild() {
      if (this.placeChild != null && !this.placeChild.stopped()) {
         this.placeChild.stop(this);
      }

      this.placeChild = null;
      this.stopCraftChild();
   }

   private void stopCraftChild() {
      if (this.craftChild != null && !this.craftChild.stopped()) {
         this.craftChild.stop(this);
      }

      this.craftChild = null;
   }

   private void updateProgress() {
      if (this.context != null && this.context.runState() != null) {
         this.context.runState().setLabelProgress(this.describeProgress());
      }

      this.setDebugState(this.phase.name());
   }

   private void report(String message, boolean milestone) {
      if (this.context != null) {
         if (this.context.runState() == null || !this.context.runState().isTerminal()) {
            this.context.controller().reportAgenticProgress(message, milestone);
         }
      }
   }

   private static String formatPos(BlockPos pos) {
      return pos.getX() + "," + pos.getY() + "," + pos.getZ();
   }

   private static Item findFirstSignItemInInventory(PlayerEngineController mod) {
      for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
         Item it = mod.getInventory().getItem(i).getItem();
         if (it instanceof SignItem) {
            return it;
         }
      }

      return Items.AIR;
   }

   private static boolean inventoryContainsItem(PlayerEngineController mod, Item target) {
      if (target != null && target != Items.AIR) {
         for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
            if (mod.getInventory().getItem(i).getItem() == target) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static Item resolveSignItemById(String idStr) {
      String trimmed = idStr.trim().toLowerCase(Locale.ROOT);
      ResourceLocation id = ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
      if (id == null) {
         return Items.AIR;
      }

      Item item = (Item)BuiltInRegistries.ITEM.get(id);
      return !(item instanceof SignItem) ? Items.AIR : item;
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished) {
         this.phase = LabelChestTask.Phase.DONE;
         this.finished = true;
         this.lastMessage = "interrupted";
         if (this.placeChild != null && !this.placeChild.stopped()) {
            this.placeChild.stop(interruptTask);
         }

         this.placeChild = null;
         if (this.craftChild != null && !this.craftChild.stopped()) {
            this.craftChild.stop(interruptTask);
         }

         this.craftChild = null;
         this.updateProgress();
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof LabelChestTask task && task.params.equals(this.params);
   }

   @Override
   protected String toDebugString() {
      return "LabelChest";
   }

   private enum Phase {
      READING_TARGET,
      CHECK_SIGN_ITEM,
      CRAFTING_SIGN,
      SELECT_ANCHOR,
      PLACING,
      DONE;
   }
}
