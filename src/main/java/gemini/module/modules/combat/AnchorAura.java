package gemini.module.modules.combat;

import gemini.util.Targets;
import gemini.util.Inventories;
import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
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
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Target and placement range", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting maxSelf = this.addSetting(new NumberSetting("Max Self Damage", "Max allowed self explosion damage", 9.0, 2.0, 18.0, 1.0));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between anchor actions", 2.0, 0.0, 10.0, 1.0));
   private int wait;

   public AnchorAura() {
      super("AnchorAura", "Place, charge, and detonate Respawn Anchors on enemies.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null && client.level.dimension() != Level.NETHER && this.wait-- <= 0) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         LivingEntity enemy = Targets.nearestEnemy(player, client.level.entitiesForRendering(), this.range.get(), true);
         if (enemy != null) {
            BlockPos targetPos = enemy.blockPosition().above(2);
            if (!BlockPlacer.inReach(player, targetPos, this.range.get())) {
               targetPos = enemy.blockPosition().above();
            }

            float selfDmg = Damage.crystal(player, Vec3.atCenterOf(targetPos));
            if (selfDmg > this.maxSelf.floatValue() || selfDmg + 1.5F >= Damage.health(player)) {
               return;
            }

            BlockState state = client.level.getBlockState(targetPos);
            BlockPos finalPos = targetPos;
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
      }
   }
}
