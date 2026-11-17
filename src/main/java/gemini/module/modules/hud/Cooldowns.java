package gemini.module.modules.hud;

import gemini.ui.font.FontWeight;
import gemini.ui.font.FontSize;
import gemini.module.HudWidget;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.UiFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item cooldowns, one row each: the item, its name, the seconds left and a thin bar that drains to empty. The game
 * only tells the client what fraction of a cooldown is left, so the seconds are worked out from how fast that
 * fraction falls ({@link CooldownClock}); they appear a moment after the cooldown starts.
 *
 * <p>Which items are cooling down is checked once a tick into reused arrays; a frame only draws.
 */
public final class Cooldowns extends HudWidget {
   private static final Item[] TRACKED = new Item[]{Items.ENDER_PEARL, Items.WIND_CHARGE, Items.GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.SHIELD};
   private static final int ROW = 20;
   private static final int ICON = 16;
   /** One stack per tracked item, built on first use (not while the class loads, before items are bound). */
   private ItemStack[] stacks;
   private String[] names;
   private final CooldownClock[] clocks = new CooldownClock[TRACKED.length];
   private final int[] active = new int[TRACKED.length];
   private final String[] times = new String[TRACKED.length];
   private final boolean[] inSeconds = new boolean[TRACKED.length];
   private int count;
   private int nameW;
   private int builtTick = Integer.MIN_VALUE;

   public Cooldowns() {
      super("Cooldowns", "Shows the cooldowns of ender pearls, wind charges, golden apples, chorus fruit and shields as bars.", "Top Center", -95, 42);
      for (int i = 0; i < this.clocks.length; i++) {
         this.clocks[i] = new CooldownClock();
      }
   }

   @Override
   public void onDisable() {
      for (CooldownClock clock : this.clocks) {
         clock.reset();
      }

      this.count = 0;
      this.builtTick = Integer.MIN_VALUE;
   }

   @Override
   public void onWorldChange() {
      this.onDisable();
   }

   /** Once a tick: which tracked items are cooling down, and their seconds left. */
   private void rebuild(HudContext hud) {
      LocalPlayer player = hud.client.player;
      int tick = player == null ? -1 : player.tickCount;
      if (tick == this.builtTick) {
         return;
      }

      this.builtTick = tick;
      this.count = 0;
      if (player == null) {
         return;
      }

      if (this.stacks == null) {
         this.stacks = new ItemStack[TRACKED.length];
         this.names = new String[TRACKED.length];
         for (int i = 0; i < TRACKED.length; i++) {
            this.stacks[i] = new ItemStack(TRACKED[i]);
            this.names[i] = this.stacks[i].getHoverName().getString();
         }

         int widest = 0;
         for (String name : this.names) {
            widest = Math.max(widest, UiFont.width(name));
         }

         this.nameW = widest;
      }

      long now = System.currentTimeMillis();
      for (int i = 0; i < TRACKED.length; i++) {
         ItemStack stack = this.stacks[i];
         if (!player.getCooldowns().isOnCooldown(stack)) {
            this.clocks[i].reset();
            continue;
         }

         double left = this.clocks[i].sample(player.getCooldowns().getCooldownPercent(stack, 0.0F), now);
         this.times[i] = Double.isNaN(left) ? "" : HudFormat.shortTime(left);
         this.inSeconds[i] = !Double.isNaN(left) && HudFormat.shortTimeInSeconds(left);
         this.active[this.count++] = i;
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      this.rebuild(hud);
      return this.count == 0 ? 0 : 2 * HudStyle.PAD_X + ICON + 6 + this.nameW + 10 + UiFont.width("0.0s");
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.count == 0 ? 0 : 2 * HudStyle.PAD_Y + this.count * ROW - (ROW - ICON);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LocalPlayer player = hud.client.player;
      if (player == null) {
         return;
      }

      GuiGraphicsExtractor g = hud.graphics;
      Font font = hud.font;
      int backdrop = this.backdrop();
      int textX = HudStyle.PAD_X + ICON + 6;
      int right = w - HudStyle.PAD_X;
      int unitW = UiFont.width("s");
      for (int row = 0; row < this.count; row++) {
         int i = this.active[row];
         ItemStack stack = this.stacks[i];
         int y = HudStyle.PAD_Y + row * ROW;
         float pct = player.getCooldowns().getCooldownPercent(stack, hud.partialTick);
         g.item(stack, HudStyle.PAD_X, y);
         UiFont.draw(g, this.names[i], textX, y, HudStyle.label(backdrop), hud.shadow);
         String time = this.times[i];
         if (!time.isEmpty()) {
            // Value white, its unit faint, right-aligned together.
            int unit = this.inSeconds[i] ? unitW : 0;
            UiFont.draw(g, time, right - unit - UiFont.width(time, FontSize.BODY, FontWeight.MEDIUM), y, hud.text, FontSize.BODY, FontWeight.MEDIUM, hud.shadow);
            if (unit > 0) {
               UiFont.draw(g, "s", right - unitW, y, HudStyle.unit(backdrop), hud.shadow);
            }
         }

         HudStyle.bar(g, textX, y + 12, right, y + 14, pct, Glass.withAlpha(hud.accent, 235), backdrop, 1.0F);
      }
   }
}
