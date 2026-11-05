package com.player2.playerengine.tasks.setup;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.*;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import java.util.*;

/** Survival stages inside the existing setup task, using its single native task tree. */
public final class SetupSurvival implements AutoCloseable {
    public static final int FOOD_RESERVE=32, FUEL_RESERVE=8;
    public enum Action { FOOD, EAT, SHELTER, ENTER, CLOSE_DOOR, FUEL, EQUIP, RETURN }
    public record Snapshot(int hunger, float health, boolean night, boolean threat,
                           int food, int fuel, boolean equipped, boolean bed) {}
    public interface Environment {
        void anchor(StarterBaseTask base) throws Exception;
        default boolean baseNearby()throws Exception{return true;}
        default double baseDistance()throws Exception{return 0;}
        Snapshot snapshot() throws Exception;
        boolean shelterVerified() throws Exception;
        boolean inside() throws Exception;
        boolean doorClosed() throws Exception;
        int shelterProgress() throws Exception;
        Task task(Action action,int target,int strategy) throws Exception;
        String location();
        default void close() {}
    }
    private final Environment world;
    private final PlayerEngineController controller;
    private final EnumMap<Action,Integer> attempts=new EnumMap<>(Action.class);
    private Task active;
    private Action action,deferred;
    private int deferredTarget,deferredTicks,deferredIdle,deferredHighWater,deferredStartProgress,deferredAttempts;
    private int target, ticks, idle, highWater=-1, waitTicks, startProgress;
    private boolean anchored, holding, waiting, done, resumePending, homeVisited, shelterEstablished;
    private String detail="Assessing survival needs";

