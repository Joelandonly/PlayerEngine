package com.player2.playerengine.reliability;
import java.lang.ref.WeakReference;import java.nio.file.Path;import java.util.*;import java.security.MessageDigest;
import com.player2.playerengine.tasks.setup.CoreGoalJournal;import com.player2.playerengine.observatory.ObservatoryTelemetry;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
/** Per-project construction location and verified bench. Personal Baritone home is never modified. */
public final class WorksiteAnchor {
 private static final Map<Object,Site> sites=Collections.synchronizedMap(new WeakHashMap<>());
 private static final class Site {
  WeakReference<Object> owner,world;RecoveryWorld.Cell origin,bench;String dimension,blueprint;CoreGoalJournal journal;long validatedAt=Long.MIN_VALUE;boolean usable;
 }
 private WorksiteAnchor(){}
 public static void open(Object owner,Object controller,Object origin,String blueprint){
  try{
   RecoveryWorld world=new RecoveryWorld(controller);Site s=new Site();s.owner=new WeakReference<>(owner);s.world=new WeakReference<>(world.world);s.origin=RecoveryWorld.cell(origin);s.dimension=world.dimension();
   s.blueprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(blueprint.getBytes(java.nio.charset.StandardCharsets.UTF_8)));sites.put(controller,s);
   try{
    Object store=callStatic("com.player2.playerengine.tasks.deferred.DeferredJobStore","get");Path root=(Path)call(store,"worldRoot");String character=String.valueOf(call(call(controller,"getAIPersistantData"),"getCharacterId"));
    String identity=s.dimension+"|"+s.origin.key()+"|"+s.blueprint;String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,32);
    s.journal=CoreGoalJournal.open(root,character,"construction_worksite_"+key);String detail=s.journal.get("detail","");String[] saved=detail.split("\\|",-1);
    if(s.journal.get("resource","").equals(s.dimension)&&saved.length==3&&saved[0].equals(s.origin.key())&&saved[1].equals(s.blueprint)&&!saved[2].isEmpty()){
     String[] p=saved[2].split(",");s.bench=new RecoveryWorld.Cell(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));
    }
    save(s);
   }catch(Exception error){ObservatoryTelemetry.event("WORKSITE_SAVE_UNAVAILABLE","Active worksite kept in memory; persistence unavailable: "+error.getClass().getSimpleName());}
   ObservatoryTelemetry.set("worksite",s.dimension+" @ "+s.origin.key());ObservatoryTelemetry.event("WORKSITE_REGISTERED","Construction anchor "+s.origin.key()+" in "+s.dimension+"; personal home unchanged");
  }catch(Exception|LinkageError error){ObservatoryTelemetry.event("WORKSITE_UNAVAILABLE",error.getClass().getSimpleName()+": "+error.getMessage());}
 }
 private static void save(Site s){if(s.journal!=null){s.journal.put("WORKSITE_V1",0,s.dimension,s.origin.key()+"|"+s.blueprint+"|"+(s.bench==null?"":s.bench.key()));if(!s.journal.isPersistent())ObservatoryTelemetry.event("WORKSITE_SAVE_UNAVAILABLE","Worksite journal was not persisted");}}
 public static void recordBench(Object controller,Object position){
  try{Site s=sites.get(controller);if(s==null||s.world.get()!=call(controller,"getWorld"))return;RecoveryWorld.Cell p=RecoveryWorld.cell(position);
   if(p.equals(s.bench)||Math.abs(p.x()-s.origin.x())+Math.abs(p.y()-s.origin.y())+Math.abs(p.z()-s.origin.z())>48)return;
   RecoveryWorld game=new RecoveryWorld(controller);if(!String.valueOf(call(game.state(p),"method_26204")).contains(":crafting_table")||!game.interact(p))return;
   s.bench=p;s.validatedAt=Long.MIN_VALUE;save(s);ObservatoryTelemetry.event("WORKSITE_BENCH","Recorded currently reachable bench "+p.key()+"; future approaches will be revalidated");
  }catch(Exception|LinkageError error){ObservatoryTelemetry.event("WORKSITE_BENCH_UNAVAILABLE",error.getClass().getSimpleName());}
 }
 public static Optional<?> preferBench(Object controller,Optional<?> fallback){
  try{Site s=sites.get(controller);if(s==null||s.bench==null||s.world.get()!=call(controller,"getWorld"))return fallback;
   RecoveryWorld game=new RecoveryWorld(controller);Object pos=RecoveryWorld.pos(s.bench);if(SharedTargetRecovery.rejected(controller,pos))return fallback;
   double[] at=game.position();double radius=((Number)call(call(controller,"getModSettings"),"getCraftingTableReuseRadius")).doubleValue();
   if(radius<=0)return fallback;
   double distance=Math.pow(at[0]-s.bench.x(),2)+Math.pow(at[1]-s.bench.y(),2)+Math.pow(at[2]-s.bench.z(),2);
   if(distance>radius*radius||!String.valueOf(call(game.state(s.bench),"method_26204")).contains(":crafting_table"))return fallback;
   long now=game.tick();if(s.validatedAt==Long.MIN_VALUE||now<s.validatedAt||now-s.validatedAt>=20){s.usable=!game.approaches(s.bench).isEmpty();s.validatedAt=now;}
   if(!s.usable)return fallback;
   return Optional.of(pos);
  }catch(Exception|LinkageError error){return fallback;}
 }
 public static void close(Object owner){synchronized(sites){sites.entrySet().removeIf(e->e.getValue().owner.get()==owner);}}
}
