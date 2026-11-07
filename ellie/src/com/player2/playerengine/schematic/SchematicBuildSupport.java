package com.player2.playerengine.schematic;

import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.util.*;
import static com.player2.playerengine.nativecommands.NativeAccess.*;
import static com.player2.playerengine.schematic.SchematicPlan.*;

/** Scoped adapters around the existing builder. Callbacks inspect immutable states, never chunks. */
public final class SchematicBuildSupport {
    private static final String CONTEXT="com.player2.playerengine.automaton.api.entity.LivingEntityPlacementContext";
    private static final Map<Object,WeakReference<Object>> ENTITIES=Collections.synchronizedMap(new WeakHashMap<>());
    private SchematicBuildSupport(){}
    private record Layer(int width,int length,Map<Long,Object> desired,Map<Long,Object> finals,Map<Long,State> specs) implements InvocationHandler {
        public Object invoke(Object proxy,Method method,Object[] a)throws Throwable {
            return switch(method.getName()){
                case "widthX"->width;case "heightY"->1;case "lengthZ"->length;
                case "inSchematic"->(int)a[0]>=0&&(int)a[0]<width&&(int)a[1]==0&&(int)a[2]>=0&&(int)a[2]<length;
                case "desiredState"->{
                    long key=((long)(int)a[0]<<32)|((int)a[2]&0xffffffffL);State spec=specs.get(key);Object current=a[3];
                    if(spec.water())yield current;
                    // Preserve a correct final state when resuming an open/waterlogged/double target.
                    Object target=desired.get(key);
                    if(current.equals(finals.get(key)))yield current;
                    yield target;
                }
                case "reset"->null;
                case "toString"->"Ellie imported layer "+width+"x"+length;
                case "hashCode"->System.identityHashCode(proxy);
                case "equals"->proxy==a[0];
                default->{if(method.isDefault())yield InvocationHandler.invokeDefault(proxy,method,a);throw new UnsupportedOperationException(method.toString());}
            };
        }
    }
    public static Object section(int width,int length,Map<Long,Object> desired,Map<Long,Object> finals,Map<Long,State> specs)throws Exception {
        Class<?> api=Class.forName("com.player2.playerengine.automaton.api.schematic.ISchematic");
        return Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},new Layer(width,length,Map.copyOf(desired),Map.copyOf(finals),Map.copyOf(specs)));
    }
    private static boolean ours(Object schematic){return schematic!=null&&Proxy.isProxyClass(schematic.getClass())&&Proxy.getInvocationHandler(schematic) instanceof Layer;}
    public static boolean active(Object builder){try{return ours(get(builder,"schematic"))||ours(get(builder,"realSchematic"));}catch(Exception e){return false;}}
    public static void bindEntity(Object entity,Object builder){ENTITIES.put(entity,new WeakReference<>(builder));}
    public static boolean sameIgnoringChestJoin(Object a,Object b)throws Exception {
        if(!call(a,"method_26204").equals(call(b,"method_26204")))return false;
        Map<String,String> x=new HashMap<>(SchematicWorld.properties(a)),y=new HashMap<>(SchematicWorld.properties(b));x.remove("type");y.remove("type");return x.equals(y);
    }
    /** -1 retains every legacy builder rule; imported builds require the intended properties. */
    public static int valid(Object builder,Object current,Object desired){
        if(!active(builder))return -1;
        try{
            if(desired==null||Objects.equals(current,desired))return 1;
            if(current==null)return 0;
            String block=String.valueOf(call(desired,"method_26204"));
            return block.endsWith(":chest}")&&sameIgnoringChestJoin(current,desired)?1:0;
        }catch(Exception e){return 0;}
    }
    public static Object predicted(Object block,Object placement,Object builder)throws Exception {
        if(!active(builder))return call(block,"method_9605",placement);
        Object entity=call(get(builder,"ctx"),"entity");callStatic(CONTEXT,"push",entity);
        try{return call(block,"method_9605",placement);}catch(Exception e){return null;}finally{callStatic(CONTEXT,"pop");}
    }
    private static Object contextEntity(Object placement)throws Exception {
        Object player=call(placement,"method_8036");if(player!=null)return player;
        Object entity=callStatic(CONTEXT,"peek");WeakReference<Object> ref=ENTITIES.get(entity);
        return ref!=null&&active(ref.get())?entity:null;
    }
    public static Object horizontal(Object placement)throws Exception {
        Object entity=contextEntity(placement);return entity==null?Class.forName("net.minecraft.class_2350").getField("field_11043").get(null):call(entity,"method_5735");
    }
    public static float rotation(Object placement)throws Exception {Object entity=contextEntity(placement);return entity==null?0:((Number)call(entity,"method_36454")).floatValue();}
}
