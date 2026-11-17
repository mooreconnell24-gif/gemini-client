package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Your active effects, one row each: the effect's icon, its name (with the level), the time left and a thin bar of
 * how much of it is left. The bar measures against the longest time seen since the effect started, so a renewed
 * effect fills it again. Harmful effects get a red bar; everything else stays neutral.
 *
 * <p>The rows are rebuilt once a tick into reused arrays; a frame only draws.
 */
public final class PotionTimers extends HudWidget {
   private static final int ICON = 18;
   private static final int ROW = 20;
   private static final int ICON_GAP = 4;
   private static final Comparator<MobEffectInstance> LONGEST_FIRST = Comparator.comparingInt(MobEffectInstance::getDuration).reversed();
   private final List<MobEffectInstance> sorted = new ArrayList<>();
   private final Map<Holder<MobEffect>, Integer> longest = new HashMap<>();
   private Identifier[] icons = new Identifier[4];
   private String[] names = new String[4];
   private String[] times = new String[4];
   private int[] timeWidths = new int[4];
   private float[] fractions = new float[4];
   private int[] barColors = new int[4];
   private int count;
   private int textW;
   private int builtTick = Integer.MIN_VALUE;

   public PotionTimers() {
      super("Potion Timers", "Shows your active effects with their remaining time.", "Right Center", 6, 0);
   }

   @Override
   public void onDisable() {
      this.longest.clear();
      this.count = 0;
      this.builtTick = Integer.MIN_VALUE;
   }

   @Override
   public void onWorldChange() {
      this.onDisable();
   }

   private void ensure(int n) {
      if (this.names.length >= n) {
         return;
      }

      int size = Math.max(n, this.names.length * 2);
      this.icons = java.util.Arrays.copyOf(this.icons, size);
      this.names = java.util.Arrays.copyOf(this.names, size);
      this.times = java.util.Arrays.copyOf(this.times, size);
      this.timeWidths = java.util.Arrays.copyOf(this.timeWidths, size);
      this.fractions = java.util.Arrays.copyOf(this.fractions, size);
      this.barColors = java.util.Arrays.copyOf(this.barColors, size);
   }

   /** Once a tick: the effects, longest first, with their names, times and bar fractions. */
   private void rebuild(HudContext hud) {
      LocalPlayer player = hud.client.player;
      int tick = player == null ? -1 : player.tickCount;
      if (tick == this.builtTick) {
         return;
      }

      this.builtTick = tick;
      this.count = 0;
      this.textW = 0;
      if (player == null) {
         return;
      }

      List<MobEffectInstance> effects = this.sorted;
      effects.clear();
      effects.addAll(player.getActiveEffects());
      if (effects.isEmpty()) {
         this.longest.clear();
         return;
      }

      effects.sort(LONGEST_FIRST);
      this.longest.keySet().removeIf(effect -> !player.hasEffect(effect));
      this.ensure(effects.size());
      Font font = hud.font;
      // Formatted the way the inventory does it: "∞" for infinite, and real seconds at the current tick rate.
      float tickRate = hud.client.level == null ? 20.0F : hud.client.level.tickRateManager().tickrate();
      for (MobEffectInstance effect : effects) {
         int i = this.count++;
         MobEffect type = effect.getEffect().value();
         String name = type.getDisplayName().getString();
         if (effect.getAmplifier() > 0) {
            name = name + " " + HudHelpers.roman(effect.getAmplifier() + 1);
         }

         int duration = effect.isInfiniteDuration() ? -1 : effect.getDuration();
         Integer seen = this.longest.get(effect.getEffect());
         if (duration >= 0 && (seen == null || duration > seen)) {
            this.longest.put(effect.getEffect(), duration);
            seen = duration;
         }

         this.icons[i] = Hud.getMobEffectSprite(effect.getEffect());
         this.names[i] = name;
         this.times[i] = net.minecraft.world.effect.MobEffectUtil.formatDuration(effect, 1.0F, tickRate).getString();
         this.timeWidths[i] = font.width(this.times[i]);
         this.fractions[i] = HudFormat.remaining(duration, seen == null ? 0 : seen);
         this.barColors[i] = type.getCategory() == MobEffectCategory.HARMFUL ? Glass.withAlpha(Glass.ERROR, 230) : Glass.withAlpha(Glass.lerp(hud.accent, 0xFFFFFFFF, 0.45F), 220);
         this.textW = Math.max(this.textW, font.width(name) + 10 + this.timeWidths[i]);
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      this.rebuild(hud);
      return this.count == 0 ? 0 : 2 * HudStyle.PAD_X + ICON + ICON_GAP + Math.max(64, this.textW);
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.count == 0 ? 0 : 2 * HudStyle.PAD_Y + this.count * ROW - (ROW - ICON);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      GuiGraphicsExtractor g = hud.graphics;
      Font font = hud.font;
      int backdrop = this.backdrop();
      int timeColor = HudStyle.label(backdrop);
      int textX = HudStyle.PAD_X + ICON + ICON_GAP;
      int right = w - HudStyle.PAD_X;
      for (int i = 0; i < this.count; i++) {
         int y = HudStyle.PAD_Y + i * ROW;
         g.blitSprite(RenderPipelines.GUI_TEXTURED, this.icons[i], HudStyle.PAD_X, y, ICON, ICON);
         g.text(font, this.names[i], textX, y + 1, Glass.TEXT, hud.shadow);
         g.text(font, this.times[i], right - this.timeWidths[i], y + 1, timeColor, hud.shadow);
         HudStyle.bar(g, textX, y + 13, right, y + 15, this.fractions[i], this.barColors[i], backdrop, 1.0F);
      }
   }
}
