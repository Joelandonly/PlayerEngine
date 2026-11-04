import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.base.Task;
import java.util.*;

/** Reproduces the startup gate and over-wide floor requirement from the supplied logs. */
public final class SetupSiteRegressionTest {
    static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    public static void main(String[] args)throws Exception{
        int passed=0;
        var anchor=new StarterBaseLayout.Pos(10,64,10);var plan=StarterBaseLayout.at(anchor);
        Set<StarterBaseLayout.Pos> support=new HashSet<>();
        for(var p:plan){support.add(p.pos().offset(0,-1,0));support.add(p.pos().offset(1,-1,0));}
        StarterBaseLayout.Probe uneven=new StarterBaseLayout.Probe(){
            public boolean air(StarterBaseLayout.Pos p){return true;}
            public boolean safeFloor(StarterBaseLayout.Pos p){return support.contains(p);}
            public boolean matches(StarterBaseLayout.Placement p){return false;}
        };
        check(StarterBaseLayout.safe(plan,uneven),"supported placement/access cells must not require an entirely flat 9x7 pad");passed++;
        StarterBaseLayout.Probe raised=new StarterBaseLayout.Probe(){
            public boolean air(StarterBaseLayout.Pos p){return p.y()>=65;}
            public boolean safeFloor(StarterBaseLayout.Pos p){return p.y()==64;}
            public boolean matches(StarterBaseLayout.Placement p){return false;}
        };
        check(StarterBaseLayout.select(new StarterBaseLayout.Pos(0,64,0),raised).orElseThrow().getFirst().pos().y()==65,"adjacent surface height must be searched");passed++;
        StarterBaseLayout.Probe hole=new StarterBaseLayout.Probe(){
            public boolean air(StarterBaseLayout.Pos p){return true;}
            public boolean safeFloor(StarterBaseLayout.Pos p){return support.contains(p)&&!p.equals(anchor.offset(0,-1,0));}
            public boolean matches(StarterBaseLayout.Placement p){return false;}
        };
        check(!StarterBaseLayout.safe(plan,hole),"target support must still be mandatory");passed++;
        StarterBaseLayout.Probe inaccessible=new StarterBaseLayout.Probe(){
            public boolean air(StarterBaseLayout.Pos p){return true;}
            public boolean safeFloor(StarterBaseLayout.Pos p){return plan.stream().anyMatch(s->s.pos().offset(0,-1,0).equals(p));}
            public boolean matches(StarterBaseLayout.Placement p){return false;}
        };
        check(!StarterBaseLayout.safe(plan,inaccessible),"workstations must keep an accessible adjacent cell");passed++;
        int[] selections={0};
        StarterBaseTask task=new StarterBaseTask(new StarterBaseTask.Environment(){
            public List<StarterBaseLayout.Placement> loadOrSelect(){selections[0]++;throw new IllegalStateException("No site yet");}
            public boolean matches(StarterBaseLayout.Placement p){return false;}
            public boolean safe(StarterBaseLayout.Placement p){return false;}
            public int count(String item){return 0;}
            public Task acquire(String item){throw new AssertionError();}
            public Task place(StarterBaseLayout.Placement p){throw new AssertionError();}
        });
        task.prepareExisting();
        check(selections[0]==0&&!task.isTerminal(),"fresh setup startup must not search or fail for lack of a workshop pad");passed++;
        var tick=StarterBaseTask.class.getDeclaredMethod("onTick");tick.setAccessible(true);tick.invoke(task);
        check(selections[0]==1&&task.isTerminal()&&!task.isSuccessful(),"site failure belongs to placement phase and cannot count as completed setup");passed++;
        System.out.println("SetupSiteRegressionTest: "+passed+" passed; supplied-log startup and terrain regression checks");
    }
}
