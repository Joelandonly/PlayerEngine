package com.player2.playerengine.schematic;

import com.player2.playerengine.nativecommands.NativeAccess;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class SchematicDepotGuard {
   private static final Map<Object, SchematicDepotGuard.Lease> LEASES = Collections.synchronizedMap(new WeakHashMap<>());

   private SchematicDepotGuard() {
   }

   public static void retainAll(Object var0, Object var1, Object var2, List<SchematicPlan.Pos> var3) {
      if (var3 != null && !var3.isEmpty()) {
         for (SchematicPlan.Pos var5 : var3) {
            retain(var0, var1, var2, var5);
         }
      }
   }

   public static void retain(Object var0, Object var1, Object var2, SchematicPlan.Pos var3) {
      if (var2 != null) {
         if (var0 != null && var1 != null && var3 != null) {
            try {
               Object var4 = NativeAccess.call(var2, "getBreakMutex");
               if (var4 == null) {
                  throw new IllegalStateException("Missing native break mutex");
               }

               synchronized (var4) {
                  List var6 = (List)NativeAccess.call(var2, "getBreakAvoiders");
                  synchronized (LEASES) {
                     SchematicDepotGuard.Lease var8 = LEASES.get(var0);
                     if (var8 == null || var8.world != var1 || !var6.contains(var8)) {
                        if (var8 != null) {
                           var6.remove(var8);
                        }

                        var8 = new SchematicDepotGuard.Lease(var0, var1);
                        var6.add(var8);
                        LEASES.put(var0, var8);
                     }

                     var8.add(var3);
                  }
               }
            } catch (Exception | LinkageError var13) {
               throw new IllegalStateException("Cannot preserve schematic depot break guard", var13);
            }
         } else {
            throw new IllegalArgumentException("Incomplete project storage guard");
         }
      }
   }

   public static boolean protects(Object var0, Object var1) {
      if (var0 != null && var1 != null) {
         SchematicDepotGuard.Lease var2;
         synchronized (LEASES) {
            var2 = LEASES.get(var0);
         }

         return var2 != null && var2.test(var1);
      } else {
         return false;
      }
   }

   public static int retainedCells(Object var0) {
      synchronized (LEASES) {
         SchematicDepotGuard.Lease var2 = LEASES.get(var0);
         return var2 == null ? 0 : var2.cells.get().size();
      }
   }

   private static final class Lease implements Predicate<Object> {
      final WeakReference<Object> owner;
      final Object world;
      final AtomicReference<Set<SchematicPlan.Pos>> cells = new AtomicReference<>(Set.of());

      Lease(Object var1, Object var2) {
         this.owner = new WeakReference<>(var1);
         this.world = var2;
      }

      void add(SchematicPlan.Pos var1) {
         HashSet var2 = new HashSet<>(this.cells.get());
         var2.add(var1);
         var2.add(var1.add(0, -1, 0));
         this.cells.set(Set.copyOf(var2));
      }

      @Override
      public boolean test(Object var1) {
         try {
            SchematicPlan.Pos var2 = SchematicWorld.point(var1);
            if (!this.cells.get().contains(var2)) {
               return false;
            }

            Object var3 = this.owner.get();
            return var3 != null && NativeAccess.call(var3, "getWorld") == this.world;
         } catch (Exception | LinkageError var4) {
            return false;
         }
      }
   }
}
