import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.*;
import com.player2.playerengine.executor.*;
import com.player2.playerengine.supervisor.*;
import com.player2.playerengine.tasks.base.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real patched Command lifecycle, supervisor and StepExecution; only native task execution is substituted. */
public final class CommandCompletionTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class OutcomeTask extends Task implements TrackedTaskOutcomeProvider {
        boolean success;OutcomeTask(boolean success){this.success=success;}
        public boolean isTerminal(){return true;}public boolean isSuccessful(){return success;}
        public String controlledReason(){return success?"inventory output verified":"BLOCKED_FUEL: no reachable source";}
    }
    static final class Adapter implements IStepExecutorAdapter {
        StepState result=StepState.SUCCEEDED;boolean synchronous=true;
        final List<Runnable> callbacks=new ArrayList<>();
        final List<StepExecution> receipts=new ArrayList<>();Runnable duringSubmit;
        public StepExecution submit(String id,String kind,Task task,RollbackPolicy rollback,Runnable callback){
            StepExecution receipt=ExecutionFixture.receipt(id,kind,result,"this receipt "+result);
            callbacks.add(callback);receipts.add(receipt);if(duringSubmit!=null)duringSubmit.run();if(synchronous)callback.run();return receipt;
        }
        public void cancel(String reason){}
        public Optional<StepExecution> getActiveExecution(){return Optional.of(ExecutionFixture.receipt("same-id","old",StepState.SUCCEEDED,"unrelated old success"));}
        public Optional<StepExecution> getLastCompletedExecution(){return getActiveExecution();}
    }
    static final class Probe extends Command {
        Task work=new OutcomeTask(true);boolean note;Runnable nested;
        Probe(){super("probe","receipt probe",new ArgBase[0]);}
        void earlyFinish(){finish();}void earlyNote(){finishWithNote("native leaf note");}
        protected void call(PlayerEngineController c,ArgParser parser){
            if(nested!=null)nested.run();
            ProjectTaskGateway.run(c,work,()->{if(note)finishWithNote("partial result details");else finish();});
        }
    }
    static final class Result {
        int success,errors,notes;String reason;
        void run(Probe command,PlayerEngineController controller)throws Exception{
            command.run(controller,"probe",()->success++,error->{errors++;reason=error.getMessage();},message->{notes++;reason=message;});
        }
    }
    public static void main(String[] args)throws Exception {
        Adapter failed=new Adapter();failed.result=StepState.FAILED;PlayerEngineController controller=new PlayerEngineController(failed);
        Probe fail=new Probe();Result a=new Result();a.run(fail,controller);
        check(a.errors==1&&a.success==0,"failed executor receipt cannot become plain command success");
        check(a.reason.contains("this receipt FAILED"),"failure must use its own captured receipt rather than unrelated previous success");
        failed.callbacks.getFirst().run();check(a.errors==1,"duplicate callback must not repeat the terminal");

        Adapter success=new Adapter();controller=new PlayerEngineController(success);Probe good=new Probe();Result b=new Result();b.run(good,controller);
        check(b.success==1&&b.errors==0,"verified success must remain success");

        Adapter misleading=new Adapter();controller=new PlayerEngineController(misleading);Probe typed=new Probe();typed.work=new OutcomeTask(false);Result c=new Result();c.run(typed,controller);
        check(c.errors==1&&c.success==0&&c.reason.contains("BLOCKED_FUEL"),"typed blocked outcome overrides a misleading succeeded adapter receipt");

        Probe note=new Probe();note.note=true;Result d=new Result();d.run(note,new PlayerEngineController(new Adapter()));
        check(d.notes==1&&d.errors==0&&d.reason.equals("partial result details"),"valid partial notes must be preserved");
        Probe failedNote=new Probe();failedNote.note=true;Result e=new Result();e.run(failedNote,new PlayerEngineController(failed));
        check(e.errors==1&&e.notes==0,"finishWithNote cannot hide a failed receipt");

        Adapter delayed=new Adapter();delayed.synchronous=false;controller=new PlayerEngineController(delayed);Result f=new Result();Probe asynchronous=new Probe();f.run(asynchronous,controller);
        check(f.success==0&&f.errors==0,"submission must not be reported as completion");
        delayed.callbacks.getFirst().run();check(f.success==1&&f.errors==0,"deferred completion retains the original command binding");

        Adapter scopes=new Adapter();controller=new PlayerEngineController(scopes);PlayerEngineController scopedController=controller;
        Probe outer=new Probe(),inner=new Probe();Result outerResult=new Result(),innerResult=new Result();
        outer.nested=()->{try{innerResult.run(inner,scopedController);}catch(Exception failure){throw new RuntimeException(failure);}scopes.result=StepState.FAILED;};
        outerResult.run(outer,controller);
        check(innerResult.success==1&&outerResult.errors==1,"nested command scope must restore the outer submission owner");

        Adapter reuse=new Adapter();reuse.result=StepState.FAILED;controller=new PlayerEngineController(reuse);Probe reused=new Probe();Result first=new Result(),second=new Result();
        first.run(reused,controller);reuse.result=StepState.SUCCEEDED;second.run(reused,controller);
        check(first.errors==1&&second.success==1&&second.errors==0,"a new run of the same registered command must clear the old binding");

        Adapter replaced=new Adapter();replaced.synchronous=false;controller=new PlayerEngineController(replaced);Result old=new Result(),current=new Result();
        old.run(new Probe(),controller);current.run(new Probe(),controller);replaced.callbacks.get(0).run();
        check(old.success==0&&old.errors==0&&current.success==0,"stale completion must not finish or continue superseded work");
        replaced.callbacks.get(1).run();check(current.success==1&&old.success==0,"current generation must retain its own completion");

        Adapter early=new Adapter();early.synchronous=false;early.result=StepState.RUNNING;
        Probe leaf=new Probe();Result earlyResult=new Result();earlyResult.run(leaf,new PlayerEngineController(early));leaf.earlyNote();
        check(earlyResult.success==0&&earlyResult.errors==0&&earlyResult.notes==0,"native leaf callback must wait for the executor receipt to settle");
        ExecutionFixture.finish(early.receipts.getFirst(),StepState.SUCCEEDED,"verified native callback");early.callbacks.getFirst().run();
        check(earlyResult.notes==1&&earlyResult.errors==0&&earlyResult.reason.equals("native leaf note"),"deferred leaf note must survive successful executor completion");

        Adapter earlyFailure=new Adapter();earlyFailure.synchronous=false;earlyFailure.result=StepState.RUNNING;
        Probe failedLeaf=new Probe();Result earlyFailed=new Result();earlyFailed.run(failedLeaf,new PlayerEngineController(earlyFailure));failedLeaf.earlyFinish();
        ExecutionFixture.finish(earlyFailure.receipts.getFirst(),StepState.FAILED,"BLOCKED_SIGN: placement failed");earlyFailure.callbacks.getFirst().run();
        check(earlyFailed.errors==1&&earlyFailed.success==0&&earlyFailed.reason.contains("BLOCKED_SIGN"),"early normal finish cannot hide a later failed native receipt");

        Adapter immediateLeaf=new Adapter();Probe immediate=new Probe();immediateLeaf.duringSubmit=immediate::earlyFinish;
        Result immediateResult=new Result();immediateResult.run(immediate,new PlayerEngineController(immediateLeaf));
        check(immediateResult.success==1&&immediateResult.errors==0,"a leaf callback inside submit must wait for and use the newly bound receipt");
        System.out.println("CommandCompletionTest: "+checks+" receipt/lifecycle checks passed; task execution boundary substituted");
    }
}
