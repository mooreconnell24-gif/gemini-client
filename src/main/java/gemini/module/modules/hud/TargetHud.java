package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.clickgui.Menus;
import gemini.util.CombatTarget;
import gemini.util.Damage;
import gemini.util.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * Card for the entity you are fighting: face or 3D model, an animated health bar with a trailing damage ghost and an
 * absorption overlay, distance, totem pops, worn armour with durability, and an honest fight estimate.
 *
 * <p>The target is picked once a tick (the target a combat module is attacking right now, else the entity you last
 * hit, for a few seconds, else the nearest player or hurt
 * mob in range) and every text on the card is rebuilt only then; a frame animates and draws. When the target goes
 * away the card fades out still showing it, then hides.
 */
public final class TargetHud extends HudWidget {
   private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final long ATTACK_MEMORY_MS = 6000L;
   /** Padding of the card and the space between the head row and the gear row. */
   private static final int PAD = 6;
   private static final int GEAR_GAP = 5;
   private final BooleanSetting showGear = this.addSetting(new BooleanSetting("Show Gear", "Show the target's held item and armour with durability bars (Modern Glass and Neon styles)", true));
   private final ModeSetting style = this.addSetting(new ModeSetting("Style", "Look of the card: Modern Glass is the standard card, Compact a single line, Neon an accent outline with a glow", "Modern Glass", "Modern Glass", "Compact", "Neon"));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest target shown, in blocks", 6.0, 3.0, 24.0, 0.5));
   private final BooleanSetting prediction = this.addSetting(
      new BooleanSetting("Prediction", "Estimate who wins from effective health (armour, absorption) and the damage per second of the held weapons", true)
   );
   private final BooleanSetting showPops = this.addSetting(new BooleanSetting("Show Pops", "Show how many totems the target has popped", true));
   private final BooleanSetting model3d = this.addSetting(new BooleanSetting("3D Model", "Draw mobs as a small 3D model (players always show their face)", true));
   private LivingEntity target;
   private LivingEntity lastAttacked;
   private long lastAttackedAt;
   private int pickedTick = Integer.MIN_VALUE;
   private float alpha;
   private float shownHealth = Float.NaN;
   private float ghostHealth = Float.NaN;
   private LivingEntity animatedFor;
   private String name = "";
   /** Health and distance as numbers (their units are drawn faint beside them), rebuilt once a tick. */
   private String hp = "";
   private String distance = "";
   private String fittedName = "";
   private String fittedFor;
   private int fittedWidth = -1;
   private String odds = "";
   private int oddsColor = Glass.TEXT_DIM;
   private String pops = "";
   private String letter = "?";
   /** Whether a target (or the menu preview) is there this tick; false while the card fades out. */
   private boolean wanted;
   private final Vector2f screen = new Vector2f();

   public TargetHud() {
      super("Target HUD", "Shows a card for your current target with its health, distance, totem pops, armour and who is likely to win.", "Bottom Center", 90, 68);
   }

   @Override
   protected boolean hasBackground() {
      // Each style draws its own card; the Background switch still turns it off.
      return false;
   }

   @Override
   public void onAttack(Entity entity) {
      if (entity instanceof LivingEntity living) {
         this.lastAttacked = living;
         this.lastAttackedAt = Util.getMillis();
      }
   }

   @Override
   public void onDisable() {
      this.target = null;
      this.lastAttacked = null;
      this.animatedFor = null;
      this.alpha = 0.0F;
   }

   @Override
   public void onWorldChange() {
      this.onDisable();
   }

