import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.setup.EllieSetupTask;
import java.lang.reflect.*;

/** Executes the real setup terminal branch with a substituted inventory boundary. */
public final class SetupCompletionTest {
    private static int passed;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static EllieSetupTask ready() throws Exception {
        EllieSetupTask task = new EllieSetupTask();
        task.controller = new PlayerEngineController(null);
        for (var step : EllieSetupTask.plannedSteps()) if (step.required) task.controller.getItemStorage().counts.put(step.resource,step.amount);
        field(task,"index",EllieSetupTask.plannedSteps().length);
        Field base = EllieSetupTask.class.getDeclaredField("baseTask"); base.setAccessible(true);
        base.set(task,StarterBaseTest.completedBase());
        return task;
    }
    private static void field(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.setInt(target,value);
    }
    private static void tick(EllieSetupTask task) throws Exception {
        Method tick = EllieSetupTask.class.getDeclaredMethod("onTick"); tick.setAccessible(true); tick.invoke(task);
    }
    public static void main(String[] args) throws Exception {
        EllieSetupTask complete = ready(); tick(complete);
        check(complete.isSuccessfullyCompleted(),"required kit should complete"); passed++;
        EllieSetupTask lost = ready(); lost.controller.getItemStorage().counts.put("shield",0); tick(lost);
        check(!lost.isSuccessfullyCompleted() && !lost.isTerminal(),"lost shield must trigger repair, not success"); passed++;
        EllieSetupTask broken = ready(); broken.controller.getItemStorage().counts.put("stone_shovel",0); tick(broken);
        check(!broken.isSuccessfullyCompleted(),"missing required stone shovel must be reverified"); passed++;
        EllieSetupTask upgrade = ready(); upgrade.controller.getItemStorage().counts.remove("wooden_pickaxe"); tick(upgrade);
        check(upgrade.isSuccessfullyCompleted(),"existing better pickaxe should satisfy wooden bootstrap"); passed++;
        EllieSetupTask unavailable = ready(); unavailable.controller.getItemStorage().unavailable = true; tick(unavailable);
        check(unavailable.isBlocked() && !unavailable.isSuccessfullyCompleted(),"unreadable inventory cannot count as success"); passed++;
        EllieSetupTask unstable = ready(); unstable.controller.getItemStorage().counts.put("shield",0);
        for (int i = 0; i < 3; i++) { field(unstable,"index",EllieSetupTask.plannedSteps().length); tick(unstable); }
        check(unstable.isBlocked() && !unstable.isSuccessful(),"repair must have a finite budget"); passed++;
        check(java.util.Arrays.stream(EllieSetupTask.plannedSteps()).allMatch(s->s.required),"setup must not contain optional objectives"); passed++;
        String[] expected = {"wooden_pickaxe", "stone_axe", "stone_pickaxe", "stone_shovel", "crafting_table", "furnace", "chest", "torch", "shield", "iron_pickaxe", "iron_sword"};
        check(java.util.Arrays.equals(java.util.Arrays.stream(EllieSetupTask.plannedSteps()).map(s->s.resource).toArray(String[]::new), expected),
            "setup must require stone shovel, early axe, shield and iron sword, with no buckets, armor or diamonds"); passed++;
        EllieSetupTask placed=ready();
        for(String item:new String[]{"crafting_table","furnace","chest"})placed.controller.getItemStorage().counts.remove(item);
        tick(placed);check(placed.isSuccessful(),"verified placed stations must credit consumed item stacks");passed++;
        EllieSetupTask better=ready();better.controller.getItemStorage().counts.remove("stone_axe");better.controller.getItemStorage().counts.put("iron_axe",1);
        tick(better);check(better.isSuccessful(),"already owned iron axe can satisfy earlier stone axe without downgrade");passed++;
        EllieSetupTask unplaced=ready();Field base=EllieSetupTask.class.getDeclaredField("baseTask");base.setAccessible(true);
        base.set(unplaced,new com.player2.playerengine.tasks.setup.StarterBaseTask(new StarterBaseTest.World()));
        tick(unplaced);check(!unplaced.isSuccessfullyCompleted(),"full inventory is not a placed base");passed++;
        EllieSetupTask bootstrap=ready();field(bootstrap,"index",1);
        bootstrap.controller.getItemStorage().counts.remove("stone_axe"); bootstrap.controller.getItemStorage().counts.put("iron_axe",1);tick(bootstrap);
        Field index=EllieSetupTask.class.getDeclaredField("index");index.setAccessible(true);
        check(index.getInt(bootstrap)==2,"existing iron axe should skip the stone axe downgrade");passed++;
        System.out.println("SetupCompletionTest: " + passed + " passed; terminal verification only, no real acquisition/world simulation");
    }
}
