package com.player2.playerengine.reliability;

import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.lang.ref.WeakReference;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Quantity/progress and failure contracts for the existing food workers and FoodChain. */
public final class FoodAcquisition {
    public static final int NO_PROGRESS_TICKS = 2400;
    private static final String PREFIX = "com.player2.playerengine.";
    private FoodAcquisition() {}

    public static int validateRequested(int points) throws com.player2.playerengine.commands.base.CommandException {
        if(points<1||points>18432)throw new com.player2.playerengine.commands.base.CommandException(
                "Food quantity must be 1..18432 hunger points, not an item count; use get <food_item> <count> for exact items.");
        return points;
    }

    public static final class Progress {
        public final Map<Object,Integer> highWater = new LinkedHashMap<>();
        public final Set<String> failedFuel = new LinkedHashSet<>();
        public double bestScore = -1;
        public int idleTicks, activeTicks;
        public String failure;
        public void observe(double score, Map<Object,Integer> inventory) {
            boolean gain = score > bestScore;
            bestScore = Math.max(bestScore, score);
            for (var entry : inventory.entrySet()) {
                Integer old = highWater.putIfAbsent(entry.getKey(), entry.getValue());
                if (old != null && entry.getValue() > old) {
                    highWater.put(entry.getKey(), entry.getValue());
                    gain = true;
                }
                if (old == null && activeTicks > 0) gain = true;
            }
            activeTicks++;
            idleTicks = gain ? 0 : idleTicks + 1;
        }
    }

    public static Progress progress(Object root) throws Exception {
        Progress state = (Progress)get(root, "ellieFoodProgress");
        if (state == null) { state = new Progress(); set(root, "ellieFoodProgress", state); }
        return state;
    }

    public static Map<Object,Integer> inventory(Object controller) throws Exception {
        Object inventory = call(call(controller, "getEntity"), "getLivingInventory");
        Object main = get(inventory, "main");
        if (!(main instanceof List<?> slots) || slots.size() != 36)
            throw new IllegalStateException("Cannot verify the 36 carrying slots");
        Map<Object,Integer> counts = new LinkedHashMap<>();
        for (Object stack : slots) {
            if (stack == null) throw new IllegalStateException("Missing inventory slot");
            if (!Boolean.TRUE.equals(call(stack, "method_7960"))) {
                counts.merge(call(stack, "method_7909"), ((Number)call(stack, "method_7947")).intValue(), Integer::sum);
            }
        }
        return counts;
    }

    public static int count(Object controller, Object item) throws Exception {
        return inventory(controller).getOrDefault(item, 0);
    }

    public static int countNamed(Object controller, String name) throws Exception {
        Object matches = callStatic(PREFIX + "TaskCatalogue", "getItemMatches", name);
        Map<Object,Integer> owned = inventory(controller);
        int total = 0;
        for (int i=0; i<Array.getLength(matches); i++) total += owned.getOrDefault(Array.get(matches,i),0);
        return total;
    }

    public static int remaining(Object controller, Object target) throws Exception {
        Object matches = call(target, "getMatches");
        Map<Object,Integer> owned = inventory(controller);
        int held = 0;
        for (int i=0; i<Array.getLength(matches); i++) held += owned.getOrDefault(Array.get(matches,i),0);
        return Math.max(0, ((Number)call(target,"getTargetCount")).intValue() - held);
    }

    public static int nextRawCount(Object item, Object root) {
        try { return Math.addExact(count(get(root,"controller"),item),1); }
        catch (Exception | LinkageError failure) {
            block(root,"BLOCKED_FOOD_INVENTORY: " + failure.getMessage());
            return Integer.MAX_VALUE;
        }
    }

