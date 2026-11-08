import java.util.*;
import java.util.function.Predicate;
import com.player2.playerengine.reliability.PlankTargetReconciliation;
import com.player2.playerengine.schematic.SchematicDepotGuard;
import com.player2.playerengine.schematic.SchematicPlan.Pos;

/** Isolated logic tests exercising the exact helper classes packaged in the P0 JAR. */
public final class P0ReliabilityRegression {
    static int checked;
    static void check(boolean result,String description){
        checked++;
        if(!result)throw new AssertionError(description);
    }
    public static final class FakeStorage {
        public int planks;
        public int getItemCount(String[] matches){return planks;}
    }
    public static final class FakeMatch {
        public String[] getMatches(){return new String[]{"minecraft:oak_planks"};}
    }
    public static final class FakeController {
        public Object world;
        public FakeStorage storage=new FakeStorage();
        public Object getWorld(){return world;}
        public FakeStorage getItemStorage(){return storage;}
    }
    public static final class FakeExtra {
        final Object mutex=new Object();
        final List<Object> breakAvoiders=new ArrayList<>();
        public Object getBreakMutex(){return mutex;}
        public List<Object> getBreakAvoiders(){return breakAvoiders;}
    }
    public static final class FakePos {
        final int x,y,z;
        FakePos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public int method_10263(){return x;}
        public int method_10264(){return y;}
        public int method_10260(){return z;}
    }
    @SuppressWarnings("unchecked")
    static boolean prevented(FakeExtra extra,FakePos pos){
        for(Object guard:extra.breakAvoiders)
            if(((Predicate<Object>)guard).test(pos))return true;
        return false;
    }
    static void crafting(){
        check(PlankTargetReconciliation.absoluteCount(92,92)==184,
            "92 held plus 92 required must request 184 absolute, never 92");
        check(PlankTargetReconciliation.absoluteCount(0,192)==192,"empty plank inventory");
        check(PlankTargetReconciliation.absoluteCount(8,1)==9,"small shortfall");
        check(PlankTargetReconciliation.absoluteCount(64,64)==128,"one-stack shortfall");
        FakeController ctrl=new FakeController();
        ctrl.storage.planks=92;
        check(PlankTargetReconciliation.absoluteTarget(ctrl,new FakeMatch(),92)==184,
            "real reflective inventory target did not include existing planks");
        ctrl.storage.planks=120;
        check(PlankTargetReconciliation.absoluteTarget(ctrl,new FakeMatch(),72)==192,
            "live inventory change must affect absolute target");
        try {PlankTargetReconciliation.absoluteCount(-1,2);throw new AssertionError("negative inventory accepted");}
        catch(IllegalArgumentException expected){checked++;}
        try {PlankTargetReconciliation.absoluteCount(12,0);throw new AssertionError("zero deficit accepted");}
        catch(IllegalArgumentException expected){checked++;}
    }
    static void storage(){
        FakeController c=new FakeController();
        c.world=new Object();
        FakeExtra extra=new FakeExtra();
        Pos chest=new Pos(91,106,266);
        SchematicDepotGuard.retainAll(c,c.world,extra,List.of(chest));
        check(extra.breakAvoiders.size()==1,"one independent break guard installed");
        check(SchematicDepotGuard.retainedCells(c)==2,"chest and support registered");
        check(prevented(extra,new FakePos(91,106,266)),"depot chest is break-protected");
        check(prevented(extra,new FakePos(91,105,266)),"support block is break-protected");
        check(!prevented(extra,new FakePos(91,107,266)),"unrelated blocks can be excavated");
        check(SchematicDepotGuard.protects(c,new FakePos(91,106,266)),"resource selectors must reject reserved chest");
        check(!SchematicDepotGuard.protects(c,new FakePos(91,108,266)),"unrelated item sources remain valid");
        // A schematic task's own ephemeral avoider disappears on stop.
        // The independent depot lease must survive that exact transition.
        Predicate<Object> ephemeral=p->false;
        extra.breakAvoiders.add(ephemeral);
        extra.breakAvoiders.remove(ephemeral);
        check(prevented(extra,new FakePos(91,106,266)),
            "superseding schematic removed the persistent depot guard");
        SchematicDepotGuard.retain(c,c.world,extra,new Pos(91,106,270));
        check(extra.breakAvoiders.size()==1,"one guard shared by multiple project depots");
        check(SchematicDepotGuard.retainedCells(c)==4,"second chest and support retained");
        check(prevented(extra,new FakePos(91,106,270)),"newly placed chest should be protected");

        c.world=new Object();
        check(!prevented(extra,new FakePos(91,106,266)),"old-world guard must not obstruct new world");
        check(!SchematicDepotGuard.protects(c,new FakePos(91,106,266)),
            "world transition must not carry old reservation");
        SchematicDepotGuard.retain(c,c.world,extra,new Pos(10,64,10));
        check(extra.breakAvoiders.size()==1,"guard replaced on world transition, not leaked");
        check(!prevented(extra,new FakePos(91,106,266)),"old world chest remained reserved");
        check(prevented(extra,new FakePos(10,64,10)),"new world depot not protected");
    }
    public static void main(String[]args){
        crafting();storage();
        System.out.println("P0ReliabilityRegression "+checked+" checks passed (native inventory/world interactions substituted)");
    }
}
