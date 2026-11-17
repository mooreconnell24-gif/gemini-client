package gemini.ui;

import gemini.Gemini;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;

/**
 * How many boss bars the vanilla HUD is showing, so HUD widgets can stay clear of them. The overlay keeps its bars
 * in a private map with no public count, so the map is read once through reflection; if that ever fails the
 * count falls back to one bar while the overlay says a boss wants music, fog or a darker sky, else zero.
 */
final class BossBars {
   private static Field events;
   private static boolean failed;

   private BossBars() {
   }

   static int count(Minecraft client) {
      BossHealthOverlay overlay = client.gui.hud.getBossOverlay();
      if (overlay == null) {
         return 0;
      }

      if (!failed) {
         try {
            if (events == null) {
               Field field = BossHealthOverlay.class.getDeclaredField("events");
               field.setAccessible(true);
               events = field;
            }

            return events.get(overlay) instanceof Map<?, ?> map ? map.size() : 0;
         } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            Gemini.LOGGER.warn("Could not count boss bars; HUD widgets assume one while a boss is around", e);
         }
      }

      return overlay.shouldPlayMusic() || overlay.shouldCreateWorldFog() || overlay.shouldDarkenScreen() ? 1 : 0;
   }
}
