package com.player2.playerengine.player2api;

import java.util.Locale;

final class RepeatedCommandFailureGuard {
   static final int MAX_IDENTICAL_FAILURES = 2;
   static final int MAX_MODEL_FAILURE_REASON_CHARS = 384;
   private static final int MAX_COMMAND_FINGERPRINT_CHARS = 256;
   private static final int MAX_COMMAND_ID_CHARS = 64;
   private static final String TRUNCATED_SUFFIX = " ...[truncated]";
   private String previousFingerprint = "";
   private int consecutiveFailures;

   RepeatedCommandFailureGuard.Decision record(String commandName, String failureReason) {
      String fingerprint = boundedCommandForModel(commandName).toLowerCase(Locale.ROOT) + "\n" + boundedFailureReason(failureReason);
      if (fingerprint.equals(this.previousFingerprint)) {
         this.consecutiveFailures++;
      } else {
         this.previousFingerprint = fingerprint;
         this.consecutiveFailures = 1;
      }

      return this.consecutiveFailures >= 2
         ? RepeatedCommandFailureGuard.Decision.HALT_AUTOMATIC_RETRY
         : RepeatedCommandFailureGuard.Decision.REPORT_AND_REPROMPT;
   }

   void reset() {
      this.previousFingerprint = "";
      this.consecutiveFailures = 0;
   }

   static String boundedFailureReason(String raw) {
      String normalized = normalizeWhitespace(raw);
      if (normalized.isEmpty()) {
         normalized = "command failed without a reason";
      }

      return bounded(normalized, 384);
   }

   static String commandId(String rawCommand) {
      String normalized = canonicalCommand(rawCommand);
      if (normalized.startsWith("@")) {
         normalized = normalized.substring(1);
      }

      int separator = normalized.length();
      int space = normalized.indexOf(32);
      int semicolon = normalized.indexOf(59);
      if (space >= 0) {
         separator = Math.min(separator, space);
      }

      if (semicolon >= 0) {
         separator = Math.min(separator, semicolon);
      }

      String id = normalized.substring(0, separator).strip();
      return id.isEmpty() ? "command" : bounded(id, 64);
   }

   static String boundedCommandForModel(String rawCommand) {
      String normalized = normalizeWhitespace(rawCommand);
      return bounded(normalized.isEmpty() ? "command" : normalized, 256);
   }

   private static String canonicalCommand(String raw) {
      return normalizeWhitespace(raw).toLowerCase(Locale.ROOT);
   }

   private static String normalizeWhitespace(String raw) {
      return raw == null ? "" : raw.replaceAll("[\\r\\n\\t ]+", " ").strip();
   }

   private static String bounded(String value, int maxChars) {
      if (value.length() <= maxChars) {
         return value;
      }

      int keep = Math.max(0, maxChars - " ...[truncated]".length());
      return value.substring(0, keep) + " ...[truncated]";
   }

   enum Decision {
      REPORT_AND_REPROMPT,
      HALT_AUTOMATIC_RETRY;
   }
}
