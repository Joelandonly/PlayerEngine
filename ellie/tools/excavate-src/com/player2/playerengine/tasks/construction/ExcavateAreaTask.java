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
public final class ExcavateAreaTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
    private static final int MAX_VOLUME=32768, SCAN_PER_TICK=128, MAX_TRANSIENT_INTERRUPTS=128;
    private final int x1,y1,z1,x2,y2,z2,volume;
    private Object world,builder,ownedSchematic;
    private int scan,airAtStart,verifyScan,remaining,airCount,verifiedAirLast=-1,noWorkTicks,buildTicks;
    private boolean building,complete,success,terminal,transientSuspended;
    private int acceptedInterrupts;
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
            // The native task runner may re-enter a suspended root. Never
            // reinitialize its bounds, builder ownership or progress budget.
            if(world!=null){worldCheck();return;}
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
            if(transientSuspended){
                // Native BuilderProcess is NOT a Task child. If equipment
                // management or defense stole its control, a new native
                // clearArea plan must follow fresh safety verification.
                boolean stillOurs=building&&builder!=null&&ownedSchematic!=null&&
                    (get(builder,"schematic")==ownedSchematic||
                     get(builder,"realSchematic")==ownedSchematic)&&
                    Boolean.TRUE.equals(call(builder,"isActive"));
                reconcileTransientResume(stillOurs);
            }
            if(!building){
                for(int limit=0;scan<volume&&limit<SCAN_PER_TICK;scan++,limit++)if(safe(cell(scan)))airAtStart++;
                if(scan<volume)return null;
                if(airAtStart==volume){terminal=success=complete=true;reason="Already clear: verified "+volume+" air blocks";
                    report("EXCAVATE_COMPLETED",reason);return null;}
                if(Boolean.TRUE.equals(call(builder,"isActive")))
                    return block("BUILDER_OCCUPIED","Other construction started during excavation preflight");
                call(builder,"clearArea",pos(new Pos(x1,y1,z1)),pos(new Pos(x2,y2,z2)));
                ownedSchematic=get(builder,"schematic");
                if(ownedSchematic==null)throw new IllegalStateException("Native clearArea did not create a builder plan");
                building=true;
                remaining=volume-airAtStart;reason="Excavating";
                report("EXCAVATE_STARTED","volume="+volume+" builder=native_clearArea");
                return null;
            }
            if(++buildTicks>240_000)return block("WORK_BUDGET","Total excavation work budget exceeded");
            if(get(builder,"schematic")!=ownedSchematic && get(builder,"realSchematic")!=ownedSchematic)
                return block("OWNERSHIP","Another task replaced the native excavation builder");
            // Scan conservatively over time: no fake progress from builder status.
            for(int n=0;n<SCAN_PER_TICK;n++){
                Pos p=cell(verifyScan);
                boolean isAir=air(p);
                if(isAir)airCount++;
                verifyScan++;
                if(verifyScan>=volume)break;
            }
            if(verifyScan>=volume){
                remaining=volume-airCount;
                if(airCount>verifiedAirLast)noWorkTicks=0;
                else noWorkTicks+=volume/SCAN_PER_TICK+1;
                verifiedAirLast=airCount;
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
    /**
     * Pure state transition separated from the live world read so regression
     * tests can exercise repeated interruptions without a fake Minecraft NPC.
     * In-game onTick MUST worldCheck() and verify builder identity first.
     */
    private void reconcileTransientResume(boolean stillOurs){
        if(!transientSuspended)return;
        transientSuspended=false;
        if(building){
            if(stillOurs){
                report("EXCAVATE_RESUMED","project="+id()+" mode=native_builder_retained"+
                    " remaining="+remaining+" interrupts="+acceptedInterrupts);
            }else{
                building=false;
                ownedSchematic=null;
                scan=0;airAtStart=0;verifyScan=0;airCount=0;
                report("EXCAVATE_RESUMED","project="+id()+" mode=preflight_then_rebuild"+
                    " remaining="+remaining+" interrupts="+acceptedInterrupts+
                    " budgetTicks="+buildTicks+" noWorkTicks="+noWorkTicks);
            }
        }else{
            // Even a partly scanned preflight can become stale while defense
            // or equipment work is in control. Recheck every selected cell.
            scan=0;airAtStart=0;
            report("EXCAVATE_RESUMED","project="+id()+" mode=preflight_recheck"+
                " scanned=0/"+volume+" interrupts="+acceptedInterrupts);
        }
    }
    @Override public boolean isFinished(){return terminal;}
    @Override public boolean isTerminal(){return terminal;}
    @Override public boolean isSuccessful(){return terminal&&success;}
    @Override public String controlledReason(){return reason;}
    /** Higher-priority gear/defense chains may pause; they do not cancel. */
    @Override public boolean prepareForTransientResume(TaskSuspensionCause cause){
        return !terminal&&(cause==TaskSuspensionCause.HIGHER_PRIORITY_CHAIN||
                           cause==TaskSuspensionCause.GESTURE_OVERLAY);
    }
    @Override public void afterChildrenStopped(){
        if(terminal)return;
        // Only invoked after native runner accepts transient suspension.
        // The owner task stays alive; the physical builder must relinquish
        // control so emergency chains can actually execute.
        if(!transientSuspended){
            transientSuspended=true;
            acceptedInterrupts++;
            report("EXCAVATE_SUSPENDED","project="+id()+" stage="+(building?"BUILD":"PREFLIGHT")+
                " remaining="+remaining+" interrupts="+acceptedInterrupts+
                " budgetTicks="+buildTicks+" noWorkTicks="+noWorkTicks);
        }
        stopOwnedBuilder();
        if(acceptedInterrupts>MAX_TRANSIENT_INTERRUPTS)
            block("PREEMPTION_BUDGET","Too many higher-priority interruptions: "+acceptedInterrupts);
    }
    @Override public void onTransientResumeAbandoned(){
        block("RESUME_ABANDONED","Native coordinator abandoned transient excavation resume");
    }
    private String id(){return Integer.toHexString(System.identityHashCode(this));}
    @Override protected void onStop(Task next){
        stopOwnedBuilder();
        transientSuspended=false;
        if(!terminal){terminal=true;success=false;reason="BLOCKED_EXCAVATE_CANCELLED: explicit owner stop/replacement";
            report("EXCAVATE_CANCELLED","project="+id()+" "+reason);}
    }
    @Override protected boolean isEqual(Task other){
        return other instanceof ExcavateAreaTask e &&
           x1==e.x1&&y1==e.y1&&z1==e.z1&&x2==e.x2&&y2==e.y2&&z2==e.z2;
    }
    @Override protected String toDebugString(){return "Excavate "+x1+","+y1+","+z1+" to "+x2+","+y2+","+z2+
        " remaining="+remaining+" "+reason;}
}
