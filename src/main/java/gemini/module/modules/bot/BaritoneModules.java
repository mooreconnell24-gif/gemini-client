package gemini.module.modules.bot;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BaritoneBridge;
import gemini.util.BlockPlacer;
import gemini.util.BuiltInBaritone;
import gemini.util.Targets;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;

public final class BaritoneModules {
   private BaritoneModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new BaritoneModules.BaritonePause());
      sink.accept(new BaritoneModules.BaritoneStatus());
      sink.accept(new BaritoneModules.AutoMineBot());
      sink.accept(new BaritoneModules.AutoGoto());
      sink.accept(new BaritoneModules.FollowBot());
      sink.accept(new BaritoneModules.AutoTunnel());
      sink.accept(new BaritoneModules.AutoExplore());
      sink.accept(new BaritoneModules.AutoExcavate());
      sink.accept(new BaritoneModules.AutoHighway());
      sink.accept(new BaritoneModules.PathRenderer());
   }

   public static final class AutoExcavate extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Horizontal radius around you to excavate", 4.0, 1.0, 16.0, 1.0));
      private final NumberSetting depth = this.addSetting(new NumberSetting("Depth", "Layers downward to clear", 3.0, 1.0, 16.0, 1.0));

      public AutoExcavate() {
         super("Auto Excavate", "Clear a cubic area around your position layer by layer using built-in Baritone.", Category.WORLD);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            BlockPos p = player.blockPosition();
            int r = this.radius.intValue();
            int d = this.depth.intValue();
            BuiltInBaritone.startExcavate(p.offset(-r, -d, -r), p.offset(r, 2, r));
         }
      }

      @Override
      public void onDisable() {
         if (BuiltInBaritone.task() == BuiltInBaritone.TaskKind.EXCAVATE) {
            BuiltInBaritone.stop();
         }
      }
   }

   public static final class AutoExplore extends Module {
      public AutoExplore() {
         super("Auto Explore", "Autonomously spiral outward from your position to explore new chunks.", Category.WORLD);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            BuiltInBaritone.startExplore(player.blockPosition());
         }
      }

      @Override
      public void onDisable() {
         if (BuiltInBaritone.task() == BuiltInBaritone.TaskKind.EXPLORE) {
            BuiltInBaritone.stop();
         }
      }
   }

   public static final class AutoGoto extends Module {
      private final NumberSetting targetX = this.addSetting(new NumberSetting("Target X", "Destination X coordinate", 0.0, -30000.0, 30000.0, 10.0));
      private final NumberSetting targetY = this.addSetting(new NumberSetting("Target Y", "Destination Y coordinate", 64.0, -64.0, 320.0, 1.0));
      private final NumberSetting targetZ = this.addSetting(new NumberSetting("Target Z", "Destination Z coordinate", 0.0, -30000.0, 30000.0, 10.0));

      public AutoGoto() {
         super("Auto Goto", "Pathfind to target coordinates using the built-in A* Baritone engine.", Category.MOVEMENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         BuiltInBaritone.gotoBlock(new BlockPos(this.targetX.intValue(), this.targetY.intValue(), this.targetZ.intValue()));
      }

      @Override
      public void onDisable() {
         if (BuiltInBaritone.task() == BuiltInBaritone.TaskKind.GOTO) {
            BuiltInBaritone.stop();
         }
      }

      @Override
      public String info() {
         return this.targetX.intValue() + " " + this.targetZ.intValue();
      }
   }

   public static final class AutoHighway extends Module {
      private final NumberSetting width = this.addSetting(new NumberSetting("Width", "Highway floor width", 3.0, 1.0, 5.0, 1.0));
      private final BooleanSetting rails = this.addSetting(new BooleanSetting("Guard Rails", "Place raised side curb blocks", true));

      public AutoHighway() {
         super("Auto Highway", "Pave a Nether/Overworld obsidian or netherrack highway ahead of you.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (player != null && client.level != null && client.gui.screen() == null) {
            Direction forward = player.getDirection();
            Direction right = forward.getClockWise();
            BlockPos base = player.blockPosition().below().relative(forward);
            int half = this.width.intValue() / 2;

            for (int w = -half; w <= half; w++) {
               BlockPos tile = base.relative(right, w);
               BlockPlacer.place(
                  client,
                  tile,
                  stack -> stack.getItem() instanceof BlockItem bi
                     && (bi.getBlock() == Blocks.OBSIDIAN || bi.getBlock() == Blocks.NETHERRACK || bi.getBlock() == Blocks.COBBLESTONE),
                  true,
                  true,
                  true
               );
            }

            if (this.rails.get()) {
               BlockPlacer.place(client, base.relative(right, -half - 1).above(), BlockPlacer::solidFiller, true, true, true);
               BlockPlacer.place(client, base.relative(right, half + 1).above(), BlockPlacer::solidFiller, true, true, true);
            }
         }
      }
   }

   public static final class AutoMineBot extends Module {
      private final ListSetting blocks = this.addSetting(
         new ListSetting(
            "Target Blocks",
            "Ores or blocks for the built-in Baritone bot to pathfind and mine",
            ListSetting.Kind.BLOCK,
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:ancient_debris",
            "minecraft:gold_ore",
            "minecraft:iron_ore"
         )
      );

      public AutoMineBot() {
         super("Auto Mine Bot", "Automatically pathfind to and mine selected ores using the built-in Baritone engine.", Category.WORLD);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         BuiltInBaritone.mineBlocks(this.blocks.blocks());
      }

      @Override
      public void onDisable() {
         if (BuiltInBaritone.task() == BuiltInBaritone.TaskKind.MINE) {
            BuiltInBaritone.stop();
         }
      }

      @Override
      public String info() {
         return BuiltInBaritone.task() == BuiltInBaritone.TaskKind.MINE ? BuiltInBaritone.statusSummary() : this.blocks.size() + " ores";
      }
   }

   public static final class AutoTunnel extends Module {
      private final NumberSetting length = this.addSetting(new NumberSetting("Length", "Blocks to tunnel ahead", 64.0, 4.0, 512.0, 4.0));

      public AutoTunnel() {
         super("Auto Tunnel", "Automatically dig a 1x2 tunnel forward and bridge gaps using built-in Baritone.", Category.WORLD);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         Direction dir = player == null ? Direction.NORTH : player.getDirection();
         BuiltInBaritone.startTunnel(dir, this.length.intValue());
      }

      @Override
      public void onDisable() {
         if (BuiltInBaritone.task() == BuiltInBaritone.TaskKind.TUNNEL) {
            BuiltInBaritone.stop();
         }
      }

      @Override
      public String info() {
         return this.length.intValue() + "m";
      }
   }

   public static final class BaritonePause extends Module {
      public BaritonePause() {
         super("Baritone Pause", "Force built-in and external Baritone pathing to pause until turned off.", Category.CLIENT);
      }

      @Override
      public boolean wantsBaritonePause() {
         return true;
      }

      @Override
      public void onEnable() {
         BaritoneBridge.pause("manual");
         CommandManager.feedback("§bBaritone §7(" + BaritoneBridge.engineName() + ") paused.");
      }

      @Override
      public void onDisable() {
         BaritoneBridge.resume("manual");
      }

      @Override
      public String info() {
         return "Hold";
      }
   }

   public static final class BaritoneStatus extends Module {
      public BaritoneStatus() {
         super("Baritone Status", "Show the status of the built-in Baritone pathfinder and active task.", Category.CLIENT);
      }

      @Override
      public boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onEnable() {
         CommandManager.feedback("§b" + BaritoneBridge.engineName() + " §7status: §f" + BuiltInBaritone.statusSummary());
         this.setEnabled(false, false);
      }
   }

   public static final class FollowBot extends Module {
      private final ModeSetting targetKind = this.addSetting(
         new ModeSetting("Target", "What entities to follow", "Players", "Players", "Hostile", "Passive", "All")
      );

      public FollowBot() {
         super("Follow Bot", "Automatically pathfind and follow nearby players or mobs.", Category.MOVEMENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         BuiltInBaritone.follow(entity -> {
            if (entity instanceof LivingEntity living) {
               String s0$ = this.targetKind.get();

               return switch (s0$) {
                  case "Players" -> living instanceof Player;
                  case "Hostile" -> Targets.isHostile(living);
                  case "Passive" -> Targets.isPassive(living);
                  default -> true;
               };
            } else {
               return false;
            }
         });
      }

      @Override
      public void onDisable() {
         if (BuiltInBaritone.task() == BuiltInBaritone.TaskKind.FOLLOW) {
            BuiltInBaritone.stop();
         }
      }

      @Override
      public String info() {
         return this.targetKind.get();
      }
   }

   public static final class PathRenderer extends Module {
      private final ColorSetting lineColor = this.addSetting(new ColorSetting("Path Color", "Color of the 3D Baritone path line", 198));
      private final ColorSetting goalColor = this.addSetting(new ColorSetting("Goal Color", "Color of the destination box", 280));

      public PathRenderer() {
         super("Path Renderer", "Customize 3D path and goal rendering for the built-in Baritone engine.", Category.RENDER);
         this.loadState(true, this.getKeybind());
      }

      @Override
      public void onTick() {
         BuiltInBaritone.configureRender(this.isEnabled(), this.lineColor.argb(), this.goalColor.argb());
      }

      @Override
      public void onDisable() {
         BuiltInBaritone.configureRender(false, this.lineColor.argb(), this.goalColor.argb());
      }
   }
}
