package com.player2.playerengine.schematic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * The native transfer's completion flag is NOT a durable storage commitment.
 * Reconcile only on failed DEPOSIT / WITHDRAW postconditions. Chest contents can be
 * edited by the player without changing the schematic bill or ending the project.
 * This adapter is reflective because v18.3's coordinator is a binary-overlay class.
 */
public final class SchematicStorageReconciler {
    private static final int MAX_NO_PROGRESS_RETRIES = 3;
    private static final Map<Object,State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private SchematicStorageReconciler() {}

    private static final class Snapshot {
        final Map<String,Integer> stock;
        final Map<String,Integer> mergeRoom;
        final int freeSlots;
        Snapshot(Map<String,Integer> stock,Map<String,Integer> mergeRoom,int freeSlots) {
            this.stock = Map.copyOf(stock);
            this.mergeRoom = Map.copyOf(mergeRoom);
            this.freeSlots = freeSlots;
        }
        int count(String id) { return stock.getOrDefault(id,0); }
        int room(String id) { return mergeRoom.getOrDefault(id,0) + freeSlots * 64; }
    }
    private static final class State {
        int actionIdentity;
        Snapshot before;
        String failureKey = "";
        int lastCarried = -1, lastStock = -1, lastRoom = -1, noProgressRetries;
    }

    /** Capture the selected chest immediately before launching a native transfer. */
    public static void recordStart(Object project,Object action,List<?> depots) {
        try {
            String kind = String.valueOf(field(action,"kind"));
            if (!"DEPOSIT".equals(kind) && !"WITHDRAW".equals(kind)) return;
            int index = ((Number)field(action,"index")).intValue();
            if (index < 0 || index >= depots.size()) return;
            Snapshot before = snapshot(depots.get(index));
            synchronized (STATES) {
                State state = STATES.computeIfAbsent(project, unused -> new State());
                state.actionIdentity = System.identityHashCode(action);
                state.before = before;
            }
            emit(project,"SCHEMATIC_STORAGE_BASELINE","action="+kind+" depot="+index+
                " item="+field(action,"item")+" expectedStockTarget="+field(action,"stockTarget")+
                " expectedInventoryTarget="+field(action,"target")+" before="+describe(before));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            emit(project,"SCHEMATIC_STORAGE_DIAGNOSTIC_ERROR","baseline="+exception(ex));
        }
    }

