package com.player2.playerengine.automaton.command.defaults;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.Settings;
import com.player2.playerengine.automaton.api.command.ICommand;
import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandNotEnoughArgumentsException;
import com.player2.playerengine.automaton.api.command.manager.ICommandManager;
import com.player2.playerengine.automaton.api.entity.IAutomatone;
import com.player2.playerengine.automaton.command.argument.ArgConsumer;
import com.player2.playerengine.automaton.command.manager.BaritoneArgumentType;
import com.player2.playerengine.automaton.command.manager.BaritoneCommandManager;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.executor.StepExecution;
import com.player2.playerengine.executor.StepState;
import com.player2.playerengine.help.ArgNote;
import com.player2.playerengine.help.HelpEntry;
import com.player2.playerengine.help.HelpRegistry;
import com.player2.playerengine.player2api.BudgetConfigCommands;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.ProfileUrlResolver;
import com.player2.playerengine.player2api.config.Player2PayerMode;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.network.Player2ServerNetworking;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public final class DefaultCommands {
   public static final ExecutionControlCommands controlCommands = new ExecutionControlCommands();
   public static final SelCommand selCommand = new SelCommand();
   public static final DynamicCommandExceptionType BARITONE_COMMAND_FAILED_EXCEPTION = new DynamicCommandExceptionType(Message.class::cast);

   public static void registerAll() {
      for (ICommand command : new ArrayList<>(
         Arrays.asList(
            new HelpCommand(),
            new SetCommand(),
            new CommandAlias(
               Arrays.asList("modified", "mod", "com/player2/playerengine/automaton", "modifiedsettings"), "List modified settings", "set modified"
            ),
            new CommandAlias("reset", "Reset all settings or just one", "set reset"),
            new GoalCommand(),
            new GotoCommand(),
            new PathCommand(),
            new ProcCommand(),
            new ETACommand(),
            new VersionCommand(),
            new RepackCommand(),
            new BuildCommand(),
            new SchematicaCommand(),
            new ComeCommand(),
            new AxisCommand(),
            new ForceCancelCommand(),
            new GcCommand(),
            new InvertCommand(),
            new TunnelCommand(),
            new RenderCommand(),
            new ChestsCommand(),
            new FollowCommand(),
            new ExploreFilterCommand(),
            new ReloadAllCommand(),
            new SaveAllCommand(),
            new ExploreCommand(),
            new BlacklistCommand(),
            new FindCommand(),
            new MineCommand(),
            new ClickCommand(),
            new SurfaceCommand(),
            new ThisWayCommand(),
            new WaypointsCommand(),
            new FishCommand(),
            new CommandAlias("sethome", "Sets your home waypoint", "waypoints save home"),
            new CommandAlias("home", "Path to your home waypoint", "waypoints goto home"),
            selCommand
         )
      )) {
         ICommandManager.registry.register(command);
      }

      CommandRegistrationEvent.EVENT.register((CommandRegistrationEvent)(dispatcher, ctx, dedicated) -> register(dispatcher));
   }

   private static void logRanCommand(CommandSourceStack source, String command, String rest) {
      if (BaritoneAPI.getGlobalSettings().echoCommands.get()) {
         String msg = command + rest;
         String toDisplay = BaritoneAPI.getGlobalSettings().censorRanCommands.get() ? command + " ..." : msg;
         source.sendSuccess(
            () -> {
               MutableComponent component = Component.translatable("message.playerengine.commands.echo_prefix", new Object[]{toDisplay});
               component.setStyle(
                  component.getStyle()
                     .applyFormat(ChatFormatting.WHITE)
                     .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.playerengine.commands.echo_hover")))
                     .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/automatone " + msg))
               );
               return component;
            },
            false
         );
      }
   }

   public static boolean runCommand(CommandSourceStack source, String msg, IBaritone baritone) throws CommandException {
      if (msg.trim().equalsIgnoreCase("damn")) {
         source.sendSuccess(() -> Component.translatable("message.playerengine.commands.easter_egg"), false);
         return false;
      }

      if (msg.trim().equalsIgnoreCase("orderpizza")) {
         PlayerEngine.LOGGER.fatal("No pizza :(");
         return false;
      }

      if (msg.isEmpty()) {
         return runCommand(source, "help", baritone);
      }

      Tuple<String, List<ICommandArgument>> pair = BaritoneCommandManager.expand(msg);
      String command = (String)pair.getA();
      String rest = msg.substring(((String)pair.getA()).length());
      ArgConsumer argc = new ArgConsumer(baritone.getCommandManager(), (List<ICommandArgument>)pair.getB(), baritone);
      if (!argc.hasAny()) {
         Settings.Setting<?> setting = BaritoneAPI.getGlobalSettings().byLowerName.get(command.toLowerCase(Locale.ROOT));
         if (setting != null) {
            logRanCommand(source, command, rest);
            if (setting.getValueClass() == Boolean.class) {
               baritone.getCommandManager().execute(source, String.format("set toggle %s", setting.getName()));
            } else {
               baritone.getCommandManager().execute(source, String.format("set %s", setting.getName()));
            }

            return true;
         }
      } else if (argc.hasExactlyOne()) {
         for (Settings.Setting<?> setting : BaritoneAPI.getGlobalSettings().allSettings) {
            if (!setting.getName().equals("logger") && setting.getName().equalsIgnoreCase((String)pair.getA())) {
               logRanCommand(source, command, rest);

               try {
                  baritone.getCommandManager().execute(source, String.format("set %s %s", setting.getName(), argc.getString()));
               } catch (CommandNotEnoughArgumentsException var10) {
               }

               return true;
            }
         }
      }

      if (ICommandManager.getCommand((String)pair.getA()) != null) {
         logRanCommand(source, command, rest);
      }

      return baritone.getCommandManager().execute(source, pair);
   }

   private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      contributePlayer2Help();
      BudgetConfigCommands.registerHelpEntries();
      LiteralArgumentBuilder<CommandSourceStack> root = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("playerengine").requires(s -> s.hasPermission(2));
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                       "player2"
                                    )
                                    .then(Commands.literal("reload").executes(ctx -> {
                                       if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                          return 0;
                                       }

                                       Player2ServerConfigHolder.load();
                                       syncPlayer2ConfigToAllPlayers((CommandSourceStack)ctx.getSource());
                                       ((CommandSourceStack)ctx.getSource())
                                          .sendSuccess(() -> Component.translatable("message.playerengine.config.reload_success"), false);
                                       return 1;
                                    })))
                                 .then(((LiteralArgumentBuilder)Commands.literal("payer").then(Commands.literal("prompter").executes(ctx -> {
                                    if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                       return 0;
                                    }

                                    Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
                                    c.setPayerMode(Player2PayerMode.PROMPTER_PAYS);
                                    Player2ServerConfigHolder.validateAndFix(c);
                                    Player2ServerConfigHolder.save();
                                    syncPlayer2ConfigToAllPlayers((CommandSourceStack)ctx.getSource());
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> Component.translatable("message.playerengine.config.payer_prompter"), false);
                                    return 1;
                                 }))).then(Commands.literal("owner").executes(ctx -> {
                                    if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                       return 0;
                                    }

                                    Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
                                    c.setPayerMode(Player2PayerMode.OWNER_PAYS_ALL);
                                    Player2ServerConfigHolder.validateAndFix(c);
                                    Player2ServerConfigHolder.save();
                                    syncPlayer2ConfigToAllPlayers((CommandSourceStack)ctx.getSource());
                                    ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.translatable("message.playerengine.config.payer_owner"), false);
                                    return 1;
                                 }))))
                              .then(Commands.literal("dedicated").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                                 if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                    return 0;
                                 }

                                 boolean v = BoolArgumentType.getBool(ctx, "value");
                                 Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
                                 c.setDedicatedClientProxy(v);
                                 Player2ServerConfigHolder.validateAndFix(c);
                                 Player2ServerConfigHolder.save();
                                 syncPlayer2ConfigToAllPlayers((CommandSourceStack)ctx.getSource());
                                 ((CommandSourceStack)ctx.getSource())
                                    .sendSuccess(() -> Component.translatable("message.playerengine.config.dedicated", new Object[]{v}), false);
                                 return 1;
                              }))))
                           .then(
                              Commands.literal("owner_offline_continue")
                                 .then(
                                    Commands.argument("value", BoolArgumentType.bool())
                                       .executes(
                                          ctx -> {
                                             if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                                return 0;
                                             }

                                             boolean v = BoolArgumentType.getBool(ctx, "value");
                                             Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
                                             c.setOwnerOfflineServerContinuation(v);
                                             Player2ServerConfigHolder.validateAndFix(c);
                                             Player2ServerConfigHolder.save();
                                             syncPlayer2ConfigToAllPlayers((CommandSourceStack)ctx.getSource());
                                             ((CommandSourceStack)ctx.getSource())
                                                .sendSuccess(
                                                   () -> Component.translatable("message.playerengine.config.owner_offline_continue", new Object[]{v}), false
                                                );
                                             return 1;
                                          }
                                       )
                                 )
                           ))
                        .then(Commands.literal("call_by_name").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                           if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                              return 0;
                           }

                           boolean v = BoolArgumentType.getBool(ctx, "value");
                           Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
                           c.setCallByNameChat(v);
                           Player2ServerConfigHolder.validateAndFix(c);
                           Player2ServerConfigHolder.save();
                           ((CommandSourceStack)ctx.getSource())
                              .sendSuccess(() -> Component.translatable("message.playerengine.config.call_by_name", new Object[]{v}), false);
                           return 1;
                        }))))
                     .then(Commands.literal("npc_max_spawn").then(Commands.argument("value", IntegerArgumentType.integer(1, 20)).executes(ctx -> {
                        if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                           return 0;
                        }

                        int v = IntegerArgumentType.getInteger(ctx, "value");
                        Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
                        c.setMaxSpawnedCompanionsPerPlayer(v);
                        Player2ServerConfigHolder.validateAndFix(c);
                        Player2ServerConfigHolder.save();
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(() -> Component.translatable("message.playerengine.config.npc_max_spawn", new Object[]{v}), false);
                        return 1;
                     }))))
                  .then(
                     Commands.literal("npc_max_stored_ids")
                        .then(
                           Commands.argument("value", IntegerArgumentType.integer(0, 100))
                              .executes(
                                 ctx -> {
                                    if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                       return 0;
                                    }

                                    int v = IntegerArgumentType.getInteger(ctx, "value");
                                    Player2ServerRuntimeConfig c = Player2ServerConfigHolder.get();
                                    c.setMaxStoredCharacterIdsPerPlayer(v);
                                    Player2ServerConfigHolder.validateAndFix(c);
                                    Player2ServerConfigHolder.save();
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> Component.translatable("message.playerengine.config.npc_max_stored_ids", new Object[]{v}), false);
                                    return 1;
                                 }
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                   "budget"
                                                )
                                                .then(
                                                   Commands.literal("soft")
                                                      .then(
                                                         Commands.argument("value", IntegerArgumentType.integer(0))
                                                            .executes(
                                                               ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                                                  ? 0
                                                                  : BudgetConfigCommands.setSoft(
                                                                     (CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value")
                                                                  )
                                                            )
                                                      )
                                                ))
                                             .then(
                                                Commands.literal("hard")
                                                   .then(
                                                      Commands.argument("value", IntegerArgumentType.integer(0))
                                                         .executes(
                                                            ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                                               ? 0
                                                               : BudgetConfigCommands.setHard(
                                                                  (CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value")
                                                               )
                                                         )
                                                   )
                                             ))
                                          .then(
                                             Commands.literal("window")
                                                .then(
                                                   Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                                      .executes(
                                                         ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                                            ? 0
                                                            : BudgetConfigCommands.setWindow(
                                                               (CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "minutes")
                                                            )
                                                      )
                                                )
                                          ))
                                       .then(
                                          Commands.literal("joules_soft")
                                             .then(
                                                Commands.argument("value", IntegerArgumentType.integer(0))
                                                   .executes(
                                                      ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                                         ? 0
                                                         : BudgetConfigCommands.setJoulesSoft(
                                                            (CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value")
                                                         )
                                                   )
                                             )
                                       ))
                                    .then(
                                       Commands.literal("joules_hard")
                                          .then(
                                             Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(
                                                   ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                                      ? 0
                                                      : BudgetConfigCommands.setJoulesHard(
                                                         (CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value")
                                                      )
                                                )
                                          )
                                    ))
                                 .then(
                                    Commands.literal("joules_refresh")
                                       .then(
                                          Commands.argument("seconds", IntegerArgumentType.integer(60, 86400))
                                             .executes(
                                                ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                                   ? 0
                                                   : BudgetConfigCommands.setJoulesRefresh(
                                                      (CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")
                                                   )
                                             )
                                       )
                                 ))
                              .then(
                                 Commands.literal("fallback_profile").then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> {
                                    if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                       return 0;
                                    }

                                    String raw = StringArgumentType.getString(ctx, "name");
                                    String profileName = raw.equalsIgnoreCase("none") ? null : raw;
                                    return BudgetConfigCommands.setFallbackProfile((CommandSourceStack)ctx.getSource(), profileName);
                                 }))
                              ))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("fallback_behavior")
                                    .then(
                                       Commands.literal("switch")
                                          .executes(
                                             ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                                ? 0
                                                : BudgetConfigCommands.setFallbackBehaviorSwitch((CommandSourceStack)ctx.getSource())
                                          )
                                    ))
                                 .then(
                                    Commands.literal("stop")
                                       .executes(
                                          ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource())
                                             ? 0
                                             : BudgetConfigCommands.setFallbackBehaviorStop((CommandSourceStack)ctx.getSource())
                                       )
                                 )
                           ))
                        .then(Commands.literal("reset").executes(ctx -> {
                           if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                              return 0;
                           }

                           BudgetTracker.resetAll();
                           JoulesCache.invalidateAll();
                           ProfileUrlResolver.invalidateCache();
                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.translatable("message.playerengine.budget.reset_success"), false);
                           return 1;
                        })))
                     .then(
                        Commands.literal("status")
                           .executes(ctx -> !isLogicalServer((CommandSourceStack)ctx.getSource()) ? 0 : BudgetConfigCommands.status((CommandSourceStack)ctx.getSource()))
                     )
               ))
            .then(
               Commands.literal("chain")
                  .then(
                     Commands.literal("status")
                        .executes(
                           ctx -> {
                              if (!isLogicalServer((CommandSourceStack)ctx.getSource())) {
                                 return 0;
                              }

                              CommandSourceStack source = (CommandSourceStack)ctx.getSource();
                              int pruned = PlayerEngineController.pruneStaleControllers(source.getServer());
                              ConcurrentHashMap<UUID, PlayerEngineController> controllers = PlayerEngineController.staticControllers;
                              if (controllers.isEmpty()) {
                                 source.sendSuccess(
                                    () -> pruned > 0
                                       ? Component.translatable("message.playerengine.chain.no_active_bots_pruned", new Object[]{pruned})
                                       : Component.translatable("message.playerengine.chain.no_active_bots"),
                                    false
                                 );
                                 return 1;
                              }

                              if (pruned > 0) {
                                 source.sendSuccess(() -> Component.translatable("message.playerengine.chain.pruned_controllers", new Object[]{pruned}), false);
                              }

                              for (Entry<UUID, PlayerEngineController> entry : controllers.entrySet()) {
                                 PlayerEngineController bot = entry.getValue();
                                 if (bot.getEntity() != null && !bot.getEntity().isRemoved()) {
                                    String botName = bot.getChainStatusDisplayName();
                                    source.sendSuccess(() -> Component.translatable("message.playerengine.chain.status_header", new Object[]{botName}), false);
                                    AgenticRunRegistry.snapshot(bot.getEntity().getUUID())
                                       .ifPresent(
                                          run -> {
                                             source.sendSuccess(
                                                () -> Component.translatable(
                                                   "message.playerengine.chain.agentic_run", new Object[]{run.runId(), run.state(), run.goalSummary()}
                                                ),
                                                false
                                             );
                                             source.sendSuccess(
                                                () -> Component.translatable(
                                                   "message.playerengine.chain.planning_info",
                                                   new Object[]{run.planningSource(), run.activeStepIndex(), run.activeStepKind()}
                                                ),
                                                false
                                             );
                                             if (run.lastMessage() != null && !run.lastMessage().isBlank()) {
                                                source.sendSuccess(
                                                   () -> Component.translatable("message.playerengine.chain.progress", new Object[]{run.lastMessage()}), false
                                                );
                                             }

                                             if (run.storageTargetSummary() != null && !run.storageTargetSummary().isBlank()) {
                                                source.sendSuccess(
                                                   () -> Component.translatable(
                                                      "message.playerengine.chain.storage_target", new Object[]{run.storageTargetSummary()}
                                                   ),
                                                   false
                                                );
                                             }

                                             if (run.storageProgress() != null && !run.storageProgress().isBlank()) {
                                                source.sendSuccess(
                                                   () -> Component.translatable(
                                                      "message.playerengine.chain.storage_phase", new Object[]{run.storageProgress()}
                                                   ),
                                                   false
                                                );
                                             }

                                             if (run.depositProgress() != null && !run.depositProgress().isBlank()) {
                                                source.sendSuccess(
                                                   () -> Component.translatable(
                                                      "message.playerengine.chain.deposit_phase", new Object[]{run.depositProgress()}
                                                   ),
                                                   false
                                                );
                                             }

                                             if (run.labelProgress() != null && !run.labelProgress().isBlank()) {
                                                source.sendSuccess(
                                                   () -> Component.translatable("message.playerengine.chain.label_phase", new Object[]{run.labelProgress()}),
                                                   false
                                                );
                                             }
                                          }
                                       );
                                    Optional<StepExecution> execOpt = bot.getStepExecutorAdapter().getActiveExecution();
                                    if (execOpt.isEmpty()) {
                                       source.sendSuccess(() -> Component.translatable("message.playerengine.chain.no_step_yet"), false);
                                    } else {
                                       StepExecution exec = execOpt.get();
                                       if (exec.getState() == StepState.RUNNING) {
                                          long secs = exec.getElapsedMs() / 1000L;
                                          source.sendSuccess(
                                             () -> Component.translatable(
                                                "message.playerengine.chain.active_step", new Object[]{exec.getStepId(), exec.getStepKind(), secs}
                                             ),
                                             false
                                          );
                                          String last = exec.getLastLogEntry();
                                          source.sendSuccess(
                                             () -> Component.translatable("message.playerengine.chain.last_log_entry", new Object[]{last}), false
                                          );
                                       } else {
                                          source.sendSuccess(
                                             () -> Component.translatable(
                                                "message.playerengine.chain.last_step", new Object[]{exec.getStepId(), exec.getStepKind(), exec.getState()}
                                             ),
                                             false
                                          );
                                       }

                                       Task userTask = bot.getUserTaskChain().getCurrentTask();
                                       if (userTask != null) {
                                          source.sendSuccess(
                                             () -> Component.translatable("message.playerengine.chain.user_task", new Object[]{userTask}), false
                                          );
                                          if (userTask instanceof DescribesProgress progress) {
                                             source.sendSuccess(
                                                () -> Component.translatable(
                                                   "message.playerengine.chain.task_progress", new Object[]{progress.describeProgress()}
                                                ),
                                                false
                                             );
                                          }

                                          String tree = userTask.getTaskTree();
                                          if (tree != null && !tree.isBlank()) {
                                             source.sendSuccess(
                                                () -> Component.translatable("message.playerengine.chain.task_tree", new Object[]{tree}), false
                                             );
                                          }
                                       }

                                       source.sendSuccess(() -> Component.translatable("message.playerengine.chain.full_log_divider"), false);

                                       for (String logEntry : exec.getLog()) {
                                          source.sendSuccess(
                                             () -> Component.translatable("message.playerengine.chain.log_entry", new Object[]{logEntry}), false
                                          );
                                       }
                                    }
                                 }
                              }

                              return 1;
                           }
                        )
                  )
            )
      );
      root.then(Commands.argument("command", StringArgumentType.greedyString()).executes(command -> {
         CommandSourceStack source = (CommandSourceStack)command.getSource();
         String sub = BaritoneArgumentType.getCommand(command, "command");
         return source.getEntity() instanceof LivingEntity living ? runCommand(source, living, sub) : runBaritoneWithoutExecutorEntity(source, sub);
      }));
      dispatcher.register(root);
   }

   private static void contributePlayer2Help() {
      HelpRegistry.register(
         new HelpEntry("playerengine", "player2 reload", "player2 reload", "help.playerengine.player2-reload.short", null, List.of(), 2, null, "config")
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 payer prompter",
            "player2 payer prompter",
            "help.playerengine.player2-payer-prompter.short",
            null,
            List.of(),
            2,
            null,
            "config"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine", "player2 payer owner", "player2 payer owner", "help.playerengine.player2-payer-owner.short", null, List.of(), 2, null, "config"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 dedicated",
            "player2 dedicated <value>",
            "help.playerengine.player2-dedicated.short",
            "help.playerengine.player2-dedicated.long",
            List.of(new ArgNote("value", "help.playerengine.player2-dedicated.arg.value")),
            2,
            null,
            "config"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 owner_offline_continue",
            "player2 owner_offline_continue <value>",
            "help.playerengine.player2-owner-offline-continue.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-owner-offline-continue.arg.value")),
            2,
            null,
            "config"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 call_by_name",
            "player2 call_by_name <value>",
            "help.playerengine.player2-call-by-name.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-call-by-name.arg.value")),
            2,
            null,
            "config"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 npc_max_spawn",
            "player2 npc_max_spawn <value>",
            "help.playerengine.player2-npc-max-spawn.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-npc-max-spawn.arg.value")),
            2,
            null,
            "config"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 npc_max_stored_ids",
            "player2 npc_max_stored_ids <value>",
            "help.playerengine.player2-npc-max-stored-ids.short",
            null,
            List.of(new ArgNote("value", "help.playerengine.player2-npc-max-stored-ids.arg.value")),
            2,
            null,
            "config"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "player2 chain status",
            "player2 chain status",
            "help.playerengine.player2-chain-status.short",
            "help.playerengine.player2-chain-status.long",
            List.of(),
            2,
            null,
            "diagnostics"
         )
      );
   }

   private static void syncPlayer2ConfigToAllPlayers(CommandSourceStack source) {
      MinecraftServer server = source.getServer();
      if (server != null) {
         for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Player2ServerNetworking.sendConfigSync(sp);
         }
      }
   }

   private static boolean isLogicalServer(CommandSourceStack source) {
      if (source.getLevel().isClientSide()) {
         source.sendFailure(Component.translatable("message.playerengine.commands.server_only"));
         return false;
      } else {
         return true;
      }
   }

   private static Baritone findAnyBaritone(MinecraftServer server) {
      if (server == null) {
         return null;
      }

      for (ServerLevel level : server.getAllLevels()) {
         for (Entity e : level.getAllEntities()) {
            if (e instanceof LivingEntity living && e instanceof IAutomatone) {
               IBaritone b = IBaritone.KEY.getNullable(living);
               if (b instanceof Baritone baritone) {
                  return baritone;
               }
            }
         }
      }

      return null;
   }

   private static int runBaritoneWithoutExecutorEntity(CommandSourceStack source, String rawCommand) throws CommandSyntaxException {
      if (!isLogicalServer(source)) {
         return 0;
      }

      MinecraftServer server = source.getServer();
      if (server == null) {
         source.sendFailure(Component.translatable("message.playerengine.commands.no_server"));
         return 0;
      }

      Baritone baritone = findAnyBaritone(server);
      if (baritone != null) {
         try {
            boolean ok = new BaritoneCommandManager(baritone).execute(source, BaritoneCommandManager.expand(rawCommand));
            if (!ok) {
               source.sendFailure(Component.translatable("message.playerengine.commands.unknown_command"));
               return 0;
            } else {
               return 1;
            }
         } catch (CommandException e) {
            throw BARITONE_COMMAND_FAILED_EXCEPTION.create(e.handle());
         }
      } else {
         String cmd = rawCommand.trim();
         if (cmd.isEmpty() || cmd.equalsIgnoreCase("help") || cmd.equals("?")) {
            sendConsoleBaritoneFallbackHelp(source);
            return 1;
         } else if (cmd.equalsIgnoreCase("version")) {
            source.sendSuccess(() -> Component.translatable("message.playerengine.commands.version_no_npc"), false);
            return 1;
         } else {
            source.sendFailure(Component.translatable("message.playerengine.commands.no_automaton_npc"));
            return 0;
         }
      }
   }

   private static void sendConsoleBaritoneFallbackHelp(CommandSourceStack source) {
      source.sendSuccess(() -> Component.translatable("message.playerengine.commands.console_help_header"), false);
      source.sendSuccess(() -> Component.translatable("message.playerengine.commands.console_help_admin"), false);
      source.sendSuccess(() -> Component.translatable("message.playerengine.commands.console_help_budget"), false);
      source.sendSuccess(() -> Component.translatable("message.playerengine.commands.console_help_chain"), false);
      source.sendSuccess(() -> Component.translatable("message.playerengine.commands.console_help_ingame"), false);
   }

   private static int runCommand(CommandSourceStack source, Entity target, String command) throws CommandSyntaxException {
      if (!(target instanceof LivingEntity)) {
         throw EntityArgument.NO_ENTITIES_FOUND.create();
      }

      try {
         for (LivingEntity entity : target.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(100.0, 100.0, 100.0), e -> true)) {
            if (entity instanceof IAutomatone) {
               runCommand(source, command, BaritoneAPI.getProvider().getBaritone(entity));
            }
         }

         return 1;
      } catch (CommandException var6) {
         throw BARITONE_COMMAND_FAILED_EXCEPTION.create(var6.handle());
      }
   }
}
