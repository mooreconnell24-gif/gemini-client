package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import gemini.ui.clickgui.Menus;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class TargetHud extends HudWidget {
   private final BooleanSetting showGear = this.addSetting(new BooleanSetting("Show Gear", "Render target held item and armor icons", true));
   private float animHealth = 20.0F;
   private float trailHealth = 20.0F;

   public TargetHud() {
      super("TargetHUD", "LiquidBounce Nextgen Target HUD card with animated health bar, gear icons, and win/loss indicator.", "Bottom Center", 90, 68);
   }

   private LivingEntity findTarget(LocalPlayer self) {
      if (self != null && self.level() != null) {
         LivingEntity best = null;
         double bestD = 36.0;

         for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e != self && e instanceof LivingEntity living && living.isAlive() && (e instanceof Player || living.hurtTime > 0)) {
               double d = self.distanceToSqr(e);
               if (d < bestD) {
                  bestD = d;
                  best = living;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 154;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.showGear.get() ? 52 : 38;
   }

   @Override
   protected boolean shouldRenderContent(HudContext hud) {
      return Menus.isMenu(hud.client.gui.screen()) || this.findTarget(hud.client.player) != null;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LocalPlayer self = hud.client.player;
      LivingEntity target = this.findTarget(self);
      if (target == null) {
         hud.graphics.text(hud.font, "TargetHUD · Preview", 8, 6, -1, hud.shadow);
         hud.graphics.text(hud.font, "20.0 HP  ·  0.0m  ·  WIN", 8, 16, -6641995, false);
         int barY = this.showGear.get() ? 42 : 27;
         hud.graphics.fill(8, barY, w - 8, barY + 6, -14142908);
         hud.graphics.fill(8, barY, w - 28, barY + 6, hud.accent);
      } else {
         float hp = target.getHealth() + target.getAbsorptionAmount();
         float maxHp = Math.max(1.0F, target.getMaxHealth() + target.getAbsorptionAmount());
         this.animHealth = Mth.lerp(0.22F, this.animHealth, hp);
         this.trailHealth = Mth.lerp(0.07F, this.trailHealth, hp);
         float ratio = Mth.clamp(this.animHealth / maxHp, 0.0F, 1.0F);
         float trailRatio = Mth.clamp(this.trailHealth / maxHp, ratio, 1.0F);
         boolean winning = self.getHealth() + self.getAbsorptionAmount() >= hp;
         if (target.hurtTime > 0) {
            hud.graphics.fill(0, 0, w, h, GeminiMark.withAlpha(-1548963, target.hurtTime * 10));
         }

         String name = target.getName().getString();
         String badge = winning ? "WIN" : "LOSE";
         hud.graphics.text(hud.font, name, 8, 5, -1, hud.shadow);
         hud.graphics.text(hud.font, badge, w - 8 - hud.font.width(badge), 5, winning ? -11086174 : -1548963, hud.shadow);
         String stats = String.format(Locale.ROOT, "%.1f HP  ·  %.1fm  ·  HT %d", hp, self.distanceTo(target), target.hurtTime);
         hud.graphics.text(hud.font, stats, 8, 15, -6641995, false);
         int barW = w - 16;
         int barY = this.showGear.get() ? 42 : 27;
         hud.graphics.fill(8, barY, 8 + barW, barY + 6, -14142908);
         hud.graphics.fill(8, barY, 8 + Math.round(barW * trailRatio), barY + 6, GeminiMark.withAlpha(-1548963, 140));
         hud.graphics.fill(8, barY, 8 + Math.round(barW * ratio), barY + 6, HudHelpers.ratioColor(ratio));
         if (this.showGear.get()) {
            ItemStack[] gear = new ItemStack[]{
               target.getMainHandItem(),
               target.getOffhandItem(),
               target.getItemBySlot(EquipmentSlot.HEAD),
               target.getItemBySlot(EquipmentSlot.CHEST),
               target.getItemBySlot(EquipmentSlot.LEGS),
               target.getItemBySlot(EquipmentSlot.FEET)
            };
            int gx = 8;

            for (ItemStack stack : gear) {
               if (!stack.isEmpty()) {
                  hud.graphics.item(stack, gx, 24);
                  gx += 18;
               }
            }
         }
      }
   }
}
