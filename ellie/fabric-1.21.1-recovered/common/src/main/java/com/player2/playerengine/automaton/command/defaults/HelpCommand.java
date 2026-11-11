package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.ICommand;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandNotFoundException;
import com.player2.playerengine.automaton.api.command.helpers.Paginator;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import com.player2.playerengine.automaton.api.command.manager.ICommandManager;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class HelpCommand extends Command {
   public HelpCommand() {
      super("help", "?");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      this.execute(source, label, args);
   }

   public void execute(CommandSourceStack source, String label, IArgConsumer args) throws CommandException {
      args.requireMax(1);
      if (args.hasAny() && !args.is(Integer.class)) {
         String commandName = args.getString().toLowerCase();
         ICommand command = ICommandManager.getCommand(commandName);
         if (command == null) {
            throw new CommandNotFoundException(commandName);
         }

         this.logDirect(source, String.format("%s - %s", String.join(" / ", command.getNames()), command.getShortDesc()));
         this.logDirect(source, "");
         command.getLongDesc().forEach(message -> this.logDirect(source, message));
         this.logDirect(source, "");
         MutableComponent returnComponent = Component.literal("Click to return to the help menu");
         returnComponent.setStyle(returnComponent.getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/automatone " + label)));
         this.logDirect(source, returnComponent);
      } else {
         Paginator.paginate(
            args,
            new Paginator<>(source, ICommandManager.registry.descendingStream().filter(commandx -> !commandx.hiddenFromHelp()).collect(Collectors.toList())),
            () -> this.logDirect(source, "All Automatone commands (clickable):"),
            commandx -> {
               String names = String.join("/", commandx.getNames());
               String name = commandx.getNames().get(0);
               MutableComponent shortDescComponent = Component.literal(" - " + commandx.getShortDesc());
               shortDescComponent.setStyle(shortDescComponent.getStyle().applyFormat(ChatFormatting.DARK_GRAY));
               MutableComponent namesComponent = Component.literal(names);
               namesComponent.setStyle(namesComponent.getStyle().applyFormat(ChatFormatting.WHITE));
               MutableComponent hoverComponent = Component.literal("");
               hoverComponent.setStyle(hoverComponent.getStyle().applyFormat(ChatFormatting.GRAY));
               hoverComponent.append(namesComponent);
               hoverComponent.append("\n" + commandx.getShortDesc());
               hoverComponent.append("\n\nClick to view full help");
               String clickCommand = "/automatone " + String.format("%s %s", label, commandx.getNames().get(0));
               MutableComponent component = Component.literal(name);
               component.setStyle(component.getStyle().applyFormat(ChatFormatting.GRAY));
               component.append(shortDescComponent);
               component.setStyle(
                  component.getStyle()
                     .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent))
                     .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand))
               );
               return component;
            },
            "/automatone " + label
         );
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      return args.hasExactlyOne() ? new TabCompleteHelper().addCommands().filterPrefix(args.getString()).stream() : Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "View all commands or help on specific ones";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Using this command, you can view detailed help information on how to use certain commands of Baritone.",
         "",
         "Usage:",
         "> help - Lists all commands and their short descriptions.",
         "> help <command> - Displays help information on a specific command."
      );
   }
}
