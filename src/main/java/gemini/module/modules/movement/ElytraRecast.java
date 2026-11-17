package gemini.module.modules.movement;

import java.util.List;
import java.util.ArrayList;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import gemini.util.ClientState;
import gemini.util.MoveUtil;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Elytra bouncing without fireworks. Landing closes the elytra; this jumps again and opens it straight away, so
 * every bounce adds a sprint-jump's push to the glide. The elytra is opened the same way Elytra Fly's takeoff and
 * the vanilla jump key do it: {@code tryToStartFallFlying()} plus the start-fall-flying command.
 */
public final class ElytraRecast extends Module {
   private final BooleanSetting autoJump = this.addSetting(
      new BooleanSetting("Auto Jump", "Jump on every landing (off: only while you hold Jump)", true)
   );
   private final BooleanSetting autoSprint = this.addSetting(new BooleanSetting("Auto Sprint", "Sprint for you while bouncing", true));
   private final ModeSetting redeploy = this.addSetting(
      new ModeSetting("Redeploy", "Instant reopens the elytra as soon as you leave the ground; Falling waits until you start to drop", "Instant", "Instant", "Falling")
   );
   private final NumberSetting minFall = this.addSetting(
      new NumberSetting("Min Fall Speed", "Downward speed needed before reopening, in blocks per tick", 0.0, 0.0, 1.0, 0.05)
         .showIf(() -> this.redeploy.is("Falling"))
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Redeploy Delay", "Ticks in the air before reopening", 0.0, 0.0, 10.0, 1.0));
   private final BooleanSetting pitchHold = this.addSetting(
      new BooleanSetting("Pitch Hold", "Hold your view at a fixed pitch, which keeps the bounces long", false)
   );
   private final NumberSetting pitch = this.addSetting(
      new NumberSetting("Pitch", "Pitch to hold, in degrees (positive looks down)", 75.0, -30.0, 90.0, 1.0).showIf(this.pitchHold::get)
   );
   private final NumberSetting minDurability = this.addSetting(
      new NumberSetting("Min Durability", "Stop when the elytra has this much durability left", 10.0, 1.0, 100.0, 1.0)
   );
   private int airTicks;
   private int bounces;
   private boolean sprintHeld;
   private boolean pitchSaved;
   private float savedPitch;

   public ElytraRecast() {
      super("Elytra Bounce", "Reopens your elytra after every landing so you keep bouncing along without fireworks.", Category.MOVEMENT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Elytra Recast");
      names.add("ElytraRecast");
      return names;
   }

   /** The elytra is worn down to the stop threshold (items that cannot break never are). */
   static boolean durabilityLow(boolean damageable, int maxDamage, int damage, int minDurability) {
      return damageable && maxDamage - damage <= minDurability;
   }

   /**
    * Whether to reopen the elytra on this airborne tick: not while it is already open, only after {@code delay}
    * ticks in the air, and in Falling mode only once the downward speed reaches {@code minFall} (at least 0.001,
    * so the top of the jump never counts as falling).
    */
   static boolean shouldRedeploy(boolean fallFlying, int airTicks, int delay, boolean fallingMode, double verticalSpeed, double minFall) {
      if (fallFlying || airTicks <= delay) {
         return false;
      }

      return !fallingMode || -verticalSpeed >= Math.max(0.001, minFall);
   }

   @Override
   public String info() {
      return this.bounces > 0 ? Integer.toString(this.bounces) : null;
   }

   @Override
   public void onEnable() {
      this.airTicks = 0;
      this.bounces = 0;
      this.sprintHeld = false;
      this.pitchSaved = false;
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      this.releaseSprint(client);
      LocalPlayer player = client.player;
      if (this.pitchSaved && player != null) {
         player.setXRot(this.savedPitch);
      }

      this.pitchSaved = false;
      this.airTicks = 0;
   }

   private void releaseSprint(Minecraft client) {
      if (this.sprintHeld) {
         this.sprintHeld = false;
         if (client.options != null) {
            client.options.keySprint.setDown(MoveUtil.physicalKeyDown(client.options.keySprint));
         }
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (!ClientState.ready(client) || player.input == null) {
         this.releaseSprint(client);
         return;
      }

      ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      if (!LivingEntity.canGlideUsing(chest, EquipmentSlot.CHEST)) {
         // Nothing to open (no elytra, or it is broken): wait without touching any keys.
         this.releaseSprint(client);
         this.airTicks = 0;
         return;
      }

      if (durabilityLow(chest.isDamageableItem(), chest.getMaxDamage(), chest.getDamageValue(), this.minDurability.intValue())) {
         HudOverlay.toast("Elytra Recast", "Elytra durability is low, stopping", HudOverlay.Severity.WARNING);
         this.setEnabled(false, false);
         return;
      }

      boolean wantsBounce = this.autoJump.get() || player.input.keyPresses.jump();
      boolean blocked = player.isInWater() || player.isInLava() || player.isPassenger() || player.getAbilities().flying || player.onClimbable();
      if (blocked || !wantsBounce || !MoveUtil.hasInput(player)) {
         this.releaseSprint(client);
         this.airTicks = 0;
         return;
      }

      if (this.autoSprint.get()) {
         client.options.keySprint.setDown(true);
         this.sprintHeld = true;
         if (player.input.hasForwardImpulse() && !player.isSprinting() && !player.isShiftKeyDown()) {
            player.setSprinting(true);
         }
      } else {
         this.releaseSprint(client);
      }

      if (this.pitchHold.get()) {
         if (!this.pitchSaved) {
            this.pitchSaved = true;
            this.savedPitch = player.getXRot();
         }

         // Gliding reads the real pitch on the client, so this turn cannot be a packet-only one. Only the pitch is
         // wanted: the yaw is put back as it was, because the manager wraps it and the camera would spin once.
         float yaw = player.getYRot();
         float headYaw = player.yHeadRot;
         RotationManager.request(player, yaw, this.pitch.floatValue(), 12.0F, false, this.priority(), 1.0F);
         player.setYRot(yaw);
         player.yHeadRot = headYaw;
      } else if (this.pitchSaved) {
         this.pitchSaved = false;
         player.setXRot(this.savedPitch);
      }

      if (player.onGround()) {
         this.airTicks = 0;
         // With the jump key held vanilla jumps by itself; jumping here as well would add the sprint push twice.
         if (this.autoJump.get() && !player.input.keyPresses.jump() && !player.isShiftKeyDown()) {
            player.jumpFromGround();
         }

         return;
      }

      this.airTicks++;
      if (!shouldRedeploy(
         player.isFallFlying(), this.airTicks, this.delay.intValue(), this.redeploy.is("Falling"), player.getDeltaMovement().y, this.minFall.get()
      )) {
         return;
      }

      ClientPacketListener connection = client.getConnection();
      if (connection != null && player.tryToStartFallFlying()) {
         connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
         this.bounces++;
      }
   }
}
