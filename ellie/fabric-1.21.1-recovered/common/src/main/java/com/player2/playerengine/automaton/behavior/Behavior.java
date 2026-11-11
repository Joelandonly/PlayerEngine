package com.player2.playerengine.automaton.behavior;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.behavior.IBehavior;
import com.player2.playerengine.automaton.api.utils.IEntityContext;

public class Behavior implements IBehavior {
   public final Baritone baritone;
   public final IEntityContext ctx;

   protected Behavior(Baritone baritone) {
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
      baritone.registerBehavior(this);
   }
}