    /**
     * Called only from an existing controlled schematic failure. Returns true when
     * a fresh authoritative scan justifies discarding the stale transfer and asking
     * the coordinator to replan on the next tick. Never claims the deposit succeeded.
     */
    public static boolean interceptBlock(Object project,String code,String detail) {
        if (!"DEPOSIT".equals(code) && !"WITHDRAW".equals(code)) return false;
        try {
            Object action = field(project,"action");
            if (action == null || !code.equals(String.valueOf(field(action,"kind")))) return false;
            Object environment = field(project,"environment");
            if (environment == null) return false;
            String item = String.valueOf(field(action,"item"));
            int index = ((Number)field(action,"index")).intValue();
            int expectedCarried = ((Number)field(action,"target")).intValue();
            int expectedStock = ((Number)field(action,"stockTarget")).intValue();
            Map<String,Integer> inventory = numericMap(invoke(environment,"inventory"));
            List<?> depots = (List<?>)invoke(environment,"depots");
            if (index < 0 || index >= depots.size()) return false;
            Snapshot current = snapshot(depots.get(index));
            int carried = inventory.getOrDefault(item,0);
            int stock = current.count(item);
            int room = current.room(item);

            String nativeDetail = "";
            Object child = field(action,"task");
            if (child != null) for (String method:List.of("outcome","code","failures","describeProgress")) {
                try {
                    Object value = invoke(child,method);
                    if (value != null) nativeDetail += " "+method+"="+truncate(String.valueOf(value),150);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }

            String key = code+"/"+item+"/"+index;
            int retry;
            Snapshot before;
            synchronized (STATES) {
                State state = STATES.computeIfAbsent(project, unused -> new State());
                before = state.actionIdentity == System.identityHashCode(action) ? state.before : null;
                if (!key.equals(state.failureKey)) {
                    state.failureKey = key;
                    state.noProgressRetries = 0;
                    state.lastCarried = -1;
                    state.lastStock = -1;
                    state.lastRoom = -1;
                }
                boolean forwardProgress = state.lastCarried < 0 || state.lastStock < 0 ||
                    ("DEPOSIT".equals(code) ? carried < state.lastCarried || stock > state.lastStock :
                                              carried > state.lastCarried || stock < state.lastStock);
                boolean moreRoom = state.lastRoom >= 0 && room > state.lastRoom;
                if (forwardProgress || moreRoom) state.noProgressRetries = 0;
                retry = ++state.noProgressRetries;
                state.lastCarried = carried;
                state.lastStock = stock;
                state.lastRoom = room;
            }
            String diagnostic = "action="+code+" item="+item+" depot="+index+
                " expectedCarried="+expectedCarried+" actualCarried="+carried+
                " expectedStock="+expectedStock+" actualStock="+stock+
                " actualFreeSlots="+current.freeSlots+" actualItemRoom="+room+
                " start="+(before==null?"unavailable":describe(before))+
                " now="+describe(current)+
                " changesSinceActionStart="+(before==null?"unavailable":delta(before.stock,current.stock))+
                " native="+truncate(nativeDetail,350)+" detail="+truncate(detail,400);
            if (retry > MAX_NO_PROGRESS_RETRIES) {
                emit(project,"SCHEMATIC_STORAGE_RECOVERY_EXHAUSTED",
                    "no-progress retries="+MAX_NO_PROGRESS_RETRIES+" "+diagnostic);
                return false;
            }
            emit(project,"SCHEMATIC_STORAGE_RECONCILE",
                "retry="+retry+"/"+MAX_NO_PROGRESS_RETRIES+" "+diagnostic);
            // The coordinator will rescan stock, reselect a depot, and launch a new child.
            setField(project,"action",null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            emit(project,"SCHEMATIC_STORAGE_DIAGNOSTIC_ERROR","rescan failed: "+exception(ex));
            return false;
        }
    }

    private static Snapshot snapshot(Object depot) throws ReflectiveOperationException {
        return new Snapshot(numericMap(invoke(depot,"stock")),numericMap(invoke(depot,"mergeRoom")),
            ((Number)invoke(depot,"freeSlots")).intValue());
    }
    private static Map<String,Integer> numericMap(Object value) {
        Map<String,Integer> result = new TreeMap<>();
        if (value instanceof Map<?,?> map) for (Map.Entry<?,?> e:map.entrySet()) {
            if (e.getKey()!=null && e.getValue() instanceof Number count)
                result.put(String.valueOf(e.getKey()),count.intValue());
        }
        return result;
    }
    private static String describe(Snapshot s) {
        return "{free="+s.freeSlots+",stock="+truncate(s.stock.toString(),950)+"}";
    }
    private static String delta(Map<String,Integer> before,Map<String,Integer> after) {
        TreeMap<String,Integer> changes = new TreeMap<>();
        for (String key:before.keySet()) {
            int difference = after.getOrDefault(key,0) - before.getOrDefault(key,0);
            if (difference!=0) changes.put(key,difference);
        }
        for (String key:after.keySet())
            if (!before.containsKey(key) && after.get(key)!=0) changes.put(key,after.get(key));
        return truncate(changes.toString(),800);
    }
    private static String truncate(String s,int max) { return s.length()<=max?s:s.substring(0,max)+"..."; }
    private static String exception(Throwable ex) { return ex.getClass().getSimpleName()+": "+ex.getMessage(); }
    private static void emit(Object project,String event,String message) {
        try { invoke(project,"event",event,message); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    private static Object field(Object object,String name) throws ReflectiveOperationException {
        Field f = locateField(object.getClass(),name);
        f.setAccessible(true);
        return f.get(object);
    }
    private static void setField(Object object,String name,Object value) throws ReflectiveOperationException {
        Field f = locateField(object.getClass(),name);
        f.setAccessible(true);
        f.set(object,value);
    }
    private static Field locateField(Class<?> type,String name) throws NoSuchFieldException {
        for (Class<?> c=type;c!=null;c=c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    private static Object invoke(Object object,String method,Object... args) throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[args.length];
        for (int i=0;i<args.length;i++) types[i]=args[i].getClass();
        for (Class<?> c=object.getClass();c!=null;c=c.getSuperclass()) {
            try {
                Method m=c.getDeclaredMethod(method,types);
                m.setAccessible(true);
                return m.invoke(object,args);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(object.getClass().getName()+"."+method);
    }
}
