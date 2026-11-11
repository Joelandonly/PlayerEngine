package com.player2.playerengine.automaton;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.IBaritoneProvider;
import com.player2.playerengine.automaton.api.Settings;
import com.player2.playerengine.automaton.api.cache.IWorldScanner;
import com.player2.playerengine.automaton.api.command.ICommandSystem;
import com.player2.playerengine.automaton.api.schematic.ISchematicSystem;
import com.player2.playerengine.automaton.cache.WorldScanner;
import com.player2.playerengine.automaton.command.CommandSystem;
import com.player2.playerengine.automaton.utils.SettingsLoader;
import com.player2.playerengine.automaton.utils.schematic.SchematicSystem;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;

public final class BaritoneProvider implements IBaritoneProvider {
   public static final BaritoneProvider INSTANCE = new BaritoneProvider();
   private final Settings settings = new Settings();

   public BaritoneProvider() {
      SettingsLoader.readAndApply(this.settings);
   }

   @Override
   public IBaritone getBaritone(LivingEntity entity) {
      if (entity.level().isClientSide()) {
         throw new IllegalStateException("Lol we only support servers now");
      } else {
         return IBaritone.KEY.get(entity);
      }
   }

   public boolean isPathing(LivingEntity entity) {
      IBaritone baritone = IBaritone.KEY.getNullable(entity);
      return baritone != null && baritone.isActive();
   }

   @Override
   public IWorldScanner getWorldScanner() {
      return WorldScanner.INSTANCE;
   }

   @Override
   public ICommandSystem getCommandSystem() {
      return CommandSystem.INSTANCE;
   }

   @Override
   public ISchematicSystem getSchematicSystem() {
      return SchematicSystem.INSTANCE;
   }

   @Override
   public Settings getGlobalSettings() {
      return this.settings;
   }

   @Override
   public <E extends LivingEntity> Function<E, IBaritone> componentFactory() {
      return Baritone::new;
   }
}