    /** Cooked targets are absolute totals; material counts are the raw items actually carried. */
    public static Object bestSmeltTarget(Object root) {
        try {
            Object controller = get(root,"controller");
            Map<Object,Integer> owned = inventory(controller);
            Object foods = get(root, isMeat(root) ? "COOKABLE_MEATS" : "COOKABLE_FOODS");
            for (int i=0; i<Array.getLength(foods); i++) {
                Object food = Array.get(foods,i), raw = call(food,"getRaw"), cooked = call(food,"getCooked");
                int rawCount = owned.getOrDefault(raw,0);
                if (rawCount == 0) continue;
                Object output = construct(PREFIX+"util.ItemTarget",cooked,owned.getOrDefault(cooked,0)+rawCount);
                Object input = construct(PREFIX+"util.ItemTarget",raw,rawCount);
                // Use the declared Item[] parameter, including modded Item subclasses.
                Class<?> type = Class.forName(PREFIX+"util.SmeltTarget");
                for (Constructor<?> ctor : type.getConstructors()) {
                    if (ctor.getParameterCount()==3 && ctor.getParameterTypes()[2].isArray()) {
                        Object empty = Array.newInstance(ctor.getParameterTypes()[2].getComponentType(),0);
                        return ctor.newInstance(output,input,empty);
                    }
                }
                throw new IllegalStateException("SmeltTarget constructor unavailable");
            }
            return null;
        } catch (Exception | LinkageError failure) {
            block(root,"BLOCKED_FOOD_INVENTORY: cannot plan cooking: " + failure.getMessage());
            return null;
        }
    }

    /** Prefer a real existing furnace; otherwise retain the original smoker acquire/place path. */
    public static Task cookingTask(Object target, Object root) {
        try {
            Object controller = get(root,"controller");
            Object furnace = Class.forName("net.minecraft.class_2246").getField("field_10181").get(null);
            Object blocks = Array.newInstance(Class.forName("net.minecraft.class_2248"),1);
            Array.set(blocks,0,furnace);
            Object nearest = call(call(controller,"getBlockScanner"),"getNearestBlock",blocks);
            String type = nearest instanceof Optional<?> found && found.isPresent()
                    ? "SmeltInFurnaceTask" : "SmeltInSmokerTask";
            Task task=(Task)construct(PREFIX+"tasks.container."+type,target);
            set(task,"ellieFoodOwner",new WeakReference<>(root));
            return task;
        } catch (Exception | LinkageError failure) {
            block(root,"BLOCKED_FOOD_STATION: cannot select cooking worker: " + failure.getMessage());
            return null;
        }
    }

    public static int remainingBatch(Object target, Object root) {
        try { return remaining(get(root,"controller"),call(target,"getItem")); }
        catch (Exception | LinkageError failure) {
            blockResource(root,"BLOCKED_COOKING_INVENTORY: " + failure.getMessage());
            return 0;
        }
    }

    public static int startingScore(Object controller, boolean meatOnly) {
        try { return (int)score(controller, meatOnly); }
        catch (Exception | LinkageError failure) { throw new IllegalStateException("Cannot verify starting food inventory",failure); }
    }

    public static double score(Object controller, boolean meatOnly) throws Exception {
        double total = 0;
        Object meats = Class.forName(PREFIX+"tasks.resources.CollectFoodTask").getField("COOKABLE_FOODS").get(null);
        for (var entry : inventory(controller).entrySet()) {
            if (meatOnly) {
                for (int i=0; i<Array.getLength(meats); i++) {
                    Object food = Array.get(meats,i);
                    if (entry.getKey().equals(call(food,"getCooked")))
                        total += entry.getValue()*((Number)call(food,"getCookedUnits")).doubleValue();
                }
            } else {
                Object food = callStatic(PREFIX+"multiversion.item.ItemVer","getFoodComponent",entry.getKey());
                if (food != null) total += entry.getValue()*((Number)call(food,"getHunger")).doubleValue();
            }
        }
        return total;
    }

    /** Entry to the existing onTick: child failure is terminal, not a request to recreate that child. */
    public static boolean before(Object root) {
        try {
            Progress state = progress(root);
            if (state.failure != null) return true;
            Object controller = get(root,"controller");
            String childFailure = descendantFailure(root);
            if (childFailure != null) { block(root,"BLOCKED_FOOD_DEPENDENCY: "+childFailure); return true; }
            double score = score(controller,isMeat(root));
            state.observe(score,inventory(controller));
            if (state.idleTicks >= NO_PROGRESS_TICKS) {
                String stage="NO_PROGRESS";
                Object dependency=get(root,"sub");
                for(int depth=0;dependency!=null&&depth<64;depth++,dependency=get(dependency,"sub")){
                    String kind=dependency.getClass().getSimpleName();
                    if(kind.equals("PlaceBlockNearbyTask")){stage="STATION_PLACEMENT";break;}
                    if(kind.equals("CraftInTableTask")||kind.equals("CraftMacroResourceTask"))stage="CRAFTING";
                }
                block(root,"BLOCKED_FOOD_"+stage+": no verified inventory gain for " + state.idleTicks
                        + " active ticks; target=" + get(root,"unitsNeeded") + " food_units; current=" + score
                        + "; subtask="+call(root,"getTaskTree"));
                return true;
            }
            if (state.activeTicks == 1 || state.activeTicks % 100 == 0) {
                event("FOOD_PROGRESS","objective=" + get(root,"unitsNeeded") + " food_units current="+score
                        + " inventory="+com.player2.playerengine.agentic.InventorySpace.inspect(controller).summary()
                        + " idle="+state.idleTicks+"/"+NO_PROGRESS_TICKS+" subtask="+call(root,"getTaskTree"));
            }
            return false;
        } catch (Exception | LinkageError failure) {
            block(root,"BLOCKED_FOOD_BOUNDARY: " + failure.getMessage());
            return true;
        }
    }

