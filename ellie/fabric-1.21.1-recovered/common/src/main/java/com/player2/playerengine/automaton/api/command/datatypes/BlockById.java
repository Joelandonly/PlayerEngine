package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import java.util.stream.Stream;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public enum BlockById implements IDatatypeFor<Block> {
   INSTANCE;

   public Block get(IDatatypeContext ctx) throws CommandException {
      ResourceLocation id = ResourceLocation.tryParse(ctx.getConsumer().getString());
      Block block;
      if ((block = (Block)BuiltInRegistries.BLOCK.getOptional(id).orElse(null)) == null) {
         throw new IllegalArgumentException("no block found by that id");
      } else {
         return block;
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString))
         .filterPrefixNamespaced(ctx.getConsumer().getString())
         .sortAlphabetically()
         .stream();
   }
}
