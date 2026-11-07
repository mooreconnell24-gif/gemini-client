package com.inferno.fabric.admin;

import com.inferno.fabric.Alerts;
import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.checks.Check;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class AdminCommands {
   private AdminCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> root = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("infernoadmin").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p != null && AdminService.isAdmin(p) ? true : src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
      });
      root.then(Commands.literal("kick").then(((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.kick(t, "Kicked by admin");
         msg((CommandSourceStack)ctx.getSource(), "Kicked " + t.getName().getString());
         return 1;
      })).then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         String reason = StringArgumentType.getString(ctx, "reason");
         AdminService.kick(t, reason);
         msg((CommandSourceStack)ctx.getSource(), "Kicked " + t.getName().getString() + ": " + reason);
         return 1;
      }))));
      root.then(
         ((LiteralArgumentBuilder)Commands.literal("ban").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p != null && AdminService.canBan(p) || src.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
            }))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                     .executes(
                        ctx -> ban(
                           (CommandSourceStack)ctx.getSource(),
                           EntityArgument.getPlayer(ctx, "player"),
                           InfernoConfig.get().defaultBanMinutes,
                           "Banned by admin"
                        )
                     ))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("minutes", IntegerArgumentType.integer(0, 525600))
                           .executes(
                              ctx -> ban(
                                 (CommandSourceStack)ctx.getSource(),
                                 EntityArgument.getPlayer(ctx, "player"),
                                 IntegerArgumentType.getInteger(ctx, "minutes"),
                                 "Banned by admin"
                              )
                           ))
                        .then(
                           Commands.argument("reason", StringArgumentType.greedyString())
                              .executes(
                                 ctx -> ban(
                                    (CommandSourceStack)ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"),
                                    IntegerArgumentType.getInteger(ctx, "minutes"),
                                    StringArgumentType.getString(ctx, "reason")
                                 )
                              )
                        )
                  )
            )
      );
      root.then(Commands.literal("login").then(Commands.argument("password", StringArgumentType.greedyString()).executes(ctx -> {
         ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         AdminRank rank = OpAuth.tryLogin(p.getStringUUID(), StringArgumentType.getString(ctx, "password"));
         if (rank.atLeast(AdminRank.HELPER)) {
            msg((CommandSourceStack)ctx.getSource(), "Logged in as " + rank.label());
         } else {
            msg((CommandSourceStack)ctx.getSource(), "Wrong password");
         }

         return 1;
      })));
      root.then(Commands.literal("unban").then(Commands.argument("target", StringArgumentType.greedyString()).executes(ctx -> {
         String target = StringArgumentType.getString(ctx, "target");
         boolean ok = AdminService.unban(target);
         msg((CommandSourceStack)ctx.getSource(), ok ? "Unbanned " + target : "No ban found for " + target);
         return ok ? 1 : 0;
      })));
      root.then(Commands.literal("freeze").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.freeze(t, true);
         msg((CommandSourceStack)ctx.getSource(), "Froze " + t.getName().getString());
         return 1;
      })));
      root.then(Commands.literal("unfreeze").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.freeze(t, false);
         msg((CommandSourceStack)ctx.getSource(), "Unfroze " + t.getName().getString());
         return 1;
      })));
      root.then(Commands.literal("exempt").then(((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.exempt(t, 0L);
         msg((CommandSourceStack)ctx.getSource(), "Exempted " + t.getName().getString() + " until cleared");
         return 1;
      })).then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400)).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         int sec = IntegerArgumentType.getInteger(ctx, "seconds");
         AdminService.exempt(t, sec);
         msg((CommandSourceStack)ctx.getSource(), "Exempted " + t.getName().getString() + " for " + sec + "s");
         return 1;
      }))));
      root.then(Commands.literal("unexempt").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.unexempt(t);
         msg((CommandSourceStack)ctx.getSource(), "Removed exempt for " + t.getName().getString());
         return 1;
      })));
      root.then(Commands.literal("tp").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer admin = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.teleportTo(admin, t);
         msg((CommandSourceStack)ctx.getSource(), "Teleported to " + t.getName().getString());
         return 1;
      })));
      root.then(Commands.literal("spectate").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer admin = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.spectate(admin, t);
         msg((CommandSourceStack)ctx.getSource(), "Spectating " + t.getName().getString());
         return 1;
      })));
      root.then(Commands.literal("vl").then(((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         Map<String, Integer> vls = Alerts.violations(t.getStringUUID());
         msg((CommandSourceStack)ctx.getSource(), AdminService.statusLine(t));
         if (vls.isEmpty()) {
            msg((CommandSourceStack)ctx.getSource(), "No violations");
         } else {
            vls.forEach((k, v) -> msg((CommandSourceStack)ctx.getSource(), "  " + k + " = " + v));
         }

         return 1;
      })).then(Commands.literal("clear").executes(ctx -> {
         ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
         AdminService.clearVl(t.getStringUUID());
         msg((CommandSourceStack)ctx.getSource(), "Cleared VL for " + t.getName().getString());
         return 1;
      }))));
      root.then(((LiteralArgumentBuilder)Commands.literal("alerts").executes(ctx -> {
         List<Alerts.Alert> recent = Alerts.recent();
         int n = 0;

         for (Alerts.Alert a : recent) {
            if (n++ >= 15) {
               break;
            }

            msg((CommandSourceStack)ctx.getSource(), a.player + " | " + a.check + " | " + a.detail + " | VL " + a.vl);
         }

         if (n == 0) {
            msg((CommandSourceStack)ctx.getSource(), "No alerts");
         }

         return 1;
      })).then(Commands.literal("clear").executes(ctx -> {
         Alerts.clearLog();
         msg((CommandSourceStack)ctx.getSource(), "Alert log cleared");
         return 1;
      })));
      root.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("check").then(Commands.literal("list").executes(ctx -> {
            for (Check c : InfernoMod.ENGINE.all()) {
               msg((CommandSourceStack)ctx.getSource(), (c.enabled() ? "ON " : "OFF") + " " + c.id() + " - " + c.displayName());
            }

            return 1;
         }))).then(Commands.literal("toggle").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
            String id = StringArgumentType.getString(ctx, "id");
            AdminService.toggleCheck(id, null);
            msg((CommandSourceStack)ctx.getSource(), id + " => " + InfernoConfig.get().enabled(id));
            return 1;
         })))).then(Commands.literal("allon").executes(ctx -> {
            AdminService.setAllChecks(true);
            msg((CommandSourceStack)ctx.getSource(), "All checks enabled");
            return 1;
         }))).then(Commands.literal("alloff").executes(ctx -> {
            AdminService.setAllChecks(false);
            msg((CommandSourceStack)ctx.getSource(), "All checks disabled");
            return 1;
         }))
      );
      root.then(Commands.literal("reload").executes(ctx -> {
         InfernoConfig.reload();
         BanList.load();
         msg((CommandSourceStack)ctx.getSource(), "Config and bans reloaded");
         return 1;
      }));
      root.then(((LiteralArgumentBuilder)Commands.literal("punish").then(Commands.argument("mode", StringArgumentType.word()).executes(ctx -> {
         String mode = StringArgumentType.getString(ctx, "mode");
         AdminService.setPunishMode(mode);
         msg((CommandSourceStack)ctx.getSource(), "Auto punish = " + AdminService.punishMode());
         return 1;
      }))).executes(ctx -> {
         msg((CommandSourceStack)ctx.getSource(), "Auto punish = " + AdminService.punishMode() + " (none|kick|ban)");
         return 1;
      }));
      root.then(Commands.literal("list").executes(ctx -> {
         for (ServerPlayer p : ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers()) {
            msg((CommandSourceStack)ctx.getSource(), AdminService.statusLine(p));
         }

         return 1;
      }));
      root.then(Commands.literal("bans").executes(ctx -> {
         List<BanList.BanEntry> bans = BanList.all();
         if (bans.isEmpty()) {
            msg((CommandSourceStack)ctx.getSource(), "No active bans");
         } else {
            for (BanList.BanEntry e : bans) {
               msg((CommandSourceStack)ctx.getSource(), e.name + " (" + e.uuid + ") - " + e.reason + (e.permanent() ? " [perm]" : ""));
            }
         }

         return 1;
      }));
      root.executes(
         ctx -> {
            msg(
               (CommandSourceStack)ctx.getSource(),
               "Inferno admin: kick ban unban freeze unfreeze exempt unexempt tp spectate vl alerts check reload punish list bans"
            );
            return 1;
         }
      );
      dispatcher.register(root);
   }

   private static int ban(CommandSourceStack src, ServerPlayer t, int minutes, String reason) {
      String by = "console";

      try {
         ServerPlayer admin = src.getPlayer();
         if (admin != null) {
            by = admin.getName().getString();
         }
      } catch (Exception var6) {
      }

      AdminService.ban(t, reason, by, minutes);
      msg(src, "Banned " + t.getName().getString() + (minutes <= 0 ? " permanently" : " for " + minutes + "m"));
      return 1;
   }

   private static void msg(CommandSourceStack src, String text) {
      src.sendSuccess(() -> Component.literal("§8[§6§lInferno§8] §f" + text), false);
   }
}
