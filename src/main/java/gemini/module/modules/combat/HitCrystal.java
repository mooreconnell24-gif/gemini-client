package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import gemini.util.ClientState;
import gemini.util.Damage;
import gemini.util.RotationManager;
import gemini.util.Targets;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The hit-crystal combo: a melee hit knocks the target back, and while it flies a block of obsidian goes down
 * where it is heading, a crystal goes on that, and the crystal is set off. Each step waits "Step Delay" ticks.
 * Where the target lands is estimated from the hit direction and the knockback it took; from the second step on
 * the target's real position and motion are used, so the crystal follows it.
 */
public final class HitCrystal extends Module {
   /** A combo that has not finished this long after the hit is abandoned. */
   private static final int COMBO_TIMEOUT = 40;
   private final ModeSetting weapons = this.addSetting(
      new ModeSetting("Weapons", "Which hits start the combo: swords, any knockback weapon, or both", "Both", "Swords", "Knockback", "Both")
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Step Delay", "Ticks between obsidian, crystal and detonation", 1.0, 0.0, 6.0, 1.0));
   private final NumberSetting range = this.addSetting(new NumberSetting("Place Range", "Farthest block or crystal you will place", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting minDamage = this.addSetting(new NumberSetting("Min Damage", "Least damage the crystal must deal the target", 4.0, 0.0, 20.0, 0.5));
   private final NumberSetting maxSelf = this.addSetting(new NumberSetting("Max Self Damage", "Most damage the crystal may deal you", 8.0, 0.0, 20.0, 0.5));
   private final BooleanSetting antiSuicide = this.addSetting(
      new BooleanSetting("Anti Suicide", "Skip any crystal whose blast would kill or pop you", true)
   );
   private final BooleanSetting placeObsidian = this.addSetting(
      new BooleanSetting("Place Obsidian", "Put obsidian down when no obsidian or bedrock is where the target lands", true)
   );
   private final BooleanSetting silentAim = this.addSetting(new BooleanSetting("Silent Aim", "Aim in packets only, leaving the camera alone", true));
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render", "Show the chosen crystal base", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight colour", 300).showIf(this.render::get));
   private LivingEntity target;
   private Vec3 landing;
   private BlockPos base;
   private Step step = Step.IDLE;
   private int wait;
   private int age;
   private float damage;

   public HitCrystal() {
      super("Hit Crystal", "After a melee hit, place obsidian and a crystal where the target is knocked to and set it off.", Category.COMBAT);
   }

   @Override
   public String info() {
      return this.step == Step.IDLE ? null : String.format(Locale.ROOT, "%.1f", this.damage);
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onWorldChange() {
      this.reset();
   }

   private void reset() {
      this.target = null;
      this.landing = null;
      this.base = null;
      this.step = Step.IDLE;
      this.wait = 0;
      this.age = 0;
      this.damage = 0.0F;
   }

   @Override
   public void onAttack(Entity entity) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null || !(entity instanceof LivingEntity living)) {
         return;
      }

      if (!Targets.isCandidate(player, living, true, true, false, true) || !this.weaponQualifies(client.level, player.getMainHandItem())) {
         return;
      }

      // Knockback goes along the attacker's facing: 0.4 base, 0.5 more per Knockback level and for a sprint hit,
      // which the target's ground friction turns into roughly two blocks of travel per unit.
      int knockback = knockbackLevel(client.level, player.getMainHandItem());
      double strength = 0.4 + 0.5 * knockback + (player.isSprinting() ? 0.5 : 0.0);
      Vec3 push = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(strength * 2.0);
      this.target = living;
      this.landing = living.position().add(push.x, 0.0, push.z);
      this.base = null;
      this.step = Step.OBSIDIAN;
      this.wait = this.delay.intValue();
      this.age = 0;
   }

   private boolean weaponQualifies(Level level, ItemStack stack) {
      boolean sword = stack.is(ItemTags.SWORDS);
      boolean knockback = knockbackLevel(level, stack) > 0;
      return switch (this.weapons.get()) {
         case "Swords" -> sword;
         case "Knockback" -> knockback;
         default -> sword || knockback;
      };
   }

