package com.player2.playerengine.schematic;

import com.player2.playerengine.automaton.api.entity.LivingEntityPlacementContext;
import com.player2.playerengine.automaton.process.BuilderProcess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
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
    /** A physical chest placement that needs the same orientation checks as imports. */
    public interface OrientedChest {
        /** The existing half the new chest must join, or null for the first half. */
        BlockPos partner();
    }
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
    public static boolean imported(Object builder){try{return ours(get(builder,"schematic"))||ours(get(builder,"realSchematic"));}catch(Exception e){return false;}}
    public static boolean active(Object builder){try{return get(builder,"realSchematic") instanceof OrientedChest||imported(builder);}catch(Exception e){return false;}}
    public static void bindEntity(Object entity,Object builder){ENTITIES.put(entity,new WeakReference<>(builder));}
    public static boolean clearPlacementStance(BlockPos feet, BlockPos target, java.util.function.Predicate<BlockPos> unfinished) {
        BlockPos head = feet.above();
        BlockPos floor = feet.below();
        return !feet.equals(target) && !head.equals(target) && !floor.equals(target)
            && !unfinished.test(feet) && !unfinished.test(head) && !unfinished.test(floor);
    }
    public static boolean sameIgnoringChestJoin(Object a,Object b)throws Exception {
        if(!call(a,"method_26204").equals(call(b,"method_26204")))return false;
        Map<String,String> x=new HashMap<>(SchematicWorld.properties(a)),y=new HashMap<>(SchematicWorld.properties(b));x.remove("type");y.remove("type");return x.equals(y);
    }
    /** -1 retains every legacy builder rule; imported builds require the intended properties. */
    public static int valid(Object builder,Object current,Object desired){
        if(!imported(builder))return -1;
        return desired == null || matchesImportedPlacement((BlockState) current, (BlockState) desired) ? 1 : 0;
    }
    public static boolean matchesImportedPlacement(BlockState actual, BlockState target) {
        if (actual == null) return false;
        if (!target.is(Blocks.CHEST) && !target.is(Blocks.TRAPPED_CHEST)) return actual.equals(target);
        if (!actual.is(target.getBlock())) return false;
        ChestType join = actual.getValue(ChestBlock.TYPE);
        return actual.setValue(ChestBlock.TYPE, target.getValue(ChestBlock.TYPE)).equals(target)
            && (join == target.getValue(ChestBlock.TYPE) || join == ChestType.SINGLE);
    }

    public static BlockState importedTarget(LivingEntity entity, BlockPos pos) {
        WeakReference<Object> ref = ENTITIES.get(entity);
        if (ref != null && ref.get() instanceof BuilderProcess builder && imported(builder)) {
            return builder.placementTarget(pos);
        }
        return null;
    }

    public static boolean plannedChest(BlockState target, Block itemBlock) {
        return target != null && target.is(itemBlock) && (itemBlock == Blocks.CHEST || itemBlock == Blocks.TRAPPED_CHEST);
    }

    /** Check the exact chest orientation and join only for a ray-traced placement, not a hotbar probe. */
    public static boolean matchesChestPlacement(BlockState actual,BlockState target){
        return actual.is(Blocks.CHEST)&&target.is(Blocks.CHEST)
            &&actual.getValue(ChestBlock.FACING)==target.getValue(ChestBlock.FACING)
            &&actual.getValue(ChestBlock.TYPE)==target.getValue(ChestBlock.TYPE)
            &&actual.getValue(BlockStateProperties.WATERLOGGED).equals(target.getValue(BlockStateProperties.WATERLOGGED));
    }
    /**
     * Source-level 1.21.1 placement prediction, retaining the imported-schematic
     * living-entity placement context without a checked reflection exception.
     * The erased legacy method below stays unchanged for ABI compatibility.
     */
    public static BlockState predicted(Block block, BlockPlaceContext placement, Object builder) {
        try {
            if (!active(builder)) return block.getStateForPlacement(placement);
            Object entity = call(get(builder, "ctx"), "entity");
            if (!(entity instanceof LivingEntity living)) return null;
            LivingEntityPlacementContext.push(living);
            try {
                return block.getStateForPlacement(placement);
            } finally {
                LivingEntityPlacementContext.pop();
            }
        } catch (Exception e) {
            // A prediction failure is not permission to place an unverified block.
            return null;
        }
    }
    public static Object predicted(Object block,Object placement,Object builder)throws Exception {
        if(!active(builder))return call(block,"method_9605",placement);
        Object entity=call(get(builder,"ctx"),"entity");callStatic(CONTEXT,"push",entity);
        try{return call(block,"method_9605",placement);}catch(Exception e){return null;}finally{callStatic(CONTEXT,"pop");}
    }
    /**
     * Strongly-typed Minecraft 1.21.1 placement orientation for the active
     * imported-schematic context. Preserve the original Object overloads for
     * binary compatibility, but do not use reflective intermediary names here.
     */
    private static LivingEntity placementEntity(BlockPlaceContext placement) {
        if (placement.getPlayer() != null) return placement.getPlayer();
        LivingEntity entity = LivingEntityPlacementContext.peek();
        if (entity == null) return null;
        WeakReference<Object> ref = ENTITIES.get(entity);
        return ref != null && active(ref.get()) ? entity : null;
    }

    public static Direction horizontal(BlockPlaceContext placement) {
        LivingEntity entity = placementEntity(placement);
        if (entity == null) return Direction.NORTH;
        if (placement.getPlayer() == null && placement.getItemInHand().getItem() instanceof BlockItem item) {
            BlockState target = importedTarget(entity, placement.getClickedPos());
            if (plannedChest(target, item.getBlock())) return target.getValue(ChestBlock.FACING).getOpposite();
        }
        return entity.getDirection();
    }

    public static float rotation(BlockPlaceContext placement) {
        LivingEntity entity = placementEntity(placement);
        return entity == null ? 0.0F : entity.getYRot();
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
