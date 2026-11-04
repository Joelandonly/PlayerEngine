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
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(elevated)).isPresent(),"height alone cannot prove a table unreachable");
        expect(CraftTableApproachRecovery.filterReachable(ctrl,Optional.of(new Target(local))).isPresent(),"native reachable candidate is retained");
        com.player2.playerengine.reliability.SharedTargetRecovery.reject(ctrl,local);
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(local)).isEmpty(),"shared rejection prevents immediate reselection");
        expect(CraftTableApproachRecovery.filterReachable(ctrl,Optional.of(new Target(local))).isEmpty(),"GPS results use the same shared rejection");
        expect(CraftTableApproachRecovery.filterReusable(new Ctrl(),Optional.of(local)).isPresent(),"other NPCs remain independent");
        FakeTask task=new FakeTask();task.craftingTablePos=local;for(int i=0;i<200;i++)CraftTableApproachRecovery.craftTick(task,ctrl);
        expect(task.craftingTablePos==local,"crafting no longer runs a competing watchdog");
        expect(CraftTableApproachRecovery.filterReusable(ctrl,Optional.of(new Pos(1,65,1))).isPresent(),"other approaches remain available");
        System.out.println("CraftTableRecoveryTest: 7 shared-policy delegation checks passed");
    }
}
