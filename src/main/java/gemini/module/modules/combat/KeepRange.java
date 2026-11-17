package gemini.module.modules.combat;

import gemini.util.InfoText;
import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class KeepRange extends Module {
   private final NumberSetting minDistance = this.addSetting(
      new NumberSetting("Min Distance", "Back away when the target is closer than this, in blocks", 2.6, 1.5, 4.0, 0.1)
   );
   private final NumberSetting maxDistance = this.addSetting(new NumberSetting("Max Distance", "Farthest target to keep away from, in blocks", 3.6, 2.5, 5.5, 0.1));
   private final BooleanSetting onlyDuringCooldown = this.addSetting(
      new BooleanSetting("Wait Cooldown", "Only back away while your attack is recharging", true)
   );
   private final Targeting targeting = Targeting.builder(this).build();
   private boolean backingUp;

   public KeepRange() {
      super("Keep Range", "Walks you backwards when your target gets closer than the set distance.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.release(Minecraft.getInstance());
      this.targeting.clear();
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   /** The key state belongs to this client, not the world: release it and forget the target. */
   @Override
   public void onWorldChange() {
      this.onDisable();
   }

   private void release(Minecraft client) {
      if (this.backingUp) {
         client.options.keyDown.setDown(MoveUtil.physicalKeyDown(client.options.keyDown));
         this.backingUp = false;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         LivingEntity enemy = this.targeting.pick(player, client.level.entitiesForRendering(), this.maxDistance.get(), null);
         if (enemy != null) {
            double dist = player.distanceTo(enemy);
            boolean recharging = !this.onlyDuringCooldown.get() || player.getAttackStrengthScale(0.0F) < 0.85F;
            if (dist < this.minDistance.get() && recharging) {
               client.options.keyDown.setDown(true);
               this.backingUp = true;
               return;
            }
         }

         this.release(client);
      } else {
         this.release(client);
         this.targeting.clear();
      }
   }

   private final InfoText infoText = new InfoText();

   @Override
   public String info() {
      return this.infoText.fixed(this.minDistance.get(), 1, "m");
   }
}
