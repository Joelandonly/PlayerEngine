package com.player2.playerengine.warehouse;

/** Pure policy shared by live allocation and deterministic tests. */
public final class WarehousePolicy {
    private WarehousePolicy() {}
    public static boolean needsRecovery(int free,int reserve){return free<0 || free<reserve;}
    public static int slotCapacity(boolean empty,boolean same,int count,int itemMax,int containerMax,boolean allowed){
        if(!allowed)return 0;
        int cap=Math.min(itemMax,containerMax);
        return empty?cap:same?Math.max(0,cap-count):0;
    }
    public static boolean prefer(boolean candidateMatching,double candidateDistance,boolean bestMatching,double bestDistance){
        return candidateMatching&&!bestMatching || candidateMatching==bestMatching&&candidateDistance<bestDistance;
    }
    public static String category(String id){
        if(id.contains("planks")||id.contains("log")||id.contains("wood"))return "Wood / Planks";
        if(id.contains("ore")||id.contains("ingot")||id.contains("diamond")||id.contains("redstone"))return "Ores / Ingots";
        if(id.contains("netherrack")||id.contains("basalt")||id.contains("blackstone"))return "Nether Materials";
        if(id.contains("stone")||id.contains("dirt")||id.contains("gravel")||id.contains("sand")||id.contains("tuff"))return "Stone / Cobblestone";
        return "Miscellaneous";
    }
}
