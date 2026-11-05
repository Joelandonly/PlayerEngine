package com.player2.playerengine.observatory;

import java.lang.reflect.*;
import java.util.*;

/** Read-only server-clock-driven distance/tick/chunk diagnostics. Never modifies AI or chunks. */
public final class DistanceDiagnostics {
    private static final Map<Object, Sample> SAMPLES = new WeakHashMap<>();
    private static long serverClock = -1, lastSample = -1, lastCleanup = -1;
    private static boolean warned;
    private static final Map<String,Long> LAST_REGISTERED = new LinkedHashMap<>();
    private static final Set<String> MISSING_REPORTED = new HashSet<>();
    private static final class Sample {
        long lastController = -1, lastAgeAdvance = -1, lastEvent = -1, lastSeen;
        int lastAge = Integer.MIN_VALUE;
        String uuid = "unknown", state = "";
    }
    private DistanceDiagnostics() {}
    private static Object call(Object receiver, String method) throws ReflectiveOperationException {
        return receiver == null ? null : receiver.getClass().getMethod(method).invoke(receiver);
    }
    private static Object field(Object receiver, String name) throws ReflectiveOperationException {
        if(receiver==null)return null;
        Class<?> type=receiver.getClass();
        while(type!=null){
            try { Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(receiver); }
            catch(NoSuchFieldException ignored){type=type.getSuperclass();}
        }
        return null;
    }
    private static Double position(Object entity, String method){
        try{Object result=call(entity,method);return result instanceof Number n?n.doubleValue():null;}
        catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
    private static Integer age(Object entity){
        try{Object a=field(entity,"field_6012");return a instanceof Number n?n.intValue():null;}
        catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
    private static String ownerDistance(Object npc,Object owner){
        if(npc==null||owner==null)return "owner unavailable";
        try{if(call(npc,"method_37908")!=call(owner,"method_37908"))return "different dimension";}
        catch(ReflectiveOperationException|RuntimeException ex){return "world unavailable";}
        Double nx=position(npc,"method_23317"),ny=position(npc,"method_23318"),nz=position(npc,"method_23321");
        Double ox=position(owner,"method_23317"),oy=position(owner,"method_23318"),oz=position(owner,"method_23321");
        if(nx==null||ny==null||nz==null||ox==null||oy==null||oz==null)return "coordinates unavailable";
        return String.format(Locale.ROOT,"%.1f blocks",Math.sqrt(Math.pow(nx-ox,2)+Math.pow(ny-oy,2)+Math.pow(nz-oz,2)));
    }
    public static long packedChunk(int x,int z){return (x&0xffffffffL)|((z&0xffffffffL)<<32);}
    private static String forcedChunk(Object npc){
        if(npc==null)return "npc unavailable";
        try{
            Object world=call(npc,"method_37908");
            Object forcedSet=call(world,"method_17984"); // ServerWorld.getForcedChunks in Minecraft 1.21.1
            Double x=position(npc,"method_23317"), z=position(npc,"method_23321");
            if(forcedSet==null||x==null||z==null)return "unavailable";
            long key=packedChunk((int)Math.floor(x/16.0),(int)Math.floor(z/16.0));
            Object exists=forcedSet.getClass().getMethod("contains",long.class).invoke(forcedSet,key);
            return Boolean.TRUE.equals(exists)?"YES (vanilla forced set)":"NO (vanilla forced set)";
        }catch(ReflectiveOperationException|RuntimeException e){return "unavailable ("+e.getClass().getSimpleName()+")";}
    }
    private static String requestedChunks(Object controller){
        try{Object tracker=field(controller,"chunkLoader"), chunks=field(tracker,"chunks");
            return chunks instanceof Collection<?> c ? c.size()+" tracked" : "unavailable";
        }catch(ReflectiveOperationException|RuntimeException e){return "unavailable";}
    }
    private static String entityId(Object npc){
        try{Object id=call(npc,"method_5667");return id==null?"unknown":id.toString();}
        catch(ReflectiveOperationException|RuntimeException ignored){return "unknown";}
    }
    public static synchronized void onControllerTick(Object controller){
        if(controller!=null)SAMPLES.computeIfAbsent(controller,c->new Sample()).lastController=serverClock;
    }
    /** Called from the GLOBAL Minecraft server tick, not from the NPC's update callback. */
    public static void onServerTick(Object server){
        if(server==null)return;
        try{
            Object value=call(server,"method_3780");if(!(value instanceof Number n))return;
            long tick=n.longValue();
            synchronized(DistanceDiagnostics.class){
                serverClock=tick;
                if(lastSample>=0&&tick>=lastSample&&tick-lastSample<20)return;
                if(tick<lastSample){LAST_REGISTERED.clear();MISSING_REPORTED.clear();}
                lastSample=tick;
            }
            Object registry=Class.forName("com.player2.playerengine.PlayerEngineController").getField("staticControllers").get(null);
            if(!(registry instanceof Map<?,?> controllers))return;
            for(Object controller:new ArrayList<>(controllers.values()))if(controller!=null){
                // Read-only sample first, then repair only tickets the existing loader still owns.
                sample(controller,tick);
                ChunkTicketWatchdog.reconcile(controller,tick);
            }
            synchronized(DistanceDiagnostics.class){
                if(lastCleanup<0||tick<lastCleanup||tick-lastCleanup>=400){
                    SAMPLES.entrySet().removeIf(e->tick-e.getValue().lastSeen>1200);
                    lastCleanup=tick;
                }
            }
        }catch(ReflectiveOperationException|RuntimeException e){
            if(!warned){warned=true;ObservatoryTelemetry.event("DISTANCE_DIAGNOSTIC_UNAVAILABLE",e.toString());}
        }
    }
    /** Also supports fixture testing without a Minecraft server. */
    public static synchronized void sample(Object controller,long tick){
        if(controller==null)return;
        Sample s=SAMPLES.computeIfAbsent(controller,c->new Sample());s.lastSeen=tick;
        Object npc=null,owner=null;
        try{npc=call(controller,"getEntity");owner=call(controller,"getOwner");}
        catch(ReflectiveOperationException|RuntimeException ignored){}
        String uuid=entityId(npc);
        if(!uuid.equals("unknown")){ LAST_REGISTERED.put(uuid,tick); MISSING_REPORTED.remove(uuid); }
        if(!uuid.equals(s.uuid)){
            s.uuid=uuid;s.lastAge=Integer.MIN_VALUE;s.lastAgeAdvance=-1;s.lastController=-1;s.state="";
        }
        Integer age=age(npc);
        if(age!=null){
            if(s.lastAge==Integer.MIN_VALUE||s.lastAge!=age||s.lastAgeAdvance>tick)s.lastAgeAdvance=tick;
            s.lastAge=age;
        }
        String distance=ownerDistance(npc,owner), forced=forcedChunk(npc), requested=requestedChunks(controller);
        String ctrlLag=s.lastController<0?"never observed":Math.max(0,tick-s.lastController)+" ticks";
        String npcLag=age==null?"unavailable":s.lastAgeAdvance<0?"never observed":Math.max(0,tick-s.lastAgeAdvance)+" ticks";
        String ctrlState=s.lastController<0?"UNKNOWN":tick-s.lastController>=60?"STALLED":"TICKING";
        String npcState=age==null?"UNKNOWN":tick-s.lastAgeAdvance>=60?"STALLED":"TICKING";
        String state="controller="+ctrlState+" npc="+npcState;
        ObservatoryTelemetry.set("distanceOwner",distance);
        ObservatoryTelemetry.set("distanceControllerTick",ctrlLag+" ("+ctrlState+")");
        ObservatoryTelemetry.set("distanceNpcTick",npcLag+" ("+npcState+") age="+(age==null?"unavailable":age));
        ObservatoryTelemetry.set("distanceForcedChunk",forced);
        ObservatoryTelemetry.set("distanceRequestedChunks",requested);
        ObservatoryTelemetry.set("distanceServerTick",tick);
        ObservatoryTelemetry.set("distanceNpcUuid",uuid);
        if(!state.equals(s.state)||s.lastEvent<0||s.lastEvent>tick||tick-s.lastEvent>=400){
            ObservatoryTelemetry.event("DISTANCE_DIAGNOSTIC","npc="+uuid+" owner="+distance+" "+state+" controllerLag="+ctrlLag+" npcLag="+npcLag+" forced="+forced+" requested="+requested);
            s.lastEvent=tick;s.state=state;
        }
        if(tick%100==0)ObservatoryTelemetry.writeSnapshot();
    }
    private static synchronized void checkMissing(long tick,Set<String> active){
        LAST_REGISTERED.entrySet().removeIf(e->tick-e.getValue()>12000||e.getValue()>tick);
        MISSING_REPORTED.retainAll(LAST_REGISTERED.keySet());
        for(var entry:LAST_REGISTERED.entrySet()){
            String uuid=entry.getKey();long absent=tick-entry.getValue();
            if(active.contains(uuid)||absent<60||!MISSING_REPORTED.add(uuid))continue;
            ObservatoryTelemetry.set("distanceControllerTick","MISSING FROM REGISTRY for "+absent+" ticks");
            ObservatoryTelemetry.set("distanceNpcTick","unobservable (controller disappeared)");
            ObservatoryTelemetry.set("distanceForcedChunk","unknown (controller disappeared)");
            ObservatoryTelemetry.set("distanceNpcUuid",uuid);
            ObservatoryTelemetry.event("DISTANCE_CONTROLLER_MISSING","npc="+uuid+" absent="+absent+" server ticks; cannot sample entity/chunk without registered controller");
        }
    }
    public static void checkMissingForTest(long tick){checkMissing(tick,Collections.emptySet());}
    public static void appendNavigationLines(List<String> lines,Map<String,String> fields){
        lines.add("");lines.add("DISTANCE / ENTITY TICK DIAGNOSTICS:");
        append(lines,"OWNER DISTANCE",fields.get("distanceOwner"));
        append(lines,"NPC ENTITY TICK",fields.get("distanceNpcTick"));
        append(lines,"CONTROLLER TICK",fields.get("distanceControllerTick"));
        append(lines,"CURRENT CHUNK FORCED",fields.get("distanceForcedChunk"));
        append(lines,"TRACKER CHUNKS",fields.get("distanceRequestedChunks"));
        append(lines,"SERVER TICK",fields.get("distanceServerTick"));
        append(lines,"MAIN INVENTORY",fields.get("inventoryMainSlots"));
        lines.add("Test at 100 / 250 / 500 blocks away.");
        lines.add("A forced chunk does NOT prove NPC ticking.");
    }
    private static void append(List<String> lines,String key,String value){lines.add(key+": "+(value==null?"not sampled":value));}
    public static synchronized void noteControllerTickForTest(Object controller,long tick){
        SAMPLES.computeIfAbsent(controller,c->new Sample()).lastController=tick;
    }
}
