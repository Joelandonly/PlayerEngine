package com.player2.playerengine.automaton.utils.schematic;

import com.player2.playerengine.automaton.api.schematic.ISchematicSystem;
import com.player2.playerengine.automaton.api.schematic.format.ISchematicFormat;
import com.player2.playerengine.automaton.utils.schematic.format.DefaultSchematicFormats;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public enum SchematicSystem implements ISchematicSystem {
   INSTANCE;

   private final List<ISchematicFormat> registry = new ArrayList<>();

   SchematicSystem() {
      Collections.addAll(this.registry, DefaultSchematicFormats.values());
   }

   @Override
   public List<ISchematicFormat> getRegistry() {
      return this.registry;
   }

   @Override
   public Optional<ISchematicFormat> getByFile(File file) {
      return this.registry.stream().filter(format -> format.isFileType(file)).findFirst();
   }
}
