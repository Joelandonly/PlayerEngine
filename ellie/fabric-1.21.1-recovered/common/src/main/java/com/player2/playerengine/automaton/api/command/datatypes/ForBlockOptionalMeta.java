package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;
import java.util.stream.Stream;

public enum ForBlockOptionalMeta implements IDatatypeFor<BlockOptionalMeta> {
   INSTANCE;

   public BlockOptionalMeta get(IDatatypeContext ctx) throws CommandException {
      return new BlockOptionalMeta(ctx.getBaritone().getEntityContext().world(), ctx.getConsumer().getString());
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      return ctx.getConsumer().tabCompleteDatatype(BlockById.INSTANCE);
   }
}
