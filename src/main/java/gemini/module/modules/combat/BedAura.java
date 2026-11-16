package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class BedAura extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Bed placement and detonation reach", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bed actions", 3.0, 1.0, 15.0, 1.0));
   private final Targeting targeting = Targeting.builder(this).box("Outline", 0).build();
   private int wait;

   public BedAura() {
      super("BedAura", "Place and detonate beds on the enemy Priority picks, in the Nether or End.", Category.COMBAT);
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
      if (!ClientState.ready(client) || client.gui.screen() != null || client.level.dimension() == Level.OVERWORLD) {
         this.targeting.clear();
         return;
      }

      if (this.wait-- > 0) {
         return;
      }

      this.wait = this.delay.intValue();
      LocalPlayer player = client.player;
      Level level = client.level;
      // An enemy whose head space holds neither a bed nor room for one cannot be bedded; go for the next one.
      LivingEntity enemy = this.targeting.pick(player, client.level.entitiesForRendering(), this.range.get(), living -> {
         BlockPos head = living.blockPosition().above();
         return level.getBlockState(head).is(BlockTags.BEDS) || BlockPlacer.replaceable(level, head);
      });
      if (enemy != null) {
         BlockPos pos = enemy.blockPosition().above();
         if (level.getBlockState(pos).is(BlockTags.BEDS)) {
            RotationManager.request(player, RotationManager.toward(player, pos), 90.0F, true, ActionPriority.COMBAT);
            CombatHelpers.click(client, player, pos, Direction.UP);
         } else {
            BlockPlacer.place(client, pos, stack -> stack.is(ItemTags.BEDS), true, true, true, false);
         }
      }
   }
}
