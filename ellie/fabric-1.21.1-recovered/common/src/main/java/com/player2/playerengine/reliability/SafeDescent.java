package com.player2.playerengine.reliability;

import java.util.*;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** A downward-edge veto, not a navigator. Level traversal/bridging and ascent are unchanged. */
public final class SafeDescent {
 public static final int UNKNOWN=-1,AIR=0,SOLID=1,WATER=2,CLIMB=3,HAZARD=4;
 public static final double SAFE_DROP=3.0,IMPOSSIBLE=1000000.0;
 public record Surface(int kind,double top,String id){}
 public interface Probe {Surface at(int x,int y,int z)throws Exception;}
 private static final Map<Object,Long> logged=Collections.synchronizedMap(new WeakHashMap<>());
 private SafeDescent(){}
 /** null means allowed. Never inspect the void below a proposed same-height bridge. */
 public static String assess(double fromY,int x,int y,int z,Probe world)throws Exception {
  if(y>=fromY)return null;
  double drop=fromY-y;
  Surface feet=world.at(x,y,z);
  if(feet.kind()==UNKNOWN||feet.kind()==HAZARD)return "unsafe or unknown destination: "+feet.id();
  // Existing water is protection. Inventory water and speculative bucket placement are not.
  if(feet.kind()==WATER)return null;
  if(feet.kind()==CLIMB){
   if(drop<=SAFE_DROP)return null;
   if(drop>384)return "unbounded climb descent";
   for(int cy=y+1;cy<fromY;cy++)if(world.at(x,cy,z).kind()!=CLIMB)return "unprotected gap before climbable landing; drop="+drop;
   return null;
  }
  if(drop>SAFE_DROP)return "planned drop="+drop+" exceeds safe="+SAFE_DROP+"; landing="+feet.id()+"; no validated deployment protection";
  // A one-step destination can still be a lethal shaft after its floor is mined.
  // Ignore cells being excavated at/above destination; inspect its actual support.
  for(int cy=y-1;fromY-(cy+1)<=SAFE_DROP;cy--){
   Surface support=world.at(x,cy,z);
   if(support.kind()==UNKNOWN||support.kind()==HAZARD)return "unsafe or unknown landing support: "+support.id();
   if(support.kind()==WATER)return null;
   if(support.kind()==SOLID){double actual=fromY-(cy+support.top());return actual<=SAFE_DROP?null:"landing shape increases fall to "+actual;}
   if(support.kind()==CLIMB)return null;
  }
  return "no verified landing within safe drop="+SAFE_DROP;
 }
 private static int n(Object o,String name)throws Exception{return ((Number)get(o,name)).intValue();}
 private static Object pos(int x,int y,int z)throws Exception{return Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(x,y,z);}
 private static Surface surface(Object world,Object state,int x,int y,int z)throws Exception {
  String id=String.valueOf(call(state,"method_26204"));
  if(Set.of("Block{minecraft:lava}","Block{minecraft:magma_block}","Block{minecraft:campfire}","Block{minecraft:soul_campfire}","Block{minecraft:cactus}","Block{minecraft:fire}","Block{minecraft:soul_fire}","Block{minecraft:powder_snow}","Block{minecraft:pointed_dripstone}").contains(id))return new Surface(HAZARD,0,id);
  if(id.equals("Block{minecraft:water}")&&String.valueOf(state).contains("level=0"))return new Surface(WATER,0,id);
  if(id.equals("Block{minecraft:ladder}")||id.equals("Block{minecraft:vine}")||id.equals("Block{minecraft:scaffolding}"))return new Surface(CLIMB,0,id);
  Object shape=call(state,"method_26220",world,pos(x,y,z));
  if(Boolean.TRUE.equals(call(shape,"method_1110")))return new Surface(AIR,0,id);
  Object axis=Class.forName("net.minecraft.class_2350$class_2351").getField("field_11052").get(null);
  double top=((Number)call(shape,"method_1105",axis)).doubleValue();
  return new Surface(SOLID,top,id);
 }
 private static Probe planning(Object context)throws Exception {
  // Shape callbacks may read neighbors. Never give a path worker the live world:
  // native BSI.access serves loaded chunk data without a synchronous chunk fetch.
  Object access=get(get(context,"bsi"),"access");
  return (x,y,z)->Boolean.TRUE.equals(call(context,"isLoaded",x,z))?surface(access,call(context,"get",x,y,z),x,y,z):new Surface(UNKNOWN,0,"unloaded chunk");
 }
 /** Called after native Moves.apply and before A* accepts the candidate cost. */
 public static void filter(Object context,int fromY,Object result){
  try{if(((Number)get(result,"cost")).doubleValue()>=IMPOSSIBLE||n(result,"y")>=fromY)return;
   String failure=assess(fromY,n(result,"x"),n(result,"y"),n(result,"z"),planning(context));
   if(failure!=null){
    set(result,"cost",IMPOSSIBLE);
    report(context,"DESCENT_EDGE_REJECTED","fromY="+fromY+" dest="+n(result,"x")+","+n(result,"y")+","+n(result,"z")+" reason="+failure);
   }
  }catch(Exception|LinkageError e){
   try{set(result,"cost",IMPOSSIBLE);}catch(Exception ignored){}
   report(context,"DESCENT_FILTER_UNAVAILABLE","Downward candidate rejected: "+e.getClass().getSimpleName()+": "+e.getMessage());
  }
 }
 /** Called before Movement.update can excavate support or issue movement inputs. */
 public static boolean rejectMovement(Object movement){
  Object baritone=null;
  try{
   Object src=get(movement,"src"),dest=get(movement,"dest");int fromY=n(src,"y"),y=n(dest,"y");
   if(y>=fromY)return false;
   baritone=get(movement,"baritone");Object ctx=get(movement,"ctx"),entity=call(ctx,"entity"),world=call(ctx,"world");
   // Do not steal inputs from an emergency controller once a fall is already in progress.
   if(!Boolean.TRUE.equals(call(entity,"method_24828")))return false;
   double actualY=((Number)call(entity,"method_23318")).doubleValue();
   Probe live=(x,cy,z)->Boolean.TRUE.equals(call(world,"method_22340",pos(x,cy,z)))?surface(world,call(world,"method_8320",pos(x,cy,z)),x,cy,z):new Surface(UNKNOWN,0,"unloaded chunk");
   String failure=assess(Math.max(fromY,actualY),n(dest,"x"),y,n(dest,"z"),live);
   if(failure==null)return false;
   report(baritone,"DESCENT_REJECTED","src="+src+" dest="+dest+" reason="+failure+"; native path cancelled/replanned before movement");return true;
  }catch(Exception|LinkageError e){report(baritone,"DESCENT_REJECTED","Downward movement boundary unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage());return true;}
 }
 private static void report(Object owner,String event,String reason){
  long now=System.nanoTime();Long last=logged.get(owner);
  if(last==null||now-last>=5000000000L){logged.put(owner,now);ObservatoryTelemetry.set("descentSafety",reason);ObservatoryTelemetry.event(event,reason);}
 }
}
