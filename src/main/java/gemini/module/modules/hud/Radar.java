package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.UiFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Nearby living entities on a small map that turns with you: straight ahead is up. The map is a disc or a rounded
 * square on the HUD backdrop with a faint range ring and cross; a small "N" on the rim shows where north is. Players
 * are drawn in the accent colour, monsters red, other mobs grey; everything stays inside the map.
 */
public final class Radar extends HudWidget {
   private static final int EDGE = 4;
   private final NumberSetting size = this.addSetting(new NumberSetting("Size", "Size of the radar, in pixels", 76.0, 50.0, 130.0, 2.0));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks shown from the centre", 48.0, 16.0, 128.0, 4.0));
   private final ModeSetting shape = this.addSetting(new ModeSetting("Shape", "Draw the map as a circle or as a rounded square", "Round", "Round", "Square"));

   public Radar() {
      super("Radar", "Shows nearby players and mobs on a small map that turns with you.", "Top Left", 6, 40);
   }

   @Override
   protected boolean hasBackground() {
      // The map draws its own disc or square on the backdrop.
      return false;
   }

   /** An even size, so the centre falls on a whole pixel. */
   private int side() {
      return this.size.intValue() & ~1;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return this.side();
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.side();
   }

   /** Sideways offset of a point from the player as seen on the radar: positive is to the player's right. */
   static double right(double dx, double dz, float yawDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      // Minecraft looks along (-sin yaw, cos yaw); the player's right is (-cos yaw, -sin yaw).
      return -dx * Math.cos(yaw) - dz * Math.sin(yaw);
   }

   /** Forward offset of a point from the player: positive is ahead (drawn upward). */
   static double forward(double dx, double dz, float yawDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      return -dx * Math.sin(yaw) + dz * Math.cos(yaw);
   }

   /** Whether a point {@code px, py} from the centre is inside the map of half-size {@code half}. */
   static boolean inside(int px, int py, int half, boolean round) {
      if (round) {
         return px * px + py * py <= half * half;
      }

      return Math.abs(px) <= half && Math.abs(py) <= half;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      GuiGraphicsExtractor g = hud.graphics;
      boolean round = this.shape.is("Round");
      int backdrop = this.backdrop();
      int side = Math.min(w, h);
      int radius = round ? side / 2 : HudStyle.RADIUS + 2;
      if (backdrop == HudStyle.NONE) {
         // No card: a faint dark disc still gives the dots something to sit on.
         Glass.roundRect(g, 0, 0, side, side, radius, Glass.withAlpha(hud.surface, 70));
      } else {
         HudStyle.card(g, 0, 0, side, side, radius, backdrop, hud.surface, 1.0F);
      }

      int c = side / 2;
      int half = c - EDGE;
      int grid = Glass.withAlpha(0xFFFFFF, 20);
      // A cross through the centre and one ring at half range.
      g.fill(c, EDGE, c + 1, side - EDGE, grid);
      g.fill(EDGE, c, side - EDGE, c + 1, grid);
      if (round) {
         Glass.roundOutline(g, c - half / 2, c - half / 2, c + half / 2 + 1, c + half / 2 + 1, half / 2, grid);
      } else {
         Glass.roundOutline(g, c - half / 2, c - half / 2, c + half / 2 + 1, c + half / 2 + 1, 2, grid);
      }

      LocalPlayer self = hud.client.player;
      if (self != null && hud.client.level != null) {
         double maxR = this.range.get();
         float yaw = self.getYRot();
         double sin = Math.sin(Math.toRadians(yaw));
         double cos = Math.cos(Math.toRadians(yaw));
         int dotLimit = half - 1;
         for (Entity entity : hud.client.level.entitiesForRendering()) {
            if (entity != self && entity instanceof LivingEntity && entity.isAlive()) {
               double dx = entity.getX() - self.getX();
               double dz = entity.getZ() - self.getZ();
               if (Math.abs(dx) > maxR || Math.abs(dz) > maxR) {
                  continue;
               }

               // same maths as right()/forward(), with the trigonometry done once per frame
               double rx = -dx * cos - dz * sin;
               double fw = -dx * sin + dz * cos;
               int px = (int)Math.round(rx / maxR * half);
               int py = -(int)Math.round(fw / maxR * half);
               if (inside(px, py, dotLimit, round)) {
                  boolean player = entity instanceof Player;
                  int color = player ? Glass.lerp(hud.accent, 0xFFFFFFFF, 0.3F) : (entity instanceof Enemy ? Glass.withAlpha(Glass.ERROR, 230) : Glass.withAlpha(Glass.TEXT_DIM, 200));
                  // Players get a 3x3 dot; everything else is a crisp 2x2.
                  int grow = player ? 2 : 1;
                  g.fill(c + px - 1, c + py - 1, c + px + grow, c + py + grow, color);
               }
            }
         }

         // North on the rim, turning with the map: north (0, -1) lands at (sin yaw, cos yaw) from the centre.
         int rim = half - 4;
         int nx = c + (int)Math.round(sin * rim);
         int ny = c + (int)Math.round(cos * rim);
         UiFont.draw(g, "N", nx - 2, ny - 3, HudStyle.label(backdrop), hud.shadow);
      }

      // You, at the centre: a small chevron pointing ahead.
      int you = Glass.TEXT;
      g.fill(c, c - 3, c + 1, c - 2, you);
      g.fill(c - 1, c - 2, c + 2, c - 1, you);
      g.fill(c - 2, c - 1, c + 3, c, you);
      g.fill(c - 2, c, c - 1, c + 1, you);
      g.fill(c + 2, c, c + 3, c + 1, you);
   }
}
