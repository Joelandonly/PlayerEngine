package com.player2.playerengine.automaton.api.entity;
import java.util.*;
/** Existing NPC context boundary; actual native prediction and proxy helpers execute unchanged. */
public final class LivingEntityPlacementContext {
 private static final ThreadLocal<Deque<Object>> STACK=ThreadLocal.withInitial(ArrayDeque::new);
 public static void push(Object e){STACK.get().push(e);}public static void pop(){STACK.get().pop();}public static Object peek(){return STACK.get().peek();}
}
