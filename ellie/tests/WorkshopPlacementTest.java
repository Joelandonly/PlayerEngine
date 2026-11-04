package com.player2.playerengine.reliability;
import net.minecraft.class_2338;
/** Native world/pathing boundaries reused from the shared-runtime harness. */
public final class WorkshopPlacementTest {
 static int cases;
 public static final class Nearby{public final Object controller;public final Object[] toPlace;Nearby(Object controller,String id){this.controller=controller;toPlace=new Object[]{new SharedRecoveryRuntimeTest.Block(id)};}}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);cases++;}
 public static void main(String[] args){
  var c=new SharedRecoveryRuntimeTest.Controller();Object table=new Nearby(c,"minecraft:crafting_table");var p=new class_2338(2,64,0);var floor=new class_2338(2,63,0);var head=new class_2338(2,65,0);
  check(WorkshopPlacement.allowed(table,p),"nearby supported ground with a standing pose should allow a bench");
  check(!WorkshopPlacement.allows(x->false,p,table),"original caller placement restriction must remain authoritative");
  c.world.blocks.put(floor,"minecraft:oak_leaves");check(!WorkshopPlacement.allowed(table,p),"leaf canopy is not a workshop floor");
  c.world.blocks.put(floor,"minecraft:dark_oak_log");check(!WorkshopPlacement.allowed(table,p),"a tree trunk top is not a workshop floor");c.world.blocks.remove(floor);
  c.world.blocks.put(head,"minecraft:stone");check(!WorkshopPlacement.allowed(table,p),"blocked headroom must be rejected");c.world.blocks.remove(head);
  check(!WorkshopPlacement.allowed(table,new class_2338(2,66,0)),"opportunistic placement must not move the station two blocks above the NPC");
  check(!WorkshopPlacement.allowed(table,new class_2338(0,64,0)),"visible body overlap near block edges must not be a placement candidate");
  c.world.blocks.put(p,"minecraft:water");check(!WorkshopPlacement.allowed(table,p),"workstation body must be dry clear air");c.world.blocks.remove(p);
  for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int y:new int[]{64,65})c.world.blocks.put(new class_2338(2+d[0],y,d[1]),"minecraft:stone");
  check(!WorkshopPlacement.allowed(table,p),"a supported block with no usable adjacent standing pose is not a workshop site");
  check(WorkshopPlacement.allowed(new Nearby(c,"minecraft:cobblestone"),p),"unrelated nearby building retains its original placement policy");
  var plain=new SharedRecoveryRuntimeTest.Controller();check(WorkshopPlacement.allowed(new Nearby(plain,"minecraft:furnace"),p),"native furnace placement uses the same dry accessible ground rules");
  check(Boolean.TRUE.equals(WorkshopPlacement.reuseAllowed(plain,new class_2338(5,64,0))),"existing supported bench remains reusable");
  plain.world.blocks.put(new class_2338(5,63,0),"minecraft:spruce_leaves");check(Boolean.FALSE.equals(WorkshopPlacement.reuseAllowed(plain,new class_2338(5,64,0))),"existing canopy bench must not be preferred as the workshop");
  check(WorkshopPlacement.reuseAllowed(new Object(),p)==null,"unknown foreign reuse boundary defers to native validation");
  System.out.println("WorkshopPlacementTest: "+cases+" checks passed; native world boundaries substituted, real placement not executed");
 }
}
