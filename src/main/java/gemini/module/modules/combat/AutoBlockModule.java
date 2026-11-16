package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.util.Swing;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import gemini.util.Targets;
import gemini.event.ActionPriority;
import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;

public final class AutoBlockModule extends Module {
   private static final int MOB_ATTACK_INTERVAL = 20;
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Mode",
         "Smart blocks when an attacker swings; Timed blocks just before an attacker's next hit is ready; Range blocks whenever an attacker is in reach",
         "Smart",
         "Timed",
         "Range",
         "Smart"
      )
   );
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest attacker to watch, in blocks", 4.0, 2.5, 6.0, 0.1));
   private final NumberSetting hold = this.addSetting(
      new NumberSetting("Hold Ticks", "Extra ticks to keep the shield up after a hit is blocked", 4.0, 0.0, 20.0, 1.0)
   );
   private final NumberSetting lead = this.addSetting(
      new NumberSetting("Lead Ticks", "Ticks before an attacker's hit is ready to raise the shield", 1.0, 0.0, 5.0, 1.0).showIf(() -> this.mode.is("Timed"))
   );
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Block melee hits from players", true));
   private final BooleanSetting hostile = this.addSetting(new BooleanSetting("Hostile", "Block melee hits from hostile mobs", true));
   private final BooleanSetting projectiles = this.addSetting(new BooleanSetting("Projectiles", "Block arrows that are about to hit you", true));
   private final BooleanSetting swap = this.addSetting(
      new BooleanSetting("Swap Shield", "Move a shield into your offhand when one is needed", true)
   );
   private final BooleanSetting look = this.addSetting(new BooleanSetting("Face Threat", "Turn towards the attacker so the shield's 90° arc covers the hit", true));
   private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Face the attacker without moving your camera", true).showIf(this.look::get));
   private final BooleanSetting workInGui = this.addSetting(new BooleanSetting("In Screens", "Keep blocking while a screen is open", false));
   private final BooleanSetting pauseBaritone = this.addSetting(new BooleanSetting("Pause Baritone", "Pause Baritone pathing while the shield is up", true));
   // Which attacker to face when several swing at once; the box marks it. Players/Hostile above are its filters.
   private final Targeting targeting = Targeting.builder(this)
      .filters(this.players::get, this.hostile::get, () -> false, () -> true)
      .box("Outline", 30)
      .build();
   private final Int2IntOpenHashMap sinceAttack = new Int2IntOpenHashMap();
   /** This tick's melee threats (reused, never reallocated); Targeting picks the one to face among them. */
   private final ArrayList<Entity> threats = new ArrayList<>();
   private final Predicate<LivingEntity> isThreat = living -> this.threats.contains(living);
   private static boolean managing;
   private int holdLeft;
   private boolean raisedByUs;
   private LivingEntity faceTarget;

   public static boolean wantsShield() {
      return managing;
   }

   public AutoBlockModule() {
      super("Auto Block", "Raises your shield just before an attack would hit you.", Category.COMBAT);
      this.sinceAttack.defaultReturnValue(-1);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public void onDisable() {
      managing = false;
      Minecraft client = Minecraft.getInstance();
      this.lower(client);
      client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
      this.sinceAttack.clear();
      this.holdLeft = 0;
      this.faceTarget = null;
      this.threats.clear();
      this.targeting.clear();
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   @Override
   public String info() {
      if (this.holdLeft <= 0) {
         return this.mode.get();
      } else {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         return player != null && player.isBlocking() ? "Blocking" : "Raising";
      }
   }

   @Override
   public boolean wantsBaritonePause() {
      return this.pauseBaritone.get() && this.holdLeft > 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client)) {
         LocalPlayer player = client.player;
         if ((this.workInGui.get() || client.gui.screen() == null) && !player.isCreative() && !player.isSpectator()) {
            this.tickCooldowns();
            int delay = shieldDelay(player);
            int needed = delay + this.hold.intValue();
            this.faceTarget = null;
            boolean threat = this.scanThreat(client, player, delay);
            if (threat) {
               this.holdLeft = Math.max(this.holdLeft, needed);
            } else if (this.holdLeft > 0) {
               this.holdLeft--;
            }

            if (this.holdLeft > 0) {
               if (this.look.get() && this.faceTarget != null) {
                  float[] rotation = Rotations.face(player, this.faceTarget);
                  RotationManager.request(player, rotation, 0.0F, this.silent.get(), ActionPriority.CRITICAL_SURVIVAL);
               }

               this.raise(client, player);
               managing = this.raisedByUs;
            } else {
               managing = false;
               this.lower(client);
            }
         } else {
            managing = false;
            this.lower(client);
            this.holdLeft = 0;
            this.faceTarget = null;
            this.targeting.clear();
         }
      }
   }

   private void tickCooldowns() {
      ObjectIterator<Entry> iterator = this.sinceAttack.int2IntEntrySet().iterator();

      while (iterator.hasNext()) {
         Entry entry = (Entry)iterator.next();
         int next = entry.getIntValue() + 1;
         if (next > 100) {
            iterator.remove();
         } else {
            entry.setValue(next);
         }
      }
   }

   private boolean scanThreat(Minecraft client, LocalPlayer player, int delay) {
      double reach = this.range.get();
      int early = delay + this.lead.intValue();
      boolean found = false;
      this.threats.clear();

      for (Entity entity : client.level.entitiesForRendering()) {
         if (this.projectiles.get() && entity instanceof AbstractArrow arrow && incomingArrow(player, arrow, early)) {
            found = true;
         } else if (entity instanceof LivingEntity living
            && Targets.isCandidate(player, living, this.players.get(), this.hostile.get(), false, true)
            && !(Targets.reach(player, living) > reach)
            && this.meleeThreat(player, living, early)) {
            found = true;
            this.noteAttackCycle(living);
            this.threats.add(living);
         }
      }

      if (this.threats.isEmpty()) {
         this.targeting.clear();
      } else {
         this.faceTarget = this.targeting.pick(player, this.threats, reach, this.isThreat);
      }

      this.threats.clear();
      return found;
   }

   private void noteAttackCycle(LivingEntity living) {
      int since = this.sinceAttack.get(living.getId());
      if (Swing.isSwinging(living) && Swing.ticks(living) <= 1) {
         this.sinceAttack.put(living.getId(), 0);
      } else if (since < 0) {
         this.sinceAttack.put(living.getId(), 0);
      }
   }

   private boolean meleeThreat(LocalPlayer player, LivingEntity living, int early) {
      if (living instanceof Mob mob) {
         if (!mob.isWithinMeleeAttackRange(player)) {
            return false;
         } else if (!Targets.visible(player, mob) && !aimedAt(mob, player)) {
            return false;
         } else if (!aimedAt(mob, player) && Targets.reach(player, mob) > 2.2) {
            return false;
         } else if (this.mode.is("Range")) {
            return true;
         } else if (this.mode.is("Smart")) {
            return Swing.isSwinging(living) && Swing.ticks(living) <= 2;
         } else {
            int since = this.sinceAttack.get(mob.getId());
            if (since < 0) {
               return true;
            } else {
               int phase = since % 20;
               int windowStart = 20 - early;
               return phase < windowStart && phase > 2 ? Swing.isSwinging(living) && Swing.ticks(living) <= 2 : true;
            }
         }
      } else if (living instanceof Player attacker) {
         if (!aimedAt(attacker, player)) {
            return false;
         } else if (this.mode.is("Range")) {
            return Targets.reach(player, attacker) <= this.range.get();
         } else if (this.mode.is("Smart")) {
            return Swing.isSwinging(attacker) && Swing.ticks(attacker) <= 2;
         } else {
            float strength = attacker.getAttackStrengthScale(0.0F);
            return strength >= 0.85F ? true : Swing.isSwinging(attacker) && Swing.ticks(attacker) <= 2;
         }
      } else {
         return Swing.isSwinging(living) && Swing.ticks(living) <= 2 && Targets.visible(player, living);
      }
   }

   private static boolean aimedAt(LivingEntity attacker, LocalPlayer player) {
      float[] look = Rotations.face(attacker, player);
      return Rotations.yawDiff(attacker.getYRot(), look[0]) <= 55.0F && Rotations.yawDiff(attacker.getXRot(), look[1]) <= 50.0F;
   }

   private static boolean incomingArrow(LocalPlayer player, AbstractArrow arrow, int early) {
      if (!arrow.isRemoved() && !arrow.onGround() && arrow.getOwner() != player) {
         Vec3 motion = arrow.getDeltaMovement();
         if (motion.lengthSqr() < 1.0E-4) {
            return false;
         } else {
            Vec3 lookAhead = arrow.position().add(motion.scale(Math.max(1, early)));
            return player.getBoundingBox().inflate(0.5).clip(arrow.position(), lookAhead).isPresent()
               || player.getBoundingBox().inflate(0.7).intersects(arrow.getBoundingBox().expandTowards(motion.scale(Math.max(1, early))));
         }
      } else {
         return false;
      }
   }

   private static int shieldDelay(LocalPlayer player) {
      ItemStack stack = player.getOffhandItem().is(Items.SHIELD)
         ? player.getOffhandItem()
         : (player.getMainHandItem().is(Items.SHIELD) ? player.getMainHandItem() : ItemStack.EMPTY);
      if (stack.isEmpty()) {
         return 5;
      } else {
         BlocksAttacks blocks = (BlocksAttacks)stack.get(DataComponents.BLOCKS_ATTACKS);
         return blocks == null ? 5 : Math.max(0, blocks.blockDelayTicks());
      }
   }

   private void raise(Minecraft client, LocalPlayer player) {
      InteractionHand hand = shieldHand(player);
      if (hand == null) {
         if (!this.swap.get() || !this.equipShield(client, player)) {
            return;
         }

         hand = shieldHand(player);
         if (hand == null) {
            return;
         }
      }

      if (player.isUsingItem()) {
         if (player.getUseItem().is(Items.SHIELD)) {
            this.raisedByUs = true;
            if (client.gui.screen() == null) {
               client.options.keyUse.setDown(true);
            }
         }
      } else {
         client.gameMode.useItem(player, hand);
         if (client.gui.screen() == null) {
            client.options.keyUse.setDown(true);
         }

         this.raisedByUs = true;
      }
   }

   private void lower(Minecraft client) {
      LocalPlayer player = client.player;
      if (player == null) {
         this.raisedByUs = false;
         client.options.keyUse.setDown(false);
      } else {
         if (this.raisedByUs && !BlockHitModule.wantsShield()) {
            if (player.isUsingItem() && player.getUseItem().is(Items.SHIELD)) {
               client.gameMode.releaseUsingItem(player);
            }

            // Back to what the player is really holding, so a held right click survives the shield going down.
            client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
         }

         this.raisedByUs = false;
      }
   }

   private static InteractionHand shieldHand(LocalPlayer player) {
      if (player.getOffhandItem().is(Items.SHIELD)) {
         return InteractionHand.OFF_HAND;
      } else {
         return player.getMainHandItem().is(Items.SHIELD) ? InteractionHand.MAIN_HAND : null;
      }
   }

   private boolean equipShield(Minecraft client, LocalPlayer player) {
      if (player.getOffhandItem().is(Items.SHIELD)) {
         return true;
      } else {
         int index = Inventories.find(player, stack -> stack.is(Items.SHIELD), false);
         if (index < 0) {
            return false;
         } else {
            Inventories.swapToOffhand(client, player, index);
            return player.getOffhandItem().is(Items.SHIELD);
         }
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.sinceAttack.clear();
      this.faceTarget = null;
      this.threats.clear();
      this.targeting.clear();
   }
}
