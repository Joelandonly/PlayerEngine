package com.player2.playerengine.automaton.utils.schematic.format;

import com.player2.playerengine.automaton.api.schematic.IStaticSchematic;
import com.player2.playerengine.automaton.api.schematic.format.ISchematicFormat;
import com.player2.playerengine.automaton.utils.schematic.format.defaults.MCEditSchematic;
import com.player2.playerengine.automaton.utils.schematic.format.defaults.SpongeSchematic;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.apache.commons.io.FilenameUtils;

public enum DefaultSchematicFormats implements ISchematicFormat {
   MCEDIT("schematic") {
      @Override
      public IStaticSchematic parse(InputStream input) throws IOException {
         return new MCEditSchematic(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
      }
   },
   SPONGE("schem") {
      @Override
      public IStaticSchematic parse(InputStream input) throws IOException {
         CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
         int version = nbt.getInt("Version");
         switch (version) {
            case 1:
            case 2:
               return new SpongeSchematic(nbt);
            default:
               throw new UnsupportedOperationException("Unsupported Version of a Sponge Schematic");
         }
      }
   };

   private final String extension;

   DefaultSchematicFormats(String extension) {
      this.extension = extension;
   }

   @Override
   public boolean isFileType(File file) {
      return this.extension.equalsIgnoreCase(FilenameUtils.getExtension(file.getAbsolutePath()));
   }
}
