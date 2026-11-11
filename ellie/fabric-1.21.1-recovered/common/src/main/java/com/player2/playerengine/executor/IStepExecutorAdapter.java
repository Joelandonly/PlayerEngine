package com.player2.playerengine.executor;

import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;

public interface IStepExecutorAdapter {
   StepExecution submit(String var1, String var2, Task var3, RollbackPolicy var4, Runnable var5);

   void cancel(String var1);

   Optional<StepExecution> getActiveExecution();
}
