package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.util.CombatTarget;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class TriggerBot extends Module {
   private final NumberSetting cooldown = this.addSetting(new NumberSetting("Cooldown %", "Attack charge needed before hitting (percent)", 92.0, 50.0, 100.0, 2.0));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Attack players under the crosshair", true));
   private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Attack hostile mobs under the crosshair", true));
   private final BooleanSetting critsOnly = this.addSetting(new BooleanSetting("Wait For Fall", "While in the air, wait until you are falling so the hit is a critical", false));
   // The target is whatever is under the crosshair, so only the filters (its own Players/Mobs) and the box apply.
   private final Targeting targeting = Targeting.builder(this)
      .filters(this.players::get, this.mobs::get, () -> false, () -> true)
      .choiceShownIf(() -> false)
      .build();

   public TriggerBot() {
      super("TriggerBot", "Attacks the entity under your crosshair once your attack is charged.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      LivingEntity aimed = null;
      if (ClientState.ready(client) && client.gui.screen() == null && client.hitResult != null && client.hitResult.getType() == Type.ENTITY) {
         Entity hit = ((EntityHitResult)client.hitResult).getEntity();
         if (hit instanceof LivingEntity living && this.targeting.accepts(player, living)) {
            aimed = living;
         }
      }

      // The box and the Target HUD follow the entity under the crosshair whether or not the weapon is charged yet.
      this.targeting.setTarget(aimed);
      CombatTarget.report(this, aimed);
      if (aimed != null && !player.isUsingItem() && player.getAttackStrengthScale(0.0F) * 100.0F >= this.cooldown.floatValue()) {
         if (this.critsOnly.get() && !player.onGround() && player.getDeltaMovement().y >= -0.02) {
            return;
         }

         client.gameMode.attack(player, aimed);
         Swing.attack(player, InteractionHand.MAIN_HAND);
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   @Override
   public void onDisable() {
      this.targeting.clear();
      CombatTarget.release(this);
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.onDisable();
   }
}
