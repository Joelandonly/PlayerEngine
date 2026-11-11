package com.player2.playerengine.memory.dense;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class DenseIndexState {
   public static final String FILE_NAME = "dense-index.bin";
   private static final int MAGIC = 1346653513;
   private static final int FORMAT_VERSION = 1;
   private final Map<String, float[]> vectors;
   private boolean backfillComplete;

   private DenseIndexState(Map<String, float[]> vectors, boolean backfillComplete) {
      this.vectors = vectors;
      this.backfillComplete = backfillComplete;
   }

   public static DenseIndexState empty() {
      return new DenseIndexState(new LinkedHashMap<>(), false);
   }

   public DenseIndexState copy() {
      Map<String, float[]> c = new LinkedHashMap<>(Math.max(16, this.vectors.size() * 2));

      for (Entry<String, float[]> e : this.vectors.entrySet()) {
         c.put(e.getKey(), (float[])e.getValue().clone());
      }

      return new DenseIndexState(c, this.backfillComplete);
   }

   public static String modelIdentityToken() {
      return EmbeddingModel.vectorModelToken();
   }

   public boolean backfillComplete() {
      return this.backfillComplete;
   }

   public void setBackfillComplete(boolean done) {
      this.backfillComplete = done;
   }

   public int size() {
      return this.vectors.size();
   }

   public float[] vector(String nodeId) {
      return this.vectors.get(nodeId);
   }

   public void put(String nodeId, float[] vector) {
      if (nodeId != null && vector != null && vector.length == 1536) {
         this.vectors.put(nodeId, (float[])vector.clone());
      }
   }

   public void remove(String nodeId) {
      this.vectors.remove(nodeId);
   }

   public void pruneTo(MemoryGraph graph) {
      if (graph != null) {
         this.vectors.keySet().removeIf(id -> graph.node(id) == null);
      }
   }

   public List<String> cosineRankedIds(MemoryGraph graph, float[] queryVector, int topK) {
      if (graph != null && queryVector != null && queryVector.length == 1536) {
         double qNorm = norm(queryVector);
         if (qNorm <= 0.0) {
            return List.of();
         }

         List<double[]> scored = new ArrayList<>();
         List<String> ids = new ArrayList<>();

         for (MemoryNode n : graph.nodes()) {
            float[] v = this.vectors.get(n.id());
            if (v == null && n.hasVector()) {
               float[] raw = n.vector();
               if (raw != null && raw.length == 1536 && EmbeddingModel.vectorModelToken().equals(n.vectorModel())) {
                  v = raw;
               }
            }

            if (v != null) {
               double cos = cosine(queryVector, qNorm, v);
               scored.add(new double[]{cos, ids.size()});
               ids.add(n.id());
            }
         }

         scored.sort((a, b) -> Double.compare(b[0], a[0]));
         int limit = Math.min(Math.max(0, topK), scored.size());
         List<String> out = new ArrayList<>(limit);

         for (int i = 0; i < limit; i++) {
            out.add(ids.get((int)scored.get(i)[1]));
         }

         return out;
      } else {
         return List.of();
      }
   }

   public static double cosine(float[] q, double qNorm, float[] v) {
      if (q != null && v != null && q.length == v.length) {
         double dot = 0.0;
         double vNorm2 = 0.0;

         for (int i = 0; i < q.length; i++) {
            dot += (double)q[i] * v[i];
            vNorm2 += (double)v[i] * v[i];
         }

         double denom = qNorm * Math.sqrt(vNorm2);
         return denom <= 0.0 ? 0.0 : dot / denom;
      } else {
         return 0.0;
      }
   }

   private static double norm(float[] v) {
      double s = 0.0;

      for (float x : v) {
         s += (double)x * x;
      }

      return Math.sqrt(s);
   }

   public static DenseIndexState load(Path path) {
      if (path != null && Files.exists(path)) {
         try (DataInputStream din = new DataInputStream(Files.newInputStream(path))) {
            if (din.readInt() != 1346653513) {
               return null;
            }

            if (din.readInt() != 1) {
               return null;
            }

            String token = din.readUTF();
            if (!modelIdentityToken().equals(token)) {
               return null;
            }

            int dim = din.readInt();
            if (dim != 1536) {
               return null;
            }

            int count = din.readInt();
            boolean backfillDone = din.readByte() != 0;
            Map<String, float[]> loaded = new LinkedHashMap<>(Math.max(16, count * 2));

            for (int i = 0; i < count; i++) {
               String nodeId = din.readUTF();
               float scale = din.readFloat();
               byte[] q = new byte[dim];
               din.readFully(q);
               loaded.put(nodeId, dequantize(q, scale));
            }

            return new DenseIndexState(loaded, backfillDone);
         } catch (IOException | RuntimeException e) {
            PlayerEngine.LOGGER.warn("Memory: dense-index.bin unreadable ({}); rebuilding in-memory.", e.getClass().getSimpleName());
            return null;
         }
      } else {
         return null;
      }
   }

   public void save(Path path) {
      if (path != null) {
         EmbeddingProvider.assertOffTick();

         try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("dense-index.bin.tmp");

            try (DataOutputStream dout = new DataOutputStream(Files.newOutputStream(tmp))) {
               dout.writeInt(1346653513);
               dout.writeInt(1);
               dout.writeUTF(modelIdentityToken());
               dout.writeInt(1536);
               dout.writeInt(this.vectors.size());
               dout.writeByte(this.backfillComplete ? 1 : 0);

               for (Entry<String, float[]> e : this.vectors.entrySet()) {
                  float[] v = e.getValue();
                  float scale = quantScale(v);
                  dout.writeUTF(e.getKey());
                  dout.writeFloat(scale);
                  dout.write(quantize(v, scale));
               }
            }

            atomicReplace(tmp, path);
         } catch (IOException | RuntimeException e) {
            PlayerEngine.LOGGER.warn("Memory: failed to write dense-index.bin ({}).", e.getClass().getSimpleName());
         }
      }
   }

   public static void delete(Path path) {
      if (path != null) {
         try {
            Files.deleteIfExists(path);
         } catch (IOException var2) {
         }
      }
   }

   static float quantScale(float[] v) {
      float maxAbs = 0.0F;

      for (float x : v) {
         float a = Math.abs(x);
         if (a > maxAbs) {
            maxAbs = a;
         }
      }

      return maxAbs == 0.0F ? 0.0F : maxAbs / 127.0F;
   }

   static byte[] quantize(float[] v, float scale) {
      byte[] out = new byte[v.length];
      if (scale <= 0.0F) {
         return out;
      }

      for (int i = 0; i < v.length; i++) {
         int q = Math.round(v[i] / scale);
         if (q > 127) {
            q = 127;
         }

         if (q < -127) {
            q = -127;
         }

         out[i] = (byte)q;
      }

      return out;
   }

   static float[] dequantize(byte[] q, float scale) {
      float[] out = new float[q.length];

      for (int i = 0; i < q.length; i++) {
         out[i] = q[i] * scale;
      }

      return out;
   }

   private static void atomicReplace(Path tmp, Path target) throws IOException {
      try {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }
}
