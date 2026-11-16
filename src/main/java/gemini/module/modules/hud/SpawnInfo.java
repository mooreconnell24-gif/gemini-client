package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.util.AttributeBoost;
import gemini.util.Coords;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;

/**
 * Where you will respawn. The world spawn comes from the client level; your own respawn point (bed or respawn
 * anchor) is only known to the server, so once a second the integrated server's copy of you is asked for it, and
 * whether the bed is still there or how many charges the anchor has left.
 */
public final class SpawnInfo extends Ticked {
   private final BooleanSetting worldSpawn = this.addSetting(new BooleanSetting("World Spawn", "Show the world spawn point", true));
   private final BooleanSetting respawn = this.addSetting(new BooleanSetting("Respawn Point", "Show your bed or respawn anchor, and whether it still works", true));
   private final BooleanSetting coordinates = this.addSetting(new BooleanSetting("Coordinates", "Show the block coordinates of each point", true));
   private final BooleanSetting distance = this.addSetting(
      new BooleanSetting("Distance", "Show how far away each point is (across the Nether link when it is in the other dimension)", true)
   );
   private final BooleanSetting heading = this.addSetting(new BooleanSetting("Heading", "Show the compass direction to each point", true));
   private volatile Respawn point;
   private int poll;

   public SpawnInfo() {
      super("Spawn Info", "World spawn and your bed or anchor: coordinates, distance, direction and whether it still works.", "Top Left", 6, 170, 250);
   }

   /** The respawn point as the server sees it. {@code kind} is Bed, Anchor, Missing (block gone or unloaded) or Spot. */
   private record Respawn(String dimension, int x, int y, int z, String kind, int charges) {
   }

   @Override
   public void onEnable() {
      this.point = null;
      this.poll = 0;
   }

   @Override
   public void onDisable() {
      this.point = null;
   }

   @Override
   public void onWorldChange() {
      this.point = null;
      this.poll = 0;
   }

   @Override
   public void onTick() {
      if (!this.respawn.get() || this.poll-- > 0) {
         return;
      }

      this.poll = 20;
      AttributeBoost.onServer(this::readRespawn);
   }

   /** Runs on the server thread. */
   private void readRespawn(ServerPlayer player) {
      ServerPlayer.RespawnConfig config = player.getRespawnConfig();
      if (config == null) {
         this.point = null;
         return;
      }

      LevelData.RespawnData data = config.respawnData();
      BlockPos pos = data.pos();
      ServerLevel level = player.level().getServer().getLevel(data.dimension());
      String kind = "Spot";
      int charges = -1;
      // An unloaded chunk cannot be checked without loading it, so the point is shown without a verdict.
      if (level != null && level.isLoaded(pos)) {
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof BedBlock) {
            kind = "Bed";
         } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            kind = "Anchor";
            charges = state.getValue(RespawnAnchorBlock.CHARGE);
         } else if (!config.forced()) {
            kind = "Missing";
         }
      }

      this.point = new Respawn(data.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), kind, charges);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      Minecraft client = hud.client;
      LocalPlayer player = client.player;
      if (player == null || client.level == null) {
         return List.of();
      }

      String here = client.level.dimension().identifier().toString();
      List<TextHudWidget.Line> lines = new ArrayList<>(2);
      if (this.worldSpawn.get()) {
         LevelData.RespawnData spawn = client.level.getRespawnData();
         if (spawn != null) {
            BlockPos pos = spawn.pos();
            String dimension = spawn.dimension().identifier().toString();
            lines.add(new TextHudWidget.Line(this.describe("Spawn", here, player, dimension, pos.getX(), pos.getY(), pos.getZ()), Glass.TEXT));
         }
      }

      if (this.respawn.get()) {
         Respawn bed = this.point;
         if (bed == null) {
            lines.add(new TextHudWidget.Line("Bed: none (world spawn)", Glass.TEXT_FAINT));
         } else {
            String label = switch (bed.kind()) {
               case "Anchor" -> "Anchor " + bed.charges() + "/" + RespawnAnchorBlock.MAX_CHARGES;
               case "Missing" -> "Bed missing";
               case "Bed" -> "Bed";
               default -> "Respawn";
            };
            int color = "Missing".equals(bed.kind()) || bed.charges() == 0 ? Glass.ERROR : ("Spot".equals(bed.kind()) ? Glass.TEXT : Glass.OK);
            lines.add(new TextHudWidget.Line(this.describe(label, here, player, bed.dimension(), bed.x(), bed.y(), bed.z()), color));
         }
      }

      return lines;
   }

   private String describe(String label, String here, LocalPlayer player, String dimension, int x, int y, int z) {
      StringBuilder text = new StringBuilder(label);
      if (!dimension.equals(here)) {
         text.append(" (").append(Coords.dimensionName(dimension)).append(')');
      }

      if (this.coordinates.get()) {
         text.append(' ').append(x).append(' ').append(y).append(' ').append(z);
      }

      double factor = Coords.scale(dimension, here);
      if (!Double.isNaN(factor) && (this.distance.get() || this.heading.get())) {
         double tx = (x + 0.5) * factor;
         double tz = (z + 0.5) * factor;
         text.append("  ");
         if (this.distance.get()) {
            text.append(Math.round(Coords.distance(here, player.getX(), player.getZ(), dimension, x + 0.5, z + 0.5))).append('m');
         }

         if (this.heading.get()) {
            text.append(this.distance.get() ? " " : "").append(Coords.heading(tx - player.getX(), tz - player.getZ()));
         }
      }

      return text.toString();
   }
}
