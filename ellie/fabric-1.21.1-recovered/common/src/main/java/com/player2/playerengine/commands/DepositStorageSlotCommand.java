package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.containeraccess.ResolvedContainer;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.containeraccess.StorageAccessCode;
import com.player2.playerengine.player2api.AiConversationFeedback;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.container.SlotPreciseTransactionTask;
import com.player2.playerengine.util.Debug;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class DepositStorageSlotCommand extends Command {
   public DepositStorageSlotCommand() throws CommandException {
      super(
         "deposit_storage_slot",
         "deposit_storage_slot <x> <y> <z> <botSlot> <count> [containerSlot]. Put exactly count items from your inventory slot botSlot (0-35) into the container at x y z. containerSlot (slot indices match deep scan_storage output; a double chest is one 0-53 space) targets a specific container slot; omit it to auto-place into the first stackable-or-empty slot. Example: deposit_storage_slot 120 64 -33 4 32 9.",
         new Arg<>(Integer.class, "x"),
         new Arg<>(Integer.class, "y"),
         new Arg<>(Integer.class, "z"),
         new Arg<>(Integer.class, "botSlot"),
         new Arg<>(Integer.class, "count"),
         new Arg<>(Integer.class, "containerSlot", -1, 5, false)
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String[] u = parser.getArgUnits();
      if (u != null && u.length >= 5) {
         int containerSlot = -1;

         int x;
         int y;
         int z;
         int botSlot;
         int count;
         try {
            x = Integer.parseInt(u[0].trim());
            y = Integer.parseInt(u[1].trim());
            z = Integer.parseInt(u[2].trim());
            botSlot = Integer.parseInt(u[3].trim());
            count = Integer.parseInt(u[4].trim());
            if (u.length > 5) {
               containerSlot = Integer.parseInt(u[5].trim());
            }
         } catch (NumberFormatException e) {
            this.failNow(mod, null, StorageAccessCode.INVALID_ARGUMENT, "all arguments must be integers; usage: " + this.getHelpRepresentation(), "?");
            return;
         }

         BlockPos pos = new BlockPos(x, y, z);
         String reqSummary = "botSlot:" + botSlot + ",count:" + count + (containerSlot != -1 ? ",slot:" + containerSlot : "");
         if (count < 1 || count > 3456) {
            this.failNow(mod, pos, StorageAccessCode.INVALID_COUNT, "count must be 1..3456, got \"" + u[4].trim() + "\"", reqSummary);
         } else if (botSlot < 0 || botSlot > 35) {
            this.failNow(mod, pos, StorageAccessCode.INVALID_SLOT, "bot inventory has slots 0-35 (got " + botSlot + ")", reqSummary);
         } else if (containerSlot != -1 && containerSlot < 0) {
            this.failNow(mod, pos, StorageAccessCode.INVALID_SLOT, "containerSlot must be >= 0 (got " + containerSlot + ")", reqSummary);
         } else {
            double cap = 115.0;
            double distSq = mod.getEntity().position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > cap * cap) {
               this.failNow(
                  mod,
                  pos,
                  StorageAccessCode.CONTAINER_TOO_FAR,
                  "target "
                     + ContainerResolver.formatPos(pos)
                     + " is "
                     + (int)Math.sqrt(distSq)
                     + " blocks away, beyond the "
                     + (int)cap
                     + "-block storage travel cap",
                  reqSummary
               );
            } else {
               SlotPreciseTransactionTask task = new SlotPreciseTransactionTask(
                  ScanReportFormatter.TransferDirection.DEPOSIT, pos, containerSlot, botSlot, count
               );
               ProjectTaskGateway.run(mod, task, () -> this.handleOutcome(mod, pos, reqSummary, task));
            }
         }
      } else {
         this.failNow(
            mod,
            null,
            StorageAccessCode.INVALID_ARGUMENT,
            "expected <x> <y> <z> <botSlot> <count> [containerSlot]; usage: " + this.getHelpRepresentation(),
            "?"
         );
      }
   }

   private void handleOutcome(PlayerEngineController mod, BlockPos pos, String reqSummary, SlotPreciseTransactionTask task) {
      if (task.outcome() == SlotPreciseTransactionTask.Outcome.COMPLETED) {
         ResolvedContainer rc = task.resolvedContainer();
         String receipt = ScanReportFormatter.slotReceipt(
            ScanReportFormatter.TransferDirection.DEPOSIT, rc, task.containerSlotUsed(), task.displayId(), task.movedCount(), task.botSlotUsed()
         );
         mod.reportAgenticProgress(
            Component.translatable(
               "message.playerengine.storage.deposit_slot_ok",
               new Object[]{
                  String.valueOf(task.movedCount()), task.displayId().replace('_', ' '), String.valueOf(task.containerSlotUsed()), humanKind(rc), humanPos(pos)
               }
            ),
            true
         );
         Debug.logMessage(
            "storage-tx ok dir=deposit_slot pos="
               + ContainerResolver.formatPos(pos)
               + " kind="
               + kindToken(rc)
               + " slot="
               + task.containerSlotUsed()
               + " moved="
               + task.displayId()
               + ":"
               + task.movedCount()
               + " bot="
               + botName(mod)
         );
         AiConversationFeedback.enqueueInfo(mod, receipt);
         this.finish();
      } else {
         this.reportFailure(mod, pos, task.resolvedContainer(), task.failures(), reqSummary);
      }
   }

   private void failNow(PlayerEngineController mod, BlockPos pos, StorageAccessCode code, String detail, String reqSummary) {
      this.reportFailure(mod, pos, null, List.of(new ScanReportFormatter.EntryFailure(code, "", detail)), reqSummary);
   }

   private void reportFailure(
      PlayerEngineController mod, BlockPos pos, ResolvedContainer rc, List<ScanReportFormatter.EntryFailure> failures, String reqSummary
   ) {
      if (failures.isEmpty()) {
         failures = List.of(
            new ScanReportFormatter.EntryFailure(StorageAccessCode.CONTAINER_UNREACHABLE, "", "transaction ended without a result (session interrupted)")
         );
      }

      String text = ScanReportFormatter.transferFailure(failures);
      String where = pos == null ? "storage" : "the " + humanKind(rc) + " at " + humanPos(pos);
      mod.reportAgenticProgress(Component.translatable("message.playerengine.storage.deposit_fail", new Object[]{where, failures.get(0).detail()}), true);
      Debug.logWarning(
         "storage-tx fail code="
            + failures.get(0).code().token()
            + " pos="
            + (pos == null ? "?" : ContainerResolver.formatPos(pos))
            + " kind="
            + kindToken(rc)
            + " req="
            + reqSummary
            + " detail=\""
            + text
            + "\" bot="
            + botName(mod)
      );
      this.finishWithError(text);
   }

   private static String humanKind(ResolvedContainer rc) {
      return rc == null ? "container" : rc.kind().token().replace('_', ' ');
   }

   private static String kindToken(ResolvedContainer rc) {
      return rc == null ? "?" : rc.kind().token();
   }

   private static String humanPos(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private static String botName(PlayerEngineController mod) {
      return mod.getEntity().getName().getString();
   }
}
