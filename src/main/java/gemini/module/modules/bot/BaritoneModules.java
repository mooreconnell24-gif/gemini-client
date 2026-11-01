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
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
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

   public static final class BaritonePause extends Module {
      public BaritonePause() {
         super("Baritone Pause", "Pause active Baritone pathing and mining.", Category.CLIENT);
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
         super("Baritone Status", "Show current Baritone engine and task status.", Category.CLIENT);
      }

      @Override
      public boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onEnable() {
         CommandManager.feedback("§b" + BaritoneBridge.engineName() + " §7status: §f" + BaritoneBridge.statusSummary());
         this.setEnabled(false, false);
      }
   }

   public static final class AutoMineBot extends Module {
      private final ListSetting blocks = this.addSetting(
         new ListSetting(
            "Target Blocks",
            "Ores or blocks for Baritone to locate and mine",
            ListSetting.Kind.BLOCK,
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:ancient_debris",
            "minecraft:gold_ore",
            "minecraft:iron_ore"
         )
      );

      public AutoMineBot() {
         super("Auto Mine Bot", "Pathfind and mine target ores via Baritone.", Category.WORLD);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         Set<Block> selected = this.blocks.blocks();
         BaritoneBridge.mineBlocks(selected);
      }

      @Override
      public void onDisable() {
         BaritoneBridge.stopAll();
      }

      @Override
      public String info() {
         return BaritoneBridge.isActive() ? BaritoneBridge.statusSummary() : this.blocks.size() + " ores";
      }
   }

   public static final class AutoGoto extends Module {
      private final NumberSetting targetX = this.addSetting(new NumberSetting("Target X", "Destination X coordinate", 0.0, -30000.0, 30000.0, 10.0));
      private final NumberSetting targetY = this.addSetting(new NumberSetting("Target Y", "Destination Y coordinate", 64.0, -64.0, 320.0, 1.0));
      private final NumberSetting targetZ = this.addSetting(new NumberSetting("Target Z", "Destination Z coordinate", 0.0, -30000.0, 30000.0, 10.0));

      public AutoGoto() {
         super("Auto Goto", "Pathfind to X/Y/Z coordinates via Baritone.", Category.MOVEMENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         BaritoneBridge.gotoBlock(new BlockPos(this.targetX.intValue(), this.targetY.intValue(), this.targetZ.intValue()));
      }

      @Override
      public void onDisable() {
         BaritoneBridge.stopAll();
      }

      @Override
      public String info() {
         return this.targetX.intValue() + " " + this.targetZ.intValue();
      }
   }

   public static final class FollowBot extends Module {
      private final ModeSetting targetKind = this.addSetting(
         new ModeSetting("Target", "Entities to follow", "Players", "Players", "Hostile", "Passive", "All")
      );

      public FollowBot() {
         super("Follow Bot", "Pathfind and follow nearby players or mobs.", Category.MOVEMENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         BaritoneBridge.follow(entity -> {
            if (!(entity instanceof LivingEntity living)) {
               return false;
            }

            return switch (this.targetKind.get()) {
               case "Players" -> living instanceof Player;
               case "Hostile" -> Targets.isHostile(living);
               case "Passive" -> Targets.isPassive(living);
               default -> true;
            };
         });
      }

      @Override
      public void onDisable() {
         BaritoneBridge.stopAll();
      }

      @Override
      public String info() {
         return this.targetKind.get();
      }
   }

   public static final class AutoTunnel extends Module {
      private final NumberSetting length = this.addSetting(new NumberSetting("Length", "Blocks to tunnel ahead", 64.0, 4.0, 512.0, 4.0));

      public AutoTunnel() {
         super("Auto Tunnel", "Dig a 1x2 tunnel and bridge gaps automatically.", Category.WORLD);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         Direction dir = player == null ? Direction.NORTH : player.getDirection();
         BaritoneBridge.startTunnel(dir, this.length.intValue());
      }

      @Override
      public void onDisable() {
         BaritoneBridge.stopAll();
      }

      @Override
      public String info() {
         return this.length.intValue() + "m";
      }
   }

   public static final class AutoExplore extends Module {
      public AutoExplore() {
         super("Auto Explore", "Autonomously spiral outward to explore chunks.", Category.WORLD);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            BaritoneBridge.startExplore(player.blockPosition());
         }
      }

      @Override
      public void onDisable() {
         BaritoneBridge.stopAll();
      }
   }

   public static final class AutoExcavate extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Horizontal excavation radius", 4.0, 1.0, 16.0, 1.0));
      private final NumberSetting depth = this.addSetting(new NumberSetting("Depth", "Layers downward to clear", 3.0, 1.0, 16.0, 1.0));

      public AutoExcavate() {
         super("Auto Excavate", "Excavate a cubic area around you layer by layer.", Category.WORLD);
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
            BaritoneBridge.startExcavate(p.offset(-r, -d, -r), p.offset(r, 2, r));
         }
      }

      @Override
      public void onDisable() {
         BaritoneBridge.stopAll();
      }
   }

   public static final class AutoHighway extends Module {
      private final NumberSetting width = this.addSetting(new NumberSetting("Width", "Highway floor width", 3.0, 1.0, 5.0, 1.0));
      private final BooleanSetting rails = this.addSetting(new BooleanSetting("Guard Rails", "Place raised side curb blocks", true));

      public AutoHighway() {
         super("Auto Highway", "Pave a multi-block highway with side curbs ahead.", Category.WORLD);
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

   public static final class PathRenderer extends Module {
      private final ColorSetting lineColor = this.addSetting(new ColorSetting("Path Color", "Color of the 3D Baritone path line", 198));
      private final ColorSetting goalColor = this.addSetting(new ColorSetting("Goal Color", "Color of the destination box", 280));

      public PathRenderer() {
         super("Path Renderer", "Render 3D path lines and goal boxes for Baritone.", Category.RENDER);
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
