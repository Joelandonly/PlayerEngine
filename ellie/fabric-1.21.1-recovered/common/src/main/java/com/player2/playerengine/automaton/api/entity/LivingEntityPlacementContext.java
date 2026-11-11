package com.player2.playerengine.automaton.api.entity;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.entity.LivingEntity;

public final class LivingEntityPlacementContext {
   private static final ThreadLocal<Deque<LivingEntity>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

   private LivingEntityPlacementContext() {
   }

   public static void push(LivingEntity entity) {
      STACK.get().push(entity);
   }

   public static void pop() {
      Deque<LivingEntity> d = STACK.get();
      if (!d.isEmpty()) {
         d.pop();
      }
   }

   public static LivingEntity peek() {
      return STACK.get().peek();
   }
}
