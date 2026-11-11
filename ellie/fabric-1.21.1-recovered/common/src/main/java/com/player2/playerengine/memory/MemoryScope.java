package com.player2.playerengine.memory;

import java.util.Objects;
import java.util.UUID;

public final class MemoryScope {
   private final UUID ownerUuid;
   private final UUID entityUuid;
   private final String companionId;

   private MemoryScope(UUID ownerUuid, UUID entityUuid, String companionId) {
      this.ownerUuid = ownerUuid;
      this.entityUuid = entityUuid;
      this.companionId = Objects.requireNonNull(companionId, "companionId");
   }

   public static MemoryScope of(UUID ownerUuid, String companionId) {
      return new MemoryScope(Objects.requireNonNull(ownerUuid, "ownerUuid"), null, companionId);
   }

   public static MemoryScope ofEntityFallback(UUID entityUuid, String companionId) {
      return new MemoryScope(null, Objects.requireNonNull(entityUuid, "entityUuid"), companionId);
   }

   public boolean hasOwner() {
      return this.ownerUuid != null;
   }

   public UUID ownerUuid() {
      return this.ownerUuid;
   }

   public UUID entityUuid() {
      return this.entityUuid;
   }

   public String companionId() {
      return this.companionId;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof MemoryScope that)
            ? false
            : Objects.equals(this.ownerUuid, that.ownerUuid) && Objects.equals(this.entityUuid, that.entityUuid) && this.companionId.equals(that.companionId);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.ownerUuid, this.entityUuid, this.companionId);
   }

   @Override
   public String toString() {
      return "MemoryScope{" + (this.ownerUuid != null ? "owner=" + this.ownerUuid : "entity=" + this.entityUuid) + ", companionId='" + this.companionId + "'}";
   }
}