   private static int knockbackLevel(Level level, ItemStack stack) {
      if (stack.isEmpty() || !stack.isEnchanted()) {
         return 0;
      }

      Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.KNOCKBACK).orElse(null);
      return holder == null ? 0 : stack.getEnchantments().getLevel(holder);
   }

   @Override
   public void onTick() {
      if (this.step == Step.IDLE) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (!ClientState.readyNoScreen(client) || this.target == null || !this.target.isAlive() || ++this.age > COMBO_TIMEOUT) {
         this.reset();
         return;
      }

      if (this.wait > 0) {
         this.wait--;
         return;
      }

      LocalPlayer player = client.player;
      Level level = client.level;
      switch (this.step) {
         case OBSIDIAN -> {
            this.base = this.chooseBase(level, player, this.landing);
            if (this.base == null) {
               this.reset();
               return;
            }

            if (!CrystalSpots.isBase(level.getBlockState(this.base))) {
               if (!this.silentAimRequest(player, this.base)
                  || !BlockPlacer.place(client, this.base, CombatHelpers.obsidianOrResistant(), true, true, false)) {
                  return;
               }
            }

            this.advance(Step.CRYSTAL);
         }
         case CRYSTAL -> {
            // The target has moved since the hit; follow it if a better base turned up.
            Vec3 now = this.target.position().add(this.target.getDeltaMovement().scale(2.0));
            BlockPos better = this.chooseBase(level, player, now);
            if (better != null && CrystalSpots.isBase(level.getBlockState(better))) {
               this.base = better;
            }

            if (!CrystalSpots.placeable(level, this.base) || !this.safe(player, this.base)) {
               this.reset();
               return;
            }

            if (CrystalSpots.place(client, player, this.base, this.silentAim.get(), this.priority())) {
               this.advance(Step.DETONATE);
            }
         }
         case DETONATE -> {
            Entity crystal = CrystalSpots.crystalOn(level, this.base);
            if (crystal == null) {
               // Placement is confirmed by the server a tick later; the timeout ends the combo if it never shows up.
               return;
            }

            if (!this.safe(player, this.base)) {
               this.reset();
               return;
            }

            if (CrystalSpots.detonate(client, player, crystal, this.silentAim.get(), this.priority())) {
               this.reset();
            }
         }
         case IDLE -> {
         }
      }
   }

   private boolean silentAimRequest(LocalPlayer player, BlockPos pos) {
      return RotationManager.request(player, RotationManager.toward(player, pos), 0.0F, this.silentAim.get(), this.priority());
   }

   private void advance(Step next) {
      this.step = next;
      this.wait = this.delay.intValue();
   }

   /** The blast from a crystal on {@code base} is within both damage limits right now. */
   private boolean safe(LocalPlayer player, BlockPos base) {
      Vec3 center = CrystalSpots.center(base);
      float self = Damage.crystal(player, center);
      if (self > this.maxSelf.floatValue() || this.antiSuicide.get() && self + 1.0F >= Damage.health(player)) {
         return false;
      }

      this.damage = Damage.crystal(this.target, center);
      return this.damage >= this.minDamage.floatValue();
   }

   /**
    * The best crystal base around {@code around} (the spot the target is expected at): in the floor beside it,
    * existing obsidian or bedrock first, otherwise a free block obsidian can be placed in. Null when none passes the
    * damage limits.
    */
   private BlockPos chooseBase(Level level, LocalPlayer player, Vec3 around) {
      BlockPos feet = BlockPos.containing(around.x, this.target.getY() + 0.1, around.z);
      BlockPos best = null;
      float bestScore = -1.0F;
      float selfHealth = Damage.health(player);
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -1; dy <= 0; dy++) {
               BlockPos base = feet.offset(dx, dy - 1, dz);
               if (!BlockPlacer.inReach(player, base, this.range.get()) || !CrystalSpots.roomAbove(level, base)) {
                  continue;
               }

               boolean existing = CrystalSpots.isBase(level.getBlockState(base));
               if (!existing
                  && (!this.placeObsidian.get()
                     || !BlockPlacer.replaceable(level, base)
                     || BlockPlacer.findPlacement(level, base, false) == null
                     || BlockPlacer.occupied(level, base)
                     || BlockPlacer.pickSlot(player, CombatHelpers.obsidianOrResistant()) < 0)) {
                  continue;
               }

               Vec3 center = CrystalSpots.center(base);
               float self = Damage.crystal(player, center);
               if (self > this.maxSelf.floatValue() || this.antiSuicide.get() && self + 1.0F >= selfHealth) {
                  continue;
               }

               // Measured from where the target will be: shifting the blast by the opposite offset is the same thing.
               Vec3 offset = around.subtract(this.target.position());
               float hit = Damage.crystal(this.target, center.subtract(offset.x, 0.0, offset.z));
               float score = hit + (existing ? 1.5F : 0.0F);
               if (hit >= this.minDamage.floatValue() && score > bestScore) {
                  bestScore = score;
                  best = base;
               }
            }
         }
      }

      return best;
   }

   @Override
   public void onRender3D(float partialTick) {
      BlockPos pos = this.base;
      if (this.render.get() && pos != null) {
         Render3D.block(pos, this.color.argb(), true, true, 1.8F, 55, true);
         if (this.damage > 0.0F) {
            Render3D.text(String.format(Locale.ROOT, "%.1f", this.damage), Vec3.atCenterOf(pos).add(0.0, 0.7, 0.0), -1, 0.8F, true);
         }
      }
   }

   private enum Step {
      IDLE,
      OBSIDIAN,
      CRYSTAL,
      DETONATE
   }
}
