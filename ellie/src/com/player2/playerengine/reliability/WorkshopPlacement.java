package com.player2.playerengine.reliability;
import java.lang.reflect.Array;import java.util.function.Predicate;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
import com.player2.playerengine.reliability.RecoveryWorld.Cell;
/** Validate opportunistic workshop placement; retain native placement and navigation execution. */
public final class WorkshopPlacement {
 private static long warned;
 private WorkshopPlacement(){}
 public static boolean isTreeSupport(String id){return id.contains("leaves")||id.endsWith("_log}")||id.endsWith("_wood}")||id.endsWith("_stem}")||id.endsWith("_hyphae}");}
 private static boolean workshop(Object task)throws Exception{
  Object blocks=get(task,"toPlace");for(int i=0;i<Array.getLength(blocks);i++){
   String id=String.valueOf(Array.get(blocks,i));if(id.endsWith(":crafting_table}")||id.endsWith(":furnace}"))return true;
  }return false;
 }
 private static boolean floor(RecoveryWorld game,Cell p)throws Exception{return game.solid(p)&&!isTreeSupport(String.valueOf(call(game.state(p),"method_26204")));}
 private static boolean geometry(RecoveryWorld game,Cell p,boolean placing)throws Exception{
  if(!game.within(p)||!game.within(p.offset(0,1,0))||!floor(game,p.offset(0,-1,0))||!game.air(p.offset(0,1,0))||game.fluid(p)||game.fluid(p.offset(0,1,0)))return false;
  if(placing&&!game.air(p))return false;
  // The standing pose must remain usable after the workstation occupies its own cell.
  for(int dy:new int[]{0,-1,1})for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
   Cell side=p.offset(offset[0],dy,offset[1]);if(game.standable(side)&&floor(game,side.offset(0,-1,0)))return true;
  }
  return false;
 }
 public static boolean allows(Predicate<Object> original,Object position,Object task){return original.test(position)&&allowed(task,position);}
 public static boolean allowed(Object task,Object position){
  try{
   if(!workshop(task))return true;
   Object controller=get(task,"controller");RecoveryWorld game=new RecoveryWorld(controller);Cell p=RecoveryWorld.cell(position);double[] at=game.position();
   if(Math.abs(p.y()-Math.floor(at[1]))>1||Math.pow(p.x()+.5-at[0],2)+Math.pow(p.z()+.5-at[2],2)>25)return false;
   if(Math.abs(at[0]-(p.x()+.5))<.8&&Math.abs(at[2]-(p.z()+.5))<.8&&at[1]<p.y()+1&&at[1]+1.8>p.y())return false;
   return geometry(game,p,true);
  }catch(Exception|LinkageError error){warn(error);return false;}
 }
 /** Null preserves native reuse checks when a foreign boundary cannot be inspected. */
 public static Boolean reuseAllowed(Object controller,Object position){try{return geometry(new RecoveryWorld(controller),RecoveryWorld.cell(position),false);}catch(Exception|LinkageError unavailable){return null;}}
 private static void warn(Throwable error){long now=System.nanoTime();if(now-warned>10_000_000_000L){warned=now;ObservatoryTelemetry.event("WORKSHOP_PLACEMENT_UNAVAILABLE",error.getClass().getSimpleName()+": "+error.getMessage());}}
}
