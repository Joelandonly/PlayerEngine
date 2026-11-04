package com.player2.playerengine.util.helpers;
import java.util.Optional;import net.minecraft.class_2338;
/** Visibility/reach boundary only. Real patched Task.tick is exercised separately. */
public final class LookHelper {public static Optional<Object> getReach(Object controller,class_2338 target)throws Exception{return Boolean.TRUE.equals(controller.getClass().getField("reachable").get(controller))?Optional.of(new Object()):Optional.empty();}}
