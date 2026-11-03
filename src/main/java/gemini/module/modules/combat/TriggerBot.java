package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class TriggerBot extends Module {
   private final NumberSetting cooldown = this.addSetting(new NumberSetting("Cooldown %", "Required weapon charge % to attack", 92.0, 50.0, 100.0, 2.0));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Attack players under crosshair", true));
   private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Attack hostile mobs under crosshair", true));
   private final BooleanSetting critsOnly = this.addSetting(new BooleanSetting("Wait For Fall", "Wait until falling when airborne for crits", false));

   public TriggerBot() {
      super("TriggerBot", "Attack targets under your crosshair when charged.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null && client.hitResult != null && client.hitResult.getType() == Type.ENTITY) {
         LocalPlayer player = client.player;
         if (!player.isUsingItem() && player.getAttackStrengthScale(0.0F) * 100.0F >= this.cooldown.floatValue()) {
            if (this.critsOnly.get() && !player.onGround() && player.getDeltaMovement().y >= -0.02) {
               return;
            }

            Entity hit = ((EntityHitResult)client.hitResult).getEntity();
            if (Targets.isCandidate(player, hit, this.players.get(), this.mobs.get(), false, true)) {
               client.gameMode.attack(player, hit);
               player.swing(InteractionHand.MAIN_HAND);
            }
         }
      }
   }
}
