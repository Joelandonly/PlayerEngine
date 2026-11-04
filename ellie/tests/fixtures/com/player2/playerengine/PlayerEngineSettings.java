package com.player2.playerengine;
/** Only the configured prefix boundary is substituted for dispatcher tests. */
public final class PlayerEngineSettings {
    public double getCraftingTableReuseRadius(){return 16;}
    public String getCommandPrefix() { return "@"; }
    public boolean isEnableAgenticPlanner(){throw new AssertionError("Native planning must ignore AI settings");}
    public boolean isAgenticEnableLabelChest(){return true;}
    public int getAgenticPlannerMaxSteps(){return 8;}
    public double getGatherLooseItemsRadius(){return 16;}
    public int getGatherLooseItemsMaxItems(){return 64;}
    public double getGatherLooseItemsSettleSeconds(){return 1;}
    public double getGatherLooseItemsTimeoutSeconds(){return 60;}
    public double getAgenticStorageSearchRadius(){return 24;}
    public double getAgenticStoragePlacementRadius(){return 8;}
    public boolean isAgenticStoragePreferExisting(){return true;}
    public boolean isAgenticStorageAllowPlacement(){return true;}
    public boolean isAgenticStorageAvoidLootChests(){return true;}
    public double getAgenticStorageResolveTimeoutSeconds(){return 60;}
    public double getAgenticDepositTimeoutSeconds(){return 60;}
    public boolean isAgenticDepositKeepTools(){return true;}
    public double getAgenticLabelTimeoutSeconds(){return 30;}
}
