package com.player2.playerengine.structureprotection;
import net.minecraft.class_2338;import java.util.*;
/** Recorded protection boundary only. Persistence and Minecraft block events are not simulated. */
public final class PlayerPlacedBlockStore {
 private static final PlayerPlacedBlockStore INSTANCE=new PlayerPlacedBlockStore();
 private final Set<String> blocks=new HashSet<>();
 public static PlayerPlacedBlockStore get(){return INSTANCE;}
 public void add(String dimension,class_2338 pos){blocks.add(dimension+":"+pos);}
 public boolean contains(String dimension,class_2338 pos){return blocks.contains(dimension+":"+pos);}
 public void clear(){blocks.clear();}
}
