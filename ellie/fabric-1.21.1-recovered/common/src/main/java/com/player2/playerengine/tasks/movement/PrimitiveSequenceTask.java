package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.LookHelper;
import java.util.ArrayDeque;
import java.util.Deque;

public class PrimitiveSequenceTask extends Task {
   private final Deque<PrimitiveSequenceTask.Step> steps = new ArrayDeque<>();
   private PrimitiveSequenceTask.Step current;
   private boolean started = false;

   private PrimitiveSequenceTask(PrimitiveSequenceTask.Sequence seq) {
      this.steps.addAll(seq.buildSteps());
   }

   public static PrimitiveSequenceTask.Sequence.Builder builder() {
      return PrimitiveSequenceTask.Sequence.builder();
   }

   @Override
   protected void onStart() {
      this.started = false;
      this.current = null;
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (!this.started) {
         this.started = true;
         this.current = this.steps.pollFirst();
         if (this.current != null) {
            this.current.start(mod);
         }
      }

      if (this.current == null) {
         return null;
      }

      if (this.current.tick(mod)) {
         this.current.stop(mod);
         this.current = this.steps.pollFirst();
         if (this.current != null) {
            this.current.start(mod);
         }
      }

      return null;
   }

   @Override
   public boolean isFinished() {
      return this.started && this.current == null && this.steps.isEmpty();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof PrimitiveSequenceTask RHS) ? false : this.steps == RHS.steps && this.started == RHS.started;
   }

   @Override
   protected void onStop(Task next) {
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
      return "PrimitiveSequenceTask";
   }

   static final class HoldInputStep implements PrimitiveSequenceTask.Step {
      private final Input input;

      HoldInputStep(Input input) {
         this.input = input;
      }

      @Override
      public void start(PlayerEngineController mod) {
         mod.getInputControls().hold(this.input);
      }

      @Override
      public boolean tick(PlayerEngineController mod) {
         return true;
      }
   }

   static final class JumpStep implements PrimitiveSequenceTask.Step {
      private static int holdTicks = 3;
      private int ticksHeld;

      @Override
      public void start(PlayerEngineController mod) {
         this.ticksHeld = 0;
         mod.getInputControls().hold(Input.JUMP);
      }

      @Override
      public boolean tick(PlayerEngineController mod) {
         this.ticksHeld++;
         if (this.ticksHeld >= holdTicks) {
            mod.getInputControls().release(Input.JUMP);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void stop(PlayerEngineController mod) {
         mod.getInputControls().release(Input.JUMP);
      }
   }

   static final class LookOverTimeStep implements PrimitiveSequenceTask.Step {
      private final int totalTicks;
      private int elapsed;
      private Rotation start;
      private Rotation target;
      private final Rotation delta;
      private final boolean relative;

      LookOverTimeStep(Rotation targetAbs, int ticks) {
         this.totalTicks = Math.max(1, ticks);
         this.relative = false;
         this.delta = null;
         this.target = targetAbs;
      }

      LookOverTimeStep(Rotation dRot, int ticks, boolean relative) {
         this.totalTicks = Math.max(1, ticks);
         this.relative = relative;
         this.delta = dRot;
      }

      @Override
      public void start(PlayerEngineController mod) {
         this.elapsed = 0;
         this.start = LookHelper.getLookRotation(mod);
         if (this.relative) {
            Rotation raw = this.start.add(this.delta);
            this.target = new Rotation(Rotation.normalizeYaw(raw.getYaw()), Rotation.clampPitch(raw.getPitch()));
         } else {
            this.target = new Rotation(Rotation.normalizeYaw(this.target.getYaw()), Rotation.clampPitch(this.target.getPitch()));
         }

         if (this.totalTicks == 1 || this.totalTicks == 0) {
            LookHelper.lookAt(mod, this.target);
         }
      }

      @Override
      public boolean tick(PlayerEngineController mod) {
         this.elapsed++;
         float alpha = Math.min(1.0F, (float)this.elapsed / this.totalTicks);
         Rotation desired = interpolate(this.start, this.target, alpha);
         LookHelper.lookAt(mod, desired);
         return this.elapsed >= this.totalTicks;
      }

      private static Rotation interpolate(Rotation a, Rotation b, float t) {
         float aYaw = Rotation.normalizeYaw(a.getYaw());
         float bYaw = Rotation.normalizeYaw(b.getYaw());
         float yawDelta = shortestYawDelta(aYaw, bYaw);
         float yaw = Rotation.normalizeYaw(aYaw + yawDelta * t);
         float pitchDelta = b.getPitch() - a.getPitch();
         float pitch = Rotation.clampPitch(a.getPitch() + pitchDelta * t);
         return new Rotation(yaw, pitch);
      }

      private static float shortestYawDelta(float from, float to) {
         float diff = Rotation.normalizeYaw(to) - Rotation.normalizeYaw(from);
         if (diff > 180.0F) {
            diff -= 360.0F;
         }

         if (diff < -180.0F) {
            diff += 360.0F;
         }

         return diff;
      }
   }

   static final class ReleaseInputStep implements PrimitiveSequenceTask.Step {
      private final Input input;

      ReleaseInputStep(Input input) {
         this.input = input;
      }

      @Override
      public void start(PlayerEngineController mod) {
         mod.getInputControls().release(this.input);
      }

      @Override
      public boolean tick(PlayerEngineController mod) {
         return true;
      }
   }

   public static final class Sequence {
      private final Deque<PrimitiveSequenceTask.Step> steps = new ArrayDeque<>();

      private Sequence() {
      }

      public static PrimitiveSequenceTask.Sequence.Builder builder() {
         return new PrimitiveSequenceTask.Sequence.Builder();
      }

      Deque<PrimitiveSequenceTask.Step> buildSteps() {
         return this.steps;
      }

      public static final class Builder {
         private final PrimitiveSequenceTask.Sequence seq = new PrimitiveSequenceTask.Sequence();

         public PrimitiveSequenceTask.Sequence.Builder hold(Input input) {
            this.seq.steps.add(new PrimitiveSequenceTask.HoldInputStep(input));
            return this;
         }

         public PrimitiveSequenceTask.Sequence.Builder release(Input input) {
            this.seq.steps.add(new PrimitiveSequenceTask.ReleaseInputStep(input));
            return this;
         }

         public PrimitiveSequenceTask.Sequence.Builder waitTicks(int ticks) {
            this.seq.steps.add(new PrimitiveSequenceTask.WaitTicksStep(ticks));
            return this;
         }

         public PrimitiveSequenceTask.Sequence.Builder jump() {
            this.seq.steps.add(new PrimitiveSequenceTask.JumpStep());
            return this;
         }

         public PrimitiveSequenceTask.Sequence.Builder lookAbs(Rotation target, int ticks) {
            this.seq.steps.add(new PrimitiveSequenceTask.LookOverTimeStep(target, ticks));
            return this;
         }

         public PrimitiveSequenceTask.Sequence.Builder lookRelative(Rotation delta, int ticks) {
            this.seq.steps.add(new PrimitiveSequenceTask.LookOverTimeStep(delta, ticks, true));
            return this;
         }

         public PrimitiveSequenceTask build() {
            return new PrimitiveSequenceTask(this.seq);
         }
      }
   }

   interface Step {
      void start(PlayerEngineController var1);

      boolean tick(PlayerEngineController var1);

      default void stop(PlayerEngineController mod) {
      }
   }

   static final class TickCountdown {
      private int remaining;

      TickCountdown(int ticks) {
         this.remaining = Math.max(0, ticks);
      }

      boolean isDone() {
         return this.remaining <= 0;
      }

      void dec() {
         if (this.remaining > 0) {
            this.remaining--;
         }
      }
   }

   static final class WaitTicksStep implements PrimitiveSequenceTask.Step {
      private final PrimitiveSequenceTask.TickCountdown timer;

      WaitTicksStep(int ticks) {
         this.timer = new PrimitiveSequenceTask.TickCountdown(ticks);
      }

      @Override
      public void start(PlayerEngineController mod) {
      }

      @Override
      public boolean tick(PlayerEngineController mod) {
         this.timer.dec();
         return this.timer.isDone();
      }
   }
}
