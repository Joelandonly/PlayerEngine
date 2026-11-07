package com.player2.playerengine.agentic;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.nativecommands.NativeAccess;
import java.lang.reflect.Array;
import java.util.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Bounded, server-thread-only bridge from native resource tasks to VERIFIED EllieGPS storage.
 * This module never moves the bot itself, never starts another runner, never trusts passive
 * sightings as inventory, and fails open to the original resource task on unavailable data.
 */
public final class StorageIntelligence {
 private StorageIntelligence() {}
 private static final String GPS="com.player2.playerengine.agentic.elliegps.";
 private static final String STORAGE="com.player2.playerengine.containeraccess.";
 private static final Map<Object,Heartbeat> HEARTBEATS=Collections.synchronizedMap(new WeakHashMap<>());
 private static long lastError;
 private static final Map<Object,Object> setupChests=Collections.synchronizedMap(new WeakHashMap<>());
 public static void setupChest(Object controller,Object pos){if(pos==null)setupChests.remove(controller);else setupChests.put(controller,pos);}
 public static boolean allowedSetupStorage(Object controller,Object pos){
  if(com.player2.playerengine.schematic.SchematicProjectTask.reservedSource(controller,pos))return false;Object allowed=setupChests.get(controller);if(allowed==null)return true;
  try{return Arrays.equals(coords(allowed),coords(pos));}catch(Exception invalid){return false;}
 }
 /** Fresh verified stock only; passive sightings never count as supplies. */
 public static int availableSetupItem(Object controller,String id){return setupStock(controller).getOrDefault("minecraft:"+id,0);}
 public static Map<String,Integer> setupStock(Object controller){
  try{
   Object position=setupChests.get(controller);if(position==null)return Map.of();
   Object world=call(controller,"getWorld");if(!Boolean.TRUE.equals(call(world,"method_22340",position))||!storageBlock(world,position))return Map.of();
   Object store=callStatic(GPS+"EllieGPSStore","get");Object record=call(store,"byPosition",dimension(controller),position);
   if(record==null||Boolean.TRUE.equals(get(record,"stale"))||!"inventory".equals(get(record,"type")))return Map.of();
   Object data=call(record,"inventoryData");if(data==null)return Map.of();Object snapshot=get(data,"snapshot");if(snapshot==null)return Map.of();
   if(!fresh(gameTime(controller),((Number)get(snapshot,"gameTime")).longValue(),FRESH_TICKS))return Map.of();
   Map<String,Integer> stock=new LinkedHashMap<>();
   for(Object item:(List<?>)get(snapshot,"items"))stock.merge(String.valueOf(call(item,"registryId")),Math.max(0,((Number)call(item,"count")).intValue()),Integer::sum);
   return Collections.unmodifiableMap(stock);
  }catch(Exception|LinkageError failure){warn("setup stock",failure);return Map.of();}
 }
 public static void refreshSetupChest(Object controller,Object pos){
  try{
   Object world=call(controller,"getWorld");if(!Boolean.TRUE.equals(call(world,"method_22340",pos))||!storageBlock(world,pos))return;
   double[] at={num(call(call(controller,"getPlayer"),"method_23317")),num(call(call(controller,"getPlayer"),"method_23318")),num(call(call(controller,"getPlayer"),"method_23321"))};
   if(squared(at[0],at[1],at[2],coords(pos))>64)return;
   Object mode=Class.forName(STORAGE+"ScanMode").getField("LIGHT").get(null);
   Object scan=callStatic(STORAGE+"ContainerScanService","scan",controller,pos,mode,null);
   if(Boolean.TRUE.equals(call(scan,"ok"))&&call(scan,"snapshot")!=null)remember(controller,call(scan,"snapshot"),"heuristic_pass");
  }catch(Exception|LinkageError e){warn("setup chest scan",e);}
 }
 private static final int MAX_WITHDRAW_ATTEMPTS=4,MAX_WAYPOINTS=160;
 private static final long FRESH_TICKS=6000;
 private static final double MAX_DISTANCE_SQUARED=96*96;
 private static final class Session { final Set<String> tried=new HashSet<>(); Object active; int attempts; long nextCheck; }
 private static final class Heartbeat { long last;long pressureLogged; int cursor; String lastMode=""; final Set<String> workstations=new HashSet<>(); }
 private record Choice(Object pos,Object item,String key,int amount,double distance,String id) {}

