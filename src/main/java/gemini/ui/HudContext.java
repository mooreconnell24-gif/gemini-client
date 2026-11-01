package gemini.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudContext {
   public final GuiGraphicsExtractor graphics;
   public final Minecraft client;
   public final Font font;
   public final int width;
   public final int height;
   public final int accent;
   public final int accent2;
   public final boolean shadow;
   public final float partialTick;

   public HudContext(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, int accent, int accent2, boolean shadow, float partialTick) {
      this.graphics = graphics;
      this.client = client;
      this.font = client.font;
      this.width = width;
      this.height = height;
      this.accent = accent;
      this.accent2 = accent2;
      this.shadow = shadow;
      this.partialTick = partialTick;
   }
}
