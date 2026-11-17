package gemini.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What a HUD hook needs to draw one frame. The overlay keeps a single instance and refills it with
 * {@link #update} every frame, so modules must read it during their {@code onRenderHud} call and not hold on to it.
 */
public final class HudContext {
   public GuiGraphicsExtractor graphics;
   public Minecraft client;
   public Font font;
   public int width;
   public int height;
   public int accent;
   public int accent2;
   /** Base panel colour of the active ClickGUI surface. */
   public int surface;
   public boolean shadow;
   /** The HUD-wide backdrop look, one of {@link HudStyle#SOFT}, {@link HudStyle#SOLID}, {@link HudStyle#NONE}. */
   public int backdrop = HudStyle.SOFT;
   public float partialTick;
   /** Seconds since the previous HUD frame, capped at 0.1; feed it to {@link Glass#approach}. */
   public float delta;

   public HudContext(
      GuiGraphicsExtractor graphics, Minecraft client, int width, int height, int accent, int accent2, int surface, boolean shadow, float partialTick
   ) {
      this.update(graphics, client, width, height, accent, accent2, surface, shadow, partialTick, 0.0F);
   }

   /** Refills the context for a new frame and returns it. */
   public HudContext update(
      GuiGraphicsExtractor graphics, Minecraft client, int width, int height, int accent, int accent2, int surface, boolean shadow, float partialTick, float delta
   ) {
      this.graphics = graphics;
      this.client = client;
      this.font = client.font;
      this.width = width;
      this.height = height;
      this.accent = accent;
      this.accent2 = accent2;
      this.surface = surface;
      this.shadow = shadow;
      this.partialTick = partialTick;
      this.delta = delta;
      return this;
   }
}
