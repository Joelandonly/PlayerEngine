package com.player2.playerengine.mixins;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.inventory.ContainerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface MixinAbstractFurnaceBlockEntity {
   @Accessor("dataAccess")
   ContainerData getPropertyDelegate();
}