   private LivingEntity pick(Minecraft client) {
      LocalPlayer self = client.player;
      if (self == null || client.level == null) {
         return null;
      }

      // A combat module that is attacking right now (KillAura, TriggerBot, ...) decides: show the same target it has.
      LivingEntity active = CombatTarget.current();
      if (active != null && active != self) {
         return active;
      }

      double max = this.range.get() * this.range.get();
      LivingEntity attacked = this.lastAttacked;
      if (attacked != null && attacked.isAlive() && !attacked.isRemoved() && Util.getMillis() - this.lastAttackedAt < ATTACK_MEMORY_MS
         && self.distanceToSqr(attacked) <= max * 1.5) {
         return attacked;
      }

      LivingEntity best = null;
      double bestD = max;
      for (Entity e : client.level.entitiesForRendering()) {
         if (e != self && e instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && (e instanceof Player || living.hurtTime > 0)) {
            double d = self.distanceToSqr(e);
            if (d < bestD) {
               bestD = d;
               best = living;
            }
         }
      }

      return best;
   }

   /** Once a tick: picks the target and rebuilds the card's texts. */
   private void refresh(HudContext hud) {
      LocalPlayer self = hud.client.player;
      int tick = self == null ? -1 : self.tickCount;
      if (tick == this.pickedTick) {
         return;
      }

      this.pickedTick = tick;
      LivingEntity next = this.pick(hud.client);
      boolean preview = next == null && Menus.isMenu(hud.client.gui.screen()) && self != null;
      if (preview) {
         next = self;
      }

      this.wanted = next != null;
      if (next != null) {
         this.target = next;
      }

      LivingEntity shown = this.target;
      if (shown == null || self == null) {
         return;
      }

      this.name = shown.getName().getString();
      this.letter = this.name.isEmpty() ? "?" : this.name.substring(0, 1);
      float hp = Damage.health(shown);
      this.hp = gemini.util.InfoText.format(hp, 1);
      this.distance = gemini.util.InfoText.format(self.distanceTo(shown), 1);
      int popped = TotemPops.count(shown);
      this.pops = popped > 0 ? popped + (popped == 1 ? " pop" : " pops") : "";
      if (shown == self) {
         this.odds = "Preview";
         this.oddsColor = Glass.TEXT_DIM;
      } else {
         int verdict = predict(Damage.health(self), dps(self, shown), Damage.health(shown), dps(shown, self));
         this.odds = verdict > 0 ? "Winning" : (verdict < 0 ? "Losing" : "Even");
         this.oddsColor = verdict > 0 ? Glass.OK : (verdict < 0 ? Glass.ERROR : Glass.WARN);
      }
   }

   /**
    * Compares how long each side needs to kill the other: effective health (health plus absorption) over the other
    * side's damage per second after armour. 1 = you would win, -1 = you would lose, 0 = within 20% either way. It
    * is an estimate from held weapons only (no criticals, enchantments, potions or reach).
    */
   static int predict(float myEffectiveHealth, float myDps, float theirEffectiveHealth, float theirDps) {
      double myTimeToKill = myDps <= 0.0F ? Double.POSITIVE_INFINITY : theirEffectiveHealth / myDps;
      double theirTimeToKill = theirDps <= 0.0F ? Double.POSITIVE_INFINITY : myEffectiveHealth / theirDps;
      if (Double.isInfinite(myTimeToKill) && Double.isInfinite(theirTimeToKill)) {
         return 0;
      }

      if (myTimeToKill < theirTimeToKill * 0.8) {
         return 1;
      }

      return theirTimeToKill < myTimeToKill * 0.8 ? -1 : 0;
   }

   /** Estimated damage per second {@code attacker} deals to {@code victim} with what it holds, after the victim's armour. */
   private static float dps(LivingEntity attacker, LivingEntity victim) {
      AttributeInstance damage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
      if (damage == null) {
         return 0.0F;
      }

      AttributeInstance speed = attacker.getAttribute(Attributes.ATTACK_SPEED);
      // Mobs have no attack speed; they swing about once a second.
      double hitsPerSecond = speed == null ? 1.0 : Mth.clamp(speed.getValue(), 0.25, 4.0);
      float perHit = (float)damage.getValue();
      AttributeInstance toughness = victim.getAttribute(Attributes.ARMOR_TOUGHNESS);
      float afterArmor = CombatRules.getDamageAfterAbsorb(
         victim, perHit, victim.damageSources().generic(), victim.getArmorValue(), toughness == null ? 0.0F : (float)toughness.getValue()
      );
      return (float)(afterArmor * hitsPerSecond);
   }

