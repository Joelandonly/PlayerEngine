import java.util.*;
import com.player2.playerengine.schematic.SchematicStorageReconciler;

/** Executes the real released storage-recovery helper against explicit mutable chest state. */
public final class StorageRecoveryRegression {
    private static int assertions;
    private static void check(boolean ok,String reason) {
        assertions++;
        if (!ok) throw new AssertionError(reason);
    }
    static final class FakeEnv {
        Map<String,Integer> inventory = new TreeMap<>();
        List<FakeDepot> depots = new ArrayList<>();
        public Map<String,Integer> inventory(){return new TreeMap<>(inventory);}
        public List<FakeDepot> depots(){return depots;}
    }
    static final class FakeDepot {
        Map<String,Integer> stock=new TreeMap<>();
        Map<String,Integer> mergeRoom=new TreeMap<>();
        int freeSlots;
        FakeDepot(Map<String,Integer> original,int slots){stock.putAll(original);freeSlots=slots;}
        public Map<String,Integer> stock(){return stock;}
        public Map<String,Integer> mergeRoom(){return mergeRoom;}
        public int freeSlots(){return freeSlots;}
    }
    static final class FakeTask {
        public String outcome(){return "FAILED";}
        public String code(){return "INSUFFICIENT_SPACE";}
        public List<String> failures(){return List.of("container space changed");}
        public String describeProgress(){return "phase=HOLD";}
    }
    enum Kind { DEPOSIT,WITHDRAW,ACQUIRE }
    static final class FakeAction {
        Kind kind;
        FakeTask task=new FakeTask();
        String item;
        int index,target,stockTarget;
        FakeAction(Kind kind,String item,int index,int target,int stockTarget){
            this.kind=kind;this.item=item;this.index=index;this.target=target;this.stockTarget=stockTarget;
        }
    }
    static final class FakeProject {
        FakeAction action;
        FakeEnv environment;
        List<String> events=new ArrayList<>();
        FakeProject(FakeEnv env,FakeAction action){this.environment=env;this.action=action;}
        private void event(String name,String detail){events.add(name+": "+detail);}
        boolean has(String value){return events.stream().anyMatch(e->e.contains(value));}
    }
    static FakeProject project() {
        FakeEnv env=new FakeEnv();
        env.inventory.put("minecraft:cobblestone",64);
        env.depots.add(new FakeDepot(Map.of("minecraft:clay_ball",156,"minecraft:cobblestone",516),2));
        return new FakeProject(env,new FakeAction(Kind.DEPOSIT,"minecraft:cobblestone",0,0,580));
    }
    static void start(FakeProject p){
        SchematicStorageReconciler.recordStart(p,p.action,p.environment.depots());
    }
    static void retry(FakeProject p){
        p.action=new FakeAction(Kind.DEPOSIT,"minecraft:cobblestone",0,0,
            p.environment.depots.get(0).stock.getOrDefault("minecraft:cobblestone",0)+64);
        start(p);
    }
    public static void main(String[] args){
        FakeProject p=project();
        start(p);
        check(p.has("SCHEMATIC_STORAGE_BASELINE"),"baseline missing");
        check(p.has("minecraft:clay_ball=156"),"clay missing from baseline");
        p.environment.depots.get(0).stock.remove("minecraft:clay_ball");
        p.environment.depots.get(0).freeSlots=5;
        check(SchematicStorageReconciler.interceptBlock(p,"DEPOSIT","native failure"),"no first reconciliation");
        check(p.action==null,"failed action not discarded");
        check(p.has("minecraft:clay_ball=-156"),"clay removal delta not reported");
        check(p.has("expectedStock=580 actualStock=516"),"required-item stock mismatch not reported");
        check(p.has("SCHEMATIC_STORAGE_RECONCILE"),"recovery event missing");
        check(!p.has("SCHEMATIC_STORAGE_RECOVERY_EXHAUSTED"),"first failure was fatal");
        retry(p);
        check(SchematicStorageReconciler.interceptBlock(p,"DEPOSIT","failure 2"),"second bounded retry refused");
        retry(p);
        check(SchematicStorageReconciler.interceptBlock(p,"DEPOSIT","failure 3"),"third bounded retry refused");
        retry(p);
        check(!SchematicStorageReconciler.interceptBlock(p,"DEPOSIT","failure 4"),"unbounded retry accepted");
        check(p.has("SCHEMATIC_STORAGE_RECOVERY_EXHAUSTED"),"exhaustion log missing");
        check(p.action!=null,"exhaustion must allow original controlled failure");

        FakeProject progressing=project();start(progressing);
        for(int i=0;i<5;i++){
            if(i>0)retry(progressing);
            progressing.environment.inventory.put("minecraft:cobblestone",64-i*2);
            progressing.environment.depots.get(0).stock.put("minecraft:cobblestone",516+i*2);
            check(SchematicStorageReconciler.interceptBlock(progressing,"DEPOSIT","partial transfer"),
                "legitimate progress exhausted retry budget "+i);
        }
        FakeProject unrelated=project();start(unrelated);
        check(!SchematicStorageReconciler.interceptBlock(unrelated,"ACQUIRE","missing material"),"unrelated error suppressed");
        check(unrelated.action!=null,"unrelated error changed action");
        FakeProject missing=project();start(missing);missing.environment.depots.clear();
        check(!SchematicStorageReconciler.interceptBlock(missing,"DEPOSIT","container removed"),"missing depot silently recovered");
        check(missing.action!=null,"missing depot must retain failed action");
        FakeProject restored=project();start(restored);
        restored.environment.depots.get(0).stock.remove("minecraft:clay_ball");
        restored.environment.depots.get(0).stock.put("minecraft:clay_ball",156);
        check(SchematicStorageReconciler.interceptBlock(restored,"DEPOSIT","transfer failure"),"restored chest did not rescan");
        check(restored.has("changesSinceActionStart={}"),"restored clay incorrectly reported as absent");
        check(restored.action==null,"restored chest was not replanned");
        System.out.println("PASS "+assertions+" packaged storage recovery assertions (mocked chest, not live Minecraft)");
    }
}