 /** Called only after the genuine UI-backed scan task reaches its terminal state. */
 public static void afterScan(Object task){
  if(task==null||!bool(task,"isFinished"))return;
  try{if(Boolean.TRUE.equals(get(task,"ellieAutonomyReported")))return;set(task,"ellieAutonomyReported",true);}catch(Exception missing){return;}
  try{
   Object mode=get(task,"mode"); // TARGETED is not a complete inventory snapshot.
   if(String.valueOf(mode).equals("TARGETED"))return;
   Object result=call(task,"result");if(!(result instanceof Optional<?> optional)||optional.isEmpty())return;
   remember(get(task,"controller"),optional.get(),"heuristic_pass");
  }catch(Exception|LinkageError failure){warn("scan",failure);}
 }
 /** Container transfer changes stock. Never leave the pre-transfer quantities as fresh. */
 public static void afterTransfer(Object task){
  if(task==null||!bool(task,"isFinished"))return;
  try{if(Boolean.TRUE.equals(get(task,"ellieAutonomyReported")))return;set(task,"ellieAutonomyReported",true);}catch(Exception missing){return;}
  try{
   Object controller=get(task,"controller"),pos=get(task,"targetPos");
   if(controller==null||pos==null)return;
   Object gps=callStatic(GPS+"EllieGPSStore","get");if(gps==null)return;
   String dim=dimension(controller);Object existing=call(gps,"byPosition",dim,pos);
   if(existing==null)return;
   call(gps,"markStale",String.valueOf(get(existing,"id")));
   event("STORAGE_INVALIDATED", "transfer ended; recheck "+key(dim,pos)+" outcome="+call(task,"outcome"));
   Object outcome=call(task,"outcome");
   if(outcome!=null&&!String.valueOf(outcome).equals("FAILED")&&!String.valueOf(outcome).equals("NOTHING")){
    // Read from the authoritative live world only after the existing transfer completed.
    // No extra walking, UI manipulation or assumed transfer success.
    Object mode=Class.forName(STORAGE+"ScanMode").getField("LIGHT").get(null);
    Object scanned=callStatic(STORAGE+"ContainerScanService","scan",controller,pos,mode,null);
    if(Boolean.TRUE.equals(call(scanned,"ok"))){Object snapshot=call(scanned,"snapshot");if(snapshot!=null)remember(controller,snapshot,"heuristic_pass");}
   }
  }catch(Exception|LinkageError failure){warn("transfer",failure);}
 }

