import com.player2.playerengine.tasks.setup.CoreAcquisitionProgress;
public final class SetupProgressTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        CoreAcquisitionProgress progress=new CoreAcquisitionProgress();
        check(!progress.observe("COLLECT_MISSING_MATERIALS","iron_ingot=0|raw_iron=0","none",false).materialProgress(),"initial sample is not material gain");
        check(progress.observe("COLLECT_MISSING_MATERIALS","iron_ingot=1|raw_iron=0","none",false).materialProgress(),"iron gain missed");
        check(!progress.observe("COLLECT_MISSING_MATERIALS","iron_ingot=0|raw_iron=0","none",false).materialProgress(),"loss counted as progress");
        check(!progress.observe("COLLECT_MISSING_MATERIALS","iron_ingot=1|raw_iron=0","none",false).materialProgress(),"repeated regain must not reset budget indefinitely");
        check(progress.observe("COLLECT_MISSING_MATERIALS","iron_ingot=2|raw_iron=0","none",false).materialProgress(),"new high water missed");
        progress.reset();check(!progress.observe("PLAN","iron_ingot=1","none",false).materialProgress(),"reset baseline");
        var setup=new com.player2.playerengine.tasks.setup.EllieSetupTask();
        setup.controller=new com.player2.playerengine.PlayerEngineController(null);
        var signature=setup.getClass().getDeclaredMethod("progressSignature",com.player2.playerengine.tasks.base.Task.class,
            com.player2.playerengine.tasks.setup.EllieSetupTask.Step.class);
        signature.setAccessible(true);
        var step=new com.player2.playerengine.tasks.setup.EllieSetupTask.Step("equipment upgrade","iron_sword",1,true,3000);
        var task=new com.player2.playerengine.tasks.base.Task();
        setup.controller.getItemStorage().counts.put("cobblestone",149);
        String first=(String)signature.invoke(setup,task,step);
        setup.controller.getItemStorage().counts.put("cobblestone",193);
        String second=(String)signature.invoke(setup,task,step);
        check(first.equals(second),"unrelated cobblestone reset iron progress as observed in supplied logs");
        setup.controller.getItemStorage().counts.put("iron_ingot",1);
        check(!second.equals(signature.invoke(setup,task,step)),"actual iron progress must remain visible");
        System.out.println("SetupProgressTest: 8 material-progress checks passed, including supplied-log cobblestone/iron regression");
    }
}
