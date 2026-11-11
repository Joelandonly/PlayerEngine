package com.player2.playerengine.reliability;

import com.player2.playerengine.tasks.base.Task;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Failure suppression on the existing FoodChain, preserving its emergency-eating priority. */
public final class FoodSurvival {
    public static final class State {
        public WeakReference<Object> failedTask = new WeakReference<>(null);
        public WeakReference<Object> world = new WeakReference<>(null);
        public Map<Object,Integer> inventory = new LinkedHashMap<>();
        public double x, z;
        public String reason;

        public boolean changed(Map<Object,Integer> current, double nowX, double nowZ) {
            if (Math.pow(nowX-x,2)+Math.pow(nowZ-z,2)>=16*16) return true;
            for (var entry : current.entrySet()) {
                if (entry.getValue()>inventory.getOrDefault(entry.getKey(),0)) return true;
            }
            inventory=new LinkedHashMap<>(current);
            return false;
        }
    }
    private FoodSurvival() {}

    private static State state(Object chain) throws Exception {
        State state=(State)get(chain,"ellieFoodRecovery");
        if(state==null){state=new State();set(chain,"ellieFoodRecovery",state);}
        return state;
    }

    private static boolean food(Object task) {
        if(task==null)return false;
        String name=task.getClass().getSimpleName();
        return name.equals("CollectFoodTask")||name.equals("CollectMeatTask");
    }

    private static Map<Object,Integer> relevantInventory(Object controller) throws Exception {
        Map<Object,Integer> relevant=new LinkedHashMap<>();
        for(var entry:FoodAcquisition.inventory(controller).entrySet()){
            Object food=callStatic("com.player2.playerengine.multiversion.item.ItemVer","getFoodComponent",entry.getKey());
            boolean fuel=Boolean.TRUE.equals(callStatic("com.player2.playerengine.util.helpers.ItemHelper","isFuel",entry.getKey()));
            if(food!=null||fuel)relevant.put(entry.getKey(),entry.getValue());
        }
        return relevant;
    }

    /** Capture failure before FoodChain.getPriority replaces its finished mainTask. */
    public static void before(Object chain) {
        try {
            Object root=get(chain,"mainTask"),controller=get(chain,"controller");
            if(controller==null)return;
            State state=state(chain);
            Object world=call(controller,"getWorld");
            if(state.world.get()!=world){
                boolean replaced=state.world.get()!=null;
                state.world=new WeakReference<>(world);state.reason=null;state.failedTask.clear();
                if(replaced){state.failedTask=new WeakReference<>(root);return;}
            }
            if(!food(root)||state.failedTask.get()==root||!FoodAcquisition.terminal(root)||FoodAcquisition.successful(root))return;
            state.failedTask=new WeakReference<>(root);
            state.reason=FoodAcquisition.reason(root);
            state.inventory=relevantInventory(controller);
            Object entity=call(controller,"getEntity");
            state.x=((Number)call(entity,"method_23317")).doubleValue();
            state.z=((Number)call(entity,"method_23321")).doubleValue();
            FoodAcquisition.event("SURVIVAL_FOOD_BLOCKED",state.reason+"; unchanged automatic retry suppressed");
            call(controller,"reportAgenticProgress","Food gathering is blocked: "+state.reason
                    +". Give me food/fuel or move me to another resource area.",true);
        } catch(Exception|LinkageError failure) {
            FoodAcquisition.event("SURVIVAL_FOOD_BOUNDARY_UNAVAILABLE",failure.getClass().getSimpleName());
        }
    }

    public static float priority(Object chain,float nativePriority) {
        try {
            if(nativePriority<=0)return nativePriority;
            Object main=get(chain,"mainTask");
            if(!food(main))return nativePriority; // EatFoodTask and all other safety actions retain priority.
            Object controller=get(chain,"controller");
            Object user=call(controller,"getUserTaskChain"),requested=get(user,"mainTask");
            if(food(requested)&&!Boolean.TRUE.equals(call(requested,"stopped"))){
                call(chain,"setTask",(Object)null);
                return Float.NEGATIVE_INFINITY;
            }
            State state=state(chain);
            if(state.reason==null)return nativePriority;
            Object entity=call(controller,"getEntity");
            double x=((Number)call(entity,"method_23317")).doubleValue(),z=((Number)call(entity,"method_23321")).doubleValue();
            if(state.changed(relevantInventory(controller),x,z)){
                state.reason=null;
                FoodAcquisition.event("SURVIVAL_FOOD_RETRY","Inventory gain or relocation permits a new bounded food attempt");
                return nativePriority;
            }
            call(chain,"setTask",(Object)null);
            Object context=call(call(controller,"getBaritone"),"getEntityContext");
            int hunger=((Number)call(call(context,"hungerManager"),"getFoodLevel")).intValue();
            if(hunger<=6&&FoodAcquisition.score(controller,false)<=0&&requested instanceof Task task&&!task.stopped()){
                String reason="BLOCKED_SURVIVAL_FOOD: "+state.reason+"; owner objective cannot continue safely without food";
                SharedTargetRecovery.block(task,reason);
                FoodAcquisition.event("OWNER_GOAL_BLOCKED",reason);
            }
            return Float.NEGATIVE_INFINITY;
        } catch(Exception|LinkageError failure) {
            FoodAcquisition.event("SURVIVAL_FOOD_BOUNDARY_UNAVAILABLE",failure.getClass().getSimpleName());
            return nativePriority;
        }
    }
}
