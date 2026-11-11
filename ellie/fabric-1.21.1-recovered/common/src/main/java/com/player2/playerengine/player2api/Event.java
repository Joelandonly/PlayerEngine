package com.player2.playerengine.player2api;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public sealed interface Event permits Event.UserMessage, Event.CharacterMessage, Event.InfoMessage {
   String message();

   String getConversationHistoryString();

   record CharacterMessage(String message, String command, AgentConversationData sendingCharacterData, @Nullable String originatingUserName) implements Event {
      public CharacterMessage(String message, String command, AgentConversationData sendingCharacterData) {
         this(message, command, sendingCharacterData, null);
      }

      @Override
      public String getConversationHistoryString() {
         return String.format("Other AI Message: [%s]: %s", this.sendingCharacterData.getName(), this.message);
      }

      @Override
      public String toString() {
         return String.format(
            "CharacterMessage(name='%s', message='%s', command='%s', originatingUser=%s)",
            this.sendingCharacterData.getName(),
            this.message,
            this.command,
            this.originatingUserName
         );
      }
   }

   record InfoMessage(String message) implements Event {
      @Override
      public String getConversationHistoryString() {
         return String.format("Info: %s", this.message);
      }

      @Override
      public String toString() {
         return this.getConversationHistoryString();
      }
   }

   record UserMessage(String message, String userName, boolean fromVoice, @Nullable UUID authenticatedUserUuid) implements Event {
      public UserMessage(String message, String userName) {
         this(message, userName, false, null);
      }

      public UserMessage(String message, String userName, boolean fromVoice) {
         this(message, userName, fromVoice, null);
      }

      public Event.UserMessage withMessage(String replacement) {
         return new Event.UserMessage(replacement, this.userName, this.fromVoice, this.authenticatedUserUuid);
      }

      @Override
      public String getConversationHistoryString() {
         return String.format("User Message: [%s]: %s", this.userName, this.message);
      }

      @Override
      public String toString() {
         return this.fromVoice
            ? String.format("UserMessage(userName='%s', message='%s', fromVoice=true)", this.userName, this.message)
            : String.format("UserMessage(userName='%s', message='%s')", this.userName, this.message);
      }
   }
}
