package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.HitResult;

public final class LookingAt extends Ticked {
   public LookingAt() {
      super("Looking At", "The block or entity under your crosshair.", "Top Center", 0, 40, 50);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      HitResult hit = hud.client.hitResult;
      if (hit instanceof BlockHitResult block && block.getType() == Type.BLOCK) {
         BlockState state = hud.client.level.getBlockState(block.getBlockPos());
         BlockPos pos = block.getBlockPos();
         return List.of(
            new TextHudWidget.Line(state.getBlock().getName().getString(), hud.accent),
            new TextHudWidget.Line(pos.getX() + "  " + pos.getY() + "  " + pos.getZ(), -6641995)
         );
      } else if (hit instanceof EntityHitResult entityHit) {
         Entity entity = entityHit.getEntity();
         List<TextHudWidget.Line> lines = new ArrayList<>(2);
         lines.add(new TextHudWidget.Line(entity.getName().getString(), hud.accent));
         if (entity instanceof LivingEntity living) {
            lines.add(
               new TextHudWidget.Line(
                  String.format(Locale.ROOT, "%.1f / %.0f HP", living.getHealth(), living.getMaxHealth()),
                  HudHelpers.ratioColor(living.getHealth() / Math.max(1.0F, living.getMaxHealth()))
               )
            );
         }

         return lines;
      } else {
         return List.of();
      }
   }
}