   private boolean compact() {
      return this.style.is("Compact");
   }

   private int cardWidth() {
      return this.compact() ? 140 : 156;
   }

   private boolean gearRow() {
      return this.showGear.get() && !this.compact();
   }

   private int headSize() {
      return this.compact() ? 16 : 24;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return this.cardWidth();
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      int base = 2 * PAD + this.headSize();
      return this.gearRow() ? base + GEAR_GAP + 19 : base;
   }

   @Override
   protected boolean shouldRenderContent(HudContext hud) {
      this.refresh(hud);
      boolean wanted = this.target != null && this.wanted;
      this.alpha = Glass.approach(this.alpha, wanted ? 1.0F : 0.0F, 10.0F, hud.delta);
      if (this.alpha <= 0.0F && !wanted) {
         this.target = null;
      }

      return this.target != null && this.alpha > 0.0F;
   }

   /** Text colour faded with the card; null-safe for the font, which draws near-zero alpha as opaque. */
   private static int fadeText(int color, float a) {
      return Glass.fade(color, a);
   }

   /**
    * Draws a value with a faint unit after it at {@code x}, and returns the x just past the unit. Widths are measured
    * here (no allocation); the strings are rebuilt once a tick in {@link #refresh}.
    */
   private static int valueUnit(GuiGraphicsExtractor g, Font font, String value, String unit, int x, int y, int valueColor, int unitColor, boolean shadow) {
      g.text(font, value, x, y, valueColor, shadow);
      int ux = x + font.width(value) + HudStyle.UNIT_GAP;
      g.text(font, unit, ux, y, unitColor, shadow);
      return ux + font.width(unit);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LivingEntity shown = this.target;
      if (shown == null) {
         return;
      }

      GuiGraphicsExtractor g = hud.graphics;
      float a = this.alpha;
      String look = this.style.get();
      boolean neon = "Neon".equals(look);
      boolean compact = "Compact".equals(look);
      int accent = hud.accent;
      int backdrop = this.backdrop();
      if (neon && super.hasBackground()) {
         Glass.glow(g, 0, 0, w, h, Glass.RADIUS, accent, a * 0.55F);
         Glass.roundRect(g, 0, 0, w, h, Glass.RADIUS, Glass.withAlpha(0xFF05060A, Math.round(232.0F * a)));
         Glass.roundOutline(g, 0, 0, w, h, Glass.RADIUS, Glass.withAlpha(accent, Math.round(230.0F * a)));
      } else {
         // The shared HUD card; a fight card reads over anything, so Soft gets a touch more body here.
         HudStyle.card(g, 0, 0, w, h, backdrop, hud.surface, backdrop == HudStyle.SOFT ? Math.min(1.0F, a * 1.35F) : a);
      }

      int head = this.headSize();
      this.drawHead(hud, shown, PAD, PAD, head, a);
      if (a < 0.05F) {
         return;
      }

      Font font = hud.font;
      boolean shadow = hud.shadow;
      int textX = PAD + head + 6;
      int right = w - PAD;
      int label = fadeText(HudStyle.label(backdrop), a);
      int unit = fadeText(HudStyle.unit(backdrop), a);
      int nameColor = fadeText(neon ? accent : Glass.TEXT, a);
      if (compact) {
         // One line: name (and pops), the fight estimate and health on the right; the bar under it.
         int hpEnd = right;
         int hpX = hpEnd - font.width(this.hp) - HudStyle.UNIT_GAP - font.width("hp");
         valueUnit(g, font, this.hp, "hp", hpX, PAD, fadeText(Glass.TEXT, a), unit, shadow);
         int limit = hpX - 6;
         if (this.prediction.get()) {
            int ox = limit - font.width(this.odds);
            g.text(font, this.odds, ox, PAD, fadeText(this.oddsColor, a), shadow);
            limit = ox - 6;
         }

         String name = this.fitName(font, limit - textX);
         g.text(font, name, textX, PAD, nameColor, shadow);
         if (this.showPops.get() && !this.pops.isEmpty() && textX + font.width(name) + 4 + font.width(this.pops) <= limit) {
            g.text(font, this.pops, textX + font.width(name) + 4, PAD, fadeText(Glass.WARN, a), shadow);
         }

         this.drawHealth(hud, shown, textX, PAD + 11, right, PAD + 14, a, neon, backdrop);
         return;
      }

      // Name and the fight estimate.
      int limit = right;
      if (this.prediction.get()) {
         int ox = right - font.width(this.odds);
         g.text(font, this.odds, ox, PAD, fadeText(this.oddsColor, a), shadow);
         limit = ox - 6;
      }

      g.text(font, this.fitName(font, limit - textX), textX, PAD, nameColor, shadow);
      // The health bar, then health, distance and pops in one compact row.
      this.drawHealth(hud, shown, textX, PAD + 11, right, PAD + 14, a, neon, backdrop);
      int infoY = PAD + head - 8;
      int x = valueUnit(g, font, this.hp, "hp", textX, infoY, fadeText(Glass.TEXT, a), unit, shadow);
      valueUnit(g, font, this.distance, "m", x + 8, infoY, label, unit, shadow);
      if (this.showPops.get() && !this.pops.isEmpty()) {
         g.text(font, this.pops, right - font.width(this.pops), infoY, fadeText(Glass.WARN, a), shadow);
      }

      if (this.gearRow() && a > 0.6F) {
         this.drawGear(g, shown, PAD, PAD + head + GEAR_GAP, a, backdrop);
      }
   }

