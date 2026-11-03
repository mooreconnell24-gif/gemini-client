package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.lwjgl.glfw.GLFW;

public final class AutoClickerModule extends Module {
   private final NumberSetting cps = this.addSetting(new NumberSetting("CPS", "Clicks per second", 8.0, 1.0, 20.0, 1.0));
   private final NumberSetting jitter = this.addSetting(new NumberSetting("Jitter", "How wide the click timing spreads (%)", 28.0, 0.0, 50.0, 1.0));
   private final BooleanSetting requireMouse = this.addSetting(new BooleanSetting("Require Mouse", "Only click while attack is held", true));
   private final BooleanSetting weaponOnly = this.addSetting(new BooleanSetting("Weapon Only", "Only while holding a weapon", true));
   private final BooleanSetting respectCooldown = this.addSetting(new BooleanSetting("Respect Cooldown", "Wait until the hit will actually count", true));
   private final Random random = new Random();
   private long nextClick;
   private int burstLeft;
   private boolean recover;
   private int untilBurst = 8;

   public AutoClickerModule() {
      super("AutoClicker", "Click with a human rhythm while you hold attack.", Category.COMBAT);
   }

   @Override
   public String info() {
      return Integer.toString(this.cps.intValue());
   }

   @Override
   public void onEnable() {
      this.nextClick = 0L;
      this.burstLeft = 0;
      this.recover = false;
      this.untilBurst = 8 + this.random.nextInt(6);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null
         && client.gameMode != null
         && client.gui.screen() == null
         && (!this.requireMouse.get() || GLFW.glfwGetMouseButton(client.getWindow().handle(), 0) == 1)
         && client.hitResult != null
         && client.hitResult.getType() == Type.ENTITY
         && !player.isUsingItem()
         && (!this.weaponOnly.get() || isWeapon(player.getMainHandItem()))) {
         long now = System.currentTimeMillis();
         if (now >= this.nextClick && (!this.respectCooldown.get() || !(player.getAttackStrengthScale(0.0F) < 0.92F))) {
            Entity entity = ((EntityHitResult)client.hitResult).getEntity();
            this.nextClick = now + this.nextDelay();
            client.gameMode.attack(player, entity);
            player.swing(InteractionHand.MAIN_HAND);
         }
      }
   }

   private static boolean isWeapon(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.TRIDENT) || stack.is(Items.MACE);
   }

   private long nextDelay() {
      double mean = 1000.0 / this.cps.get();
      double spread = mean * (this.jitter.get() / 100.0) * 0.55;
      double delay = mean + this.random.nextGaussian() * spread;
      if (this.burstLeft > 0) {
         delay *= 0.4 + this.random.nextDouble() * 0.16;
         this.burstLeft--;
         this.recover = this.burstLeft == 0;
      } else if (this.recover) {
         delay *= 1.35 + this.random.nextDouble() * 0.35;
         this.recover = false;
      } else if (--this.untilBurst <= 0) {
         this.burstLeft = this.random.nextFloat() < 0.35F ? 2 : 1;
         this.untilBurst = 7 + this.random.nextInt(8);
         delay *= 0.4 + this.random.nextDouble() * 0.16;
         this.burstLeft--;
         this.recover = this.burstLeft == 0;
      }

      return (long)Mth.clamp(delay, mean * 0.32, mean * 1.9);
   }
}