 /** Injected into ResourceTask.onTick. Native collection remains the fallback/owner. */
 public static Task choose(Object root,Task nativeTask){
  Task choice=preferStoredResource(root);return choice==null?nativeTask:choice;
 }
 public static Task preferStoredResource(Object root){
  if(root==null)return null;
  try{
   Object controller=get(root,"controller");if(controller==null||bool(root,"isFinished"))return null;
   Session s=(Session)get(root,"ellieStorageSession");
   if(s==null){s=new Session();set(root,"ellieStorageSession",s);}
   if(s.active!=null){
    if(!bool(s.active,"isFinished"))return (Task)s.active;
    Object result=call(s.active,"outcome");event("STORAGE_WITHDRAW_RESULT",String.valueOf(result)+" attempt="+s.attempts+" task="+root.getClass().getSimpleName());
    s.active=null;
   }
   if(s.attempts>=MAX_WITHDRAW_ATTEMPTS)return null;
   long tick=gameTime(controller);if(s.nextCheck>tick)return null;s.nextCheck=tick+80;
   Object targets=call(root,"getItemTargets");if(targets==null||Array.getLength(targets)!=1)return null;
   Object target=Array.get(targets,0);if(target==null)return null;
   Object matches=call(target,"getMatches");if(matches==null||Array.getLength(matches)==0)return null;
   Object inventory=call(controller,"getItemStorage");
   int required=((Number)call(target,"getTargetCount")).intValue();
   if(required<1)return null;
   Object store=callStatic(GPS+"EllieGPSStore","get");if(store==null)return null;
   List<?> records=(List<?>)call(store,"publishedRecords");if(records==null||records.size()>MAX_WAYPOINTS)return null;
   String dim=dimension(controller);long now=gameTime(controller);
   Object player=call(controller,"getPlayer");if(player==null)return null;
   Object vec=call(player,"method_19538");
   double px=num(get(vec,"field_1352")),py=num(get(vec,"field_1351")),pz=num(get(vec,"field_1350"));
   Object world=call(controller,"getWorld");if(world==null)return null;
   Choice best=null;
   for(Object record:records){
    if(record==null||Boolean.TRUE.equals(get(record,"stale"))||!"inventory".equals(get(record,"type"))||!dim.equals(get(record,"dimension")))continue;
    Object data=call(record,"inventoryData");if(data==null)continue;
    Object snapshot=get(data,"snapshot");if(snapshot==null)continue; // a passive sighting is NOT a stock count
    long when=((Number)get(snapshot,"gameTime")).longValue();
    if(!fresh(now,when,FRESH_TICKS))continue;
    Object pos=call(record,"canonicalBlockPos");if(pos==null||!Boolean.TRUE.equals(call(world,"method_22340",pos)))continue;
    if(!storageBlock(world,pos))continue;
    if(!allowedSetupStorage(controller,pos))continue;
    String place=key(dim,pos);if(s.tried.contains(place))continue;
    int[] xyz=coords(pos);double distance=squared(px,py,pz,xyz);if(distance>MAX_DISTANCE_SQUARED)continue;
    List<?> items=(List<?>)get(snapshot,"items");if(items==null)continue;
    for(int mi=0;mi<Array.getLength(matches);mi++){
     Object item=Array.get(matches,mi);if(item==null)continue;
     String itemId=itemId(item);if(itemId==null)continue;
     int own=countInventory(inventory,item);int missing=required-own;if(missing<=0)return null;
     int stored=0;for(Object entry:items)if(itemId.equals(call(entry,"registryId")))stored+=((Number)call(entry,"count")).intValue();
     if(stored<=0)continue;
     int amount=Math.min(missing,Math.min(stored,64));
     if(best==null||distance<best.distance())best=new Choice(pos,item,place,amount,distance,itemId);
    }
   }
   if(best==null)return null;
   s.tried.add(best.key());s.attempts++;
   Object transfer=callStatic("com.player2.playerengine.tasks.container.BoundedContainerTransferTask","withdrawUpTo",best.pos(),best.item(),best.amount());
   s.active=transfer;
   event("STORAGE_SELECTED",best.id()+" x"+best.amount()+" from "+best.key()+" remaining_at_goal="+required+" attempt="+s.attempts+"/"+MAX_WITHDRAW_ATTEMPTS);
   return (Task)transfer;
  }catch(Exception|LinkageError failure){warn("resource",failure);return null;}
 }
 private static int countInventory(Object inventory,Object item)throws Exception{
  Class<?> type=item.getClass();Class<?> base=Class.forName("net.minecraft.class_1792");
  Object array=Array.newInstance(base,1);Array.set(array,0,item);
  return ((Number)call(inventory,"getItemCountInventoryOnly",array)).intValue();
 }
 private static String itemId(Object item){
  try{Class<?> registries=Class.forName("net.minecraft.class_7923");Object reg=registries.getField("field_41178").get(null);return String.valueOf(call(reg,"method_10221",item));}
  catch(Exception|LinkageError unavailable){return null;}
 }
 private static boolean storageBlock(Object world,Object pos)throws Exception{
  Object state=call(world,"method_8320",pos);String name=String.valueOf(call(state,"method_26204"));
  return name.contains("chest")||name.contains("barrel")||name.contains("shulker_box");
 }
 private static double squared(double x,double y,double z,int[] at){double dx=x-at[0]-.5,dy=y-at[1]-.5,dz=z-at[2]-.5;return dx*dx+dy*dy+dz*dz;}
 private static int[] coords(Object pos)throws Exception{return new int[]{((Number)call(pos,"method_10263")).intValue(),((Number)call(pos,"method_10264")).intValue(),((Number)call(pos,"method_10260")).intValue()};}
 private static String key(String dim,Object pos)throws Exception{return dim+":"+Arrays.toString(coords(pos));}
 private static double num(Object n){return ((Number)n).doubleValue();}
 private static boolean bool(Object task,String method){try{return Boolean.TRUE.equals(call(task,method));}catch(Exception ignored){return false;}}
 private static String dimension(Object controller)throws Exception{return String.valueOf(call(call(call(controller,"getWorld"),"method_27983"),"method_29177"));}
 private static long gameTime(Object controller)throws Exception{return ((Number)call(call(controller,"getWorld"),"method_8510")).longValue();}
 public static boolean fresh(long now,long sample,long ttl){return now>=sample&&now-sample<=ttl;}
 private static void remember(Object controller,Object snapshot,String origin)throws Exception{
  if(controller==null||snapshot==null)return;
  Object store=callStatic(GPS+"EllieGPSStore","get");if(store==null)return;
  Object pos=call(snapshot,"canonicalPos");String dim=String.valueOf(call(snapshot,"dimensionId"));
  Object prior=call(store,"byPosition",dim,pos);
  Object next=callStatic(GPS+"WaypointIngestionService","ingest",controller,snapshot,prior==null?origin:get(prior,"origin"),prior);
  Object outcome=call(store,"upsert",next);
  event("STORAGE_SNAPSHOT",key(dim,pos)+" entries="+((List<?>)call(snapshot,"aggregate")).size()+" result="+outcome);
 }

