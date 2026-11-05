import com.player2.playerengine.*;
import com.player2.playerengine.tasks.base.*;
import com.player2.playerengine.tasks.setup.*;
import java.lang.reflect.*;
import java.util.*;

/** Runs the production setup entry/lifecycle and survival coordinator together.
 * Game acquisition, avatar state and world effects are explicit substituted boundaries.
 */
public final class SurvivalSetupTest {
    static final Method START,TICK,BASE;
    static {try{
        START=EllieSetupTask.class.getDeclaredMethod("onStart");START.setAccessible(true);
        TICK=EllieSetupTask.class.getDeclaredMethod("onTick");TICK.setAccessible(true);
        BASE=StarterBaseTask.class.getDeclaredMethod("onTick");BASE.setAccessible(true);
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static final class Op extends Task implements TrackedTaskOutcomeProvider {
        final Runnable effect;boolean done,success=true;
        Op(Runnable effect){this.effect=effect;}
        void execute(){if(!done){effect.run();done=true;}}
        public boolean isFinished(){return done;}
        public boolean isTerminal(){return done;}
        public boolean isSuccessful(){return done&&success;}
        public String controlledReason(){return success?"verified simulated effect":"simulated inaccessible shelter";}
    }
    static final class Environment implements SetupSurvival.Environment {
        int hunger=20,food,fuel;float health=20;
        boolean night,threat,shelter,inside,closed,equipped,bed,nearby=true;
        boolean noFood,noFuel,falseShelter,falseEquip,unsafeSite,unreachableEntrance;
        int anchors,closes;String location="saved chest 14,64,10";
        final List<SetupSurvival.Action> actions=new ArrayList<>();
        final List<Integer> strategies=new ArrayList<>();
        public void anchor(StarterBaseTask base)throws Exception{base.prepare();anchors++;}
        public SetupSurvival.Snapshot snapshot(){return new SetupSurvival.Snapshot(hunger,health,night,threat,food,fuel,equipped,bed);}
        public boolean shelterVerified(){return shelter;}
        public boolean inside(){return inside&&shelter;}
        public boolean doorClosed(){return closed&&shelter;}
        public int shelterProgress(){return shelter?125:0;}
        public boolean baseNearby(){return nearby;}
        public double baseDistance(){return nearby?0:80;}
        public String location(){return location;}
        public void close(){closes++;}
        public Task task(SetupSurvival.Action action,int target,int strategy){
            actions.add(action);strategies.add(strategy);
            if(action==SetupSurvival.Action.SHELTER&&unsafeSite)throw new IllegalStateException("No clear supported site; refusing to demolish existing structures");
            return new Op(()->{switch(action){
                case RETURN -> nearby=true;
                case FOOD -> {if(!noFood)food=target;}
                case EAT -> {if(food>0){food=Math.max(0,food-5);hunger=Math.min(20,hunger+5);}}
                case SHELTER -> {if(!falseShelter)shelter=true;}
                case ENTER -> {if(!unreachableEntrance)inside=true;}
                case CLOSE_DOOR -> closed=true;
                case FUEL -> {if(!noFuel)fuel=target;}
                case EQUIP -> {if(!falseEquip)equipped=true;}
            }});
        }
    }
    static final class Run {
        final PlayerEngineController controller=new PlayerEngineController(null);
        final SetupChainTest.World world=new SetupChainTest.World(controller);
        final Environment survival=new Environment();
        final EllieSetupTask setup=new EllieSetupTask(survival,new StarterBaseTask(world));
        final Set<Task> performed=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<String> requests=new ArrayList<>();
        boolean noIron,holdSurvivalActions;int ticks;
        Run(){setup.controller=controller;}
        void execute(Task child)throws Exception{
            if(child instanceof StarterBaseTask base)execute((Task)BASE.invoke(base));
            else if(child instanceof Op op){if(!holdSurvivalActions)op.execute();}
            else if(child instanceof SurfaceLightingTest.Op op)op.execute();
            else if(child instanceof TaskCatalogue.NativeAcquisition get&&performed.add(child)){
                requests.add(get.id);
                if(!noIron||!get.id.startsWith("iron_"))controller.getItemStorage().counts.merge(get.id,get.count,Math::max);
            }
        }
        void start()throws Exception{START.invoke(setup);}
        void tick()throws Exception{execute((Task)TICK.invoke(setup));ticks++;}
        void run()throws Exception{start();for(;ticks<18000&&!setup.isTerminal();)tick();check(setup.isTerminal(),"entire setup must terminate or finish its bounded safety wait");}
    }
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception{
        int scenarios=0;
        Run fresh=new Run();fresh.run();
        check(fresh.setup.isSuccessful()&&fresh.survival.shelter&&fresh.survival.inside&&fresh.survival.closed,"full setup verifies an entered, closed shelter");
        check(fresh.survival.food>=32&&fresh.survival.fuel>=8&&fresh.survival.equipped,"food/fuel/equipment are actual final predicates");
        check(fresh.requests.equals(Arrays.stream(EllieSetupTask.plannedSteps()).map(s->s.resource).toList())&&fresh.world.torchPlaced==0,"required kit preserved, no excluded acquisitions or torch placement");
        check(fresh.setup.outcomeForCommand().contains("lighting remains manual"),"completion must not claim spawn-proof lighting");scenarios++;
        Run remote=new Run();remote.survival.nearby=false;remote.run();
        check(remote.setup.isSuccessful()&&remote.survival.actions.getFirst()==SetupSurvival.Action.RETURN,"rerun away from base returns to saved chest instead of relocating");scenarios++;
        Run hungry=new Run();hungry.survival.hunger=3;hungry.run();
        check(hungry.setup.isSuccessful()&&hungry.survival.hunger>=18,"multiple verified meals restore hunger, not just one completed eating task");
        check(hungry.survival.actions.getFirst()==SetupSurvival.Action.FOOD,"urgent food precedes tool acquisition");scenarios++;
        Run night=new Run();night.survival.night=true;night.start();
        for(int i=0;i<40;i++)night.tick();
        check(night.setup.isWaitingForSafety()&&night.survival.inside&&night.survival.closed&&night.requests.isEmpty(),"night shelters first and does not mine outside while waiting");
        night.survival.night=false;while(!night.setup.isTerminal()&&night.ticks<18000)night.tick();
        check(night.setup.isSuccessful(),"dawn resumes the same setup");scenarios++;
        Run partial=new Run();for(var step:EllieSetupTask.plannedSteps())partial.controller.getItemStorage().counts.put(step.resource,step.amount);
        partial.world.blocks.addAll(SurfaceLightingTest.BASE);partial.survival.shelter=partial.survival.inside=partial.survival.closed=partial.survival.equipped=partial.survival.bed=true;
        partial.survival.food=32;partial.survival.fuel=8;partial.run();
        check(partial.setup.isSuccessful()&&partial.requests.isEmpty()&&partial.survival.actions.isEmpty()&&partial.survival.anchors==1,"existing kit/base/shelter/stocks are verified and reused without rebuilding");scenarios++;
        Run foodMissing=new Run();foodMissing.survival.hunger=5;foodMissing.survival.noFood=true;foodMissing.run();
        check(foodMissing.setup.isBlocked()&&foodMissing.setup.controlledReason().contains("FOOD blocked")&&foodMissing.survival.actions.size()==2,"two distinct food strategies then specific BLOCKED, no endless hunger loop");scenarios++;
        Run ironMissing=new Run();ironMissing.noIron=true;ironMissing.run();
        check(ironMissing.setup.isBlocked()&&ironMissing.setup.controlledReason().contains("iron_pickaxe"),"missing iron cannot become full survival success");scenarios++;
        Run site=new Run();site.survival.unsafeSite=true;site.run();
        check(site.setup.isBlocked()&&site.setup.controlledReason().contains("refusing to demolish"),"unsafe shelter site is specific and nondestructive");scenarios++;
        Run fake=new Run();fake.survival.falseShelter=true;fake.run();
        check(fake.setup.isBlocked()&&fake.setup.controlledReason().contains("SHELTER blocked"),"builder completion without a verified enclosure is not success");scenarios++;
        Run entrance=new Run();entrance.survival.unreachableEntrance=true;entrance.run();
        check(entrance.setup.isBlocked()&&entrance.setup.controlledReason().contains("ENTER blocked"),"unreachable entrance blocks rather than claiming a usable shelter");scenarios++;
        Run equip=new Run();equip.survival.falseEquip=true;equip.run();
        check(equip.setup.isBlocked()&&equip.setup.controlledReason().contains("EQUIP blocked"),"unverified equipped slots cannot pass");scenarios++;
        Run fuel=new Run();fuel.survival.noFuel=true;fuel.run();
        check(fuel.setup.isBlocked()&&fuel.setup.controlledReason().contains("FUEL blocked"),"fuel recipe/source retries are bounded");scenarios++;
        Run damage=new Run();damage.survival.health=10;damage.survival.shelter=true;damage.survival.inside=true;damage.survival.closed=true;
        damage.start();for(int i=0;i<50;i++)damage.tick();check(damage.setup.isWaitingForSafety(),"low health rests with food instead of resuming mining");
        damage.survival.health=16;while(!damage.setup.isTerminal()&&damage.ticks<18000)damage.tick();check(damage.setup.isSuccessful(),"verified health recovery resumes setup");scenarios++;
        Run wounded=new Run();wounded.survival.health=8;wounded.start();
        for(int i=0;i<20;i++)wounded.tick();check(wounded.survival.actions.getFirst()==SetupSurvival.Action.SHELTER&&wounded.setup.isWaitingForSafety(),"low health can establish shelter before the ordinary kit");
        wounded.survival.health=16;while(!wounded.setup.isTerminal()&&wounded.ticks<18000)wounded.tick();check(wounded.setup.isSuccessful(),"low-health start resumes after recovery");scenarios++;
        Run dusk=new Run();dusk.start();while(dusk.requests.size()<11&&dusk.ticks<1000)dusk.tick();
        dusk.holdSurvivalActions=true;while(!dusk.survival.actions.contains(SetupSurvival.Action.FOOD)&&dusk.ticks<1100)dusk.tick();
        dusk.survival.night=true;dusk.holdSurvivalActions=false;for(int i=0;i<15;i++)dusk.tick();
        check(dusk.setup.isWaitingForSafety()&&dusk.survival.inside&&dusk.survival.closed,"dusk preempts reserve collection to enter cover even after the kit is done");
        dusk.survival.night=false;while(!dusk.setup.isTerminal()&&dusk.ticks<18000)dusk.tick();check(dusk.setup.isSuccessful(),"reserve collection resumes after dawn");scenarios++;
        Run frozenNight=new Run();frozenNight.survival.night=true;frozenNight.run();check(frozenNight.setup.isBlocked()&&frozenNight.setup.controlledReason().contains("bounded night"),"disabled daylight cycle cannot wait forever");scenarios++;
        Run changed=new Run();changed.start();while(!changed.survival.shelter&&changed.ticks<100)changed.tick();
        changed.survival.shelter=false;changed.survival.unsafeSite=true;
        while(!changed.setup.isTerminal()&&changed.ticks<18000)changed.tick();check(changed.setup.isBlocked(),"shelter removal during setup must be detected");scenarios++;
        Run uneven=new Run();uneven.world.uneven=true;uneven.run();check(uneven.setup.isSuccessful()&&uneven.world.repairs==1,"workshop terrain repair remains compatible with expanded survival setup");scenarios++;
        Run interrupted=new Run();var coordinator=new SetupSurvival(interrupted.survival,interrupted.controller);var base=new StarterBaseTask(interrupted.world);
        Task pending=coordinator.tick(false,true,base);
        for(int i=0;i<3;i++){coordinator.childrenStopped();pending=coordinator.tick(false,true,base);}
        check(interrupted.survival.strategies.stream().allMatch(i->i==1),"defense interruptions retain the same strategy budget rather than consuming retries");
        ((Op)pending).execute();coordinator.tick(false,true,base);check(interrupted.survival.shelter,"interrupted shelter stage can resume and verify output");scenarios++;
        Run hungerDuringWork=new Run();hungerDuringWork.start();
        while(hungerDuringWork.requests.size()<2&&hungerDuringWork.ticks<100)hungerDuringWork.tick();
        hungerDuringWork.survival.hunger=4;
        while(!hungerDuringWork.setup.isTerminal()&&hungerDuringWork.ticks<18000)hungerDuringWork.tick();
        check(hungerDuringWork.setup.isSuccessful()&&hungerDuringWork.survival.hunger>=18,"new hunger suspends ordinary kit work and resumes from actual inventory");
        check(Collections.frequency(hungerDuringWork.requests,"stone_axe")==1,"already acquired tool survives survival preemption");scenarios++;
        var stop=EllieSetupTask.class.getDeclaredMethod("onStop",Task.class);stop.setAccessible(true);stop.invoke(fresh.setup,new Object[]{null});
        check(fresh.survival.closes==1&&fresh.setup.isSuccessful(),"normal completion releases setup-only context without changing the result");scenarios++;
        System.out.println("SurvivalSetupTest: "+scenarios+" complete production-entry scenarios passed (game boundaries substituted; live Minecraft unverified)");
    }
}
