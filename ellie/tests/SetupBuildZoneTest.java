import com.player2.playerengine.tasks.setup.*;
import com.player2.playerengine.tasks.base.*;
import java.util.List;
import com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard;

/** Runs the real shipped village guard, not a replacement implementation. */
public final class SetupBuildZoneTest {
    record Pos(int x,int z) {public int getX(){return x;}public int getZ(){return z;}}
    static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    static boolean allowed(Object controller,int x,int z){return VillageSiteGuard.allowResourceMining(controller,new Pos(x,z));}
    public static void main(String[] args)throws Exception{
        Object controller=new Object(),other=new Object();
        SetupBuildZone zone=new SetupBuildZone(controller,-2,-5,7,3);
        for(int x=-2;x<=4;x++)for(int z=-5;z<=-3;z++)check(!allowed(controller,x,z),"foundation/resource source inside zone must be excluded");
        check(allowed(controller,-3,-5)&&allowed(controller,5,-3)&&allowed(controller,-2,-6)&&allowed(controller,4,-2),"outside materials remain available");
        check(allowed(other,0,0),"other characters must remain independent");
        zone.ensure();check(!allowed(controller,0,-4),"repeated updates retain protection");
        // Nested workshop, shelter and tiny repair plans must coexist.
        SetupBuildZone replacement=new SetupBuildZone(controller,20,30,7,3);
        check(!allowed(controller,0,-4)&&!allowed(controller,20,30),"nested build cannot replace outer protection");
        replacement.ensure();zone.ensure();
        check(!allowed(controller,0,-4)&&!allowed(controller,20,30),"interleaved owners retain both zones");
        zone.close();zone.ensure();
        check(!allowed(controller,20,30)&&allowed(controller,0,-4),"stale owner cleanup must preserve replacement footprint");
        replacement.close();check(allowed(controller,20,30),"terminal setup releases its protection");
        SetupBuildZone lifetime=new SetupBuildZone(controller,9,9,7,3);
        var environment=new SetupTerraformTest.World(){
            public void protect(List<StarterBaseLayout.Placement> plan){lifetime.ensure();}
            public void releaseProtection(){lifetime.close();}
        };
        StarterBaseTask base=new StarterBaseTask(environment);SetupTerraformTest.run(base);
        var stopBase=StarterBaseTask.class.getDeclaredMethod("onStop",Task.class);stopBase.setAccessible(true);stopBase.invoke(base,new Object[]{null});
        check(base.isSuccessful()&&!allowed(controller,10,10),"base completion must retain guard for later kit acquisition");
        check(base.prepareForTransientResume(TaskSuspensionCause.HIGHER_PRIORITY_CHAIN)==false,"finished base is terminal");
        EllieSetupTask root=new EllieSetupTask();
        var baseField=EllieSetupTask.class.getDeclaredField("baseTask");baseField.setAccessible(true);baseField.set(root,base);
        var completed=EllieSetupTask.class.getDeclaredField("completed");completed.setAccessible(true);completed.setBoolean(root,true);
        var stopRoot=EllieSetupTask.class.getDeclaredMethod("onStop",Task.class);stopRoot.setAccessible(true);stopRoot.invoke(root,new Object[]{null});
        check(allowed(controller,10,10),"whole-goal completion releases protection");
        System.out.println("SetupBuildZoneTest: 9 passed against production VillageSiteGuard, including all 21 footprint cells");
    }
}