   /** The target's name cut to {@code width}; cached for the last width so a frame does not rebuild it. */
   private String fitName(Font font, int width) {
      if (width != this.fittedWidth || this.fittedFor != this.name) {
         this.fittedWidth = width;
         this.fittedFor = this.name;
         this.fittedName = gemini.ui.Theme.fit(this.name, width, font::width);
      }

      return this.fittedName;
   }

   /**
    * Health colour that slides smoothly from green (full) through amber (half) to red (empty), so the bar changes
    * colour as gradually as it shrinks.
    */
   static int healthColor(float ratio) {
      float r = Mth.clamp(ratio, 0.0F, 1.0F);
      return r >= 0.5F ? Glass.lerp(Glass.WARN, Glass.OK, (r - 0.5F) * 2.0F) : Glass.lerp(Glass.ERROR, Glass.WARN, r * 2.0F);
   }

   private void drawHealth(HudContext hud, LivingEntity shown, int x1, int y1, int x2, int y2, float a, boolean neon, int backdrop) {
      float max = Math.max(1.0F, shown.getMaxHealth());
      float health = Mth.clamp(shown.getHealth(), 0.0F, max);
      if (this.animatedFor != shown || Float.isNaN(this.shownHealth)) {
         this.animatedFor = shown;
         this.shownHealth = health;
         this.ghostHealth = health;
      }

      this.shownHealth = Glass.approach(this.shownHealth, health, 14.0F, hud.delta);
      // The trail lingers on lost health and then drains slowly; healing pulls it straight up.
      this.ghostHealth = health >= this.ghostHealth ? this.shownHealth : Glass.approach(this.ghostHealth, health, 2.6F, hud.delta);
      float ratio = this.shownHealth / max;
      GuiGraphicsExtractor g = hud.graphics;
      int width = x2 - x1;
      g.fill(x1, y1, x2, y2, Glass.fade(backdrop == HudStyle.NONE ? 0x70000000 : 0x2CFFFFFF, a));
      int ghostW = HudStyle.fillWidth(width, this.ghostHealth / max);
      if (ghostW > 0) {
         g.fill(x1, y1, x1 + ghostW, y2, Glass.withAlpha(Glass.lerp(Glass.ERROR, 0xFFFFFFFF, 0.35F), Math.round(170.0F * a)));
      }

      int color = neon ? hud.accent : healthColor(ratio);
      int fill = HudStyle.fillWidth(width, ratio);
      if (fill > 0) {
         if (neon) {
            Glass.gradientBar(g, x1, y1, x1 + fill, y2, Glass.withAlpha(hud.accent, Math.round(255.0F * a)), Glass.withAlpha(hud.accent2, Math.round(255.0F * a)));
         } else {
            g.fill(x1, y1, x1 + fill, y2, Glass.withAlpha(color, Math.round(255.0F * a)));
         }
      }

      float absorption = shown.getAbsorptionAmount();
      if (absorption > 0.0F) {
         // Absorption as a gold line along the top of the bar.
         int absW = HudStyle.fillWidth(width, absorption / max);
         g.fill(x1, y1 - 1, x1 + absW, y1, Glass.withAlpha(0xFFFFC83D, Math.round(230.0F * a)));
      }
   }

