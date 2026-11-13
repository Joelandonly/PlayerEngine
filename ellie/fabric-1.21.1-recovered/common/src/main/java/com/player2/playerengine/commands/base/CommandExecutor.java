package com.player2.playerengine.commands.base;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.nativecommands.NativeCommandText;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.helpers.FuzzySearchHelper;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CommandExecutor {
   private static final Map<String, String> COMMAND_ALIASES = Map.of("drop", "give");
   private final HashMap<String, Command> commandSheet = new HashMap<>();
   private final PlayerEngineController mod;

   public CommandExecutor(PlayerEngineController mod) {
      this.mod = mod;
   }

   public void registerNewCommand(Command... commands) {
      for (Command command : commands) {
         if (this.commandSheet.containsKey(command.getName())) {
            Debug.logInternal("Command with name " + command.getName() + " already exists! Can't register that name twice.");
         } else {
            this.commandSheet.put(command.getName(), command);
         }
      }
   }

   public String getCommandPrefix() {
      return this.mod.getModSettings().getCommandPrefix();
   }

   public boolean isClientCommand(String line) {
      return line.startsWith(this.getCommandPrefix());
   }

   private void executeRecursive(
      Command[] commands,
      String[] parts,
      int index,
      String accumulatedNote,
      Runnable onFinish,
      Consumer<String> onFinishWithNote,
      Consumer<CommandException> getException
   ) {
      if (index >= commands.length) {
         if (accumulatedNote != null && !accumulatedNote.isBlank()) {
            onFinishWithNote.accept(accumulatedNote);
         } else {
            onFinish.run();
         }
      } else {
         Command command = commands[index];
         String part = parts[index];

         try {
            if (command == null) {
               getException.accept(new CommandException("Invalid command:" + part));
               this.executeRecursive(commands, parts, index + 1, accumulatedNote, onFinish, onFinishWithNote, getException);
            } else {
               command.run(
                  this.mod,
                  part,
                  () -> this.executeRecursive(commands, parts, index + 1, accumulatedNote, onFinish, onFinishWithNote, getException),
                  getException,
                  note -> this.executeRecursive(commands, parts, index + 1, joinNotes(accumulatedNote, note), onFinish, onFinishWithNote, getException)
               );
            }
         } catch (CommandException var9) {
            getException.accept(new CommandException(var9.getMessage() + "\nUsage: " + command.getHelpRepresentation(), var9));
         }
      }
   }

   private static String joinNotes(String existing, String next) {
      if (next == null || next.isBlank()) {
         return existing;
      } else {
         return existing != null && !existing.isBlank() ? existing + "; " + next : next;
      }
   }

   public void execute(String line, Runnable onFinish, Consumer<CommandException> getException) {
      this.execute(line, () -> {}, onFinish, getException);
   }

   public void execute(String line, Runnable onAccepted, Runnable onFinish, Consumer<CommandException> getException) {
      this.execute(line, onAccepted, onFinish, note -> onFinish.run(), getException);
   }

   public void execute(String line, Runnable onAccepted, Runnable onFinish, Consumer<String> onFinishWithNote, Consumer<CommandException> getException) {
      if (this.isClientCommand(line)) {
         line = line.substring(this.getCommandPrefix().length());
         String[] parts = NativeCommandText.splitCommands(line);
         Command[] commands = new Command[parts.length];

         try {
            for (int i = 0; i < parts.length; i++) {
               commands[i] = this.getCommand(parts[i]);
            }
         } catch (CommandException var7) {
            getException.accept(var7);
            return;
         }

         if (onAccepted != null) {
            onAccepted.run();
         }

         this.executeRecursive(commands, parts, 0, null, onFinish, onFinishWithNote, getException);
      }
   }

   public void execute(String line, Consumer<CommandException> getException) {
      this.execute(line, () -> {}, () -> {}, getException);
   }

   public void execute(String line) {
      this.execute(line, ex -> Debug.logWarning(ex.getMessage()));
   }

   public void executeWithPrefix(String line) {
      if (!line.startsWith(this.getCommandPrefix())) {
         line = this.getCommandPrefix() + line;
      }

      this.execute(line);
   }

   public static String resolveName(String raw) {
      return raw == null ? null : COMMAND_ALIASES.getOrDefault(raw.toLowerCase(Locale.ROOT), raw);
   }

   private Command getCommand(String line) throws CommandException {
      line = line.trim();
      if (line.length() != 0) {
         String command = line;
         int firstSpace = line.indexOf(32);
         if (firstSpace != -1) {
            command = line.substring(0, firstSpace);
         }

         String target = resolveName(command);
         if (this.commandSheet.containsKey(target)) {
            // Management calls can overlap the tracked task. Give each call its own callbacks.
            if (target.equals("ellie_storage")) return new com.player2.playerengine.commands.EllieStorageCommand();
            if (target.equals("patrol")) return new com.player2.playerengine.commands.PatrolCommand();
            if (target.equals("netherbridge")) return new com.player2.playerengine.commands.NetherBridgeCommand();
            if (target.equals("hero")) return new com.player2.playerengine.commands.HeroCommand();
            return this.commandSheet.get(target);
         }

         String suggestion = FuzzySearchHelper.getClosestMatchWithinThreshold(command, this.commandNames());
         String message = "Command " + command + " does not exist.";
         if (suggestion != null) {
            message = message + " Did you mean \"" + suggestion + "\"?";
         }

         throw new UnknownCommandException(message);
      } else {
         return null;
      }
   }

   private List<String> commandNames() {
      return this.commandSheet.values().stream().map(Command::getName).collect(Collectors.toList());
   }

   public Collection<Command> allCommands() {
      return this.commandSheet.values();
   }

   public Command get(String name) {
      return this.commandSheet.getOrDefault(name, null);
   }
}
