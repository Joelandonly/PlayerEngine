import com.player2.playerengine.executor.StepState;
import com.player2.playerengine.tasks.base.Task;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Real packaged terminal mapper and its record; unrelated Minecraft-dependent adapter methods
 * are removed only in this test loader. Worker outcomes are explicit boundary objects. */
public final class PackagedCompletionMapTest {
    static int checks;
    static final String ADAPTER="com.player2.playerengine.executor.TaskStepExecutorAdapter";
    public static class StoreInAnyContainerTask extends Task {public boolean isFinished(){return true;}public boolean succeeded(){return false;}public String reason(){return "partial_remaining";}}
    public record CookingOutcome(String kind,int collected,String reasonLabel){}
    public static class SmeltDeferredTask extends Task {
        final CookingOutcome result;SmeltDeferredTask(String kind,int count){result=new CookingOutcome(kind,count,"test outcome");}
        public CookingOutcome outcome(){return result;}public boolean isFinished(){return true;}
    }
    public static class BoundedContainerTransferTask extends Task {
        final String result;BoundedContainerTransferTask(String result){this.result=result;}
        public String outcome(){return result;}public boolean isFinished(){return true;}
    }
    public static class ScanContainerTask extends Task {public Optional<Object> result(){return Optional.empty();}public Optional<String> failure(){return Optional.of("container_unreachable");}public boolean isFinished(){return true;}}
    public static class SlotPreciseTransactionTask extends Task {final String result;SlotPreciseTransactionTask(String result){this.result=result;}public String outcome(){return result;}public boolean isFinished(){return true;}}
    public static class PlaceSignTask extends Task {private String ellieSignFailure;PlaceSignTask(String failure){ellieSignFailure=failure;}public boolean isFinished(){return true;}}

    static final class Loader extends ClassLoader implements AutoCloseable {
        final ZipFile jar;Loader(String path)throws Exception{super(PackagedCompletionMapTest.class.getClassLoader());jar=new ZipFile(path);}
        protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.equals(ADAPTER)&&!name.equals(ADAPTER+"$TerminalDecision"))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){
                Class<?> prior=findLoadedClass(name);if(prior!=null)return prior;
                try{
                    ClassNode type=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry(name.replace('.','/')+".class")).readAllBytes()).accept(type,0);
                    if(name.equals(ADAPTER)){type.interfaces.clear();type.fields.clear();type.methods.removeIf(m->!Set.of("mapTerminalOutcome","boundedReason").contains(m.name));}
                    ClassWriter writer=new ClassWriter(0);type.accept(writer);byte[] bytes=writer.toByteArray();Class<?> result=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(result);return result;
                }catch(Exception failure){throw new ClassNotFoundException(name,failure);}
            }
        }
        public void close()throws Exception{jar.close();}
    }
    static void expect(Method map,Task task,StepState expected,String detail)throws Exception {
        Object result=map.invoke(null,task);Method state=result.getClass().getDeclaredMethod("state"),reason=result.getClass().getDeclaredMethod("detail");
        state.setAccessible(true);reason.setAccessible(true);checks++;
        if(state.invoke(result)!=expected||!String.valueOf(reason.invoke(result)).contains(detail))throw new AssertionError(task.getClass().getSimpleName()+" => "+result);
    }
    public static void main(String[] args)throws Exception {
        try(Loader loader=new Loader(args[0])){
            Method map=loader.loadClass(ADAPTER).getDeclaredMethod("mapTerminalOutcome",Task.class);map.setAccessible(true);
            expect(map,new StoreInAnyContainerTask(),StepState.FAILED,"partial_remaining");
            expect(map,new SmeltDeferredTask("NO_FUEL",0),StepState.FAILED,"NO_FUEL");
            expect(map,new SmeltDeferredTask("PARTIAL_OUT_OF_FUEL",2),StepState.SUCCEEDED,"verified");
            expect(map,new SmeltDeferredTask("CLEAN_SUCCESS",4),StepState.SUCCEEDED,"verified");
            expect(map,new BoundedContainerTransferTask("NOTHING"),StepState.FAILED,"NOTHING");
            expect(map,new BoundedContainerTransferTask("FAILED"),StepState.FAILED,"FAILED");
            expect(map,new BoundedContainerTransferTask("MOVED_ALL"),StepState.SUCCEEDED,"verified");
            expect(map,new BoundedContainerTransferTask("MOVED_PARTIAL"),StepState.SUCCEEDED,"verified");
            expect(map,new ScanContainerTask(),StepState.FAILED,"container_unreachable");
            expect(map,new SlotPreciseTransactionTask("FAILED"),StepState.FAILED,"FAILED");
            expect(map,new SlotPreciseTransactionTask("COMPLETED"),StepState.SUCCEEDED,"verified");
            expect(map,new PlaceSignTask("set_text_failed"),StepState.FAILED,"set_text_failed");
            expect(map,new PlaceSignTask(null),StepState.SUCCEEDED,"verified");
            expect(map,new Task(),StepState.FAILED,"task_stopped_without_finish");
            System.out.println("PackagedCompletionMapTest: "+checks+" native terminal-mapper checks passed; worker outcome boundaries substituted");
        }
    }
}
