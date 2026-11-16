package gemini.module.modules.misc;

import gemini.Gemini;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.world.WaypointsModule;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.ui.HudOverlay;
import gemini.util.Coords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Copies where you are to the clipboard in one key press: the position in the format you choose, optionally with
 * the dimension, the biome and the matching Nether or Overworld coordinates, and can pin the spot in Waypoints.
 * It runs once and switches itself off, so bind it to a key.
 */
public final class CoordsCopier extends Module {
   private final ModeSetting format = this.addSetting(
      new ModeSetting(
         "Format",
         "Plain: x y z. Comma: x, y, z. Teleport: a /tp command. Execute: a /execute in <dimension> run tp command that works from any dimension",
         "Plain",
         "Plain",
         "Comma",
         "Teleport",
         "Execute"
      )
   );
   private final BooleanSetting exact = this.addSetting(new BooleanSetting("Exact", "Copy your exact position with two decimals instead of block coordinates", false));
   private final BooleanSetting dimension = this.addSetting(new BooleanSetting("Dimension", "Add the dimension name after the coordinates", false));
   private final BooleanSetting biome = this.addSetting(new BooleanSetting("Biome", "Add the biome you are standing in", false));
   private final BooleanSetting linked = this.addSetting(
      new BooleanSetting("Linked Coords", "Add the matching Nether coordinates in the Overworld, or Overworld coordinates in the Nether", false)
   );
   private final BooleanSetting waypoint = this.addSetting(new BooleanSetting("Pin Waypoint", "Also pin this spot in Waypoints", false));

   public CoordsCopier() {
      super("Coords Copier", "Copy your coordinates (and optionally dimension, biome and Nether coords) to the clipboard with one key.", Category.CLIENT);
      this.addSetting(new ActionSetting("Copy Now", "Copy your coordinates right away", this::copy));
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
   public boolean showInArrayList() {
      return false;
   }

   @Override
   public void onEnable() {
      this.copy();
      this.setEnabled(false, false);
   }

   /** The clipboard text for a position; split out so the module and the action button build exactly the same thing. */
   static String text(String format, boolean exact, String dimensionId, String biomeId, boolean withDimension, boolean withLinked, double x, double y, double z) {
      StringBuilder text = new StringBuilder(Coords.format(format, dimensionId, x, y, z, exact));
      if (withDimension && !"Execute".equals(format)) {
         text.append(" (").append(Coords.dimensionName(dimensionId)).append(')');
      }

      if (biomeId != null) {
         int colon = biomeId.indexOf(':');
         text.append(" [").append(colon >= 0 ? biomeId.substring(colon + 1) : biomeId).append(']');
      }

      if (withLinked) {
         String other = Coords.NETHER.equals(dimensionId) ? Coords.OVERWORLD : (Coords.OVERWORLD.equals(dimensionId) ? Coords.NETHER : null);
         if (other != null) {
            double factor = Coords.scale(dimensionId, other);
            text.append(" | ").append(Coords.dimensionName(other)).append(": ")
               .append(Coords.number(x * factor, exact)).append(' ').append(Coords.number(y, exact)).append(' ').append(Coords.number(z * factor, exact));
         }
      }

      return text.toString();
   }

   private void copy() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null) {
         return;
      }

      String dimensionId = client.level.dimension().identifier().toString();
      BlockPos feet = player.blockPosition();
      String biomeId = this.biome.get() ? client.level.getBiome(feet).unwrapKey().map(key -> key.identifier().toString()).orElse("unknown") : null;
      String text = text(
         this.format.get(), this.exact.get(), dimensionId, biomeId, this.dimension.get(), this.linked.get(), player.getX(), player.getY(), player.getZ()
      );
      client.keyboardHandler.setClipboard(text);
      HudOverlay.toast("Coords Copier", text, HudOverlay.Severity.SUCCESS);
      if (this.waypoint.get() && Gemini.getInstance() != null && Gemini.getInstance().modules() != null) {
         Gemini.getInstance().modules().byClass(WaypointsModule.class).ifPresent(waypoints -> waypoints.pin(null, feet.getX(), feet.getY(), feet.getZ()));
      }
   }
}
