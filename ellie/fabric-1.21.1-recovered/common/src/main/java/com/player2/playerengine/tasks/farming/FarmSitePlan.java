package com.player2.playerengine.tasks.farming;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class FarmSitePlan {
   private final BlockPos anchor;
   private final BlockPos center;
   private final boolean explicit;
   private final boolean waterRequired;
   private final int grassColumns;
   private final int existingFarmland;
   private final int repairColumns;
   private final int estimatedEdits;
   private final int rankingEdits;
   private final List<FarmSitePlan.ClearAction> clearActions;
   private final List<FarmSitePlan.FillAction> fillActions;
   private final List<FarmSitePlan.TillAction> tillActions;
   private final FarmSitePlan.WaterAction waterAction;
   private final BlockPos acquisitionReturnStance;
   private final FarmSiteSnapshot snapshot;

   public FarmSitePlan(
      BlockPos anchor,
      BlockPos center,
      boolean explicit,
      boolean waterRequired,
      int grassColumns,
      int existingFarmland,
      int repairColumns,
      List<FarmSitePlan.ClearAction> clearActions,
      List<FarmSitePlan.FillAction> fillActions,
      List<FarmSitePlan.TillAction> tillActions,
      FarmSitePlan.WaterAction waterAction,
      BlockPos acquisitionReturnStance,
      FarmSiteSnapshot snapshot
   ) {
      this.anchor = immutable(anchor, "anchor");
      this.center = immutable(center, "center");
      this.explicit = explicit;
      this.waterRequired = waterRequired;
      if (grassColumns >= 0 && grassColumns <= 81 && existingFarmland >= 0 && existingFarmland <= 80 && repairColumns >= 0 && repairColumns <= 16) {
         this.grassColumns = grassColumns;
         this.existingFarmland = existingFarmland;
         this.repairColumns = repairColumns;
         this.clearActions = List.copyOf(Objects.requireNonNull(clearActions, "clearActions"));
         this.fillActions = List.copyOf(Objects.requireNonNull(fillActions, "fillActions"));
         this.tillActions = List.copyOf(Objects.requireNonNull(tillActions, "tillActions"));
         this.waterAction = waterAction;
         this.acquisitionReturnStance = immutable(acquisitionReturnStance, "acquisitionReturnStance");
         this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
         if (!this.center.equals(snapshot.center()) || explicit != snapshot.explicit()) {
            throw new IllegalArgumentException("manifest and snapshot candidate do not match");
         }

         if (waterRequired != (waterAction != null)) {
            throw new IllegalArgumentException("water action must exist exactly when water is required");
         }

         this.estimatedEdits = Math.addExact(
            Math.addExact(this.clearActions.size(), this.fillActions.size()), Math.addExact(this.tillActions.size(), waterRequired ? 1 : 0)
         );
         int neutralGroundCoverClears = (int)this.clearActions.stream().filter(action -> action.purpose() == FarmSitePlan.ClearPurpose.VEGETATION).count();
         this.rankingEdits = Math.subtractExact(this.estimatedEdits, neutralGroundCoverClears);
      } else {
         throw new IllegalArgumentException("farm-site counts are outside fixed bounds");
      }
   }

   public BlockPos anchor() {
      return this.anchor;
   }

   public BlockPos center() {
      return this.center;
   }

   public boolean explicit() {
      return this.explicit;
   }

   public boolean waterRequired() {
      return this.waterRequired;
   }

   public int grassColumns() {
      return this.grassColumns;
   }

   public int existingFarmland() {
      return this.existingFarmland;
   }

   public int repairColumns() {
      return this.repairColumns;
   }

   public int estimatedEdits() {
      return this.estimatedEdits;
   }

   public int rankingEdits() {
      return this.rankingEdits;
   }

   public int requiredDirt() {
      return this.fillActions.size();
   }

   public int requiredHoeDamage() {
      return this.tillActions.size();
   }

   public List<FarmSitePlan.ClearAction> clearActions() {
      return this.clearActions;
   }

   public List<FarmSitePlan.FillAction> fillActions() {
      return this.fillActions;
   }

   public List<FarmSitePlan.TillAction> tillActions() {
      return this.tillActions;
   }

   public FarmSitePlan.WaterAction waterAction() {
      return this.waterAction;
   }

   public BlockPos acquisitionReturnStance() {
      return this.acquisitionReturnStance;
   }

   public FarmSiteSnapshot snapshot() {
      return this.snapshot;
   }

   public String fingerprint() {
      return this.snapshot.fingerprint();
   }

   public boolean matches(FarmSiteSnapshot current) {
      return this.snapshot.sameFacts(current);
   }

   private static BlockPos immutable(BlockPos position, String name) {
      return Objects.requireNonNull(position, name).immutable();
   }

   public record ClearAction(BlockPos target, BlockPos stance, String expectedStateFingerprint, FarmSitePlan.ClearPurpose purpose) {
      public ClearAction {
         target = FarmSitePlan.immutable(target, "target");
         stance = FarmSitePlan.immutable(stance, "stance");
         Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
         Objects.requireNonNull(purpose, "purpose");
      }
   }

   public enum ClearPurpose {
      VEGETATION,
      LEVEL_SURFACE,
      REPLACE_STONE,
      OPEN_CENTER;
   }

   public record FillAction(BlockPos target, BlockPos stance, String expectedStateFingerprint) {
      public FillAction {
         target = FarmSitePlan.immutable(target, "target");
         stance = FarmSitePlan.immutable(stance, "stance");
         Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
      }
   }

   public record TillAction(BlockPos target, BlockPos stance, String expectedStateFingerprint) {
      public TillAction {
         target = FarmSitePlan.immutable(target, "target");
         stance = FarmSitePlan.immutable(stance, "stance");
         Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
      }
   }

   public record WaterAction(BlockPos target, BlockPos stance, BlockPos support, String expectedStateFingerprint) {
      public WaterAction {
         target = FarmSitePlan.immutable(target, "target");
         stance = FarmSitePlan.immutable(stance, "stance");
         support = FarmSitePlan.immutable(support, "support");
         Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
      }
   }
}
