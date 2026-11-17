package gemini.module.modules.combat;

import gemini.util.CombatTarget;
import gemini.util.Inventories;
import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

public final class ShieldBreaker extends Module {
   private static final double RANGE = 4.2;
   private static final Predicate<LivingEntity> BLOCKING = LivingEntity::isBlocking;
   private final Targeting targeting = Targeting.builder(this).defaults(true, false, false).build();
   private int cooldown;

   public ShieldBreaker() {
      super("Shield Breaker", "Switches to an axe and hits an enemy that is holding up a shield.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.ready(client) || client.gui.screen() != null) {
         this.targeting.clear();
         CombatTarget.release(this);
         if (this.cooldown > 0) {
            this.cooldown--;
         }
         return;
      }

      // Only an enemy holding up a shield counts; Priority decides between several.
      LocalPlayer player = client.player;
      LivingEntity target = this.targeting.pick(player, client.level.entitiesForRendering(), RANGE, BLOCKING);
      CombatTarget.report(this, target);
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (target != null) {
         Inventories.withHotbarItem(player, s -> s.is(ItemTags.AXES), () -> {
            client.gameMode.attack(player, target);
            Swing.attack(player, InteractionHand.MAIN_HAND);
            this.cooldown = 6;
         });
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
      this.cooldown = 0;
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.onDisable();
   }
}
