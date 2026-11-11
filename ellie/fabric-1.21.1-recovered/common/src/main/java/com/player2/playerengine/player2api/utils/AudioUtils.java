package com.player2.playerengine.player2api.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine.Info;

public class AudioUtils {
   private static final String WEB_API_URL = "https://api.player2.game";
   private static final int HTTP_CONNECT_TIMEOUT_MS = 30000;
   private static final int HTTP_READ_TIMEOUT_MS = 120000;
   private static final AtomicInteger activePlaybackCount = new AtomicInteger();

   public static boolean isPlaybackActive() {
      return activePlaybackCount.get() > 0;
   }

   public static void streamAudio(String clientId, String token, String text, double speed, String[] voiceIds) {
      playAudioBytes(fetchAudioBytes(clientId, token, text, speed, voiceIds));
   }

   public static byte[] fetchAudioBytes(String clientId, String token, String text, double speed, String[] voiceIds) {
      String localApiUrl = LocalAPIDiscovery.getLocalApiUrl();
      if (localApiUrl != null) {
         byte[] local = fetchViaLocalApi(localApiUrl, clientId, token, text, speed, voiceIds);
         if (local != null) {
            return local;
         }
      }

      return fetchViaWebApi(clientId, token, text, speed, voiceIds);
   }

   public static void playAudioBytes(byte[] wavBytes) {
      if (wavBytes != null && wavBytes.length != 0) {
         activePlaybackCount.incrementAndGet();

         try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(wavBytes)))) {
            AudioFormat format = audioStream.getFormat();
            Info info = new Info(SourceDataLine.class, format);

            try (SourceDataLine sourceDataLine = (SourceDataLine)AudioSystem.getLine(info)) {
               sourceDataLine.open(format);
               sourceDataLine.start();
               byte[] buffer = new byte[4096];

               int bytesRead;
               while ((bytesRead = audioStream.read(buffer)) != -1) {
                  sourceDataLine.write(buffer, 0, bytesRead);
               }

               sourceDataLine.drain();
               sourceDataLine.stop();
            }
         } catch (Exception e) {
            System.err.println("Error playing TTS audio: " + e.getMessage());
         } finally {
            activePlaybackCount.decrementAndGet();
         }
      }
   }

   private static byte[] fetchViaLocalApi(String localApiUrl, String clientId, String token, String text, double speed, String[] voiceIds) {
      HttpURLConnection connection = null;

      try {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("text", text);
         requestBody.addProperty("speed", speed);
         requestBody.addProperty("play_in_app", false);
         requestBody.addProperty("audio_format", "wav");
         JsonArray voiceIdsArray = new JsonArray();

         for (String id : voiceIds) {
            voiceIdsArray.add(id);
         }

         requestBody.add("voice_ids", voiceIdsArray);
         URL url = new URL(localApiUrl + "/v1/tts/speak");
         connection = (HttpURLConnection)url.openConnection();
         connection.setConnectTimeout(30000);
         connection.setReadTimeout(120000);
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("player2-game-key", clientId);
         connection.setRequestProperty("Authorization", "Bearer " + token);
         connection.setDoOutput(true);

         try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
         }

         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            System.err.println("Local TTS /tts/speak returned HTTP " + responseCode + ", falling back to web API.");
            return null;
         }

         StringBuilder responseBuilder = new StringBuilder();
         BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));

         String line;
         try {
            while ((line = reader.readLine()) != null) {
               responseBuilder.append(line);
            }
         } catch (Throwable var26) {
            try {
               reader.close();
            } catch (Throwable var23) {
               var26.addSuppressed(var23);
            }

            throw var26;
         }

         reader.close();
         JsonObject responseJson = JsonParser.parseString(responseBuilder.toString()).getAsJsonObject();
         line = responseJson.get("data").getAsString();
         if (line != null && !line.isEmpty()) {
            return Base64.getDecoder().decode(line);
         }

         System.err.println("Local TTS returned empty audio data, falling back to web API.");
         return null;
      } catch (Exception e) {
         System.err.println("Error during local TTS, falling back to web API: " + e.getMessage());
         return null;
      } finally {
         if (connection != null) {
            connection.disconnect();
         }
      }
   }

   private static byte[] fetchViaWebApi(String clientId, String token, String text, double speed, String[] voiceIds) {
      HttpURLConnection connection = null;

      try {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("text", text);
         requestBody.addProperty("speed", speed);
         requestBody.addProperty("audio_format", "wav");
         JsonArray voiceIdsArray = new JsonArray();

         for (String id : voiceIds) {
            voiceIdsArray.add(id);
         }

         requestBody.add("voice_ids", voiceIdsArray);
         URL url = new URL("https://api.player2.game/v1/tts/stream");
         connection = (HttpURLConnection)url.openConnection();
         connection.setConnectTimeout(30000);
         connection.setReadTimeout(120000);
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("Accept", "audio/wav");
         connection.setRequestProperty("player2-game-key", clientId);
         connection.setRequestProperty("Authorization", "Bearer " + token);
         connection.setDoOutput(true);

         try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
         }

         try (InputStream inputStream = connection.getInputStream()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
               baos.write(buffer, 0, bytesRead);
            }

            return baos.toByteArray();
         }
      } catch (Exception e) {
         System.err.println("Error during TTS streaming: " + e.getMessage());
         e.printStackTrace();
         return null;
      } finally {
         if (connection != null) {
            connection.disconnect();
         }
      }
   }
}
