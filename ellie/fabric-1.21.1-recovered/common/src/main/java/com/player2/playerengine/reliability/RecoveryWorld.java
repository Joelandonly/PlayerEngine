package com.player2.playerengine.reliability;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard;
import java.lang.reflect.*;import java.util.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
/** Native navigation/interaction boundary. All movement and excavation use existing game APIs. */
final class RecoveryWorld {
 final Object controller,world,baritone,player;
 private static final String P="com.player2.playerengine.";
 record Cell(int x,int y,int z){String key(){return x+","+y+","+z;}Cell offset(int x,int y,int z){return new Cell(this.x+x,this.y+y,this.z+z);}}
 RecoveryWorld(Object controller)throws Exception{this.controller=controller;world=call(controller,"getWorld");baritone=call(controller,"getBaritone");player=call(controller,"getPlayer");}
 static Cell cell(Object p)throws Exception{return new Cell(((Number)call(p,"method_10263")).intValue(),((Number)call(p,"method_10264")).intValue(),((Number)call(p,"method_10260")).intValue());}
 static Object pos(Cell p)throws Exception{return Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(p.x,p.y,p.z);}
 double[] position()throws Exception{return new double[]{((Number)call(player,"method_23317")).doubleValue(),((Number)call(player,"method_23318")).doubleValue(),((Number)call(player,"method_23321")).doubleValue()};}
 long tick()throws Exception{return ((Number)call(world,"method_8510")).longValue();}
 String dimension()throws Exception{return String.valueOf(call(call(world,"method_27983"),"method_29177"));}
 boolean criticalAir()throws Exception{return ((Number)call(player,"method_5669")).intValue()<100&&Boolean.TRUE.equals(call(player,"method_5869"));}
 Object state(Cell p)throws Exception{return call(world,"method_8320",pos(p));}
 boolean air(Cell p)throws Exception{return Boolean.TRUE.equals(call(state(p),"method_26215"));}
 boolean fluid(Cell p)throws Exception{return !Boolean.TRUE.equals(call(call(state(p),"method_26227"),"method_15769"));}
 boolean within(Cell p)throws Exception{return Boolean.TRUE.equals(call(world,"method_22340",pos(p)))&&Boolean.TRUE.equals(call(world,"method_24794",pos(p)))&&Boolean.TRUE.equals(call(call(world,"method_8621"),"method_11952",pos(p)));}
 boolean solid(Cell p)throws Exception{if(!within(p)||fluid(p))return false;String block=String.valueOf(call(state(p),"method_26204"));if(List.of("magma_block","campfire","cactus","fire","powder_snow").stream().anyMatch(block::contains))return false;return Boolean.TRUE.equals(call(state(p),"method_26234",world,pos(p)));}
 boolean standable(Cell p)throws Exception{return within(p)&&within(p.offset(0,1,0))&&air(p)&&air(p.offset(0,1,0))&&!fluid(p)&&!fluid(p.offset(0,1,0))&&solid(p.offset(0,-1,0));}
 List<Cell> clickTargets(Cell target)throws Exception{
  if(!air(target))return List.of(target);List<Cell> anchors=new ArrayList<>();for(int[] d:new int[][]{{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0}}){Cell anchor=target.offset(d[0],d[1],d[2]);if(solid(anchor))anchors.add(anchor);}return anchors;
 }
 boolean interact(Cell target)throws Exception{for(Cell click:clickTargets(target))if(((Optional<?>)callStatic(P+"util.helpers.LookHelper","getReach",controller,pos(click))).isPresent())return true;return false;}
 Object ray(double x,double y,double z,Cell target)throws Exception{
  Class<?> vec=Class.forName("net.minecraft.class_243"),shape=Class.forName("net.minecraft.class_3959$class_3960"),fluids=Class.forName("net.minecraft.class_3959$class_242");
  Object from=vec.getConstructor(double.class,double.class,double.class).newInstance(x,y,z);
  Object to=vec.getConstructor(double.class,double.class,double.class).newInstance(target.x+.5,target.y+.5,target.z+.5);
  Class<?> entity=Class.forName("net.minecraft.class_1297");Object context=Class.forName("net.minecraft.class_3959").getConstructor(vec,vec,shape,fluids,entity).newInstance(from,to,shape.getField("field_17558").get(null),fluids.getField("field_1348").get(null),player);
  return call(world,"method_17742",context);
 }
 private boolean blockHit(Object hit)throws Exception{return call(hit,"method_17783")==Class.forName("net.minecraft.class_239$class_240").getField("field_1332").get(null);}
 String activeInputs(){try{Object inputs=call(baritone,"getInputOverrideHandler");List<String> active=new ArrayList<>();for(Object input:Class.forName(P+"automaton.api.utils.input.Input").getEnumConstants())if(Boolean.TRUE.equals(call(inputs,"isInputForcedDown",input)))active.add(String.valueOf(input));return active.toString();}catch(Exception|LinkageError unavailable){return "unavailable";}}
 String lookHit(){try{Object hit=call(call(baritone,"getEntityContext"),"objectMouseOver");if(hit==null)return "none";return blockHit(hit)?cell(call(hit,"method_17777")).key():String.valueOf(call(hit,"method_17783"));}catch(Exception unknown){return "unavailable";}}
 Cell blocking(Cell target)throws Exception{
  Object eye=call(player,"method_5836",1.0f);
  Object hit=ray(((Number)get(eye,"field_1352")).doubleValue(),((Number)get(eye,"field_1351")).doubleValue(),((Number)get(eye,"field_1350")).doubleValue(),target);
  if(!blockHit(hit))return null;
  Cell cell=cell(call(hit,"method_17777"));return cell.equals(target)?null:cell;
 }
 List<Cell> approaches(Cell target)throws Exception{
  List<Cell> cells=new ArrayList<>();double[] feet=position();
  Object ctx=call(baritone,"getEntityContext");double reach=((Number)call(call(ctx,"playerController"),"getBlockReachDistance")).doubleValue()-.2;
  List<Cell> clicks=clickTargets(target);
  for(int y=-1;y<=1;y++)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
   Cell p=target.offset(x,y,z);if(!standable(p))continue;
   double dx=p.x+.5-(target.x+.5),dy=p.y+1.62-(target.y+.5),dz=p.z+.5-(target.z+.5);
   if(dx*dx+dy*dy+dz*dz>reach*reach)continue;
   for(Cell click:clicks){double cx=p.x+.5-(click.x+.5),cy=p.y+1.62-(click.y+.5),cz=p.z+.5-(click.z+.5);if(cx*cx+cy*cy+cz*cz>reach*reach)continue;Object hit=ray(p.x+.5,p.y+1.62,p.z+.5,click);
   if(blockHit(hit)&&cell(call(hit,"method_17777")).equals(click)){cells.add(p);break;}}
  }
  cells.sort(Comparator.comparingDouble(p->Math.pow(p.x+.5-feet[0],2)+Math.pow(p.y-feet[1],2)+Math.pow(p.z+.5-feet[2],2)));return cells;
 }
 List<Cell> breathableExits()throws Exception{
  double[] at=position();Cell start=new Cell((int)Math.floor(at[0]),(int)Math.floor(at[1]),(int)Math.floor(at[2]));List<Cell> cells=new ArrayList<>();
  // Sideways candidates are considered as well as directly above an obstructed head.
  for(int dy=0;dy<=6;dy++)for(int r=0;r<=4;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
   if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;Cell p=start.offset(dx,dy,dz);
   if(within(p)&&air(p)&&air(p.offset(0,1,0))&&!fluid(p)&&!fluid(p.offset(0,1,0))&&(fluid(p.offset(0,-1,0))||solid(p.offset(0,-1,0))))cells.add(p);
  }return cells;
 }
 boolean safeClear(Cell p,Cell target)throws Exception{
  if(p==null||p.equals(target)||!within(p)||fluid(p)||call(world,"method_8321",pos(p))!=null)return false;
  double[] at=position();if(p.y<Math.floor(at[1])||Math.pow(p.x+.5-at[0],2)+Math.pow(p.y+.5-(at[1]+1.4),2)+Math.pow(p.z+.5-at[2],2)>9)return false;
  if(!VillageSiteGuard.allowResourceMining(controller,pos(p)))return false;
  Object store=callStatic(P+"structureprotection.PlayerPlacedBlockStore","get");if(Boolean.TRUE.equals(call(store,"contains",dimension(),pos(p)))||Boolean.TRUE.equals(call(store,"contains",dimension(),pos(p.offset(0,1,0)))))return false;
  String id=String.valueOf(call(state(p),"method_26204"));
  if(List.of("minecraft:stone}","minecraft:dirt}","minecraft:grass_block}","minecraft:cobblestone}","minecraft:deepslate}").stream().noneMatch(id::endsWith))return false;
  String above=String.valueOf(call(state(p.offset(0,1,0)),"method_26204"));
  if(List.of("sand","gravel","concrete_powder","anvil","dragon_egg","pointed_dripstone","crafting_table","furnace","chest","bed").stream().anyMatch(above::contains))return false;
  for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})if(fluid(p.offset(d[0],d[1],d[2])))return false;
  return ((Number)call(state(p),"method_26214",world,pos(p))).floatValue()>=0;
 }
 String progressSafely(Cell target)throws Exception{
  StringBuilder out=new StringBuilder(String.valueOf(state(target)));
  out.append(inventorySignature());return out.toString();
 }
 String inventorySignature(){
  // Aggregate item identity/count, not slot ordering or equipment state. Moving
  // stacks, consuming items and swapping tools cannot renew the watchdog.
  try{Object entity=call(controller,"getEntity"),inventory=call(entity,"getLivingInventory");
   Object main=get(inventory,"main");if(!(main instanceof List<?> stacks)||stacks.size()!=36)return "";
   Map<String,Integer> totals=new TreeMap<>();
   for(Object stack:stacks)if(stack!=null&&!Boolean.TRUE.equals(call(stack,"method_7960")))
    totals.merge(String.valueOf(call(stack,"method_7909")),((Number)call(stack,"method_7947")).intValue(),Integer::sum);
   StringBuilder out=new StringBuilder();totals.forEach((item,count)->out.append('|').append(item).append('=').append(count));return out.toString();
  }catch(Exception|LinkageError unavailable){return "";}
 }
 double breakingProgress(Cell target){
  try{Object manager=call(call(call(baritone,"getEntityContext"),"playerController"),"getInteractionManager");
   if(!Boolean.TRUE.equals(get(manager,"mining"))||!cell(get(manager,"miningPos")).equals(target))return 0;
   int elapsed=((Number)get(manager,"tickCounter")).intValue()-((Number)get(manager,"startMiningTime")).intValue()+1;
   return Math.min(1,elapsed*((Number)call(manager,"calcBlockBreakingDelta",state(target),player,world,pos(target))).doubleValue());
  }catch(Exception|LinkageError error){return 0;}
 }
 boolean acquireMovement()throws Exception{
  Object path=call(baritone,"getPathingBehavior");if(!Boolean.TRUE.equals(call(path,"isSafeToCancel")))return false;
  call(path,"cancelEverything");call(call(baritone,"getInputOverrideHandler"),"clearAllKeys");return true;
 }
 Object path(Cell destination)throws Exception{
  Object goal=Class.forName(P+"automaton.api.pathing.goals.GoalBlock").getConstructor(int.class,int.class,int.class).newInstance(destination.x,destination.y,destination.z);
  call(call(baritone,"getCustomGoalProcess"),"setGoalAndPath",goal);return goal;
 }
 void release(Object ownedGoal)throws Exception{
  Object custom=call(baritone,"getCustomGoalProcess");if(ownedGoal!=null&&call(custom,"getGoal")==ownedGoal)call(custom,"onLostControl");
 }
 boolean at(Cell p)throws Exception{double[] at=position();return Math.abs(at[0]-(p.x+.5))<.65&&Math.abs(at[1]-p.y)<.5&&Math.abs(at[2]-(p.z+.5))<.65;}
 Task clear(Cell p)throws Exception{return (Task)Class.forName(P+"tasks.construction.DestroyBlockTask").getConstructor(Class.forName("net.minecraft.class_2338")).newInstance(pos(p));}
}
