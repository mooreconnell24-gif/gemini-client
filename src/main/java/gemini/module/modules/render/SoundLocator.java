package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SoundLocator extends Module {
   public static SoundLocator instance;
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Maximum distance to locate sounds", 48.0, 12.0, 96.0, 4.0));
   private final BooleanSetting onlyImportant = this.addSetting(
      new BooleanSetting("Important Only", "Only show chests, explosions, teleports, totems", true)
   );
   private final ArrayDeque<SoundLocator.SoundPing> pings = new ArrayDeque<>();

   public SoundLocator() {
      super("SoundLocator", "Visualize nearby explosions.", Category.RENDER);
      instance = this;
   }

   @Override
   public void onDisable() {
      this.pings.clear();
   }

   public void recordSound(String path, double x, double y, double z) {
      if (this.isEnabled()) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            double maxD = this.range.get();
            double d2 = client.player.distanceToSqr(x, y, z);
            if (!(d2 < 2.25) && !(d2 > maxD * maxD)) {
               String label = classifySound(path, this.onlyImportant.get());
               if (label != null) {
                  Vec3 pos = new Vec3(x, y, z);
                  synchronized (this.pings) {
                     for (SoundLocator.SoundPing existing : this.pings) {
                        if (existing.pos.distanceToSqr(pos) < 4.0 && existing.label.equals(label)) {
                           return;
                        }
                     }

                     this.pings.addLast(new SoundLocator.SoundPing(label, pos, System.currentTimeMillis()));

                     while (this.pings.size() > 14) {
                        this.pings.removeFirst();
                     }
                  }
               }
            }
         }
      }
   }

   private static String classifySound(String path, boolean importantOnly) {
      if (path.contains("chest") || path.contains("shulker") || path.contains("barrel")) {
         return "Container";
      } else if (path.contains("explode") || path.contains("tnt")) {
         return "Explosion";
      } else if (path.contains("teleport") || path.contains("chorus") || path.contains("ender_pearl")) {
         return "Teleport";
      } else if (path.contains("totem")) {
         return "Totem Pop";
      } else if (!path.contains("Drink") && !path.contains("drink") && !path.contains("burp")) {
         return importantOnly || !path.contains("step") && !path.contains("swim") ? null : "Movement";
      } else {
         return "Consumable";
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      long now = System.currentTimeMillis();
      synchronized (this.pings) {
         Iterator<SoundLocator.SoundPing> it = this.pings.iterator();

         while (it.hasNext()) {
            SoundLocator.SoundPing ping = it.next();
            float t = (float)(now - ping.createdAt) / 1400.0F;
            if (t >= 1.0F) {
               it.remove();
            } else {
               int color = !ping.label.equals("Explosion") && !ping.label.equals("Totem Pop") ? -16718337 : -48043;
               AABB box = new AABB(ping.pos.x - 0.25, ping.pos.y - 0.25, ping.pos.z - 0.25, ping.pos.x + 0.25, ping.pos.y + 0.25, ping.pos.z + 0.25);
               Render3D.box(box, color, true, true, 1.5F, Math.round(65.0F * (1.0F - t)), true);
               Render3D.text(ping.label, ping.pos.add(0.0, 0.55, 0.0), color, 0.78F, true);
            }
         }
      }
   }

   private record SoundPing(String label, Vec3 pos, long createdAt) {
   }
}