    public SetupSurvival(Environment world,PlayerEngineController controller) {
        this.world=Objects.requireNonNull(world);this.controller=controller;
    }
    public Task tick(boolean kitComplete,boolean toolsReady,StarterBaseTask base) throws Exception {
        holding=false;waiting=false;
        if(!anchored){world.anchor(base);anchored=true;event("SETUP_ASSESS",world.location()+"; "+world.snapshot());}
        Snapshot s=world.snapshot();
        if(s.health()<=0)throw new IllegalStateException("NPC is not alive; survival setup cannot continue");
        if(resumePending){
            active=Objects.requireNonNull(world.task(action,target,attempts.getOrDefault(action,1)));
            active.controller=controller;resumePending=false;
            event("SETUP_SURVIVAL_RESUME",action+" retains its work/retry budget after safety interruption");
        }
        if(active!=null&&deferred==null&&(action==Action.FUEL||action==Action.FOOD&&target==FOOD_RESERVE)
                &&(s.night()||s.threat()||s.health()<12)){
            deferred=action;deferredTarget=target;deferredTicks=ticks;deferredIdle=idle;deferredHighWater=highWater;deferredStartProgress=startProgress;deferredAttempts=attempts.getOrDefault(action,1);
            active.interrupt(null,TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);active=null;action=null;
            event("SETUP_SURVIVAL_PAUSE","Pausing reserve collection for cover; objective and work budget retained");
        }
        if(active!=null) {
            int progress=progress(s);
            if(satisfied(s)) {
                event("SETUP_SURVIVAL_VERIFIED",action+" verified; "+s);
                attempts.remove(action);active=null;action=null;ticks=idle=0;highWater=-1;
            } else {
                ticks++;idle++;
                if(progress>highWater){highWater=progress;idle=0;if(action==Action.SHELTER)com.player2.playerengine.reliability.SharedTargetRecovery.verifiedWorldOutput(controller,"Shelter verified cells="+progress);}
                String failure=null;
                if(active instanceof TrackedTaskOutcomeProvider outcome && outcome.isTerminal()&&!outcome.isSuccessful())failure=outcome.controlledReason();
                else if(active.isFinished()||active.stopped())failure="native task ended without verified output";
                else if(idle>2400||ticks>(action==Action.SHELTER?12000:4800))failure="bounded work budget exhausted without verified output";
                if(failure!=null) {
                    Action failed=action;int goal=target;active=null;
                    // Only food and fuel have an existing, genuinely different second strategy.
                    if(failed==Action.EAT&&progress>startProgress&&s.food()<1)return start(Action.FOOD,12,s);
                    if(failed==Action.EAT&&progress>startProgress&&attempts.getOrDefault(failed,0)<8){
                        event("SETUP_SURVIVAL_PROGRESS","Food consumed and hunger increased; eating another carried item toward target "+goal);
                        return start(failed,goal,s);
                    }
                    if((failed==Action.FOOD||failed==Action.FUEL)&&attempts.getOrDefault(failed,0)<2){
                        event("SETUP_SURVIVAL_ALTERNATE",failed+": "+failure+"; using alternate native recipe/source");
                        return start(failed,goal,s);
                    }
                    throw new IllegalStateException(failed+" blocked: "+failure+"; "+world.location()+"; observed="+s);
                }
                holding=true;detail=action+" target="+target+" verified="+progress+" idle="+idle+" attempt="+attempts.get(action)+"/"+(action==Action.EAT?8:2);
                if(ticks%100==0)event("SETUP_SURVIVAL_PROGRESS",detail);
                return active;
            }
        }
        // Native food/defense chains retain higher priority while any child is running.
        // At each setup decision point, hunger outranks ordinary collection/upgrades.
        if(s.hunger()<12 || s.health()<12 && s.hunger()<18) {
            if(s.food()<6)return start(Action.FOOD,12,s);
            return start(Action.EAT,18,s);
        }
        boolean nearby=world.baseNearby();
        if(!homeVisited||kitComplete||s.night()||s.threat()||s.health()<12||toolsReady&&!shelterEstablished){
            if(!nearby)return start(Action.RETURN,1,s);
            homeVisited=true;
        }
        // A previously observed enclosure can guide daytime remote collection,
        // but final success always returns home and rechecks actual blocks.
        boolean hasShelter=nearby?world.shelterVerified():shelterEstablished;
        if(nearby&&hasShelter)shelterEstablished=true;
        if((toolsReady||s.night()||s.threat()||s.health()<12||kitComplete)&&!hasShelter)return start(Action.SHELTER,1,s);
        if((s.night()||s.threat()||s.health()<12)&&hasShelter) {
            if(!world.inside())return start(Action.ENTER,1,s);
            if(!world.doorClosed())return start(Action.CLOSE_DOOR,1,s);
            if(!kitComplete || s.food()<FOOD_RESERVE || s.fuel()<FUEL_RESERVE || s.health()<12 || s.threat()) {
                holding=waiting=true;
                detail=s.health()<12?"Resting in verified shelter":s.threat()?"Taking cover; native defense retains priority":"Waiting in shelter for daylight";
                if(++waitTicks>13200)throw new IllegalStateException(detail+": conditions did not improve within one bounded night/rest window");
                if(waitTicks==1||waitTicks%200==0)event("SETUP_SAFETY_WAIT",detail+"; "+s);
                return null;
            }
        }
        waitTicks=0;
        if(deferred!=null){
            Action next=deferred;deferred=null;
            boolean already=next==Action.FOOD?s.food()>=deferredTarget:s.fuel()>=deferredTarget;
            if(!already){
                action=next;target=deferredTarget;attempts.put(next,deferredAttempts);
                active=Objects.requireNonNull(world.task(next,target,deferredAttempts));active.controller=controller;
                ticks=deferredTicks;idle=deferredIdle;highWater=deferredHighWater;startProgress=deferredStartProgress;holding=true;
                detail="Resuming "+next+" after cover with its original work budget";event("SETUP_SURVIVAL_RESUME",detail);return active;
            }
            attempts.remove(next);
        }
        if(!kitComplete){detail="Survival needs checked; continuing required kit";return null;}
        if(s.hunger()<18){if(s.food()<6)return start(Action.FOOD,12,s);return start(Action.EAT,18,s);}
        if(s.food()<FOOD_RESERVE)return start(Action.FOOD,FOOD_RESERVE,s);
        if(s.fuel()<FUEL_RESERVE)return start(Action.FUEL,FUEL_RESERVE,s);
        if(!s.equipped())return start(Action.EQUIP,1,s);
        if(!world.inside())return start(Action.ENTER,1,s);
        if(!world.doorClosed())return start(Action.CLOSE_DOOR,1,s);
        done=true;detail="Survival setup verified at "+world.location()+": enclosed shelter, food="+s.food()+" nutrition units, fuel="+s.fuel()+" coal-equivalents, sword/shield equipped; "+(s.bed()?"bed carried (not placed)":"bed unavailable (not required)")+"; lighting remains manual";
        return null;
    }
    private Task start(Action next,int goal,Snapshot s) throws Exception {
        action=next;target=goal;int strategy=attempts.merge(next,1,Integer::sum);
        if(strategy>(next==Action.EAT?8:2))throw new IllegalStateException(next+" exhausted its bounded strategy budget");
        active=Objects.requireNonNull(world.task(next,goal,strategy),"No native task for "+next);
        active.controller=controller;ticks=idle=0;highWater=progress(s);startProgress=highWater;holding=true;
        detail=next+" target="+goal+" strategy="+strategy;
        event("SETUP_SURVIVAL_ACTION",detail+" at "+world.location());return active;
    }
    private boolean satisfied(Snapshot s)throws Exception {return switch(action){
        case FOOD -> s.food()>=target;case EAT -> s.hunger()>=target;
        case SHELTER -> world.shelterVerified()&&active.isFinished()&&(!(active instanceof TrackedTaskOutcomeProvider outcome)||outcome.isSuccessful());case ENTER -> world.inside();
        case CLOSE_DOOR -> world.doorClosed();case FUEL -> s.fuel()>=target;case EQUIP -> s.equipped();case RETURN -> world.baseNearby();
    };}
    private int progress(Snapshot s)throws Exception {return switch(action){
        case FOOD -> s.food();case EAT -> s.hunger();case SHELTER -> world.shelterProgress();
        case FUEL -> s.fuel();case EQUIP -> s.equipped()?1:0;case ENTER -> world.inside()?1:0;case CLOSE_DOOR -> world.doorClosed()?1:0;case RETURN -> -(int)Math.ceil(world.baseDistance());
    };}
    public boolean holding(){return holding;}
    public boolean waiting(){return waiting;}
    public boolean complete(){return done;}
    public String describe(){return detail;}
    public void childrenStopped(){if(active!=null){active=null;resumePending=true;}}
    public void close(){world.close();}
    private static void event(String type,String detail){ObservatoryTelemetry.event(type,detail);ObservatoryTelemetry.set("setupSurvival",detail);}
}
