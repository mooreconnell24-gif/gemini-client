package gemini.module.modules;

import gemini.util.VersionBridge;
import com.google.gson.JsonObject;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SoloModules {
   private SoloModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new SoloModules.Home());
      sink.accept(new SoloModules.Gamemode());
      sink.accept(new SoloModules.ItemMagnet());
      sink.accept(new SoloModules.SlimeChunks());
   }

   public static boolean teleport(String dimension, double x, double y, double z) {
      return dimension != null && dimension.matches("[a-z0-9_.:\\-]+")
         ? SpCommands.run(String.format(Locale.ROOT, "execute in %s run tp @s %.3f %.3f %.3f", dimension, x, y, z))
         : false;
   }

   public static boolean gamemode(String mode) {
      String clean = mode == null ? "" : mode.toLowerCase(Locale.ROOT);

      return switch (clean) {
         case "0", "s", "survival" -> SpCommands.run("gamemode survival");
         case "1", "c", "creative" -> SpCommands.run("gamemode creative");
         case "2", "a", "adventure" -> SpCommands.run("gamemode adventure");
         case "3", "sp", "spectator" -> SpCommands.run("gamemode spectator");
         default -> false;
      };
   }

   public static final class Gamemode extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Gamemode to apply", "Creative", "Survival", "Creative", "Adventure", "Spectator")
      );

      public Gamemode() {
         super("Gamemode", "Switch survival, creative, adventure or spectator.", Category.PLAYER);
         this.addSetting(new ActionSetting("Apply", "Switch to the selected gamemode", this::apply));
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onEnable() {
         this.apply();
         this.setEnabled(false, false);
      }

      private void apply() {
         if (SoloModules.gamemode(this.mode.get())) {
            CommandManager.feedback("§bGamemode §7" + this.mode.get().toLowerCase(Locale.ROOT) + ".");
         } else {
            CommandManager.feedback(SingleplayerGate.denialMessage());
         }
      }

      @Override
      public String info() {
         return this.mode.get();
      }
   }

   public static final class Home extends Module {
      public static SoloModules.Home instance;
      private boolean saved;
      private String dimension = "minecraft:overworld";
      private double x;
      private double y;
      private double z;
      private boolean hasBack;
      private String backDimension = "minecraft:overworld";
      private double backX;
      private double backY;
      private double backZ;

      public Home() {
         super("Home", "Save a spot in this world and teleport back to it.", Category.WORLD);
         instance = this;
         this.addSetting(new ActionSetting("Set Home", "Save where you are standing", this::setHere));
         this.addSetting(new ActionSetting("Go Home", "Teleport to the saved spot", this::go));
         this.addSetting(new ActionSetting("Back", "Return to where you were before the last teleport", this::back));
      }

      public static SoloModules.Home get() {
         return instance;
      }

      public void setHere() {
         LocalPlayer player = player();
         if (player != null) {
            this.dimension = player.level().dimension().identifier().toString();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.saved = true;
            this.save();
            CommandManager.feedback(String.format(Locale.ROOT, "§bHome §7set to §f%.1f %.1f %.1f", this.x, this.y, this.z));
            if (!this.isSavedEnabled()) {
               this.setEnabled(true, false);
            }
         }
      }

      public void go() {
         LocalPlayer player = player();
         if (player != null) {
            if (!this.saved) {
               CommandManager.feedback("§cNo home yet. Use §f.sethome §cor the Set Home button.");
            } else {
               this.remember(player);
               if (SoloModules.teleport(this.dimension, this.x, this.y, this.z)) {
                  CommandManager.feedback("§bHome §7teleporting.");
               }
            }
         }
      }

      public void back() {
         if (player() != null) {
            if (!this.hasBack) {
               CommandManager.feedback("§cNowhere to go back to.");
            } else if (SoloModules.teleport(this.backDimension, this.backX, this.backY, this.backZ)) {
               this.hasBack = false;
               CommandManager.feedback("§bHome §7back.");
            }
         }
      }

      private void remember(LocalPlayer player) {
         this.backDimension = player.level().dimension().identifier().toString();
         this.backX = player.getX();
         this.backY = player.getY();
         this.backZ = player.getZ();
         this.hasBack = true;
      }

      private static LocalPlayer player() {
         Minecraft client = Minecraft.getInstance();
         if (SingleplayerGate.allowed() && client.player != null) {
            return client.player;
         } else {
            CommandManager.feedback(SingleplayerGate.denialMessage());
            return null;
         }
      }

      @Override
      public String info() {
         return this.saved ? String.format(Locale.ROOT, "%.0f %.0f %.0f", this.x, this.y, this.z) : "unset";
      }

      @Override
      public void writeExtra(JsonObject object) {
         object.addProperty("homeSet", this.saved);
         object.addProperty("homeDim", this.dimension);
         object.addProperty("homeX", this.x);
         object.addProperty("homeY", this.y);
         object.addProperty("homeZ", this.z);
      }

      @Override
      public void readExtra(JsonObject object) {
         this.saved = object.has("homeSet") && object.get("homeSet").getAsBoolean();
         if (object.has("homeDim")) {
            this.dimension = object.get("homeDim").getAsString();
         }

         if (object.has("homeX")) {
            this.x = object.get("homeX").getAsDouble();
            this.y = object.get("homeY").getAsDouble();
            this.z = object.get("homeZ").getAsDouble();
         }
      }
   }

   public static final class ItemMagnet extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Blocks around you that drops are pulled from", 8.0, 2.0, 24.0, 1.0));

      public ItemMagnet() {
         super("Item Magnet", "Pull your own dropped items toward you.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         IntegratedServer server = client.getSingleplayerServer();
         LocalPlayer player = client.player;
         if (SingleplayerGate.allowed() && server != null && player != null) {
            UUID id = player.getUUID();
            double reach = this.radius.get();
            server.execute(() -> pull(server, id, reach));
         }
      }

      private static void pull(IntegratedServer server, UUID id, double reach) {
         ServerPlayer serverPlayer = server.getPlayerList().getPlayer(id);
         if (serverPlayer != null) {
            AABB area = serverPlayer.getBoundingBox().inflate(reach);

            for (ItemEntity item : serverPlayer.level().getEntitiesOfClass(ItemEntity.class, area, entity -> entity.isAlive() && !entity.isRemoved())) {
               Vec3 delta = serverPlayer.position().add(0.0, 0.6, 0.0).subtract(item.position());
               double distance = delta.length();
               if (!(distance < 0.8) && !(distance > reach)) {
                  item.setDeltaMovement(delta.scale(Math.min(0.45, 1.4 / distance)));
                  item.setPickUpDelay(0);
               }
            }
         }
      }

      @Override
      public String info() {
         return this.radius.intValue() + "m";
      }
   }

   public static final class SlimeChunks extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Chunks around you to check", 6.0, 1.0, 12.0, 1.0));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight color", 120));
      private long seed;
      private boolean haveSeed;

      public SlimeChunks() {
         super("Slime Chunks", "Outline overworld chunks where slimes can spawn.", Category.RENDER);
      }

      @Override
      public void onEnable() {
         this.haveSeed = false;
         IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
         if (server != null) {
            this.seed = server.overworld().getSeed();
            this.haveSeed = true;
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (this.haveSeed && player != null && client.level != null && client.level.dimension() == Level.OVERWORLD) {
            int cx = player.chunkPosition().x();
            int cz = player.chunkPosition().z();
            int extra = this.radius.intValue();
            int floor = Mth.floor(player.getY());
            int argb = this.color.argb();

            for (int x = cx - extra; x <= cx + extra; x++) {
               for (int z = cz - extra; z <= cz + extra; z++) {
                  if (isSlimeChunk(this.seed, x, z)) {
                     BlockPos mark = new BlockPos(x * 16 + 8, floor, z * 16 + 8);
                     Render3D.floorMark(mark, 0.05, argb, true, true, 1.5F, 50, false);
                     Render3D.box(
                        new AABB(x * 16, Math.max(VersionBridge.minWorldY(client.level), floor - 2), z * 16, x * 16 + 16, Math.min(40, floor + 1), z * 16 + 16),
                        argb,
                        true,
                        false,
                        1.0F,
                        0,
                        false
                     );
                  }
               }
            }
         }
      }

      static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
         Random random = new Random(worldSeed + chunkX * chunkX * 4987142 + chunkX * 5947611 + chunkZ * chunkZ * 4392871L + chunkZ * 389711 ^ 987234911L);
         return random.nextInt(10) == 0;
      }

      @Override
      public String info() {
         return this.haveSeed ? "seed" : "no seed";
      }
   }
}
