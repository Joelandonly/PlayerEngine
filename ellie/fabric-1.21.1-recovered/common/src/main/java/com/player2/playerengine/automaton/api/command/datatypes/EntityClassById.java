package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import java.util.stream.Stream;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public enum EntityClassById implements IDatatypeFor<EntityType<?>> {
   INSTANCE;

   public EntityType<?> get(IDatatypeContext ctx) throws CommandException {
      ResourceLocation id = ResourceLocation.tryParse(ctx.getConsumer().getString());
      EntityType<?> entity;
      if ((entity = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null)) == null) {
         throw new IllegalArgumentException("no entity found by that id");
      } else {
         return entity;
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(BuiltInRegistries.ENTITY_TYPE.stream().map(Object::toString))
         .filterPrefixNamespaced(ctx.getConsumer().getString())
         .sortAlphabetically()
         .stream();
   }
}
