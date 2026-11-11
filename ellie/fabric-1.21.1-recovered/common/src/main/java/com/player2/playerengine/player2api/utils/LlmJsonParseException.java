package com.player2.playerengine.player2api.utils;

public class LlmJsonParseException extends Exception {
   public static final String SENTINEL = "LLM_JSON_PARSE_FAILURE";
   private final String rawContent;

   public LlmJsonParseException(String rawContent, Throwable cause) {
      super("LLM_JSON_PARSE_FAILURE: model reply was not valid JSON", cause);
      this.rawContent = rawContent;
   }

   public String getRawContent() {
      return this.rawContent;
   }
}
