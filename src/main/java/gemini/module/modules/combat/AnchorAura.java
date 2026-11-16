package gemini.module.modules.combat;

import gemini.util.Inventories;
import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.Damage;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class AnchorAura extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest enemy and anchor spot, in blocks", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting maxSelf = this.addSetting(new NumberSetting("Max Self Damage", "Most damage an anchor blast may deal you", 9.0, 2.0, 18.0, 1.0));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between anchor actions", 2.0, 0.0, 10.0, 1.0));
   private final Targeting targeting = Targeting.builder(this).box("Outline", 30).build();
   private int wait;

   public AnchorAura() {
      super("AnchorAura", "Places, charges and sets off respawn anchors next to the chosen enemy, skipping any blast that would hurt you too much.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.targeting.clear();
   }

   @Override
   public void onWorldChange() {
      this.targeting.clear();
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.ready(client) || client.gui.screen() != null || client.level.dimension() == Level.NETHER) {
         this.targeting.clear();
         return;
      }

      if (this.wait-- > 0) {
         return;
      }

      this.wait = this.delay.intValue();
      LocalPlayer player = client.player;
      LivingEntity enemy = this.targeting.pick(player, client.level.entitiesForRendering(), this.range.get(), living -> this.anchorSpot(player, living) != null);
      if (enemy == null) {
         return;
      }

      BlockPos finalPos = this.anchorSpot(player, enemy);
      BlockState state = client.level.getBlockState(finalPos);
      if (state.is(Blocks.RESPAWN_ANCHOR)) {
         RotationManager.request(player, RotationManager.toward(player, finalPos), 90.0F, true, ActionPriority.COMBAT);
         int charges = (Integer)state.getValue(RespawnAnchorBlock.CHARGE);
         if (charges == 0) {
            Inventories.withHotbarItem(player, s -> s.is(Items.GLOWSTONE), () -> CombatHelpers.click(client, player, finalPos, Direction.UP));
         } else {
            Inventories.withHotbarItem(player, s -> !s.is(Items.GLOWSTONE), () -> CombatHelpers.click(client, player, finalPos, Direction.UP));
         }
      } else if (BlockPlacer.replaceable(client.level, finalPos)) {
         BlockPlacer.place(client, finalPos, s -> s.is(Items.RESPAWN_ANCHOR), true, true, true);
      }
   }

   /**
    * Where the anchor goes for {@code enemy}: two blocks above its feet, or one when that is out of reach. Null
    * when the blast there would deal more than Max Self Damage or leave you within 1.5 health of dying.
    */
   private BlockPos anchorSpot(LocalPlayer player, LivingEntity enemy) {
      BlockPos pos = enemy.blockPosition().above(2);
      if (!BlockPlacer.inReach(player, pos, this.range.get())) {
         pos = enemy.blockPosition().above();
      }

      float selfDmg = Damage.crystal(player, Vec3.atCenterOf(pos));
      return selfDmg > this.maxSelf.floatValue() || selfDmg + 1.5F >= Damage.health(player) ? null : pos;
   }
}