   private void drawGear(GuiGraphicsExtractor g, LivingEntity shown, int x, int y, float a, int backdrop) {
      int gx = x;
      ItemStack held = shown.getMainHandItem();
      if (!held.isEmpty()) {
         g.item(held, gx, y);
         gx += 20;
      }

      for (EquipmentSlot slot : ARMOR) {
         ItemStack stack = shown.getItemBySlot(slot);
         if (stack.isEmpty()) {
            continue;
         }

         g.item(stack, gx, y);
         if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            float left = 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage();
            int color = HudStyle.level(left);
            HudStyle.bar(g, gx + 1, y + 17, gx + 15, y + 18, left, color == Glass.TEXT ? Glass.withAlpha(Glass.TEXT, 200) : color, backdrop, a);
         }

         gx += 20;
      }
   }

   private void drawHead(HudContext hud, LivingEntity shown, int x, int y, int size, float a) {
      GuiGraphicsExtractor g = hud.graphics;
      if (shown instanceof AbstractClientPlayer player) {
         if (a > 0.35F) {
            PlayerFaceExtractor.extractRenderState(g, player.getSkin(), x, y, size);
         }

         return;
      }

      Glass.roundRect(g, x, y, x + size, y + size, HudStyle.RADIUS_SMALL, Glass.withAlpha(0xFF101318, Math.round(150.0F * a)));
      if (this.model3d.get() && a > 0.6F) {
         // The picture-in-picture entity renderer takes screen coordinates, not the HUD's pose, so map the box first.
         Matrix3x2f pose = g.pose();
         pose.transformPosition(x, y, this.screen);
         int sx0 = Math.round(this.screen.x);
         int sy0 = Math.round(this.screen.y);
         pose.transformPosition(x + size, y + size, this.screen);
         int sx1 = Math.round(this.screen.x);
         int sy1 = Math.round(this.screen.y);
         float fit = Math.max(shown.getBbHeight(), shown.getBbWidth());
         int scale = Math.max(4, Math.round((sy1 - sy0) * 0.85F / Math.max(0.3F, fit)));
         try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, sx0, sy0, sx1, sy1, scale, 0.0625F, (sx0 + sx1) / 2.0F, (sy0 + sy1) / 2.0F, shown);
            return;
         } catch (RuntimeException e) {
            // Some entity renderers cannot be drawn this way; the letter below stands in.
         }
      }

      g.centeredText(hud.font, this.letter, x + size / 2, y + (size - 8) / 2, Glass.withAlpha(Glass.TEXT, Math.round(255.0F * a)));
   }
}
