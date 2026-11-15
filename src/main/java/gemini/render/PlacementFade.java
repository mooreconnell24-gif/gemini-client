package gemini.render;

import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * Blocks a module has just placed, drawn as boxes that fade out (and shrink a little) over a configurable time.
 *
 * <p>The positions live in a fixed ring of packed longs and timestamps, so recording a placement and drawing the
 * fades allocate nothing; the oldest entry is overwritten once the ring is full. Drawing goes through
 * {@link Render3D#boxArgb}, which culls off-screen boxes before building anything.
 */
public final class PlacementFade {
   private static final int CAPACITY = 64;
   private final BooleanSetting enabled;
   private final NumberSetting duration;
   private final ColorSetting color;
   private final long[] positions = new long[CAPACITY];
   private final long[] placedAt = new long[CAPACITY];
   private int head;
   private int size;

   /**
    * Adds "Placed Fade", "Fade Time" and "Fade Color" to the module. {@code visible} gates the whole group in the
    * menu (for a module that already has its own highlight switch).
    */
   public PlacementFade(Module module, boolean defaultOn, int defaultHue, BooleanSupplier visible) {
      this.enabled = module.addSetting(
         new BooleanSetting("Placed Fade", "Draw each block you place as a box that fades out", defaultOn).showIf(visible)
      );
      this.duration = module.addSetting(
         new NumberSetting("Fade Time", "How long a placed block stays highlighted, in milliseconds", 650.0, 100.0, 3000.0, 50.0)
            .showIf(() -> visible.getAsBoolean() && this.enabled.get())
      );
      this.color = module.addSetting(
         new ColorSetting("Fade Color", "Colour of the placed-block fade", defaultHue).showIf(() -> visible.getAsBoolean() && this.enabled.get())
      );
   }

   public boolean enabled() {
      return this.enabled.get();
   }

   /** Remembers a placement made just now. */
   public void record(BlockPos pos) {
      if (pos == null || !this.enabled.get()) {
         return;
      }

      int slot = (this.head + this.size) % CAPACITY;
      if (this.size == CAPACITY) {
         this.head = (this.head + 1) % CAPACITY;
      } else {
         this.size++;
      }

      this.positions[slot] = pos.asLong();
      this.placedAt[slot] = Util.getMillis();
   }

   /** Forgets everything; for disable and for a world change, where the positions mean nothing any more. */
   public void clear() {
      this.head = 0;
      this.size = 0;
   }

   /** Draws the fades and drops the ones that have run out. Call from {@code onRender3D}. */
   public void render() {
      if (this.size == 0) {
         return;
      }

      if (!this.enabled.get()) {
         this.clear();
         return;
      }

      long now = Util.getMillis();
      double life = Math.max(1.0, this.duration.get());
      while (this.size > 0 && now - this.placedAt[this.head] >= life) {
         this.head = (this.head + 1) % CAPACITY;
         this.size--;
      }

      int base = this.color.argb();
      float baseAlpha = (base >>> 24) / 255.0F;
      int rgb = base & 0xFFFFFF;
      for (int i = 0; i < this.size; i++) {
         int slot = (this.head + i) % CAPACITY;
         float progress = (float)Math.min(1.0, (now - this.placedAt[slot]) / life);
         float remaining = fadeCurve(progress);
         int strokeAlpha = Math.round(235.0F * remaining * baseAlpha);
         int fillAlpha = Math.round(70.0F * remaining * baseAlpha);
         if (strokeAlpha <= 0 && fillAlpha <= 0) {
            continue;
         }

         long packed = this.positions[slot];
         int x = BlockPos.getX(packed);
         int y = BlockPos.getY(packed);
         int z = BlockPos.getZ(packed);
         double inset = 0.12 * progress;
         Render3D.boxArgb(
            x + inset, y + inset, z + inset, x + 1.0 - inset, y + 1.0 - inset, z + 1.0 - inset,
            strokeAlpha << 24 | rgb, fillAlpha << 24 | rgb, 1.6F, false
         );
      }
   }

   /** 1 at placement down to 0 at the end, easing out: bright for a moment, then a soft tail. */
   static float fadeCurve(float progress) {
      float t = Math.max(0.0F, Math.min(1.0F, progress));
      float inverse = 1.0F - t;
      return inverse * inverse * (1.0F + 2.0F * t);
   }
}
