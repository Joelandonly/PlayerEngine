package com.player2.playerengine;

import com.google.common.collect.Streams;
import com.player2.playerengine.control.KillAura;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.util.BlockRange;
import com.player2.playerengine.util.helpers.ConfigHelper;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.serialization.IFailableConfigFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public class PlayerEngineSettings implements IFailableConfigFile {
   private static final String CONFIG_FILE = "playerengine_settings.json";
   static final int ADMIN_ITEM_LIST_MAX_SERIALIZED_LENGTH = 3500;
   private transient boolean failedToLoad = false;
   private boolean showDebugTickMs = false;
   private boolean showTaskChains = true;
   private boolean hideAllWarningLogs = false;
   private String commandPrefix = "@";
   private String logLevel = "NORMAL";
   private String chatLogPrefix = "[Alto Clef] ";
   private boolean showTimer = true;
   private float containerItemMoveDelay = 0.2F;
   private boolean useCraftingBookToCraft = true;
   private float resourcePickupDropRange = 16.0F;
   private int minimumFoodAllowed = 0;
   private int foodUnitsToCollect = 0;
   private float resourceChestLocateRange = 500.0F;
   private float resourceMineRange = 100.0F;
   private boolean avoidSearchingDungeonChests = true;
   private boolean avoidOceanBlocks = false;
   private float entityReachRange = 4.0F;
   private boolean collectPickaxeFirst = true;
   private boolean replantCrops = true;
   private boolean mobDefense = true;
   private KillAura.Strategy forceFieldStrategy = KillAura.Strategy.SMART;
   private boolean dodgeProjectiles = true;
   private boolean killOrAvoidAnnoyingHostiles = true;
   private boolean avoidDrowning = true;
   private boolean autoCloseScreenWhenLookingOrMining = true;
   private boolean extinguishSelfWithWater = true;
   private boolean autoEat = true;
   private boolean hungerEnabled = false;
   private boolean deathByHungerMatchesDifficulty = true;
   private boolean autoMLGBucket = true;
   private boolean autoReconnect = true;
   private boolean autoRespawn = true;
   private DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR overworldToNetherBehaviour = DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR.BUILD_PORTAL_VANILLA;
   private int netherFastTravelWalkingRange = 600;
   private String idleCommand = "idle";
   private String deathCommand = "";
   private boolean fastCraftMacrosEnabled = true;
   private float craftDelaySeconds = 0.5F;
   private float craftTableLookHoldSeconds = 0.25F;
   private boolean preferLocalCraftingTable = true;
   private double craftingTableReuseRadius = 48.0;
   private boolean enableAgenticPlanner = false;
   private boolean enableLookAtOwnerIdle = false;
   private int agenticPlannerRagTopK = 8;
   private int agenticPlannerMaxSteps = 4;
   private boolean agenticPlannerFallbackGather = true;
   private float gatherLooseItemsRadius = 16.0F;
   private int gatherLooseItemsMaxItems = 64;
   private float gatherLooseItemsTimeoutSeconds = 60.0F;
   private float gatherLooseItemsSettleSeconds = 3.0F;
   private float agenticStorageSearchRadius = 20.0F;
   private float agenticStoragePlacementRadius = 4.0F;
   private float agenticMaxTravelRadius = 96.0F;
   private boolean agenticStorageAllowPlacement = true;
   private boolean agenticStoragePreferExisting = true;
   private boolean agenticStorageAvoidLootChests = true;
   private float agenticStorageResolveTimeoutSeconds = 180.0F;
   private float agenticDepositTimeoutSeconds = 120.0F;
   private boolean agenticDepositKeepTools = true;
   private boolean agenticEnableLabelChest = true;
   private float agenticLabelTimeoutSeconds = 60.0F;
   private boolean agenticLabelUseModelText = false;
   private boolean enableDeferredSmelt = true;
   private int deferredSmeltMaxBatch = 64;
   private boolean enableSmithing = true;
   private int smithMaxBatch = 64;
   private int deferredSmeltStallPolls = 200;
   private int deferredSmeltTimeoutSeconds = 600;
   private float aggregateCountDropRadius = 16.0F;
   private float aggregateLocalSourceBlockRadius = 32.0F;
   private float mineCollectSettleSeconds = 1.0F;
   private double mineBlockRadius = 32.0;
   private int mineBlockMaxBlocks = 16;
   private double mineBlockTimeoutSeconds = 120.0;
   private int toolAcquireMaxContainers = 8;
   private int toolAcquireClimbBudget = 6;
   private int wanderBoundDefaultSeconds = 90;
   private int wanderNoImprovementSeconds = 30;
   private boolean ellieGpsEnabled = true;
   private int ellieGpsSnapshotSlotThreshold = 45;
   private boolean ellieGpsUseModelDescription = true;
   private List<Item> throwawayItems = Arrays.asList(
      Items.DRIPSTONE_BLOCK,
      Items.ROOTED_DIRT,
      Items.GRAVEL,
      Items.SAND,
      Items.DIORITE,
      Items.ANDESITE,
      Items.GRANITE,
      Items.TUFF,
      Items.COBBLESTONE,
      Items.DIRT,
      Items.COBBLED_DEEPSLATE,
      Items.ACACIA_LEAVES,
      Items.BIRCH_LEAVES,
      Items.DARK_OAK_LEAVES,
      Items.OAK_LEAVES,
      Items.JUNGLE_LEAVES,
      Items.SPRUCE_LEAVES,
      Items.NETHERRACK,
      Items.MAGMA_BLOCK,
      Items.SOUL_SOIL,
      Items.SOUL_SAND,
      Items.NETHER_BRICKS,
      Items.NETHER_BRICK,
      Items.BASALT,
      Items.BLACKSTONE,
      Items.END_STONE,
      Items.SANDSTONE,
      Items.STONE_BRICKS
   );
   private int reservedBuildingBlockCount = 64;
   private boolean dontThrowAwayCustomNameItems = true;
   private boolean dontThrowAwayEnchantedItems = true;
   private boolean throwAwayUnusedItems = true;
   private List<Item> importantItems = Streams.concat(
         new Stream[]{
            Stream.of(
               Items.TOTEM_OF_UNDYING,
               Items.ENCHANTED_GOLDEN_APPLE,
               Items.ENDER_EYE,
               Items.TRIDENT,
               Items.DIAMOND,
               Items.DIAMOND_BLOCK,
               Items.NETHERITE_SCRAP,
               Items.NETHERITE_INGOT,
               Items.NETHERITE_BLOCK
            ),
            Stream.of(ItemHelper.DIAMOND_ARMORS),
            Stream.of(ItemHelper.NETHERITE_ARMORS),
            Stream.of(ItemHelper.DIAMOND_TOOLS),
            Stream.of(ItemHelper.NETHERITE_TOOLS),
            Stream.of(ItemHelper.SHULKER_BOXES)
         }
      )
      .toList();
   private boolean limitFuelsToSupportedFuels = true;
   private List<Item> supportedFuels = Streams.concat(new Stream[]{Stream.of(Items.COAL, Items.CHARCOAL)}).toList();
   private BlockPos homeBasePosition = new BlockPos(0, 64, 0);
   private List<BlockRange> areasToProtect = Collections.emptyList();

   public static void load(Consumer<PlayerEngineSettings> onReload) {
      PlayerEngineSettings initial = ConfigHelper.getConfig("playerengine_settings.json", PlayerEngineSettings::new, PlayerEngineSettings.class);
      initial.normalizeAdminSupportedValues();
      onReload.accept(initial);
      ConfigHelper.registerReload("playerengine_settings.json", PlayerEngineSettingsAdminService::reloadFromDiskAndPublish);
   }

   public boolean shouldShowTaskChain() {
      return this.showTaskChains;
   }

   public boolean shouldShowDebugTickMs() {
      return this.showDebugTickMs;
   }

   public boolean shouldHideAllWarningLogs() {
      return this.hideAllWarningLogs;
   }

   public String getLogLevel() {
      return this.logLevel;
   }

   public String getCommandPrefix() {
      return this.commandPrefix;
   }

   public String getChatLogPrefix() {
      return this.chatLogPrefix;
   }

   public boolean shouldShowTimer() {
      return this.showTimer;
   }

   public float getResourcePickupRange() {
      return this.resourcePickupDropRange;
   }

   public float getResourceChestLocateRange() {
      return this.resourceChestLocateRange;
   }

   public float getResourceMineRange() {
      return this.resourceMineRange;
   }

   public float getContainerItemMoveDelay() {
      return this.containerItemMoveDelay;
   }

   public boolean shouldUseCraftingBookToCraft() {
      return this.useCraftingBookToCraft;
   }

   public int getFoodUnitsToCollect() {
      return this.foodUnitsToCollect;
   }

   public int getMinimumFoodAllowed() {
      return this.minimumFoodAllowed;
   }

   public boolean isMobDefense() {
      return this.mobDefense;
   }

   public boolean isDodgeProjectiles() {
      return this.dodgeProjectiles;
   }

   public boolean isAutoEat() {
      return this.autoEat;
   }

   public boolean isHungerEnabled() {
      return this.hungerEnabled;
   }

   public boolean isDeathByHungerMatchesDifficulty() {
      return this.deathByHungerMatchesDifficulty;
   }

   public boolean isAutoReconnect() {
      return this.autoReconnect;
   }

   public boolean isAutoRespawn() {
      return this.autoRespawn;
   }

   public boolean shouldReplantCrops() {
      return this.replantCrops;
   }

   public boolean shouldDealWithAnnoyingHostiles() {
      return this.killOrAvoidAnnoyingHostiles;
   }

   public KillAura.Strategy getForceFieldStrategy() {
      return this.forceFieldStrategy;
   }

   public String getIdleCommand() {
      return this.idleCommand == null ? "" : this.idleCommand;
   }

   public String getDeathCommand() {
      return this.deathCommand;
   }

   public boolean shouldRunIdleCommandWhenNotActive() {
      return this.idleCommand != null && !this.idleCommand.isBlank();
   }

   public boolean shouldAutoMLGBucket() {
      return this.autoMLGBucket;
   }

   public boolean shouldCollectPickaxeFirst() {
      return this.collectPickaxeFirst;
   }

   public boolean shouldAvoidDrowning() {
      return this.avoidDrowning;
   }

   public boolean shouldCloseScreenWhenLookingOrMining() {
      return this.autoCloseScreenWhenLookingOrMining;
   }

   public boolean shouldExtinguishSelfWithWater() {
      return this.extinguishSelfWithWater;
   }

   public boolean shouldAvoidSearchingForDungeonChests() {
      return this.avoidSearchingDungeonChests;
   }

   public boolean shouldAvoidOcean() {
      return this.avoidOceanBlocks;
   }

   public boolean isThrowaway(Item item) {
      return this.throwawayItems.contains(item);
   }

   public boolean isImportant(Item item) {
      return this.importantItems.contains(item);
   }

   public boolean shouldThrowawayUnusedItems() {
      return this.throwAwayUnusedItems;
   }

   public int getReservedBuildingBlockCount() {
      return this.reservedBuildingBlockCount;
   }

   public boolean getDontThrowAwayCustomNameItems() {
      return this.dontThrowAwayCustomNameItems;
   }

   public boolean getDontThrowAwayEnchantedItems() {
      return this.dontThrowAwayEnchantedItems;
   }

   public float getEntityReachRange() {
      return this.entityReachRange;
   }

   public Item[] getThrowawayItems(PlayerEngineController mod, boolean includeProtected) {
      return this.throwawayItems.stream().filter(item -> includeProtected || !mod.getBehaviour().isProtected(item)).toArray(Item[]::new);
   }

   public Item[] getThrowawayItems(PlayerEngineController mod) {
      return this.getThrowawayItems(mod, false);
   }

   public boolean shouldLimitFuelsToSupportedFuels() {
      return this.limitFuelsToSupportedFuels;
   }

   public boolean isSupportedFuel(Item item) {
      return !this.limitFuelsToSupportedFuels || this.supportedFuels.contains(item);
   }

   public Item[] getSupportedFuelItems() {
      return this.supportedFuels.toArray(Item[]::new);
   }

   public DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR getOverworldToNetherBehaviour() {
      return this.overworldToNetherBehaviour;
   }

   public int getNetherFastTravelWalkingRange() {
      return this.netherFastTravelWalkingRange;
   }

   public BlockPos getHomeBasePosition() {
      return this.homeBasePosition;
   }

   public boolean isFastCraftMacrosEnabled() {
      return this.fastCraftMacrosEnabled;
   }

   public double getCraftDelaySeconds() {
      return clamp(this.craftDelaySeconds, 0.0F, 5.0F);
   }

   public double getCraftTableLookHoldSeconds() {
      return clamp(this.craftTableLookHoldSeconds, 0.0F, 3.0F);
   }

   public boolean isPreferLocalCraftingTable() {
      return this.preferLocalCraftingTable;
   }

   public double getCraftingTableReuseRadius() {
      return clamp((float)this.craftingTableReuseRadius, 3.5F, 128.0F);
   }

   public boolean isEnableAgenticPlanner() {
      return this.enableAgenticPlanner;
   }

   public boolean isEnableLookAtOwnerIdle() {
      return this.enableLookAtOwnerIdle;
   }

   public int getAgenticPlannerRagTopK() {
      return (int)clamp(this.agenticPlannerRagTopK, 1, 20);
   }

   public int getAgenticPlannerMaxSteps() {
      return (int)clamp(this.agenticPlannerMaxSteps, 1, 4);
   }

   public boolean isAgenticPlannerFallbackGather() {
      return this.agenticPlannerFallbackGather;
   }

   public double getGatherLooseItemsRadius() {
      return clamp(this.gatherLooseItemsRadius, 2.0F, 64.0F);
   }

   public int getGatherLooseItemsMaxItems() {
      return (int)clamp(this.gatherLooseItemsMaxItems, 1, 1024);
   }

   public double getGatherLooseItemsTimeoutSeconds() {
      return clamp(this.gatherLooseItemsTimeoutSeconds, 5.0F, 300.0F);
   }

   public double getGatherLooseItemsSettleSeconds() {
      return clamp(this.gatherLooseItemsSettleSeconds, 0.5F, 20.0F);
   }

   public double getMineBlockRadius() {
      return clamp((float)this.mineBlockRadius, 4.0F, 128.0F);
   }

   public int getMineBlockMaxBlocks() {
      return (int)clamp(this.mineBlockMaxBlocks, 1, 256);
   }

   public double getMineBlockTimeoutSeconds() {
      return clamp((float)this.mineBlockTimeoutSeconds, 10.0F, 600.0F);
   }

   public int getToolAcquireMaxContainers() {
      return (int)clamp(this.toolAcquireMaxContainers, 1, 32);
   }

   public int getToolAcquireClimbBudget() {
      return (int)clamp(this.toolAcquireClimbBudget, 1, 16);
   }

   public double getAgenticStorageSearchRadius() {
      return clamp(this.agenticStorageSearchRadius, 4.0F, 64.0F);
   }

   public double getAgenticStoragePlacementRadius() {
      return clamp(this.agenticStoragePlacementRadius, 2.0F, 8.0F);
   }

   public double getAgenticMaxTravelRadius() {
      return clamp(this.agenticMaxTravelRadius, 16.0F, 160.0F);
   }

   public boolean isAgenticStorageAllowPlacement() {
      return this.agenticStorageAllowPlacement;
   }

   public boolean isAgenticStoragePreferExisting() {
      return this.agenticStoragePreferExisting;
   }

   public boolean isAgenticStorageAvoidLootChests() {
      return this.agenticStorageAvoidLootChests;
   }

   public double getAgenticStorageResolveTimeoutSeconds() {
      return clamp(this.agenticStorageResolveTimeoutSeconds, 10.0F, 300.0F);
   }

   public double getAgenticDepositTimeoutSeconds() {
      return clamp(this.agenticDepositTimeoutSeconds, 10.0F, 300.0F);
   }

   public boolean isAgenticDepositKeepTools() {
      return this.agenticDepositKeepTools;
   }

   public boolean isAgenticEnableLabelChest() {
      return this.agenticEnableLabelChest;
   }

   public double getAgenticLabelTimeoutSeconds() {
      return clamp(this.agenticLabelTimeoutSeconds, 10.0F, 180.0F);
   }

   public boolean isAgenticLabelUseModelText() {
      return this.agenticLabelUseModelText;
   }

   public boolean isEnableDeferredSmelt() {
      return this.enableDeferredSmelt;
   }

   public int getDeferredSmeltMaxBatch() {
      return (int)clamp(this.deferredSmeltMaxBatch, 1, 512);
   }

   public boolean isEnableSmithing() {
      return this.enableSmithing;
   }

   public int getSmithMaxBatch() {
      return (int)clamp(this.smithMaxBatch, 1, 512);
   }

   public int getDeferredSmeltStallPolls() {
      return (int)clamp(this.deferredSmeltStallPolls, 20, 2000);
   }

   public int getDeferredSmeltTimeoutSeconds() {
      return (int)clamp(this.deferredSmeltTimeoutSeconds, 60, 3600);
   }

   public double getAggregateCountDropRadius() {
      return clamp(this.aggregateCountDropRadius, 0.0F, 64.0F);
   }

   public double getAggregateLocalSourceBlockRadius() {
      return clamp(this.aggregateLocalSourceBlockRadius, 0.0F, 128.0F);
   }

   public double getMineCollectSettleSeconds() {
      return clamp(this.mineCollectSettleSeconds, 0.0F, 5.0F);
   }

   public int getWanderBoundDefaultSeconds() {
      return (int)clamp(this.wanderBoundDefaultSeconds, 0, 600);
   }

   public int getWanderNoImprovementSeconds() {
      return (int)clamp(this.wanderNoImprovementSeconds, 0, 300);
   }

   public boolean getEllieGpsEnabled() {
      return this.ellieGpsEnabled;
   }

   public int getEllieGpsSnapshotSlotThreshold() {
      return (int)clamp(this.ellieGpsSnapshotSlotThreshold, 1, 54);
   }

   public boolean getEllieGpsUseModelDescription() {
      return this.ellieGpsUseModelDescription;
   }

   PlayerEngineSettings defensiveCopy() {
      PlayerEngineSettings copy = new PlayerEngineSettings();
      copy.failedToLoad = this.failedToLoad;
      copy.showDebugTickMs = this.showDebugTickMs;
      copy.showTaskChains = this.showTaskChains;
      copy.hideAllWarningLogs = this.hideAllWarningLogs;
      copy.commandPrefix = this.commandPrefix;
      copy.logLevel = this.logLevel;
      copy.chatLogPrefix = this.chatLogPrefix;
      copy.showTimer = this.showTimer;
      copy.containerItemMoveDelay = this.containerItemMoveDelay;
      copy.useCraftingBookToCraft = this.useCraftingBookToCraft;
      copy.resourcePickupDropRange = this.resourcePickupDropRange;
      copy.minimumFoodAllowed = this.minimumFoodAllowed;
      copy.foodUnitsToCollect = this.foodUnitsToCollect;
      copy.resourceChestLocateRange = this.resourceChestLocateRange;
      copy.resourceMineRange = this.resourceMineRange;
      copy.avoidSearchingDungeonChests = this.avoidSearchingDungeonChests;
      copy.avoidOceanBlocks = this.avoidOceanBlocks;
      copy.entityReachRange = this.entityReachRange;
      copy.collectPickaxeFirst = this.collectPickaxeFirst;
      copy.replantCrops = this.replantCrops;
      copy.mobDefense = this.mobDefense;
      copy.forceFieldStrategy = this.forceFieldStrategy;
      copy.dodgeProjectiles = this.dodgeProjectiles;
      copy.killOrAvoidAnnoyingHostiles = this.killOrAvoidAnnoyingHostiles;
      copy.avoidDrowning = this.avoidDrowning;
      copy.autoCloseScreenWhenLookingOrMining = this.autoCloseScreenWhenLookingOrMining;
      copy.extinguishSelfWithWater = this.extinguishSelfWithWater;
      copy.autoEat = this.autoEat;
      copy.hungerEnabled = this.hungerEnabled;
      copy.deathByHungerMatchesDifficulty = this.deathByHungerMatchesDifficulty;
      copy.autoMLGBucket = this.autoMLGBucket;
      copy.autoReconnect = this.autoReconnect;
      copy.autoRespawn = this.autoRespawn;
      copy.overworldToNetherBehaviour = this.overworldToNetherBehaviour;
      copy.netherFastTravelWalkingRange = this.netherFastTravelWalkingRange;
      copy.idleCommand = this.idleCommand;
      copy.deathCommand = this.deathCommand;
      copy.fastCraftMacrosEnabled = this.fastCraftMacrosEnabled;
      copy.craftDelaySeconds = this.craftDelaySeconds;
      copy.craftTableLookHoldSeconds = this.craftTableLookHoldSeconds;
      copy.preferLocalCraftingTable = this.preferLocalCraftingTable;
      copy.craftingTableReuseRadius = this.craftingTableReuseRadius;
      copy.enableAgenticPlanner = this.enableAgenticPlanner;
      copy.enableLookAtOwnerIdle = this.enableLookAtOwnerIdle;
      copy.agenticPlannerRagTopK = this.agenticPlannerRagTopK;
      copy.agenticPlannerMaxSteps = this.agenticPlannerMaxSteps;
      copy.agenticPlannerFallbackGather = this.agenticPlannerFallbackGather;
      copy.gatherLooseItemsRadius = this.gatherLooseItemsRadius;
      copy.gatherLooseItemsMaxItems = this.gatherLooseItemsMaxItems;
      copy.gatherLooseItemsTimeoutSeconds = this.gatherLooseItemsTimeoutSeconds;
      copy.gatherLooseItemsSettleSeconds = this.gatherLooseItemsSettleSeconds;
      copy.agenticStorageSearchRadius = this.agenticStorageSearchRadius;
      copy.agenticStoragePlacementRadius = this.agenticStoragePlacementRadius;
      copy.agenticMaxTravelRadius = this.agenticMaxTravelRadius;
      copy.agenticStorageAllowPlacement = this.agenticStorageAllowPlacement;
      copy.agenticStoragePreferExisting = this.agenticStoragePreferExisting;
      copy.agenticStorageAvoidLootChests = this.agenticStorageAvoidLootChests;
      copy.agenticStorageResolveTimeoutSeconds = this.agenticStorageResolveTimeoutSeconds;
      copy.agenticDepositTimeoutSeconds = this.agenticDepositTimeoutSeconds;
      copy.agenticDepositKeepTools = this.agenticDepositKeepTools;
      copy.agenticEnableLabelChest = this.agenticEnableLabelChest;
      copy.agenticLabelTimeoutSeconds = this.agenticLabelTimeoutSeconds;
      copy.agenticLabelUseModelText = this.agenticLabelUseModelText;
      copy.enableDeferredSmelt = this.enableDeferredSmelt;
      copy.deferredSmeltMaxBatch = this.deferredSmeltMaxBatch;
      copy.enableSmithing = this.enableSmithing;
      copy.smithMaxBatch = this.smithMaxBatch;
      copy.deferredSmeltStallPolls = this.deferredSmeltStallPolls;
      copy.deferredSmeltTimeoutSeconds = this.deferredSmeltTimeoutSeconds;
      copy.aggregateCountDropRadius = this.aggregateCountDropRadius;
      copy.aggregateLocalSourceBlockRadius = this.aggregateLocalSourceBlockRadius;
      copy.mineCollectSettleSeconds = this.mineCollectSettleSeconds;
      copy.mineBlockRadius = this.mineBlockRadius;
      copy.mineBlockMaxBlocks = this.mineBlockMaxBlocks;
      copy.mineBlockTimeoutSeconds = this.mineBlockTimeoutSeconds;
      copy.toolAcquireMaxContainers = this.toolAcquireMaxContainers;
      copy.toolAcquireClimbBudget = this.toolAcquireClimbBudget;
      copy.wanderBoundDefaultSeconds = this.wanderBoundDefaultSeconds;
      copy.wanderNoImprovementSeconds = this.wanderNoImprovementSeconds;
      copy.ellieGpsEnabled = this.ellieGpsEnabled;
      copy.ellieGpsSnapshotSlotThreshold = this.ellieGpsSnapshotSlotThreshold;
      copy.ellieGpsUseModelDescription = this.ellieGpsUseModelDescription;
      copy.throwawayItems = copyItems(this.throwawayItems);
      copy.reservedBuildingBlockCount = this.reservedBuildingBlockCount;
      copy.dontThrowAwayCustomNameItems = this.dontThrowAwayCustomNameItems;
      copy.dontThrowAwayEnchantedItems = this.dontThrowAwayEnchantedItems;
      copy.throwAwayUnusedItems = this.throwAwayUnusedItems;
      copy.importantItems = copyItems(this.importantItems);
      copy.limitFuelsToSupportedFuels = this.limitFuelsToSupportedFuels;
      copy.supportedFuels = copyItems(this.supportedFuels);
      copy.homeBasePosition = this.homeBasePosition == null ? null : this.homeBasePosition.immutable();
      copy.areasToProtect = copyBlockRanges(this.areasToProtect);
      return copy;
   }

   void copyNextTaskValuesFrom(PlayerEngineSettings source) {
      if (source != null) {
         this.collectPickaxeFirst = source.collectPickaxeFirst;
         this.replantCrops = source.replantCrops;
         this.fastCraftMacrosEnabled = source.fastCraftMacrosEnabled;
         this.craftDelaySeconds = source.craftDelaySeconds;
         this.craftTableLookHoldSeconds = source.craftTableLookHoldSeconds;
         this.preferLocalCraftingTable = source.preferLocalCraftingTable;
         this.craftingTableReuseRadius = source.craftingTableReuseRadius;
         this.enableAgenticPlanner = source.enableAgenticPlanner;
         this.agenticPlannerRagTopK = source.agenticPlannerRagTopK;
         this.agenticPlannerMaxSteps = source.agenticPlannerMaxSteps;
         this.agenticPlannerFallbackGather = source.agenticPlannerFallbackGather;
         this.gatherLooseItemsRadius = source.gatherLooseItemsRadius;
         this.gatherLooseItemsMaxItems = source.gatherLooseItemsMaxItems;
         this.gatherLooseItemsTimeoutSeconds = source.gatherLooseItemsTimeoutSeconds;
         this.gatherLooseItemsSettleSeconds = source.gatherLooseItemsSettleSeconds;
         this.agenticStorageSearchRadius = source.agenticStorageSearchRadius;
         this.agenticStoragePlacementRadius = source.agenticStoragePlacementRadius;
         this.agenticMaxTravelRadius = source.agenticMaxTravelRadius;
         this.agenticStorageAllowPlacement = source.agenticStorageAllowPlacement;
         this.agenticStoragePreferExisting = source.agenticStoragePreferExisting;
         this.agenticStorageAvoidLootChests = source.agenticStorageAvoidLootChests;
         this.agenticStorageResolveTimeoutSeconds = source.agenticStorageResolveTimeoutSeconds;
         this.agenticDepositTimeoutSeconds = source.agenticDepositTimeoutSeconds;
         this.agenticDepositKeepTools = source.agenticDepositKeepTools;
         this.agenticEnableLabelChest = source.agenticEnableLabelChest;
         this.agenticLabelTimeoutSeconds = source.agenticLabelTimeoutSeconds;
         this.agenticLabelUseModelText = source.agenticLabelUseModelText;
         this.enableDeferredSmelt = source.enableDeferredSmelt;
         this.deferredSmeltMaxBatch = source.deferredSmeltMaxBatch;
         this.enableSmithing = source.enableSmithing;
         this.smithMaxBatch = source.smithMaxBatch;
         this.deferredSmeltStallPolls = source.deferredSmeltStallPolls;
         this.deferredSmeltTimeoutSeconds = source.deferredSmeltTimeoutSeconds;
         this.aggregateCountDropRadius = source.aggregateCountDropRadius;
         this.aggregateLocalSourceBlockRadius = source.aggregateLocalSourceBlockRadius;
         this.mineCollectSettleSeconds = source.mineCollectSettleSeconds;
         this.wanderBoundDefaultSeconds = source.wanderBoundDefaultSeconds;
         this.wanderNoImprovementSeconds = source.wanderNoImprovementSeconds;
         this.throwawayItems = copyItems(source.throwawayItems);
         this.dontThrowAwayCustomNameItems = source.dontThrowAwayCustomNameItems;
         this.dontThrowAwayEnchantedItems = source.dontThrowAwayEnchantedItems;
         this.throwAwayUnusedItems = source.throwAwayUnusedItems;
         this.importantItems = copyItems(source.importantItems);
         this.limitFuelsToSupportedFuels = source.limitFuelsToSupportedFuels;
         this.supportedFuels = copyItems(source.supportedFuels);
         this.homeBasePosition = source.homeBasePosition == null ? null : source.homeBasePosition.immutable();
      }
   }

   void normalizeAdminSupportedValues() {
      if (!isValidCommandPrefix(this.commandPrefix)) {
         this.commandPrefix = "@";
      }

      if (!isValidIdleCommand(this.idleCommand)) {
         this.idleCommand = "";
      }

      if (this.forceFieldStrategy == null) {
         this.forceFieldStrategy = KillAura.Strategy.SMART;
      }

      if (this.overworldToNetherBehaviour == null) {
         this.overworldToNetherBehaviour = DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR.BUILD_PORTAL_VANILLA;
      }

      this.resourcePickupDropRange = normalizeWhole(this.resourcePickupDropRange, 0, 128, 16.0F);
      this.minimumFoodAllowed = normalizeInt(this.minimumFoodAllowed, 0, 64);
      this.foodUnitsToCollect = normalizeInt(this.foodUnitsToCollect, 0, 64);
      if (!this.hasValidFoodSafety()) {
         this.foodUnitsToCollect = this.minimumFoodAllowed;
      }

      this.resourceChestLocateRange = normalizeWhole(this.resourceChestLocateRange, 0, 4096, 500.0F);
      this.resourceMineRange = normalizeWhole(this.resourceMineRange, 0, 4096, 100.0F);
      this.entityReachRange = normalizeWhole(this.entityReachRange, 1, 6, 4.0F);
      this.netherFastTravelWalkingRange = normalizeInt(this.netherFastTravelWalkingRange, 0, 100000);
      this.craftDelaySeconds = normalizeMilliseconds(this.craftDelaySeconds, 0, 5000, 0.5F);
      this.craftTableLookHoldSeconds = normalizeMilliseconds(this.craftTableLookHoldSeconds, 0, 3000, 0.25F);
      this.craftingTableReuseRadius = normalizeWhole(this.craftingTableReuseRadius, 4, 128, 48.0);
      this.agenticPlannerRagTopK = normalizeInt(this.agenticPlannerRagTopK, 1, 20);
      this.agenticPlannerMaxSteps = normalizeInt(this.agenticPlannerMaxSteps, 1, 4);
      this.gatherLooseItemsRadius = normalizeWhole(this.gatherLooseItemsRadius, 2, 64, 16.0F);
      this.gatherLooseItemsMaxItems = normalizeInt(this.gatherLooseItemsMaxItems, 1, 1024);
      this.gatherLooseItemsTimeoutSeconds = normalizeWhole(this.gatherLooseItemsTimeoutSeconds, 5, 300, 60.0F);
      this.gatherLooseItemsSettleSeconds = normalizeMilliseconds(this.gatherLooseItemsSettleSeconds, 500, 20000, 3.0F);
      this.agenticStorageSearchRadius = normalizeWhole(this.agenticStorageSearchRadius, 4, 64, 20.0F);
      this.agenticStoragePlacementRadius = normalizeWhole(this.agenticStoragePlacementRadius, 2, 8, 4.0F);
      this.agenticMaxTravelRadius = normalizeWhole(this.agenticMaxTravelRadius, 16, 160, 96.0F);
      this.agenticStorageResolveTimeoutSeconds = normalizeWhole(this.agenticStorageResolveTimeoutSeconds, 10, 300, 180.0F);
      this.agenticDepositTimeoutSeconds = normalizeWhole(this.agenticDepositTimeoutSeconds, 10, 300, 120.0F);
      this.agenticLabelTimeoutSeconds = normalizeWhole(this.agenticLabelTimeoutSeconds, 10, 180, 60.0F);
      this.deferredSmeltMaxBatch = normalizeInt(this.deferredSmeltMaxBatch, 1, 512);
      this.smithMaxBatch = normalizeInt(this.smithMaxBatch, 1, 512);
      this.deferredSmeltStallPolls = normalizeInt(this.deferredSmeltStallPolls, 20, 2000);
      this.deferredSmeltTimeoutSeconds = normalizeInt(this.deferredSmeltTimeoutSeconds, 60, 3600);
      this.aggregateCountDropRadius = normalizeWhole(this.aggregateCountDropRadius, 0, 64, 16.0F);
      this.aggregateLocalSourceBlockRadius = normalizeWhole(this.aggregateLocalSourceBlockRadius, 0, 128, 32.0F);
      this.mineCollectSettleSeconds = normalizeMilliseconds(this.mineCollectSettleSeconds, 0, 5000, 1.0F);
      this.wanderBoundDefaultSeconds = normalizeInt(this.wanderBoundDefaultSeconds, 0, 600);
      this.wanderNoImprovementSeconds = normalizeInt(this.wanderNoImprovementSeconds, 0, 300);
      this.ellieGpsSnapshotSlotThreshold = normalizeInt(this.ellieGpsSnapshotSlotThreshold, 1, 54);
      PlayerEngineSettings defaults = new PlayerEngineSettings();
      this.throwawayItems = normalizeItems(this.throwawayItems, defaults.throwawayItems);
      this.importantItems = normalizeItems(this.importantItems, defaults.importantItems);
      this.supportedFuels = normalizeItems(this.supportedFuels, defaults.supportedFuels);
      if (this.homeBasePosition == null) {
         this.homeBasePosition = new BlockPos(0, 64, 0);
      }
   }

   boolean hasValidFoodSafety() {
      return this.foodUnitsToCollect == 0 || this.foodUnitsToCollect >= this.minimumFoodAllowed;
   }

   void setAdminString(String key, String value) {
      switch (key) {
         case "commandPrefix":
            this.commandPrefix = value;
            break;
         case "idleCommand":
            this.idleCommand = value;
            break;
         default:
            throw new IllegalArgumentException("Unsupported string setting: " + key);
      }
   }

   void setAdminBoolean(String key, boolean value) {
      switch (key) {
         case "avoidSearchingDungeonChests":
            this.avoidSearchingDungeonChests = value;
            break;
         case "avoidOceanBlocks":
            this.avoidOceanBlocks = value;
            break;
         case "collectPickaxeFirst":
            this.collectPickaxeFirst = value;
            break;
         case "replantCrops":
            this.replantCrops = value;
            break;
         case "mobDefense":
            this.mobDefense = value;
            break;
         case "dodgeProjectiles":
            this.dodgeProjectiles = value;
            break;
         case "killOrAvoidAnnoyingHostiles":
            this.killOrAvoidAnnoyingHostiles = value;
            break;
         case "avoidDrowning":
            this.avoidDrowning = value;
            break;
         case "extinguishSelfWithWater":
            this.extinguishSelfWithWater = value;
            break;
         case "autoEat":
            this.autoEat = value;
            break;
         case "hungerEnabled":
            this.hungerEnabled = value;
            break;
         case "autoMLGBucket":
            this.autoMLGBucket = value;
            break;
         case "fastCraftMacrosEnabled":
            this.fastCraftMacrosEnabled = value;
            break;
         case "preferLocalCraftingTable":
            this.preferLocalCraftingTable = value;
            break;
         case "enableAgenticPlanner":
            this.enableAgenticPlanner = value;
            break;
         case "enableLookAtOwnerIdle":
            this.enableLookAtOwnerIdle = value;
            break;
         case "agenticPlannerFallbackGather":
            this.agenticPlannerFallbackGather = value;
            break;
         case "agenticStorageAllowPlacement":
            this.agenticStorageAllowPlacement = value;
            break;
         case "agenticStoragePreferExisting":
            this.agenticStoragePreferExisting = value;
            break;
         case "agenticStorageAvoidLootChests":
            this.agenticStorageAvoidLootChests = value;
            break;
         case "agenticDepositKeepTools":
            this.agenticDepositKeepTools = value;
            break;
         case "agenticEnableLabelChest":
            this.agenticEnableLabelChest = value;
            break;
         case "agenticLabelUseModelText":
            this.agenticLabelUseModelText = value;
            break;
         case "enableDeferredSmelt":
            this.enableDeferredSmelt = value;
            break;
         case "enableSmithing":
            this.enableSmithing = value;
            break;
         case "ellieGpsEnabled":
            this.ellieGpsEnabled = value;
            break;
         case "ellieGpsUseModelDescription":
            this.ellieGpsUseModelDescription = value;
            break;
         case "dontThrowAwayCustomNameItems":
            this.dontThrowAwayCustomNameItems = value;
            break;
         case "dontThrowAwayEnchantedItems":
            this.dontThrowAwayEnchantedItems = value;
            break;
         case "throwAwayUnusedItems":
            this.throwAwayUnusedItems = value;
            break;
         case "limitFuelsToSupportedFuels":
            this.limitFuelsToSupportedFuels = value;
            break;
         default:
            throw new IllegalArgumentException("Unsupported boolean setting: " + key);
      }
   }

   void setAdminInt(String key, int value) {
      switch (key) {
         case "minimumFoodAllowed":
            this.minimumFoodAllowed = value;
            break;
         case "foodUnitsToCollect":
            this.foodUnitsToCollect = value;
            break;
         case "netherFastTravelWalkingRange":
            this.netherFastTravelWalkingRange = value;
            break;
         case "agenticPlannerRagTopK":
            this.agenticPlannerRagTopK = value;
            break;
         case "agenticPlannerMaxSteps":
            this.agenticPlannerMaxSteps = value;
            break;
         case "gatherLooseItemsMaxItems":
            this.gatherLooseItemsMaxItems = value;
            break;
         case "deferredSmeltMaxBatch":
            this.deferredSmeltMaxBatch = value;
            break;
         case "smithMaxBatch":
            this.smithMaxBatch = value;
            break;
         case "deferredSmeltStallPolls":
            this.deferredSmeltStallPolls = value;
            break;
         case "deferredSmeltTimeoutSeconds":
            this.deferredSmeltTimeoutSeconds = value;
            break;
         case "wanderBoundDefaultSeconds":
            this.wanderBoundDefaultSeconds = value;
            break;
         case "wanderNoImprovementSeconds":
            this.wanderNoImprovementSeconds = value;
            break;
         case "ellieGpsSnapshotSlotThreshold":
            this.ellieGpsSnapshotSlotThreshold = value;
            break;
         default:
            throw new IllegalArgumentException("Unsupported integer setting: " + key);
      }
   }

   void setAdminWholeNumber(String key, int value) {
      switch (key) {
         case "resourcePickupDropRange":
            this.resourcePickupDropRange = value;
            break;
         case "resourceChestLocateRange":
            this.resourceChestLocateRange = value;
            break;
         case "resourceMineRange":
            this.resourceMineRange = value;
            break;
         case "entityReachRange":
            this.entityReachRange = value;
            break;
         case "craftingTableReuseRadius":
            this.craftingTableReuseRadius = value;
            break;
         case "gatherLooseItemsRadius":
            this.gatherLooseItemsRadius = value;
            break;
         case "gatherLooseItemsTimeoutSeconds":
            this.gatherLooseItemsTimeoutSeconds = value;
            break;
         case "agenticStorageSearchRadius":
            this.agenticStorageSearchRadius = value;
            break;
         case "agenticStoragePlacementRadius":
            this.agenticStoragePlacementRadius = value;
            break;
         case "agenticMaxTravelRadius":
            this.agenticMaxTravelRadius = value;
            break;
         case "agenticStorageResolveTimeoutSeconds":
            this.agenticStorageResolveTimeoutSeconds = value;
            break;
         case "agenticDepositTimeoutSeconds":
            this.agenticDepositTimeoutSeconds = value;
            break;
         case "agenticLabelTimeoutSeconds":
            this.agenticLabelTimeoutSeconds = value;
            break;
         case "aggregateCountDropRadius":
            this.aggregateCountDropRadius = value;
            break;
         case "aggregateLocalSourceBlockRadius":
            this.aggregateLocalSourceBlockRadius = value;
            break;
         default:
            throw new IllegalArgumentException("Unsupported whole-number setting: " + key);
      }
   }

   void setAdminMilliseconds(String key, int milliseconds) {
      float seconds = milliseconds / 1000.0F;
      switch (key) {
         case "craftDelaySeconds":
            this.craftDelaySeconds = seconds;
            break;
         case "craftTableLookHoldSeconds":
            this.craftTableLookHoldSeconds = seconds;
            break;
         case "gatherLooseItemsSettleSeconds":
            this.gatherLooseItemsSettleSeconds = seconds;
            break;
         case "mineCollectSettleSeconds":
            this.mineCollectSettleSeconds = seconds;
            break;
         default:
            throw new IllegalArgumentException("Unsupported millisecond setting: " + key);
      }
   }

   void setAdminForceFieldStrategy(KillAura.Strategy value) {
      this.forceFieldStrategy = value;
   }

   void setAdminOverworldToNetherBehaviour(DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR value) {
      this.overworldToNetherBehaviour = value;
   }

   void setAdminItems(String key, List<Item> value) {
      List<Item> copy = List.copyOf(value);
      switch (key) {
         case "throwawayItems":
            this.throwawayItems = copy;
            break;
         case "importantItems":
            this.importantItems = copy;
            break;
         case "supportedFuels":
            this.supportedFuels = copy;
            break;
         default:
            throw new IllegalArgumentException("Unsupported item-list setting: " + key);
      }
   }

   void setAdminHomeBasePosition(BlockPos value) {
      this.homeBasePosition = value.immutable();
   }

   List<Item> adminThrowawayItems() {
      return List.copyOf(this.throwawayItems);
   }

   List<Item> adminImportantItems() {
      return List.copyOf(this.importantItems);
   }

   List<Item> adminSupportedFuels() {
      return List.copyOf(this.supportedFuels);
   }

   private static boolean isValidCommandPrefix(String value) {
      return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= 8
         ? value.codePoints().noneMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp))
         : false;
   }

   private static boolean isValidIdleCommand(String value) {
      return value != null && value.codePointCount(0, value.length()) <= 128 && value.codePoints().noneMatch(Character::isISOControl);
   }

   private static int normalizeInt(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static float normalizeWhole(float value, int min, int max, float fallback) {
      float safe = Float.isFinite(value) ? value : fallback;
      return normalizeInt(Math.round(safe), min, max);
   }

   private static double normalizeWhole(double value, int min, int max, double fallback) {
      double safe = Double.isFinite(value) ? value : fallback;
      long rounded = Math.round(safe);
      return Math.max(min, Math.min(max, rounded));
   }

   private static float normalizeMilliseconds(float seconds, int minMs, int maxMs, float fallback) {
      float safe = Float.isFinite(seconds) ? seconds : fallback;
      int milliseconds = normalizeInt(Math.round(safe * 1000.0F), minMs, maxMs);
      return milliseconds / 1000.0F;
   }

   private static List<Item> normalizeItems(List<Item> source, List<Item> fallback) {
      List<Item> selected = source == null ? fallback : source;
      LinkedHashSet<Item> unique = new LinkedHashSet<>();
      int serializedLength = 0;

      for (Item item : selected) {
         if (item != null && item != Items.AIR && !unique.contains(item)) {
            ResourceLocation id = BuiltInRegistries.ITEM.getResourceKey(item).map(key -> key.location()).orElse(null);
            if (id != null) {
               int addedLength = id.toString().length() + (unique.isEmpty() ? 0 : 1);
               if (serializedLength + addedLength > 3500) {
                  break;
               }

               unique.add(item);
               serializedLength += addedLength;
               if (unique.size() == 64) {
                  break;
               }
            }
         }
      }

      return List.copyOf(unique);
   }

   private static List<Item> copyItems(List<Item> source) {
      return source == null ? null : List.copyOf(source);
   }

   private static List<BlockRange> copyBlockRanges(List<BlockRange> source) {
      if (source == null) {
         return null;
      }

      List<BlockRange> copy = new ArrayList<>(source.size());

      for (BlockRange range : source) {
         copy.add(
            range == null
               ? null
               : new BlockRange(range.start == null ? null : range.start.immutable(), range.end == null ? null : range.end.immutable(), range.dimension)
         );
      }

      return Collections.unmodifiableList(copy);
   }

   private static double clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   @Override
   public void onFailLoad() {
      this.failedToLoad = true;
   }

   @Override
   public boolean failedToLoad() {
      return this.failedToLoad;
   }
}
