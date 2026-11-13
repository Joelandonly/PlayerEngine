package com.player2.playerengine.commands.base;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.supervisor.CommandCompletion;
import com.player2.playerengine.util.Debug;
import java.util.function.Consumer;

public abstract class Command {
   protected final ArgParser parser;
   private final String name;
   private final String description;
   protected PlayerEngineController mod;
   private Runnable onFinish = null;
   private Consumer<CommandException> onError = null;
   private Consumer<String> onFinishWithNote = null;
   private boolean ended = false;

   public Command(String name, String description, ArgBase... args) {
      this.name = name;
      this.description = description;
      this.parser = new ArgParser(args);
   }

   public void run(PlayerEngineController mod, String line, Runnable onFinish) throws CommandException {
      this.run(mod, line, onFinish, null);
   }

   public void run(PlayerEngineController mod, String line, Runnable onFinish, Consumer<CommandException> onError) throws CommandException {
      this.run(mod, line, onFinish, onError, null);
   }

   public void run(PlayerEngineController mod, String line, Runnable onFinish, Consumer<CommandException> onError, Consumer<String> onFinishWithNote) throws CommandException {
      this.onFinish = onFinish;
      this.onError = onError;
      this.onFinishWithNote = onFinishWithNote;
      this.ended = false;
      this.mod = mod;
      this.parser.loadArgs(line, true);
      CommandCompletion.invoke(this, mod, this.parser);
   }

   protected void finish() {
      if (!CommandCompletion.beforeFinish(this, null, false)) {
         if (!this.ended) {
            this.ended = true;
            if (this.onFinish != null) {
               this.onFinish.run();
            }
         }
      }
   }

   protected void finishWithError(String message) {
      this.finishException(new CommandException(message));
   }

   protected void finishWithCancellation(String message) {
      this.finishException(new CommandException.Cancelled(message));
   }

   private void finishException(CommandException exception) {
      if (!this.ended) {
         this.ended = true;
         if (this.onError != null) {
            this.onError.accept(exception);
         } else if (this.onFinish != null) {
            this.onFinish.run();
         }
      }
   }

   protected void finishWithNote(String note) {
      if (!CommandCompletion.beforeFinish(this, note, true)) {
         if (!this.ended) {
            this.ended = true;
            if (note != null && !note.isBlank()) {
               if (this.onFinishWithNote != null) {
                  this.onFinishWithNote.accept(note);
               } else if (this.onFinish != null) {
                  this.onFinish.run();
               }
            } else {
               if (this.onFinish != null) {
                  this.onFinish.run();
               }
            }
         }
      }
   }

   protected void finishWithInfo(String payload) {
      if (payload != null && !payload.isBlank()) {
         this.finishWithNote("[info-result] " + payload);
      } else {
         this.finish();
      }
   }

   public String getHelpRepresentation() {
      StringBuilder sb = new StringBuilder(this.name);

      for (ArgBase arg : this.parser.getArgs()) {
         sb.append(" ");
         sb.append(arg.getHelpRepresentation());
      }

      return sb.toString();
   }

   protected void log(Object message) {
      Debug.logMessage(message.toString());
   }

   protected void logError(Object message) {
      Debug.logError(message.toString());
   }

   protected abstract void call(PlayerEngineController var1, ArgParser var2) throws CommandException;

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }
}
