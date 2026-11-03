package gemini.module.modules.player;

import com.mojang.authlib.GameProfile;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class FakePlayer extends Module {
   private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Starting health of the dummy", 20.0, 1.0, 40.0, 1.0));
   private final BooleanSetting copyGear = this.addSetting(new BooleanSetting("Copy Gear", "Wear the same armor and items as you", true));
   private final BooleanSetting glow = this.addSetting(new BooleanSetting("Glowing", "Give the dummy a glowing outline", false));
   private static int nextClientId = -1000000;
   private RemotePlayer dummy;

   public FakePlayer() {
      super("Fake Player", "Spawn a dummy player next to you to test combat.", Category.PLAYER);
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      RemotePlayer target = this.dummy;
      if (target == null) {
         return false;
      } else {
         return packet instanceof ServerboundAttackPacket attack
            ? attack.entityId() == target.getId()
            : packet instanceof ServerboundInteractPacket interact && interact.entityId() == target.getId();
      }
   }

   @Override
   public void onEnable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player != null && level != null) {
         this.dummy = new RemotePlayer(level, new GameProfile(UUID.randomUUID(), "Fake_Player"));
         this.dummy.setId(nextClientId--);
         this.dummy.snapTo(player.position().add(player.getLookAngle().multiply(2.0, 0.0, 2.0)), player.getYRot() + 180.0F, 0.0F);
         this.dummy.setYHeadRot(this.dummy.getYRot());
         this.dummy.setHealth(this.health.floatValue());
         if (this.copyGear.get()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
               this.dummy.setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
         }

         level.addEntity(this.dummy);
         CommandManager.feedback("§bFake Player §7spawned. Hits are simulated on your side only.");
      }
   }

   @Override
   public void onDisable() {
      ClientLevel level = Minecraft.getInstance().level;
      if (this.dummy != null && level != null) {
         level.removeEntity(this.dummy.getId(), RemovalReason.DISCARDED);
      }

      this.dummy = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.dummy != null) {
         if (this.dummy.level() == client.level && !this.dummy.isRemoved()) {
            this.dummy.setGlowingTag(this.glow.get());
         } else {
            this.dummy = null;
            this.setEnabled(false, false);
         }
      }
   }

   @Override
   public void onAttack(Entity target) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (this.dummy != null && target == this.dummy && player != null) {
         float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + player.getAttackStrengthScale(0.5F) * 0.8F);
         float remaining = this.dummy.getHealth() - damage;
         this.dummy.animateHurt(player.getYRot());
         this.dummy.hurtTime = 10;
         this.dummy.hurtDuration = 10;
         if (remaining <= 0.0F) {
            client.particleEngine.createTrackingEmitter(this.dummy, ParticleTypes.TOTEM_OF_UNDYING, 30);
            this.dummy.setHealth(this.health.floatValue());
         } else {
            this.dummy.setHealth(remaining);
         }
      }
   }

   @Override
   public String info() {
      return this.dummy == null ? null : String.format("%.0f", this.dummy.getHealth());
   }
}
