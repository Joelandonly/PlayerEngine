package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.tasks.base.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BodyLanguageTask extends Task {
   private static final Logger LOGGER = LogManager.getLogger();
   private final BodyLanguageTask.Type type;
   private static final float shakeNodHeadAngle = 40.0F;
   private static final int shakeNodHeadTime = 10;
   private final PrimitiveSequenceTask seqGreeting = makeGreeting();
   private final PrimitiveSequenceTask seqNodHead = makeNodHead(2);
   private final PrimitiveSequenceTask seqShake = makeShakeHead(2);
   private final PrimitiveSequenceTask seqVictory = makeVictoryDance();
   private Task actuallyRunningTask;

   public BodyLanguageTask(BodyLanguageTask.Type type) {
      this.type = type;
   }

   public static BodyLanguageTask.Type resolveType(String t) {
      try {
         return BodyLanguageTask.Type.valueOf(t.toUpperCase());
      } catch (IllegalArgumentException e) {
         throw new IllegalArgumentException("Unknown bodylang action: '" + t + "'. Valid: greeting, nod_head, shake_head, victory.");
      }
   }

   @Override
   protected void onStart() {
      boolean followWasActive = false;
      if (this.controller != null) {
         Task utTask = this.controller.getUserTaskChain().getCurrentTask();
         followWasActive = utTask instanceof FollowPlayerTask && utTask.isActive();
      }

      LOGGER.info("[FollowDiag] GESTURE-START: type={} followWasActiveInUserTaskChain={}", this.type, followWasActive);
      switch (this.type) {
         case GREETING:
            this.actuallyRunningTask = this.seqGreeting;
            break;
         case NOD_HEAD:
            this.actuallyRunningTask = this.seqNodHead;
            break;
         case SHAKE_HEAD:
            this.actuallyRunningTask = this.seqShake;
            break;
         case VICTORY:
            this.actuallyRunningTask = this.seqVictory;
      }
   }

   @Override
   protected Task onTick() {
      return this.actuallyRunningTask;
   }

   @Override
   public boolean isFinished() {
      return this.actuallyRunningTask != null && this.actuallyRunningTask.isFinished();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof BodyLanguageTask o) ? false : o.type == this.type;
   }

   @Override
   protected void onStop(Task next) {
      LOGGER.info(
         "[FollowDiag] GESTURE-STOP: type={} finished={} nextTask={}", this.type, this.isFinished(), next != null ? next.getClass().getSimpleName() : "(none)"
      );
      PlayerEngineController mod = this.controller;
      mod.getInputControls().release(Input.SNEAK);
      mod.getInputControls().release(Input.JUMP);
      mod.getInputControls().release(Input.SPRINT);
      mod.getInputControls().release(Input.MOVE_FORWARD);
      mod.getInputControls().release(Input.MOVE_BACK);
      mod.getInputControls().release(Input.MOVE_LEFT);
      mod.getInputControls().release(Input.MOVE_RIGHT);
   }

   @Override
   protected String toDebugString() {
      return "BodyLanguage(" + this.type + ")";
   }

   private static PrimitiveSequenceTask makeGreeting() {
      int holdTicks = 8;
      int pauseTicks = 6;
      int bowCount = 2;
      PrimitiveSequenceTask.Sequence.Builder b = PrimitiveSequenceTask.builder();

      for (int i = 0; i < bowCount; i++) {
         b.hold(Input.SNEAK).waitTicks(holdTicks).release(Input.SNEAK).waitTicks(pauseTicks);
      }

      return b.build();
   }

   private static PrimitiveSequenceTask makeNodHead(int nods) {
      PrimitiveSequenceTask.Sequence.Builder b = PrimitiveSequenceTask.builder();

      for (int i = 0; i < nods; i++) {
         b.lookRelative(new Rotation(0.0F, 40.0F), 10);
         b.lookRelative(new Rotation(0.0F, -40.0F), 10);
      }

      b.waitTicks(2);
      return b.build();
   }

   private static PrimitiveSequenceTask makeShakeHead(int shakes) {
      PrimitiveSequenceTask.Sequence.Builder b = PrimitiveSequenceTask.builder();

      for (int i = 0; i < shakes; i++) {
         b.lookRelative(new Rotation(40.0F, 0.0F), 10);
         b.lookRelative(new Rotation(-40.0F, 0.0F), 10);
      }

      return b.build();
   }

   private static PrimitiveSequenceTask makeVictoryDance() {
      return PrimitiveSequenceTask.builder()
         .jump()
         .waitTicks(4)
         .lookRelative(new Rotation(180.0F, 0.0F), 20)
         .lookRelative(new Rotation(-180.0F, 0.0F), 20)
         .hold(Input.SNEAK)
         .waitTicks(6)
         .release(Input.SNEAK)
         .jump()
         .build();
   }

   public enum Type {
      GREETING,
      NOD_HEAD,
      SHAKE_HEAD,
      VICTORY;
   }
}
