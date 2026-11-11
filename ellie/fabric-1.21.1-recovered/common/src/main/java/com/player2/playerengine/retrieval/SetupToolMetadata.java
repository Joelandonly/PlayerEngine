package com.player2.playerengine.retrieval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SetupToolMetadata {
   private SetupToolMetadata() {
   }

   public static Collection<ToolDocument> withSetup(Collection<ToolDocument> var0) {
      ArrayList<ToolDocument> var1 = new ArrayList<>(var0);
      if (var1.stream().noneMatch(var0x -> "setup".equals(var0x.id()))) {
         var1.add(
            new ToolDocument(
               "setup",
               "setup",
               "Prepare the companion's starter inventory with real tools, crafting equipment, chests and supplies; resumable. This does not place chests or construct a home.",
               "Use when the owner says setup, get started, prepare yourself or resume setup. Never use setup_farm unless the owner explicitly asks for a farm, farmland or crops. Starter setup acquires equipment and supplies; it does not build farms.",
               List.of("setup", "get started", "prepare yourself", "resume setup"),
               List.of(
                  "setup",
                  "get started",
                  "prepare yourself",
                  "starter equipment",
                  "starter survival supplies",
                  "resume setup",
                  "tools",
                  "crafting table",
                  "furnace",
                  "chests"
               ),
               List.of("survival", "equipment", "preparation")
            )
         );
      }

      return var1;
   }
}
