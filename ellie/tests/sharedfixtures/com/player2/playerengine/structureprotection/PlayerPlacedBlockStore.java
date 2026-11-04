package com.player2.playerengine.structureprotection;
import net.minecraft.class_2338;
public final class PlayerPlacedBlockStore {public static boolean protectedBlock;private static final PlayerPlacedBlockStore INSTANCE=new PlayerPlacedBlockStore();public static PlayerPlacedBlockStore get(){return INSTANCE;}public boolean contains(String dimension,class_2338 pos){return protectedBlock;}}
