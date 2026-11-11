package com.player2.playerengine.reliability;

import java.lang.reflect.Constructor;
import java.util.List;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Collision geometry at the existing navigator's support/feet boundaries. No movement owner. */
public final class PartialBlockSupport {
    private static final String P="com.player2.playerengine.automaton.";
    private static final double EPS=1.0e-5, THIN=0.125, MAX_RISE=1.25;
    private PartialBlockSupport() {}

    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}

    /** A horizontal landing at the node center, with a quarter-block-wide supporting patch.
     * Vertical sheets and shapes extending above the cell are not floor blocks. */
    public static double supportTop(List<Box> boxes) {
        double top=0;
        for(Box b:boxes) {
            if(b.maxY>1+EPS||b.minY < -EPS)return 0;
            if(b.minX<=0.375+EPS&&b.maxX>=0.625-EPS&&b.minZ<=0.375+EPS&&b.maxZ>=0.625-EPS)
                top=Math.max(top,b.maxY);
        }
        return top>THIN+EPS ? top : 0;
    }

    /** A face beneath the NPC defines its logical foot cell, including while hopping
     * over that face. Thin overlays retain the existing walk-through representation. */
    public static int footY(double y,double localX,double localZ,double halfWidth,List<Box> boxes) {
        int coarse=(int)Math.floor(y+0.1251);
        for(Box b:boxes) {
            if(b.maxY>1+EPS||b.maxY<=THIN+EPS)continue;
            if(coarse+b.maxY>y+0.003)continue;
            if(localX+halfWidth<=b.minX+EPS||localX-halfWidth>=b.maxX-EPS
                    ||localZ+halfWidth<=b.minZ+EPS||localZ-halfWidth>=b.maxZ-EPS)continue;
            return coarse+1;
        }
        return coarse;
    }

    public static boolean reachableRise(double fromTop,double toTop) {
        return fromTop<=0||toTop<=0||1+toTop-fromTop<=MAX_RISE+EPS;
    }

    public static boolean needsJump(double rise,double stepHeight) {
        return rise>stepHeight+0.01&&rise<=MAX_RISE+EPS;
    }
    /** Existing stair movement can approach its upper step before jumping. Do not use
     * only the center patch's lower height to reject that native ascent. */
    private static double highestTop(List<Box> boxes) {
        double top=0;
        for(Box b:boxes){if(b.maxY>1+EPS)return 0;top=Math.max(top,b.maxY);}
        return top;
    }

    private static Object position(int x,int y,int z)throws Exception {
        return Class.forName("net.minecraft.class_2338").getConstructor(int.class,int.class,int.class).newInstance(x,y,z);
    }
    private static Object view(Object world)throws Exception {
        for(Constructor<?> ctor:Class.forName(P+"utils.BlockStateInterface").getConstructors())
            if(ctor.getParameterCount()==1&&ctor.getParameterTypes()[0].isInstance(world))return ctor.newInstance(world);
        throw new NoSuchMethodException("Non-loading block view constructor");
    }
    private static List<Box> boxes(Object bsi,Object state,int x,int y,int z)throws Exception {
        Object shape=call(state,"method_26220",get(bsi,"access"),position(x,y,z));
        @SuppressWarnings("unchecked") List<Object> raw=(List<Object>)call(shape,"method_1090");
        var result=new java.util.ArrayList<Box>(raw.size());
        for(Object b:raw)result.add(new Box(number(b,"field_1323"),number(b,"field_1322"),number(b,"field_1321"),
                number(b,"field_1320"),number(b,"field_1325"),number(b,"field_1324")));
        return result;
    }
    private static double number(Object box,String field)throws Exception {return ((Number)get(box,field)).doubleValue();}

    /** Called only at native canWalkOn's final unsupported-block branch. Existing special
     * cases, slab settings, liquids, climbables and full blocks keep their native result. */
    public static boolean extraSupport(Object bsi,int x,int y,int z,Object state,Object settings) {
        try {
            if(!Boolean.TRUE.equals(call(bsi,"isLoaded",x,z)))return false;
            if(Boolean.TRUE.equals(callStatic(P+"pathing.movement.MovementHelper","avoidWalkingInto",state)))return false;
            Object avoid=get(settings,"blocksToAvoid");
            if(((java.util.Collection<?>)call(avoid,"get")).contains(call(state,"method_26204")))return false;
            // Reject vanilla dangerous partial blocks even where a horizontal face exists.
            String id=String.valueOf(call(state,"method_26204"));
            if(id.equals("Block{minecraft:campfire}")||id.equals("Block{minecraft:soul_campfire}")
                    ||id.equals("Block{minecraft:powder_snow}")||id.equals("Block{minecraft:pointed_dripstone}")
                    ||id.equals("Block{minecraft:big_dripleaf}")||id.equals("Block{minecraft:turtle_egg}"))return false;
            return supportTop(boxes(bsi,state,x,y,z))>0;
        }catch(Exception|LinkageError unavailable){return false;}
    }

    /** Water inside a solid partial block is not open swimming space. The native fluid
     * branch otherwise bypasses land collision checks for waterlogged stairs/fences. */
    public static boolean blocksWaterloggedBody(Object bsi,int x,int y,int z,Object state) {
        try {
            if(Boolean.TRUE.equals(call(call(state,"method_26227"),"method_15769")))return false;
            Object climbable=Class.forName("net.minecraft.class_3481").getField("field_22414").get(null);
            if(Boolean.TRUE.equals(call(state,"method_26164",climbable)))return false;
            if(!Boolean.TRUE.equals(call(bsi,"isLoaded",x,z)))return false;
            for(Box box:boxes(bsi,state,x,y,z))if(box.maxY>THIN+EPS)return true;
            return false;
        }catch(Exception|LinkageError unavailable){return true;}
    }

    /** Return only the corrected Y; the native hook constructs its own BetterBlockPos. */
    public static int feetY(Object ctx,double x,double y,double z) {
        int coarse=(int)Math.floor(y+0.1251);
        try {
            Object entity=call(ctx,"entity");
            Object world=call(ctx,"world");if(world==null)return coarse;
            int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
            Object bsi=view(world);
            if(!Boolean.TRUE.equals(call(bsi,"isLoaded",bx,bz)))return coarse;
            double halfWidth=((Number)call(entity,"method_17681")).doubleValue()/2;
            if(!(halfWidth>0&&halfWidth<=2))return coarse;
            // Physical feet do not depend on permissions to route ONTO a block. An NPC
            // already standing on an avoided slab must still be able to walk off it.
            // Its bounding box can also straddle a support/chunk edge.
            for(int sx=(int)Math.floor(x-halfWidth+EPS);sx<=(int)Math.floor(x+halfWidth-EPS);sx++)
                for(int sz=(int)Math.floor(z-halfWidth+EPS);sz<=(int)Math.floor(z+halfWidth-EPS);sz++){
                    if(!Boolean.TRUE.equals(call(bsi,"isLoaded",sx,sz)))continue;
                    Object state=call(bsi,"get0",sx,coarse,sz);
                    int corrected=footY(y,x-sx,z-sz,halfWidth,boxes(bsi,state,sx,coarse,sz));
                    if(corrected>coarse)return corrected;
                }
            return coarse;
        }catch(Exception|LinkageError unavailable){return coarse;}
    }

    /** Additional support shapes must not create an impossible 1.5-block ascend. Native
     * placement and unknown-data policy are retained when either support is absent. */
    public static boolean allowAscent(Object context,int x,int y,int z,int destX,int destZ) {
        try {
            Object bsi=get(context,"bsi");
            if(!Boolean.TRUE.equals(call(bsi,"isLoaded",x,z))||!Boolean.TRUE.equals(call(bsi,"isLoaded",destX,destZ)))return true;
            double from=highestTop(boxes(bsi,call(bsi,"get0",x,y-1,z),x,y-1,z));
            double to=highestTop(boxes(bsi,call(bsi,"get0",destX,y,destZ),destX,y,destZ));
            return reachableRise(from,to);
        }catch(Exception|LinkageError unavailable){return true;}
    }

    /** Reuse the native movement's own input state. A level grid edge can still rise more
     * than automatic step height (e.g. deep snow to a full block). Never jump for a
     * missing bridge floor, a paused/backing/sneaking movement, or an unverified rise. */
    public static void adjustStepInput(Object movement,Object state) {
        try {
            String kind=movement.getClass().getSimpleName();
            if(!kind.equals("MovementTraverse")&&!kind.equals("MovementDiagonal"))return;
            if(!String.valueOf(call(state,"getStatus")).equals("RUNNING"))return;
            Object src=get(movement,"src"),dest=get(movement,"dest");
            int sy=((Number)get(src,"y")).intValue(),dy=((Number)get(dest,"y")).intValue();
            if(sy!=dy)return;
            Class<?> input=Class.forName(P+"api.utils.input.Input");
            Object forward=input.getField("MOVE_FORWARD").get(null);
            @SuppressWarnings("unchecked") var inputs=(java.util.Map<Object,Object>)call(state,"getInputStates");
            if(!Boolean.TRUE.equals(inputs.get(forward)))return;
            if(Boolean.TRUE.equals(inputs.get(input.getField("SNEAK").get(null)))
                    ||Boolean.TRUE.equals(inputs.get(input.getField("MOVE_BACK").get(null))))return;
            Object ctx=get(movement,"ctx"),entity=call(ctx,"entity");
            if(!Boolean.TRUE.equals(call(entity,"method_24828")))return;
            int x=((Number)get(dest,"x")).intValue(),z=((Number)get(dest,"z")).intValue();
            double ex=((Number)call(entity,"method_23317")).doubleValue(),ez=((Number)call(entity,"method_23321")).doubleValue();
            if(Math.abs(ex-(x+0.5))>1.3||Math.abs(ez-(z+0.5))>1.3)return;
            Object bsi=view(call(ctx,"world"));if(!Boolean.TRUE.equals(call(bsi,"isLoaded",x,z)))return;
            Object support=call(bsi,"get0",x,dy-1,z),settings=call(call(ctx,"baritone"),"settings");
            if(!Boolean.TRUE.equals(callStatic(P+"pathing.movement.MovementHelper","canWalkOn",bsi,x,dy-1,z,support,settings)))return;
            double top=supportTop(boxes(bsi,support,x,dy-1,z));if(top<=0)return;
            double rise=dy-1+top-((Number)call(entity,"method_23318")).doubleValue();
            if(needsJump(rise,((Number)call(entity,"method_49476")).doubleValue()))
                call(state,"setInput",input.getField("JUMP").get(null),true);
        }catch(Exception|LinkageError unavailable){/* Retain native input decisions. */}
    }
}