    private static String descendantFailure(Object root) throws Exception {
        Object node = get(root,"sub");
        for (int depth=0; node!=null && depth<64; depth++) {
            if (Boolean.TRUE.equals(call(node,"stopped"))) {
                try {
                    Object reason = call(node,"getAggregatedFailureReason");
                    if (reason instanceof Optional<?> present && present.isPresent()) return String.valueOf(present.get());
                } catch (NoSuchMethodException ignored) {}
            }
            Object next = get(node,"sub");
            if (next == node) break;
            node = next;
        }
        return null;
    }

    public static boolean terminal(Object root) {
        try { return progress(root).failure != null || successful(root); }
        catch (Exception | LinkageError unavailable) { return false; }
    }

    public static boolean successful(Object root) {
        try {
            if (progress(root).failure != null || get(root,"controller")==null) return false;
            return score(get(root,"controller"),isMeat(root)) >= ((Number)get(root,"unitsNeeded")).doubleValue();
        } catch (Exception | LinkageError unavailable) { return false; }
    }

    public static String reason(Object root) {
        try {
            String failure = progress(root).failure;
            return failure != null ? failure : successful(root) ? "Food goal verified in carrying inventory"
                    : "Food goal interrupted before completion";
        } catch (Exception | LinkageError unavailable) { return "Food state unavailable"; }
    }

    public static void block(Object root, String reason) {
        try {
            Progress state = progress(root);
            if (state.failure != null) return;
            state.failure = reason;
            event("FOOD_BLOCKED",reason);
            call(root,"stop");
        } catch (Exception | LinkageError failure) { throw new IllegalStateException("Cannot stop failed food work",failure); }
    }

    public static void blockResource(Object root, String reason) {
        try {
            invokeProtected(root,"recordFailureReason",reason);
            event("COOKING_BLOCKED",reason);
            call(root,"stop");
        } catch (Exception | LinkageError failure) { throw new IllegalStateException("Cannot stop failed cooking work",failure); }
    }

    public static void invokeProtected(Object root, String name, String value) throws Exception {
        for (Class<?> type=root.getClass(); type!=null; type=type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name,String.class);
                method.setAccessible(true);
                method.invoke(root,value);
                return;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }

    public static Object construct(String name, Object... arguments) throws Exception {
        for (Constructor<?> ctor : Class.forName(name).getConstructors()) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length!=arguments.length) continue;
            boolean matches = true;
            for (int i=0; i<types.length; i++) {
                if (arguments[i] == null) {
                    if (types[i].isPrimitive()) { matches=false; break; }
                    continue;
                }
                Class<?> type = boxed(types[i]);
                if (!type.isInstance(arguments[i])) { matches=false; break; }
            }
            if (matches) return ctor.newInstance(arguments);
        }
        throw new NoSuchMethodException(name+" constructor for "+java.util.Arrays.toString(
                java.util.Arrays.stream(arguments).map(a->a==null?"null":a.getClass().getTypeName()).toArray(String[]::new)));
    }

    private static Class<?> boxed(Class<?> type) {
        if (type==boolean.class) return Boolean.class;
        if (type==int.class) return Integer.class;
        if (type==long.class) return Long.class;
        if (type==double.class) return Double.class;
        if (type==float.class) return Float.class;
        if (type==short.class) return Short.class;
        if (type==byte.class) return Byte.class;
        if (type==char.class) return Character.class;
        return type;
    }

    private static boolean isMeat(Object root) { return root.getClass().getSimpleName().equals("CollectMeatTask"); }
    public static void event(String type, String detail) {
        String safe = detail==null ? "" : detail.replace('\n',' ').replace('\r',' ');
        com.player2.playerengine.observatory.ObservatoryTelemetry.event(type,safe);
    }
}
