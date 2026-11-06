package com.player2.playerengine.supervisor;

import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.executor.StepExecution;
import com.player2.playerengine.executor.StepState;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** Translates the receipt belonging to this command; never borrows another job's last result. */
public final class CommandCompletion {
    private static final ThreadLocal<Object> SUBMITTING = new ThreadLocal<>();
    private static final Map<Object, Receipt> RECEIPTS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Receipt {
        StepExecution execution;
        final WeakReference<Task> task;
        boolean pending, note;
        String text;
        Receipt(Task task) { this.task=new WeakReference<>(task); }
    }
    public record TaskOutcome(StepState state,String detail) {}
    private CommandCompletion() {}

    /** Existing Command.run calls this in place of its protected call(), with the same exception contract. */
    public static void invoke(Object command, Object controller, Object parser) throws CommandException {
        Object previous = SUBMITTING.get();
        RECEIPTS.remove(command);
        SUBMITTING.set(command);
        try {
            Method entry = null;
            for (Class<?> type = command.getClass(); type != null && entry == null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals("call") && method.getParameterCount() == 2
                            && method.getParameterTypes()[0].isInstance(controller)
                            && method.getParameterTypes()[1].isInstance(parser)) {
                        entry = method;
                        break;
                    }
                }
            }
            if (entry == null) throw new IllegalStateException("Command has no compatible call method");
            entry.setAccessible(true);
            entry.invoke(command, controller, parser);
        } catch (InvocationTargetException wrapped) {
            Throwable failure = wrapped.getCause();
            if (failure instanceof CommandException checked) throw checked;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Command invocation failed", failure);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Command invocation unavailable", failure);
        } finally {
            if (previous == null) SUBMITTING.remove();
            else SUBMITTING.set(previous);
        }
    }

    public static void prepare(Task task) {
        Object command = SUBMITTING.get();
        if(command!=null)RECEIPTS.put(command,new Receipt(task));
    }

    public static void bind(Task task, StepExecution execution) {
        Object command=SUBMITTING.get();
        if(command==null)return;
        synchronized(RECEIPTS){
            Receipt receipt=RECEIPTS.get(command);
            if(receipt==null||receipt.task.get()!=task){receipt=new Receipt(task);RECEIPTS.put(command,receipt);}
            receipt.execution=execution;
        }
    }

    /** Existing callback-driven workers may request command completion before the chain reaps them. */
    public static void completed(StepExecution execution) {
        Map<Object,Receipt> pending=new java.util.LinkedHashMap<>();
        synchronized(RECEIPTS){
            for(var entry:RECEIPTS.entrySet())if(entry.getValue().execution==execution&&entry.getValue().pending){
                entry.getValue().pending=false;pending.put(entry.getKey(),entry.getValue());
            }
        }
        for(var entry:pending.entrySet()){
            Receipt receipt=entry.getValue();
            try{invokeFinish(entry.getKey(),receipt.note?"finishWithNote":"finish",receipt.text,receipt.note);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot settle deferred command completion",failure);}
        }
    }

    public static String failure(StepExecution execution, Task task) {
        String shared = com.player2.playerengine.reliability.SharedTargetRecovery.failure(task);
        if (shared != null) return bounded(shared);
        if (task instanceof TrackedTaskOutcomeProvider typed) {
            if(!typed.isTerminal())return "Task ended before its terminal result was verified";
            if(!typed.isSuccessful())return bounded(typed.controlledReason());
        }
        TaskOutcome nativeOutcome=taskOutcome(task);
        if(nativeOutcome!=null&&nativeOutcome.state()!=StepState.SUCCEEDED)return bounded(nativeOutcome.detail());
        if (execution.getState() == StepState.SUCCEEDED) return null;
        return bounded(execution.getLastLogEntry() == null
                ? "executor=" + execution.getState() : execution.getLastLogEntry());
    }

    /** Called before finish()/finishWithNote() set ended; finishWithError retains its original route. */
    public static boolean beforeFinish(Object command,String text,boolean note) {
        Receipt receipt;
        synchronized(RECEIPTS){
            receipt=RECEIPTS.get(command);
            if(receipt!=null&&(receipt.execution==null||!receipt.execution.isTerminal())){
                if(!receipt.pending){receipt.pending=true;receipt.note=note;receipt.text=text;}
                return true;
            }
            RECEIPTS.remove(command);
        }
        if (receipt == null) return false;
        String reason = failure(receipt.execution, receipt.task.get());
        if (reason == null) return false;
        try {
            invokeFinish(command,"finishWithError",reason,true);
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot report unsuccessful command receipt", failure);
        }
    }

    private static void invokeFinish(Object command,String name,String text,boolean argument) throws ReflectiveOperationException {
        for(Class<?> type=command.getClass();type!=null;type=type.getSuperclass()){
            try{
                Method method=argument?type.getDeclaredMethod(name,String.class):type.getDeclaredMethod(name);
                method.setAccessible(true);
                if(argument)method.invoke(command,text);else method.invoke(command);
                return;
            }catch(NoSuchMethodException ignored){}
        }
        throw new NoSuchMethodException(name);
    }

    /** Read existing workers' own outcomes instead of equating their terminal flag with success. */
    public static TaskOutcome taskOutcome(Task task) {
        if(task==null)return null;
        String shared=com.player2.playerengine.reliability.SharedTargetRecovery.failure(task);
        if(shared!=null)return new TaskOutcome(StepState.FAILED,"FATAL:"+bounded(shared));
        if(task instanceof TrackedTaskOutcomeProvider)return null;
        String type=task.getClass().getSimpleName();
        try {
            boolean success;
            String detail;
            switch(type){
                case "StoreInAnyContainerTask" -> {success=Boolean.TRUE.equals(call(task,"succeeded"));detail=String.valueOf(call(task,"reason"));}
                case "ScanContainerTask" -> {success=((java.util.Optional<?>)call(task,"result")).isPresent();detail=String.valueOf(call(task,"failure"));}
                case "BoundedContainerTransferTask","SlotPreciseTransactionTask" -> {
                    Object outcome=call(task,"outcome");if(outcome==null)return null;
                    detail=String.valueOf(outcome);success=detail.equals("MOVED_ALL")||detail.equals("MOVED_PARTIAL")||detail.equals("COMPLETED");
                }
                case "SmeltDeferredTask","SmithDeferredTask" -> {
                    Object outcome=call(task,"outcome");if(outcome==null)return null;
                    String kind=String.valueOf(call(outcome,"kind"));
                    success=kind.equals("CLEAN_SUCCESS")||((Number)call(outcome,"collected")).intValue()>0;
                    detail=kind+": "+call(outcome,"reasonLabel");
                }
                case "PlaceSignTask" -> {Object failure=get(task,"ellieSignFailure");success=failure==null&&task.isFinished();detail=failure==null?(success?"verified sign placement":"sign placement interrupted before verification"):String.valueOf(failure);}
                default -> {return null;}
            }
            return new TaskOutcome(success?StepState.SUCCEEDED:StepState.FAILED,
                    success?"task_finished_verified":"FATAL:BLOCKED_"+type+": "+bounded(detail));
        }catch(Exception|LinkageError failure){
            return new TaskOutcome(StepState.FAILED,"FATAL:BLOCKED_RESULT: cannot verify "+type+": "+failure.getClass().getSimpleName());
        }
    }

    private static String bounded(String reason) {
        if (reason == null || reason.isBlank()) return "Task ended without verified success";
        reason = reason.replace('\n', ' ').replace('\r', ' ');
        return reason.length() > 700 ? reason.substring(0, 700) : reason;
    }
}
