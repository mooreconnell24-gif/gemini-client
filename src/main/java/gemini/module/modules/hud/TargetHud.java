package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudShapes;
import gemini.ui.HudStyle;
import gemini.ui.clickgui.Menus;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import gemini.util.CombatTarget;
import gemini.util.Damage;
import gemini.util.TotemPops;
import net.minecraft.client.Minecraft;
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
 * Card for the entity you are fighting, in three styles. <b>Card</b>: face or 3D model, name, an animated health
 * bar in the theme gradient with a trailing damage ghost and an absorption line, distance, totem pops, worn armour
 * with durability, and an honest fight estimate. <b>Compact</b>: the same on one line. <b>Ring</b>: the health as a
 * ring around the head. The card rises and fades in when a target appears and sinks away when it goes.
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
   /** Ring style: outer radius and width of the health ring, and the size of the head inside it. */
   private static final int RING_RADIUS = 17;
   private static final int RING_STROKE = 3;
   private static final int RING_HEAD = 20;
   private final ModeSetting style = this.addSetting(
      new ModeSetting("Style", "Look of the card: Card shows everything, Compact is a single line, Ring draws the health as a ring around the head", "Card", "Card", "Compact", "Ring")
   );
   private final BooleanSetting showGear = this.addSetting(
      new BooleanSetting("Show Gear", "Show the target's held item and armour with durability bars", true).showIf(() -> this.style.is("Card"))
   );
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
   private int fittedGeneration = -1;
   private String odds = "";
   /** 1 winning, -1 losing, 0 even, 2 the menu preview; picks the colour of {@link #odds} from the theme when drawn. */
   private int verdict = 2;
   private String pops = "";
   private String letter = "?";
   /** Whether a target (or the menu preview) is there this tick; false while the card fades out. */
   private boolean wanted;
   /** Whether the target has anything for the gear row; without it the card is just the head row. */
   private boolean hasGear;
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

      this.hasGear = hasGear(shown);
      this.name = shown.getName().getString();
      this.letter = this.name.isEmpty() ? "?" : this.name.substring(0, 1);
      float hp = Damage.health(shown);
      this.hp = gemini.util.InfoText.format(hp, 1);
      this.distance = gemini.util.InfoText.format(self.distanceTo(shown), 1);
      int popped = TotemPops.count(shown);
      this.pops = popped > 0 ? popped + (popped == 1 ? " pop" : " pops") : "";
      if (shown == self) {
         this.odds = "Preview";
         this.verdict = 2;
      } else {
         this.verdict = predict(Damage.health(self), dps(self, shown), Damage.health(shown), dps(shown, self));
         this.odds = this.verdict > 0 ? "Winning" : (this.verdict < 0 ? "Losing" : "Even");
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

   private boolean ring() {
      return this.style.is("Ring");
   }

   private boolean gearRow() {
      return this.hasGear && this.showGear.get() && this.style.is("Card");
   }

   /** Whether {@code entity} holds or wears anything the gear row would show. */
   private static boolean hasGear(LivingEntity entity) {
      if (!entity.getMainHandItem().isEmpty()) {
         return true;
      }

      for (EquipmentSlot slot : ARMOR) {
         if (!entity.getItemBySlot(slot).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private int headSize() {
      return this.compact() ? 16 : (this.ring() ? RING_HEAD : 26);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return this.compact() ? 168 : (this.ring() ? 132 : 158);
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      if (this.ring()) {
         return 2 * PAD + 2 * RING_RADIUS;
      }

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

   /** Text colour faded with the card. */
   private static int fadeText(int color, float a) {
      return Glass.fade(color | 0xFF000000, a);
   }

   /**
    * Draws a value (medium weight) with a faint unit after it at {@code x}, and returns the x just past the unit.
    * Widths are measured here (no allocation); the strings are rebuilt once a tick in {@link #refresh}.
    */
   private static int valueUnit(GuiGraphicsExtractor g, String value, String unit, int x, int y, int valueColor, int unitColor, boolean shadow) {
      UiFont.draw(g, value, x, y, valueColor, FontSize.BODY, FontWeight.MEDIUM, shadow);
      int ux = x + UiFont.width(value, FontSize.BODY, FontWeight.MEDIUM) + HudStyle.UNIT_GAP;
      UiFont.draw(g, unit, ux, y, unitColor, shadow);
      return ux + UiFont.width(unit);
   }

   private int oddsColor(HudContext hud) {
      return this.verdict == 2 ? hud.textDim : (this.verdict > 0 ? hud.theme.ok : (this.verdict < 0 ? hud.theme.error : hud.theme.warn));
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LivingEntity shown = this.target;
      if (shown == null) {
         return;
      }

      GuiGraphicsExtractor g = hud.graphics;
      float a = this.alpha;
      boolean compact = this.compact();
      boolean ring = this.ring();
      int backdrop = this.backdrop();
      // The card rises a few pixels into place as it fades in, and sinks as it leaves.
      int rise = Math.round((1.0F - Glass.easeOutCubic(a)) * 5.0F);
      g.pose().pushMatrix();
      g.pose().translate(0.0F, rise);
      // The shared HUD card; a fight card has to read over anything, so Soft gets more body here.
      HudStyle.card(g, 0, 0, w, h, backdrop, hud.surface, backdrop == HudStyle.SOFT ? Math.min(1.0F, a * 1.4F) : a);
      this.animateHealth(hud, shown);
      if (ring) {
         this.paintRing(hud, shown, w, h, a, backdrop);
      } else {
         this.paintCard(hud, shown, w, a, compact, backdrop);
      }

      g.pose().popMatrix();
   }

   private void paintCard(HudContext hud, LivingEntity shown, int w, float a, boolean compact, int backdrop) {
      GuiGraphicsExtractor g = hud.graphics;
      int head = this.headSize();
      this.drawHead(hud, shown, PAD, PAD, head, a);
      if (a < 0.05F) {
         // the font draws near-zero alpha as opaque
         return;
      }

      boolean shadow = hud.shadow;
      int textX = PAD + head + 6;
      int right = w - PAD;
      int label = fadeText(HudStyle.label(backdrop), a);
      int unit = fadeText(HudStyle.unit(backdrop), a);
      int text = fadeText(hud.text, a);
      if (compact) {
         // One line: name (and pops), the fight estimate and health on the right; the bar under it.
         int hpX = right - UiFont.width(this.hp, FontSize.BODY, FontWeight.MEDIUM) - HudStyle.UNIT_GAP - UiFont.width("hp");
         valueUnit(g, this.hp, "hp", hpX, PAD, text, unit, shadow);
         int limit = hpX - 6;
         if (this.prediction.get()) {
            int ox = limit - UiFont.width(this.odds);
            UiFont.draw(g, this.odds, ox, PAD, fadeText(this.oddsColor(hud), a), shadow);
            limit = ox - 6;
         }

         String name = this.fitName(limit - textX);
         UiFont.draw(g, name, textX, PAD, text, FontSize.BODY, FontWeight.MEDIUM, shadow);
         int nameW = UiFont.width(name, FontSize.BODY, FontWeight.MEDIUM);
         if (this.showPops.get() && !this.pops.isEmpty() && textX + nameW + 4 + UiFont.width(this.pops) <= limit) {
            UiFont.draw(g, this.pops, textX + nameW + 4, PAD, fadeText(hud.theme.warn, a), shadow);
         }

         this.drawHealthBar(hud, shown, textX, PAD + 12, right, PAD + 15, a, backdrop);
         return;
      }

      // Name and the fight estimate.
      int limit = right;
      if (this.prediction.get()) {
         int ox = right - UiFont.width(this.odds);
         UiFont.draw(g, this.odds, ox, PAD, fadeText(this.oddsColor(hud), a), shadow);
         limit = ox - 6;
      }

      UiFont.draw(g, this.fitName(limit - textX), textX, PAD, text, FontSize.BODY, FontWeight.MEDIUM, shadow);
      // The health bar, then health, distance and pops in one compact row.
      this.drawHealthBar(hud, shown, textX, PAD + 11, right, PAD + 15, a, backdrop);
      int infoY = PAD + head - UiFont.capHeight(FontSize.BODY);
      int x = valueUnit(g, this.hp, "hp", textX, infoY, text, unit, shadow);
      valueUnit(g, this.distance, "m", x + 8, infoY, label, unit, shadow);
      if (this.showPops.get() && !this.pops.isEmpty()) {
         UiFont.draw(g, this.pops, right - UiFont.width(this.pops), infoY, fadeText(hud.theme.warn, a), shadow);
      }

      if (this.gearRow() && a > 0.6F) {
         this.drawGear(g, shown, PAD, PAD + head + GEAR_GAP, a, backdrop);
      }
   }

   /** Ring style: the head inside a ring of health, with the name, health, distance and estimate beside it. */
   private void paintRing(HudContext hud, LivingEntity shown, int w, int h, float a, int backdrop) {
      GuiGraphicsExtractor g = hud.graphics;
      int cx = PAD + RING_RADIUS;
      int cy = h / 2;
      this.drawHead(hud, shown, cx - RING_HEAD / 2, cy - RING_HEAD / 2, RING_HEAD, a);
      float max = Math.max(1.0F, shown.getMaxHealth());
      int track = backdrop == HudStyle.NONE ? HudStyle.TRACK_BARE : HudStyle.TRACK;
      HudShapes.ring(g, 0, cx, cy, RING_RADIUS, RING_STROKE, 1.0F, Glass.fade(track, a));
      float ghost = this.ghostHealth / max;
      float ratio = this.shownHealth / max;
      if (ghost > ratio + 0.004F) {
         HudShapes.ring(g, 2, cx, cy, RING_RADIUS, RING_STROKE, ghost, Glass.withAlpha(Glass.lerp(hud.theme.error, 0xFFFFFFFF, 0.35F), Math.round(170.0F * a)));
      }

      HudShapes.ring(g, 1, cx, cy, RING_RADIUS, RING_STROKE, ratio, fadeText(Glass.lerp(hud.accent, hud.accent2, 1.0F - ratio), a));
      if (a < 0.05F) {
         return;
      }

      boolean shadow = hud.shadow;
      int textX = PAD + 2 * RING_RADIUS + 8;
      int right = w - PAD;
      int unit = fadeText(HudStyle.unit(backdrop), a);
      int label = fadeText(HudStyle.label(backdrop), a);
      int text = fadeText(hud.text, a);
      int line = 12;
      boolean odds = this.prediction.get();
      int rows = odds ? 3 : 2;
      int y = cy - ((rows - 1) * line + UiFont.capHeight(FontSize.BODY)) / 2;
      UiFont.draw(g, this.fitName(right - textX), textX, y, text, FontSize.BODY, FontWeight.MEDIUM, shadow);
      y += line;
      int x = valueUnit(g, this.hp, "hp", textX, y, text, unit, shadow);
      valueUnit(g, this.distance, "m", x + 8, y, label, unit, shadow);
      if (odds) {
         y += line;
         UiFont.draw(g, this.odds, textX, y, fadeText(this.oddsColor(hud), a), shadow);
         if (this.showPops.get() && !this.pops.isEmpty()) {
            UiFont.draw(g, this.pops, right - UiFont.width(this.pops), y, fadeText(hud.theme.warn, a), shadow);
         }
      }
   }

   /** The target's name (medium weight) cut to {@code width}; cached for the last width so a frame does not rebuild it. */
   private String fitName(int width) {
      int generation = UiFont.generation();
      if (width != this.fittedWidth || this.fittedFor != this.name || generation != this.fittedGeneration) {
         this.fittedWidth = width;
         this.fittedFor = this.name;
         this.fittedGeneration = generation;
         this.fittedName = UiFont.ellipsize(this.name, width, FontSize.BODY, FontWeight.MEDIUM);
      }

      return this.fittedName;
   }

   /**
    * Health colour that slides smoothly from green (full) through amber (half) to red (empty); see
    * {@link HudStyle#health}.
    */
   static int healthColor(float ratio) {
      return HudStyle.health(ratio);
   }

   /** Moves the shown health toward the real one, and the damage trail after it. Once a frame. */
   private void animateHealth(HudContext hud, LivingEntity shown) {
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
   }

   /** The health bar: a quiet rounded track, the damage trail, the fill in the theme gradient and a gold absorption line. */
   private void drawHealthBar(HudContext hud, LivingEntity shown, int x1, int y1, int x2, int y2, float a, int backdrop) {
      float max = Math.max(1.0F, shown.getMaxHealth());
      float ratio = this.shownHealth / max;
      GuiGraphicsExtractor g = hud.graphics;
      int width = x2 - x1;
      int radius = (y2 - y1) / 2;
      Glass.roundRect(g, x1, y1, x2, y2, radius, Glass.fade(backdrop == HudStyle.NONE ? HudStyle.TRACK_BARE : HudStyle.TRACK, a));
      int ghostW = HudStyle.fillWidth(width, this.ghostHealth / max);
      int fill = HudStyle.fillWidth(width, ratio);
      if (ghostW > fill) {
         Glass.roundRect(g, x1, y1, x1 + ghostW, y2, radius, Glass.withAlpha(Glass.lerp(hud.theme.error, 0xFFFFFFFF, 0.35F), Math.round(170.0F * a)));
      }

      if (fill > 0) {
         int alpha = Math.round(255.0F * a);
         // The gradient spans the whole track, so the visible part keeps its colours as the bar shrinks.
         int end = Glass.lerp(hud.accent, hud.accent2, (float)fill / width);
         Glass.roundGradient(g, x1, y1, x1 + fill, y2, radius, Glass.withAlpha(hud.accent, alpha), Glass.withAlpha(end, alpha));
      }

      float absorption = shown.getAbsorptionAmount();
      if (absorption > 0.0F) {
         // Absorption as a gold line along the top of the bar.
         int absW = HudStyle.fillWidth(width, absorption / max);
         g.fill(x1 + radius, y1 - 1, Math.max(x1 + radius, x1 + absW - radius), y1, Glass.withAlpha(0xFFFFC83D, Math.round(230.0F * a)));
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
            HudStyle.bar(g, gx + 1, y + 17, gx + 15, y + 18, left, color == HudStyle.PRIMARY ? Glass.withAlpha(Glass.text(), 200) : color, backdrop, a);
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

      Glass.roundRect(g, x, y, x + size, y + size, HudStyle.RADIUS_SMALL, Glass.withAlpha(hud.theme.surface(2), Math.round(170.0F * a)));
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

      if (a >= 0.05F) {
         UiFont.drawCentered(g, this.letter, x + size / 2, UiFont.centerY(y, size), fadeText(hud.text, a), FontSize.BODY, FontWeight.MEDIUM, true);
      }
   }
}
