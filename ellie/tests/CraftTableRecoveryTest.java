import java.util.*;
import com.player2.playerengine.tasks.crafting.CraftTableApproachRecovery;
public final class CraftTableRecoveryTest {
    public static final class Pos {
        final int x,y,z;
        Pos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public int method_10263(){return x;}
        public int method_10264(){return y;}
        public int method_10260(){return z;}
    }
    public record Target(Pos pos){}
    public static final class Player {
        public double x=0.5,y=65,z=0.5;
        public double method_23317(){return x;}
        public double method_23318(){return y;}
        public double method_23321(){return z;}
    }
    public static final class Ctrl {
        final Object world=new Object(); final Player player=new Player();
        public Object getWorld(){return world;}
        public Player getPlayer(){return player;}
    }
    public static final class FakeTask {public Pos craftingTablePos;}
    static void expect(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        Ctrl ctrl=new Ctrl();
        Pos elevated=new Pos(0,68,1),local=new Pos(0,65,1);
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(elevated)).isEmpty(),"elevated table not selected from ravine");
        expect(CraftTableApproachRecovery.filterReachable(ctrl,Optional.of(new Target(elevated))).isEmpty(),"GPS memory also blocked across vertical gap");
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(local)).isPresent(),"same-level table remains selectable");
        FakeTask task=new FakeTask();task.craftingTablePos=elevated;
        CraftTableApproachRecovery.craftTick(task,ctrl);
        expect(task.craftingTablePos==null,"obsolete table cleared from active crafting task");
        // A same-level goal that goes nowhere must also be dropped.
        FakeTask stuck=new FakeTask();stuck.craftingTablePos=local;
        for(int tick=0;tick<101;tick++)CraftTableApproachRecovery.craftTick(stuck,ctrl);
        expect(stuck.craftingTablePos==null,"stationary approach reaches finite retry limit");
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(local)).isEmpty(),"failed table not immediately reselected");
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(new Pos(1,65,1))).isPresent(),"other nearby table still allowed");
        ctrl.player.y=68;
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(new Pos(2,68,2))).isPresent(),"elevation change permits matching-level new station");
        System.out.println("CraftTableRecoveryTest: 7 checks passed");
    }
}
