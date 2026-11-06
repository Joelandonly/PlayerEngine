package com.player2.playerengine.automaton.utils;
import net.minecraft.*;
/** Native BSI ABI; loaded-chunk reads substituted for the Minecraft cache. */
public class BlockStateInterface {
 private final class_3218 world;
 public BlockStateInterface(class_1937 world){this.world=(class_3218)world;}
 public boolean isLoaded(int x,int z){return world.loaded(x,z);}
 public class_2680 get0(int x,int y,int z){return world.cached(x,y,z);}
}
