package com.player2.playerengine.containeraccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ScanReportFormatter {
   private ScanReportFormatter() {
   }

   public static String scanReport(ScanMode mode, ContainerSnapshot snap, List<StorageItemArgs.ItemQuery> targets) {
      StringBuilder out = new StringBuilder(header(mode, snap));
      out.append('\n');
      switch (mode) {
         case LIGHT:
            out.append(lightBody(snap));
            break;
         case DEEP:
            out.append(deepBody(snap));
            break;
         case TARGETED:
            out.append(targetedBody(snap, targets));
      }

      return out.toString();
   }

   public static String transferReceipt(ScanReportFormatter.TransferDirection dir, ResolvedContainer rc, List<ScanReportFormatter.EntryOutcome> entries) {
      StringJoiner joined = new StringJoiner(", ");

      for (ScanReportFormatter.EntryOutcome entry : entries) {
         joined.add(entryText(entry));
      }

      return verb(dir) + " " + posText(rc.canonicalPos(), rc.secondaryPos()) + ": " + joined;
   }

   public static String transferFailure(List<ScanReportFormatter.EntryFailure> failures) {
      StringJoiner joined = new StringJoiner("; ");

      for (ScanReportFormatter.EntryFailure failure : failures) {
         joined.add(failure.code().token() + ": " + failure.detail());
      }

      return joined.toString();
   }

   public static String slotReceipt(
      ScanReportFormatter.TransferDirection dir, ResolvedContainer rc, int containerSlot, String displayId, int count, Integer botSlot
   ) {
      StringBuilder sb = new StringBuilder(verb(dir))
         .append(' ')
         .append(posText(rc.canonicalPos(), rc.secondaryPos()))
         .append(" slot ")
         .append(containerSlot)
         .append(": ")
         .append(displayId)
         .append(" x")
         .append(count);
      if (botSlot != null) {
         sb.append(dir == ScanReportFormatter.TransferDirection.WITHDRAW ? "; to bot slot " : "; from bot slot ").append(botSlot);
      }

      return sb.toString();
   }

   private static String header(ScanMode mode, ContainerSnapshot snap) {
      StringBuilder sb = new StringBuilder("storage ")
         .append(mode.token())
         .append(' ')
         .append(posText(snap.canonicalPos(), snap.secondaryPos()))
         .append(' ')
         .append(snap.kind().token());
      if (mode == ScanMode.LIGHT) {
         sb.append(' ').append(snap.totalSlots()).append(" slots, ").append(snap.emptySlots()).append(" empty");
      } else if (mode == ScanMode.DEEP) {
         sb.append(' ').append(snap.totalSlots()).append(" slots");
      }

      return sb.toString();
   }

   private static String lightBody(ContainerSnapshot snap) {
      if (snap.aggregate().isEmpty()) {
         return "(empty)";
      }

      StringJoiner joined = new StringJoiner("; ");

      for (ItemCount line : snap.aggregate()) {
         joined.add(displayOf(line.registryId()) + " x" + line.count());
      }

      return joined.toString();
   }

   private static String deepBody(ContainerSnapshot snap) {
      List<IndexedSlot> slots = snap.slots() == null ? List.of() : snap.slots();
      StringBuilder body = new StringBuilder();
      if (!slots.isEmpty()) {
         StringJoiner joined = new StringJoiner("|");

         for (IndexedSlot slot : slots) {
            joined.add(slot.slot() + ":" + displayOf(slot.registryId()) + (slot.hasExtraData() ? "*" : "") + " x" + slot.count());
         }

         body.append(joined);
      }

      String ranges = compressedEmptyRanges(snap.totalSlots(), slots);
      if (!ranges.isEmpty()) {
         if (body.length() > 0) {
            body.append('\n');
         }

         body.append("empty: ").append(ranges);
      }

      return body.length() == 0 ? "(empty)" : body.toString();
   }

   private static String targetedBody(ContainerSnapshot snap, List<StorageItemArgs.ItemQuery> targets) {
      if (targets != null && !targets.isEmpty()) {
         Map<String, Integer> have = new HashMap<>();

         for (ItemCount line : snap.aggregate()) {
            have.put(line.registryId(), line.count());
         }

         StringJoiner joined = new StringJoiner("; ");

         for (StorageItemArgs.ItemQuery query : targets) {
            String fullId = BuiltInRegistries.ITEM.getKey(query.item()).toString();
            int present = have.getOrDefault(fullId, 0);
            StringBuilder entry = new StringBuilder(query.displayId()).append(' ').append(present);
            if (!query.isAll()) {
               entry.append('/').append(query.countOrAll());
               if (present < query.countOrAll()) {
                  entry.append(" (short ").append(query.countOrAll() - present).append(')');
               }
            }

            joined.add(entry.toString());
         }

         return joined.toString();
      } else {
         return "(empty)";
      }
   }

   private static String verb(ScanReportFormatter.TransferDirection dir) {
      return dir == ScanReportFormatter.TransferDirection.WITHDRAW ? "withdrew from" : "deposited to";
   }

   private static String entryText(ScanReportFormatter.EntryOutcome entry) {
      boolean isShort = entry.shortReason() != null && !entry.shortReason().isEmpty();
      if (isShort) {
         return entry.displayId() + " x" + entry.moved() + " of " + entry.present() + " (" + entry.shortReason() + ")";
      } else {
         return entry.requested() == -1
            ? entry.displayId() + " x" + entry.moved() + " (all)"
            : entry.displayId() + " x" + entry.moved() + "/" + entry.requested();
      }
   }

   private static String posText(BlockPos canonical, BlockPos secondary) {
      String text = ContainerResolver.formatPos(canonical);
      return secondary == null ? text : text + "+" + ContainerResolver.formatPos(secondary);
   }

   private static String displayOf(String registryId) {
      ResourceLocation id = ResourceLocation.tryParse(registryId);
      return id == null ? registryId : StorageItemArgs.displayIdFor((Item)BuiltInRegistries.ITEM.get(id));
   }

   private static String compressedEmptyRanges(int totalSlots, List<IndexedSlot> slots) {
      boolean[] occupied = new boolean[Math.max(0, totalSlots)];

      for (IndexedSlot slot : slots) {
         if (slot.slot() >= 0 && slot.slot() < occupied.length) {
            occupied[slot.slot()] = true;
         }
      }

      StringBuilder sb = new StringBuilder();
      int i = 0;

      while (i < occupied.length) {
         if (occupied[i]) {
            i++;
         } else {
            int start = i;

            while (i < occupied.length && !occupied[i]) {
               i++;
            }

            if (sb.length() > 0) {
               sb.append(',');
            }

            int end = i - 1;
            sb.append(start);
            if (end > start) {
               sb.append('-').append(end);
            }
         }
      }

      return sb.toString();
   }

   public record EntryFailure(StorageAccessCode code, String displayId, String detail) {
   }

   public record EntryOutcome(String displayId, int moved, int requested, int present, String shortReason) {
   }

   public enum TransferDirection {
      WITHDRAW,
      DEPOSIT;
   }
}
