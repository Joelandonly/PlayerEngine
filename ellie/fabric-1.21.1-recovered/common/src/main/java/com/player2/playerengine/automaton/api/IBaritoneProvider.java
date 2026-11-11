package com.player2.playerengine.automaton.api;

import com.player2.playerengine.automaton.api.cache.IWorldScanner;
import com.player2.playerengine.automaton.api.command.ICommandSystem;
import com.player2.playerengine.automaton.api.schematic.ISchematicSystem;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;

public interface IBaritoneProvider {
   IBaritone getBaritone(LivingEntity var1);

   IWorldScanner getWorldScanner();

   ICommandSystem getCommandSystem();

   ISchematicSystem getSchematicSystem();

   Settings getGlobalSettings();

   <E extends LivingEntity> Function<E, IBaritone> componentFactory();
}
