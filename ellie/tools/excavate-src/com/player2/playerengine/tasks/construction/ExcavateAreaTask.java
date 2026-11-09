package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.tasks.base.*;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.schematic.SchematicDepotGuard;
import com.player2.playerengine.schematic.SchematicWorld;
import com.player2.playerengine.schematic.SchematicPlan.Pos;
import java.util.Objects;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/**
 * Owner-selected finite excavation: the existing NPC Baritone builder does
 * the digging. Before it starts, reject unloaded chunks, fluids, storage/
 * block entities and persistent schematic depots. Never drop inventory items.
 *
 * This is an explicit user goal. It may supersede a previous schematic goal
 * through the normal ProjectTaskGateway, but it cannot silently run inside
 * an active schematic as an independent planner.
 */
public final class ExcavateAreaTask extends Task implements TrackedTaskOutcomeProvider {
    private static final int MAX_VOLUME=32768, SCAN_PER_TICK=128;
    private final int x1,y1,z1,x2,y2,z2,volume;
    private Object world,builder,ownedSchematic;
    private int scan,verifyScan,remaining,airCount,noWorkTicks,buildTicks;
    private boolean building,complete,success,terminal;
    private String reason="Preflight pending";

    public ExcavateAreaTask(int ax,int ay,int az,int bx,int by,int bz){
        int[] n=normalize(ax,ay,az,bx,by,bz);
        x1=n[0];y1=n[1];z1=n[2];x2=n[3];y2=n[4];z2=n[5];volume=n[6];
    }
    /** Pure validation: inclusive rectangular bounds, bounded workload. */
    public static int[] normalize(int ax,int ay,int az,int bx,int by,int bz){
        int lx=Math.min(ax,bx),ly=Math.min(ay,by),lz=Math.min(az,bz);
        int hx=Math.max(ax,bx),hy=Math.max(ay,by),hz=Math.max(az,bz);
        if(lx < -29_999_000||hx > 29_999_000||lz < -29_999_000||hz > 29_999_000||
           ly < -64||hy > 319)throw new IllegalArgumentException("Excavation exceeds vanilla world bounds");
        long sx=(long)hx-lx+1,sy=(long)hy-ly+1,sz=(long)hz-lz+1;
        long cells=sx*sy*sz;
        if(cells<1||cells>MAX_VOLUME)throw new IllegalArgumentException(
            "Excavation would affect "+cells+" blocks; limit is "+MAX_VOLUME+" per command");
        return new int[]{lx,ly,lz,hx,hy,hz,(int)cells};
    }
    private Pos cell(int n) {
        int dx=x2-x1+1,dz=z2-z1+1;
        int x=n%dx, z=(n/dx)%dz, y=n/(dx*dz);
        return new Pos(x1+x,y1+y,z1+z);
    }
    private void report(String type,String detail){
        try{callStatic("com.player2.playerengine.observatory.ObservatoryTelemetry",
            "event",type,"area="+x1+","+y1+","+z1+".."+x2+","+y2+","+z2+" "+detail);}
        catch(Exception | LinkageError ignored){}
    }
    @Override protected void onStart(){
        try{
            world=call(controller,"getWorld");
            if(world==null||call(controller,"getEntity")==null)
                throw new IllegalStateException("NPC/world unavailable");
            int min=((Number)call(world,"method_31607")).intValue();
            int max=((Number)call(world,"method_31600")).intValue();
            if(y1<min||y2>=max)
                throw new IllegalStateException("Area exceeds world height "+min+".."+(max-1));
            Object baritone=call(controller,"getBaritone");
            builder=call(baritone,"getBuilderProcess");
            if(Boolean.TRUE.equals(call(builder,"isActive")))
                throw new IllegalStateException("Existing Baritone construction is active; do not hijack it");
            report("EXCAVATE_PREFLIGHT","volume="+volume+" safety=fail_closed");
        }catch(Exception | LinkageError error){block("START",error);}
    }
    private void worldCheck()throws Exception{
        if(world==null||call(controller,"getWorld")!=world||call(controller,"getEntity")==null)
            throw new IllegalStateException("NPC/world changed; excavation cancelled");
    }
    private Object pos(Pos p)throws Exception{return SchematicWorld.position(p);}
    private boolean air(Pos p)throws Exception{
        Object state=call(world,"method_8320",pos(p));
        return Boolean.TRUE.equals(call(state,"method_26215"));
    }
    private boolean safe(Pos p)throws Exception{
        Object point=pos(p);
        if(!Boolean.TRUE.equals(call(world,"method_22340",point)))
            throw new IllegalStateException("Unloaded chunk at "+p.key());
        if(SchematicDepotGuard.protects(controller,point))
            throw new IllegalStateException("Protected schematic depot/support at "+p.key());
        if(call(world,"method_8321",point)!=null)
            throw new IllegalStateException("Storage/block entity in excavation at "+p.key());
        Object state=call(world,"method_8320",point);
        Object block=call(state,"method_26204");
        Object registry=Class.forName("net.minecraft.class_7923").getField("field_41175").get(null);
        String name=String.valueOf(call(registry,"method_10221",block));
        if(name.equals("minecraft:water")||name.equals("minecraft:lava")||
           name.equals("minecraft:bedrock")||name.equals("minecraft:end_portal")||
           name.equals("minecraft:end_portal_frame")||name.equals("minecraft:nether_portal"))
            throw new IllegalStateException("Unsafe or unbreakable block "+name+" at "+p.key());
        return Boolean.TRUE.equals(call(state,"method_26215"));
    }
    private Task block(String code,Throwable reason){
        return block(code,reason.getClass().getSimpleName()+": "+reason.getMessage());
    }
    private Task block(String code,String detail){
        if(terminal)return null;
        terminal=true;success=false;reason="BLOCKED_EXCAVATE_"+code+": "+detail;
        report("EXCAVATE_BLOCKED",reason);fail(reason);stopOwnedBuilder();return null;
    }
    private void stopOwnedBuilder(){
        if(builder==null||ownedSchematic==null)return;
        try{
            if(get(builder,"schematic")==ownedSchematic||get(builder,"realSchematic")==ownedSchematic)
                call(builder,"onLostControl");
        }catch(Exception | LinkageError ignored){}
    }
    @Override protected Task onTick(){
        if(terminal)return null;
        try{
            worldCheck();
            if(!building){
                for(int limit=0;scan<volume&&limit<SCAN_PER_TICK;scan++,limit++)safe(cell(scan));
                if(scan<volume)return null;
                if(Boolean.TRUE.equals(call(builder,"isActive")))
                    return block("BUILDER_OCCUPIED","Other construction started during excavation preflight");
                call(builder,"clearArea",pos(new Pos(x1,y1,z1)),pos(new Pos(x2,y2,z2)));
                ownedSchematic=get(builder,"schematic");
                if(ownedSchematic==null)throw new IllegalStateException("Native clearArea did not create a builder plan");
                building=true;
                remaining=volume;reason="Excavating";
                report("EXCAVATE_STARTED","volume="+volume+" builder=native_clearArea");
                return null;
            }
            if(++buildTicks>240_000)return block("WORK_BUDGET","Total excavation work budget exceeded");
            // Scan conservatively over time: no fake progress from builder status.
            int prior=airCount;
            for(int n=0;n<SCAN_PER_TICK;n++){
                Pos p=cell(verifyScan);
                boolean isAir=air(p);
                if(isAir)airCount++;
                verifyScan++;
                if(verifyScan>=volume)break;
            }
            if(verifyScan>=volume){
                remaining=volume-airCount;
                if(airCount>prior)noWorkTicks=0;
                else noWorkTicks+=volume/SCAN_PER_TICK+1;
                if(remaining==0){
                    terminal=success=complete=true;reason="Verified "+volume+" air blocks";
                    stopOwnedBuilder();report("EXCAVATE_COMPLETED",reason);return null;
                }
                verifyScan=0;airCount=0;
                if(noWorkTicks>2400)return block("STALLED",
                    "No verified clearing in 2,400 ticks; "+remaining+" blocks remain");
                if(!Boolean.TRUE.equals(call(builder,"isActive")))return block("INTERRUPTED",
                    "Native builder stopped before the whole area became air; "+remaining+" blocks remain");
                if(buildTicks%200<SCAN_PER_TICK)report("EXCAVATE_PROGRESS","remaining="+remaining+" active=true");
            }
            return null;
        }catch(Exception | LinkageError error){return block("BOUNDARY",error);}
    }
    @Override public boolean isFinished(){return terminal;}
    @Override public boolean isTerminal(){return terminal;}
    @Override public boolean isSuccessful(){return terminal&&success;}
    @Override public String controlledReason(){return reason;}
    @Override protected void onStop(Task next){
        stopOwnedBuilder();
        if(!terminal){terminal=true;success=false;reason="BLOCKED_EXCAVATE_CANCELLED: owner stop/replacement";
            report("EXCAVATE_CANCELLED",reason);}
    }
    @Override protected boolean isEqual(Task other){
        return other instanceof ExcavateAreaTask e &&
           x1==e.x1&&y1==e.y1&&z1==e.z1&&x2==e.x2&&y2==e.y2&&z2==e.z2;
    }
    @Override protected String toDebugString(){return "Excavate "+x1+","+y1+","+z1+" to "+x2+","+y2+","+z2+
        " remaining="+remaining+" "+reason;}
}
