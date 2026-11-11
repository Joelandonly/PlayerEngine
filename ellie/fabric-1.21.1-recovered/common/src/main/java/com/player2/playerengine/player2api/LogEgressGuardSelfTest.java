package com.player2.playerengine.player2api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public final class LogEgressGuardSelfTest {
   private LogEgressGuardSelfTest() {
   }

   public static void main(String[] args) {
      int failed = 0;
      failed += run("single message cap does not mutate source", () -> {
         JsonObject original = message("user", repeat("a", 32868));
         JsonObject capped = LogEgressGuard.cappedMessage(original);
         assertTrue(content(capped).length() <= 32768, "marker stays inside the hard cap");
         assertTrue(content(capped).contains("TRUNCATED"), "marker appended");
         assertEq(32868, content(original).length(), "source length");
      });
      failed += run("message allowlist strips unknown and malformed fields", () -> {
         JsonObject unknown = message("user", "safe");
         unknown.addProperty("unexpected", repeat("x", 100000));
         JsonObject sanitized = LogEgressGuard.cappedMessage(unknown);
         assertTrue(!sanitized.has("unexpected"), "unknown request fields must be removed");
         assertEq("safe", content(sanitized), "valid content retained");
         JsonObject structured = new JsonObject();
         structured.addProperty("role", "user");
         structured.add("content", new JsonArray());
         JsonObject replaced = LogEgressGuard.cappedMessage(structured);
         assertEq("system", replaced.get("role").getAsString(), "malformed message role");
         assertTrue(content(replaced).contains("malformed"), "malformed content must be model-visible");
         JsonObject oversizedRole = message(repeat("r", 100000), "small");
         JsonObject roleReplaced = LogEgressGuard.cappedMessage(oversizedRole);
         assertEq("system", roleReplaced.get("role").getAsString(), "oversized role rejected");
      });
      failed += run("request message count is bounded", () -> {
         List<JsonObject> history = new ArrayList<>();

         for (int i = 0; i < 100; i++) {
            history.add(message("user", "message-" + i));
         }

         JsonArray capped = LogEgressGuard.cappedMessages(history, "message-count");
         assertTrue(capped.size() <= 32, "serialized message count must be bounded");
         assertTrue(containsContent(capped, "Context notice:"), "model must see count-based history omission");
         assertTrue(containsContent(capped, "message-99"), "newest message must survive count culling");
      });
      failed += run("whole request cap keeps newest turns", () -> {
         List<JsonObject> history = new ArrayList<>();
         history.add(message("system", repeat("s", 32768)));

         for (int i = 0; i < 12; i++) {
            history.add(message(i % 2 == 0 ? "user" : "assistant", "old-" + i + " " + repeat("x", 32768)));
         }

         history.add(message("user", "latest " + repeat("z", 32768)));
         JsonArray capped = LogEgressGuard.cappedMessages(history, "self-test");
         assertTrue(totalContentChars(capped) <= 24576, "bounded total content including markers");
         assertEq("system", capped.get(0).getAsJsonObject().get("role").getAsString(), "system retained");
         assertTrue(content(capped.get(capped.size() - 1).getAsJsonObject()).startsWith("latest"), "latest turn retained");
         assertTrue(capped.size() < history.size(), "old turns omitted");
      });
      failed += run("within-budget request preserves full system prompt", () -> {
         String system = repeat("s", 13312);
         List<JsonObject> history = List.of(message("system", system), message("user", "small request"));
         JsonArray capped = LogEgressGuard.cappedMessages(history, "system-preservation");
         assertEq(system.length(), content(capped.get(0).getAsJsonObject()).length(), "system prompt must not be truncated when total request fits");
         assertEq(2, capped.size(), "fitting request message count");
      });
      failed += run("whole request cap keeps a contiguous newest suffix", () -> {
         List<JsonObject> history = new ArrayList<>();
         history.add(message("system", repeat("s", 100)));
         history.add(message("user", "oldest-small-message"));
         history.add(message("assistant", repeat("m", 20000)));
         history.add(message("user", repeat("n", 5000)));
         JsonArray capped = LogEgressGuard.cappedMessages(history, "contiguous-suffix");
         assertTrue(totalContentChars(capped) <= 24576, "contiguous suffix request stays bounded");
         assertTrue(!containsContent(capped, "oldest-small-message"), "scan must not jump across a non-fitting middle message");
         assertTrue(containsContent(capped, "Context notice:"), "model must see that older context was omitted");
      });
      failed += run("completion token cap preserves lower values", () -> {
         JsonObject request = new JsonObject();
         request.addProperty("max_tokens", 128);
         LogEgressGuard.applyChatCompletionRequestCaps(request, "self-test");
         assertEq(128, request.get("max_tokens").getAsInt(), "lower cap preserved");
      });
      failed += run("completion token cap fills and clamps", () -> {
         JsonObject absent = new JsonObject();
         LogEgressGuard.applyChatCompletionRequestCaps(absent, "self-test");
         assertEq(2048, absent.get("max_tokens").getAsInt(), "absent cap filled");
         JsonObject excessive = new JsonObject();
         excessive.addProperty("max_tokens", 2049);
         LogEgressGuard.applyChatCompletionRequestCaps(excessive, "self-test");
         assertEq(2048, excessive.get("max_tokens").getAsInt(), "excessive cap clamped");
      });
      failed += run("completion token cap stays context-safe", () -> {
         assertEq(24576, 24576, "conservative input content budget");
         assertEq(2048, 2048, "conservative output token budget");
         JsonObject configured = new JsonObject();
         configured.addProperty("max_tokens", 2049);
         LogEgressGuard.applyChatCompletionRequestCaps(configured, "self-test");
         assertEq(2048, configured.get("max_tokens").getAsInt(), "context-safe cap");
      });
      failed += run("live retry transcript shape stays below request budget", () -> {
         List<JsonObject> history = new ArrayList<>();
         history.add(message("system", repeat("s", 6849)));

         for (int i = 0; i < 14; i++) {
            history.add(message(i % 2 == 0 ? "user" : "assistant", repeat("h", 250)));
         }

         history.add(message("user", repeat("v", 16431)));
         JsonArray capped = LogEgressGuard.cappedMessages(history, "live-retry-regression");
         assertTrue(totalContentChars(capped) <= 24576, "captured request shape must stay inside conservative budget");
         assertEq(16431, content(capped.get(capped.size() - 1).getAsJsonObject()).length(), "newest status/tool turn retained whole");
         assertTrue(capped.size() < history.size(), "older retry history must be culled first");
      });
      if (failed == 0) {
         System.out.println("LogEgressGuardSelfTest: PASS (10 cases)");
      } else {
         System.err.println("LogEgressGuardSelfTest: FAIL (" + failed + " case(s) failed)");
         System.exit(1);
      }
   }

   private static JsonObject message(String role, String content) {
      JsonObject msg = new JsonObject();
      msg.addProperty("role", role);
      msg.addProperty("content", content);
      return msg;
   }

   private static String content(JsonObject msg) {
      return msg.get("content").getAsString();
   }

   private static int totalContentChars(JsonArray messages) {
      int total = 0;

      for (int i = 0; i < messages.size(); i++) {
         total += content(messages.get(i).getAsJsonObject()).length();
      }

      return total;
   }

   private static boolean containsContent(JsonArray messages, String needle) {
      for (int i = 0; i < messages.size(); i++) {
         if (content(messages.get(i).getAsJsonObject()).contains(needle)) {
            return true;
         }
      }

      return false;
   }

   private static String repeat(String value, int count) {
      StringBuilder sb = new StringBuilder(value.length() * count);

      for (int i = 0; i < count; i++) {
         sb.append(value);
      }

      return sb.toString();
   }

   private static int run(String name, Runnable test) {
      try {
         test.run();
         System.out.println("  OK  " + name);
         return 0;
      } catch (AssertionError e) {
         System.err.println("  FAIL " + name + ": " + e.getMessage());
         return 1;
      } catch (Exception e) {
         System.err.println("  FAIL " + name + ": " + e);
         return 1;
      }
   }

   private static void assertEq(int expected, int actual, String label) {
      if (expected != actual) {
         throw new AssertionError(label + ": expected " + expected + " got " + actual);
      }
   }

   private static void assertEq(String expected, String actual, String label) {
      if (!expected.equals(actual)) {
         throw new AssertionError(label + ": expected '" + expected + "' got '" + actual + "'");
      }
   }

   private static void assertTrue(boolean cond, String label) {
      if (!cond) {
         throw new AssertionError(label + ": expected true");
      }
   }
}
