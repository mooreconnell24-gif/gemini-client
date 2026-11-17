package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.AttributeBoost;
import gemini.util.BlockBreaking;
import gemini.util.BlockPlacer;
import gemini.util.ClientState;
import gemini.util.InfoText;
import gemini.util.Swing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

/**
 * Everything about mining faster in one place: a speed multiplier, breaking on the first hit, no pause between
 * blocks, and finishing a block after the attack button is let go.
 */
public final class FastBreakModule extends Module {
   private static final String BOOST_ID = "fastbreak";
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Multiplier mines faster by the set amount; Instant breaks a block on the first hit", "Multiplier", "Multiplier", "Instant")
   );
   private final NumberSetting speed = this.addSetting(
      new NumberSetting("Speed", "Mining speed multiplier", 2.0, 1.0, 20.0, 0.5).showIf(() -> this.mode.is("Multiplier"))
   );
   private final BooleanSetting drops = this.addSetting(
      new BooleanSetting("Drops", "Blocks broken instantly still drop their items", true).showIf(() -> this.mode.is("Instant"))
   );
   private final BooleanSetting noDelay = this.addSetting(
      new BooleanSetting("No Delay", "Remove the short pause the game adds between one block and the next", true)
   );
   private final BooleanSetting keepMining = this.addSetting(
      new BooleanSetting("Keep Mining", "Finish the block you started on after you let go of the attack button", false)
   );
   private final InfoText infoText = new InfoText();
   private BlockPos keptPos;

   public FastBreakModule() {
      super("Fast Break", "Makes you mine blocks faster, or break them on the first hit.", Category.PLAYER);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("InstantMine");
      names.add("PacketMine");
      names.add("SpeedMine");
      return names;
   }

   /** Read by the mining mixin every tick the attack button is held. */
   public boolean removesDelay() {
      return this.noDelay.get();
   }

   public boolean instant() {
      return this.mode.is("Instant");
   }

   @Override
   public void onDisable() {
      AttributeBoost.clear(Attributes.BLOCK_BREAK_SPEED, BOOST_ID);
      Minecraft client = Minecraft.getInstance();
      if (this.keptPos != null && client.gameMode != null && !client.options.keyAttack.isDown()) {
         BlockBreaking.release();
         client.gameMode.stopDestroyBlock();
      }

      this.keptPos = null;
   }

   /** The remembered block belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.keptPos = null;
   }

   @Override
   public void onTick() {
      if (this.mode.is("Multiplier") && this.speed.get() > 1.0) {
         AttributeBoost.apply(Attributes.BLOCK_BREAK_SPEED, BOOST_ID, this.speed.get() - 1.0, Operation.ADD_MULTIPLIED_TOTAL);
      } else {
         AttributeBoost.clear(Attributes.BLOCK_BREAK_SPEED, BOOST_ID);
      }

      Minecraft client = Minecraft.getInstance();
      if (!this.keepMining.get() || this.instant() || !ClientState.ready(client) || client.gui.screen() != null) {
         this.keptPos = null;
         return;
      }

      boolean attacking = client.options.keyAttack.isDown();
      if (attacking && client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         this.keptPos = hit.getBlockPos().immutable();
      }

      if (this.keptPos != null) {
         if (client.level.getBlockState(this.keptPos).isAir() || !BlockPlacer.inReach(client.player, this.keptPos, 5.2)) {
            this.keptPos = null;
         } else if (!attacking) {
            // While the button is held vanilla already mines the block under the crosshair; driving it here as
            // well would add a second dose of progress every tick. Take over only once the button is let go.
            BlockBreaking.keep();
            client.gameMode.continueDestroyBlock(this.keptPos, Direction.UP);
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.keptPos != null) {
         Render3D.block(this.keptPos, -16748289, true, true, 1.8F, 55, true);
      }
   }

   /** Instant mode: breaks {@code pos} now, on both sides of the single-player world. False when it cannot be broken. */
   public boolean breakNow(BlockPos pos) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null || client.player == null || client.player.isCreative()) {
         return false;
      }

      BlockState state = client.level.getBlockState(pos);
      if (state.isAir() || state.getDestroySpeed(client.level, pos) < 0.0F) {
         return false;
      }

      BlockPos target = pos.immutable();
      boolean drop = this.drops.get();
      if (client.getConnection() != null) {
         client.getConnection().send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, target, Direction.UP));
         client.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, target, Direction.UP));
      }

      client.level.destroyBlock(target, drop, client.player);
      Swing.attack(client.player, InteractionHand.MAIN_HAND);
      AttributeBoost.onServer(serverPlayer -> {
         if (drop) {
            serverPlayer.gameMode.destroyBlock(target);
         } else {
            serverPlayer.level().destroyBlock(target, false, serverPlayer);
         }
      });
      return true;
   }

   @Override
   public String info() {
      return this.instant() ? "Instant" : this.infoText.fixed(this.speed.get(), 1);
   }
}
