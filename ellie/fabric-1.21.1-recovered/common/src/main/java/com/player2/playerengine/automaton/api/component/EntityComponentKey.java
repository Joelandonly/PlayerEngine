package com.player2.playerengine.automaton.api.component;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class EntityComponentKey<C> {
   private final Function<LivingEntity, C> factory;

   public interface Holder {
      @Nullable Object playerengine$getEntityComponent(EntityComponentKey<?> key);
      Object playerengine$getOrCreateEntityComponent(EntityComponentKey<?> key, Supplier<?> factory);
   }

   public EntityComponentKey(Function<LivingEntity, C> factory) {
      this.factory = factory;
   }

   @Nullable
   @SuppressWarnings("unchecked")
   public C getNullable(Object object) {
      return object instanceof Holder holder ? (C)holder.playerengine$getEntityComponent(this) : null;
   }

   @SuppressWarnings("unchecked")
   public final C get(Object object) {
      if (object instanceof LivingEntity entity && object instanceof Holder holder) {
         // A portal can replace the entity object while preserving its UUID.
         return (C)holder.playerengine$getOrCreateEntityComponent(this, () -> this.factory.apply(entity));
      } else {
         throw new NoSuchElementException();
      }
   }

   public final Optional<C> maybeGet(@Nullable Object object) {
      return Optional.ofNullable(this.getNullable(object));
   }
}
