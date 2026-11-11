package com.player2.playerengine.supervisor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

public final class CoreGoalJournal {
   private static final String DIRECTORY = "ellie-core-goals";
   private final Path file;
   private final Properties data = new Properties();
   private String error;

   private CoreGoalJournal(Path var1) {
      this.file = var1;
      if (var1 == null) {
         this.error = "world journal location not available";
      } else if (Files.isRegularFile(var1)) {
         try (InputStream var2 = Files.newInputStream(var1)) {
            this.data.load(var2);
            if (!"1".equals(this.data.getProperty("schema"))) {
               this.data.clear();
               this.error = "unsupported journal schema";
            }
         } catch (IOException | IllegalArgumentException var7) {
            this.data.clear();
            this.error = "journal read failed: " + var7.getClass().getSimpleName();
         }
      }
   }

   public static CoreGoalJournal transientJournal() {
      return new CoreGoalJournal(null);
   }

   public static CoreGoalJournal open(Path var0, String var1, String var2) {
      if (var0 != null && var1 != null && !var1.isBlank() && var2 != null && !var2.isBlank()) {
         String var3 = var1 + "\u0000" + var2;

         String var4;
         try {
            byte[] var5 = MessageDigest.getInstance("SHA-256").digest(var3.getBytes(StandardCharsets.UTF_8));
            var4 = HexFormat.of().formatHex(var5, 0, 12);
         } catch (NoSuchAlgorithmException var6) {
            return transientJournal();
         }

         Path var7 = var0.resolve("ellie-core-goals").resolve("goal-" + var4 + ".properties");
         return new CoreGoalJournal(var7);
      } else {
         return transientJournal();
      }
   }

   public synchronized String get(String var1, String var2) {
      return this.data.getProperty(var1, var2);
   }

   public synchronized void put(String var1, int var2, String var3, String var4) {
      this.data.setProperty("schema", "1");
      this.data.setProperty("status", safe(var1, 24));
      this.data.setProperty("step", Integer.toString(Math.max(0, var2)));
      this.data.setProperty("resource", safe(var3, 96));
      this.data.setProperty("detail", safe(var4, 360));
      this.data.setProperty("updatedAt", Long.toString(System.currentTimeMillis()));
      this.save();
   }

   public synchronized void noteOptionalFailure(String var1, String var2) {
      String var3 = "optional." + safe(var1, 48).replaceAll("[^a-zA-Z0-9_-]", "_");
      this.data.setProperty(var3 + ".time", Long.toString(System.currentTimeMillis()));
      this.data.setProperty(var3 + ".materials", safe(var2, 300));
      this.save();
   }

   public synchronized boolean optionalCooldown(String var1, String var2, long var3) {
      String var5 = "optional." + safe(var1, 48).replaceAll("[^a-zA-Z0-9_-]", "_");
      if (!safe(var2, 300).equals(this.data.getProperty(var5 + ".materials"))) {
         return false;
      }

      try {
         long var6 = Long.parseLong(this.data.getProperty(var5 + ".time", "0"));
         return var3 >= var6 && var3 - var6 < 120000L;
      } catch (NumberFormatException var8) {
         return false;
      }
   }

   public synchronized String error() {
      return this.error;
   }

   public boolean isPersistent() {
      return this.file != null && this.error == null;
   }

   private void save() {
      if (this.file != null) {
         Path var1 = null;

         try {
            Files.createDirectories(this.file.getParent());
            var1 = Files.createTempFile(this.file.getParent(), "ellie-goal-", ".tmp");

            try (OutputStream var2 = Files.newOutputStream(var1)) {
               this.data.store(var2, "Ellie goal checkpoints; verify against world state on every restart");
            }

            try {
               Files.move(var1, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var17) {
               Files.move(var1, this.file, StandardCopyOption.REPLACE_EXISTING);
            }

            this.error = null;
         } catch (IOException | SecurityException var19) {
            this.error = "journal write failed: " + var19.getClass().getSimpleName();
         } finally {
            if (var1 != null) {
               try {
                  Files.deleteIfExists(var1);
               } catch (IOException var15) {
               }
            }
         }
      }
   }

   private static String safe(String var0, int var1) {
      if (var0 == null) {
         return "";
      }

      String var2 = var0.replace('\n', ' ').replace('\r', ' ').replace('\u0000', ' ');
      return var2.length() <= var1 ? var2 : var2.substring(0, var1);
   }
}