 /** Bounded passive discovery; no inventory read, no movement or commands. */
 public static void heartbeat(Object controller){
  if(controller==null)return;
  try{
   Object world=call(controller,"getWorld"),player=call(controller,"getPlayer");if(world==null||player==null)return;
   long now=gameTime(controller);
   Heartbeat h=HEARTBEATS.computeIfAbsent(controller,ignored->new Heartbeat());
   if(h.last>now)h.last=0; // world time resets should not disable discovery
   if(now-h.last<80)return;
   h.last=now;
   Object snapshot=callStatic("com.player2.playerengine.supervisor.CoreGoalSupervisor","snapshot",controller);
   String mode="IDLE";
   if(snapshot!=null){String status=String.valueOf(call(snapshot,"status"));mode=switch(status){case "RUNNING"->"WORKING";case "SUSPENDED"->"WAITING";case "BLOCKED","FAILED"->"BLOCKED";case "SUCCEEDED"->"COMPLETE";default->"IDLE";};}
   if(!mode.equals(h.lastMode)){h.lastMode=mode;event("AUTONOMY_STATE","mode="+mode+" active_goal="+(snapshot==null?"none":String.valueOf(call(snapshot,"kind"))));}
   // Actual 36-slot NPC inventory, independently checked against the repaired tracker.
   InventorySpace.Snapshot slots=InventorySpace.inspect(controller);
   InventorySpace.publish(controller);
   if(slots.known() && slots.free()==0 && now-h.pressureLogged>800){
    h.pressureLogged=now;event("INVENTORY_PRESSURE", "Inventory ACTUALLY full: "+slots.summary()+"; items retained; safe storage policy still required.");
   }
   Object gps=callStatic(GPS+"EllieGPSStore","get");if(gps==null)return;
   Object vec=call(player,"method_19538");int x=(int)Math.floor(num(get(vec,"field_1352"))),y=(int)Math.floor(num(get(vec,"field_1351"))),z=(int)Math.floor(num(get(vec,"field_1350")));
   String dim=dimension(controller);
   List<?> known=(List<?>)call(gps,"publishedRecords");
   if(known!=null){int seen=0;for(Object record:known){
    if(seen++>=12)break;
    if(record==null||Boolean.TRUE.equals(get(record,"stale"))||!dim.equals(get(record,"dimension"))||!"inventory".equals(get(record,"type")))continue;
    Object at=call(record,"canonicalBlockPos");int[] xyz=coords(at);
    if(Math.abs(xyz[0]-x)>5||Math.abs(xyz[1]-y)>3||Math.abs(xyz[2]-z)>5||!Boolean.TRUE.equals(call(world,"method_22340",at)))continue;
    if(!storageBlock(world,at)){
     call(gps,"markStale",String.valueOf(get(record,"id")));
     event("STORAGE_INVALIDATED",key(dim,at)+" container no longer exists");
    }
   }}
   // Visit a rotating part of a small 9x5x9 volume; no full-world or chunk search.
   for(int i=0;i<20;i++){
    int index=(h.cursor++)%(9*5*9),dx=index%9-4,dy=(index/9)%5-2,dz=index/45-4;
    Object pos=Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(x+dx,y+dy,z+dz);
    if(!Boolean.TRUE.equals(call(world,"method_22340",pos)))continue;
    String block=String.valueOf(call(call(world,"method_8320",pos),"method_26204"));
    if(block.contains("crafting_table")||block.contains("furnace")||block.contains("smoker")||block.contains("blast_furnace")){
     String k=key(dim,pos);if(h.workstations.add(k)){event("WORKSTATION_DISCOVERED",k+" kind="+block);if(h.workstations.size()>256)h.workstations.clear();}
    }
    if(!storageBlock(world,pos))continue;
    // The container resolver knows which half of a double chest is canonical.
    // Do not register duplicate addresses or invent remote inventory contents.
    Object resolution=callStatic(STORAGE+"ContainerResolver","resolve",world,pos);
    if(!Boolean.TRUE.equals(call(resolution,"ok")))continue;
    Object resolved=call(resolution,"resolved");if(resolved==null)continue;
    Object canonical=call(resolved,"canonicalPos");if(canonical==null)continue;
    Object prior=call(gps,"byPosition",dim,canonical);
    if(prior!=null)continue;
    Object record=Class.forName(GPS+"WaypointRecord").getConstructor().newInstance();
    set(record,"id",callStatic(GPS+"WaypointRecord","idFor",dim,canonical));set(record,"type","inventory");set(record,"dimension",dim);set(record,"pos",coords(canonical));
    set(record,"description","Discovered nearby storage (contents unverified)");set(record,"keywords",List.of("storage","unscanned"));set(record,"origin","heuristic_pass");
    set(record,"createdGameTime",now);set(record,"updatedGameTime",now);
    Object inventoryData=Class.forName(GPS+"InventoryWaypointData").getConstructor().newInstance();set(inventoryData,"containerKind",block.contains("barrel")?"barrel":"chest");
    Object secondary=call(resolved,"secondaryPos");if(secondary!=null)set(inventoryData,"secondaryPos",coords(secondary));
    set(record,"data",inventoryData);
    Object result=call(gps,"upsert",record);
    event("STORAGE_DISCOVERED",key(dim,canonical)+" contents=unknown status="+result);
   }
  }catch(Exception|LinkageError failure){warn("heartbeat",failure);}
 }
 private static void event(String action,String detail){System.out.println("[EllieAutonomy] "+action+": "+detail);try{callStatic("com.player2.playerengine.observatory.ObservatoryTelemetry","event",action,detail);}catch(Exception|LinkageError ignored){}}
 private static void warn(String context,Throwable e){long now=System.currentTimeMillis();if(now-lastError>30_000){lastError=now;event("AUTONOMY_DEGRADED",context+" "+e.getClass().getSimpleName()+" (native fallback retained)");}}
}
