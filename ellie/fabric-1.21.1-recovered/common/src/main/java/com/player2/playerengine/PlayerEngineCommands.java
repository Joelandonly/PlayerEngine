package com.player2.playerengine;

import com.player2.playerengine.agentic.elliegps.FarmWaypointService;
import com.player2.playerengine.commands.AcquireBlockCommand;
import com.player2.playerengine.commands.BlockInfoCommand;
import com.player2.playerengine.commands.LocateBlockCommand;
import com.player2.playerengine.commands.AgenticCommand;
import com.player2.playerengine.commands.AttackPlayerOrMobCommand;
import com.player2.playerengine.commands.AuditWaypointCommand;
import com.player2.playerengine.commands.BodyLanguageCommand;
import com.player2.playerengine.commands.BuildSchematicCommand;
import com.player2.playerengine.commands.BuildStructureCommand;
import com.player2.playerengine.commands.CompareWaypointCommand;
import com.player2.playerengine.commands.CreateWaypointCommand;
import com.player2.playerengine.commands.DeleteWaypointCommand;
import com.player2.playerengine.commands.DepositCommand;
import com.player2.playerengine.commands.CleanupStorageCommand;
import com.player2.playerengine.commands.DepositStorageSlotCommand;
import com.player2.playerengine.commands.DepositToStorageCommand;
import com.player2.playerengine.commands.EatFoodCommand;
import com.player2.playerengine.commands.EllieSetupCommand;
import com.player2.playerengine.commands.EquipCommand;
import com.player2.playerengine.commands.ExcavateAreaCommand;
import com.player2.playerengine.commands.FishCommand;
import com.player2.playerengine.commands.FollowCommand;
import com.player2.playerengine.commands.FoodCommand;
import com.player2.playerengine.commands.GetCommand;
import com.player2.playerengine.commands.GiveCommand;
import com.player2.playerengine.commands.GotoCommand;
import com.player2.playerengine.commands.HarvestFarmCommand;
import com.player2.playerengine.commands.HeroCommand;
import com.player2.playerengine.commands.PatrolCommand;
import com.player2.playerengine.commands.NetherBridgeCommand;
import com.player2.playerengine.commands.IdleCommand;
import com.player2.playerengine.commands.LeaveBoatCommand;
import com.player2.playerengine.commands.LocateStorageCommand;
import com.player2.playerengine.commands.LocateStructureCommand;
import com.player2.playerengine.commands.LocateWaypointsCommand;
import com.player2.playerengine.commands.MeatCommand;
import com.player2.playerengine.commands.MineCommand;
import com.player2.playerengine.commands.PickupDropsCommand;
import com.player2.playerengine.commands.PlaceSignCommand;
import com.player2.playerengine.commands.PlantFarmCommand;
import com.player2.playerengine.commands.PrepareSchematicCommand;
import com.player2.playerengine.commands.ReadNearbySignsCommand;
import com.player2.playerengine.commands.ReloadSettingsCommand;
import com.player2.playerengine.commands.ResetMemoryCommand;
import com.player2.playerengine.commands.ScanStorageCommand;
import com.player2.playerengine.commands.SetAIBridgeEnabledCommand;
import com.player2.playerengine.commands.SetFollowModeCommand;
import com.player2.playerengine.commands.SetHostileAttackCommand;
import com.player2.playerengine.commands.SetupFarmCommand;
import com.player2.playerengine.commands.SmeltCommand;
import com.player2.playerengine.commands.SmithCommand;
import com.player2.playerengine.commands.StopCommand;
import com.player2.playerengine.commands.WithdrawFromStorageCommand;
import com.player2.playerengine.commands.WithdrawStorageSlotCommand;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.random.ScanCommand;
import com.player2.playerengine.tasks.farming.FarmWorldObservationProvider;

public class PlayerEngineCommands {
   public static void init(PlayerEngineController controller) throws CommandException {
      FarmWaypointService.installProductionObservationProvider(FarmWorldObservationProvider.INSTANCE);
      controller.getCommandExecutor()
         .registerNewCommand(
            new GetCommand(),
            new EquipCommand(),
            new BodyLanguageCommand(),
            new DepositCommand(),
            new CleanupStorageCommand(),
            new com.player2.playerengine.commands.EllieStorageCommand(),
            new GotoCommand(),
            new IdleCommand(),
            new HeroCommand(),
            new PatrolCommand(),
            new NetherBridgeCommand(),
            new LocateStructureCommand(),
            new StopCommand(),
            new FoodCommand(),
            new MeatCommand(),
            new SmeltCommand(),
            new SmithCommand(),
            new MineCommand(),
            new ReloadSettingsCommand(),
            new ResetMemoryCommand(),
            new FollowCommand(),
            new LeaveBoatCommand(),
            new GiveCommand(),
            new ScanCommand(),
            new AttackPlayerOrMobCommand(),
            new SetAIBridgeEnabledCommand(),
            new EatFoodCommand(),
            new PickupDropsCommand(),
            new AgenticCommand(),
            new SetHostileAttackCommand(),
            new SetFollowModeCommand(),
            new FishCommand(),
            new ReadNearbySignsCommand(),
            new PlaceSignCommand(),
            new ScanStorageCommand(),
            new WithdrawFromStorageCommand(),
            new DepositToStorageCommand(),
            new WithdrawStorageSlotCommand(),
            new DepositStorageSlotCommand(),
            new LocateStorageCommand(),
            new CreateWaypointCommand(),
            new DeleteWaypointCommand(),
            new AuditWaypointCommand(),
            new CompareWaypointCommand(),
            new SetupFarmCommand(),
            new HarvestFarmCommand(),
            new PlantFarmCommand(),
            new LocateWaypointsCommand()
         );
      controller.getCommandExecutor().registerNewCommand(new BuildStructureCommand());
      controller.getCommandExecutor().registerNewCommand(new EllieSetupCommand());
      controller.getCommandExecutor().registerNewCommand(new PrepareSchematicCommand(), new BuildSchematicCommand());
      controller.getCommandExecutor().registerNewCommand(new ExcavateAreaCommand());
      controller.getCommandExecutor().registerNewCommand(new LocateBlockCommand(), new BlockInfoCommand(), new AcquireBlockCommand());
   }
}
