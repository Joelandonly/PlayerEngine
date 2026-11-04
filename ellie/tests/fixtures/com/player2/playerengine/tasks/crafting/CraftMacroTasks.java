package com.player2.playerengine.tasks.crafting;
/** Fail if setup accidentally re-enters dynamic resolution instead of the native catalogue. */
public final class CraftMacroTasks {public static Object macroOutcome(Object controller,Object target){throw new AssertionError("Setup must use native acquisition, not dynamic macro resolution");}}
