package com.player2.playerengine.automaton.utils.player;

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.cache.IWorldData;
import com.player2.playerengine.automaton.api.entity.IHungerManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.pathing.calc.Avoidance;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.IInteractionController;
import com.player2.playerengine.automaton.api.utils.RayTraceUtils;
import com.player2.playerengine.reliability.PartialBlockSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class EntityContext implements IEntityContext {
   private final LivingEntity entity;
   @Nullable
   private Supplier<List<Avoidance>> avoidanceFinder;

   public EntityContext(LivingEntity entity) {
      this.entity = entity;
   }

   @Override
   public LivingEntity entity() {
      return this.entity;
   }

   @Nullable
   @Override
   public LivingEntityInventory inventory() {
      return this.entity instanceof IInventoryProvider ? ((IInventoryProvider)this.entity).getLivingInventory() : null;
   }

   @Nullable
   @Override
   public LivingEntityHungerManager hungerManager() {
      return this.entity instanceof IHungerManagerProvider ? ((IHungerManagerProvider)this.entity).getHungerManager() : null;
   }

   @Override
   public IInteractionController playerController() {
      return IInteractionController.KEY.get(this.entity);
   }

   @Override
   public ServerLevel world() {
      Level world = this.entity.level();
      if (world.isClientSide) {
         throw new IllegalStateException();
      } else {
         return (ServerLevel)world;
      }
   }

   @Override
   public IWorldData worldData() {
      return BaritoneAPI.getProvider().getBaritone(this.entity).getEntityContext().worldData();
   }

   @Override
   public HitResult objectMouseOver() {
      return RayTraceUtils.rayTraceTowards(this.entity(), this.entityRotations(), this.playerController().getBlockReachDistance());
   }

   @Override
   public BetterBlockPos feetPos() {
      double var1 = this.entity().getX();
      double var3 = this.entity().getY();
      double var5 = this.entity().getZ();
      return new BetterBlockPos(var1, PartialBlockSupport.feetY(this, var1, var3, var5), var5);
   }

   private Stream<Entity> streamHostileEntities() {
      return this.worldEntitiesStream()
         .filter(entity -> entity instanceof Mob)
         .filter(entity -> !(entity instanceof Spider) || entity.getLightLevelDependentMagicValue() < 0.5)
         .filter(entity -> !(entity instanceof ZombifiedPiglin) || ((ZombifiedPiglin)entity).getLastHurtByMob() != null)
         .filter(entity -> !(entity instanceof EnderMan) || ((EnderMan)entity).isCreepy());
   }

   @Override
   public void setAvoidanceFinder(@Nullable Supplier<List<Avoidance>> avoidanceFinder) {
      this.avoidanceFinder = avoidanceFinder;
   }

   @Override
   public List<Avoidance> listAvoidedAreas() {
      if (!this.baritone().settings().avoidance.get()) {
         return Collections.emptyList();
      }

      if (this.avoidanceFinder != null) {
         return this.avoidanceFinder.get();
      }

      List<Avoidance> res = new ArrayList<>();
      double mobCoeff = this.baritone().settings().mobAvoidanceCoefficient.get();
      if (mobCoeff != 1.0) {
         this.streamHostileEntities()
            .forEach(entity -> res.add(new Avoidance(entity.blockPosition(), mobCoeff, this.baritone().settings().mobAvoidanceRadius.get())));
      }

      return res;
   }
}
