package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.ShapeSettings;
import gemini.util.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AirPlace extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far ahead the block is placed", 5.0, 2.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between blocks while the button is held", 3.0, 0.0, 10.0, 1.0));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when placing", true));
   private final BooleanSetting preview = this.addSetting(new BooleanSetting("Preview", "Outline the spot the block will go", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Colour of the preview outline", 175).showIf(this.preview::get));
   private final ShapeSettings shape = new ShapeSettings(this, "Both", true, this.preview::get);
   private int cooldown;

   public AirPlace() {
      super("Air Place", "Place the block in your hand anywhere you aim.", Category.WORLD);
   }

   private BlockPos target(LocalPlayer player, float partialTick) {
      Vec3 end = player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(this.range.get()));
      return BlockPos.containing(end);
   }

   private static boolean aimingAtNothing(Minecraft client) {
      HitResult hit = client.hitResult;
      return hit == null || hit.getType() == Type.MISS;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (ClientState.readyNoScreen(client) && client.options.keyUse.isDown() && !client.player.isUsingItem() && aimingAtNothing(client)) {
         Item held = client.player.getMainHandItem().getItem();
         if (held instanceof BlockItem
            && BlockPlacer.place(client, this.target(client.player, 1.0F), stack -> stack.getItem() == held, this.swing.get(), false, true)) {
            this.cooldown = this.delay.intValue();
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (this.preview.get()
         && player != null
         && client.gui.screen() == null
         && player.getMainHandItem().getItem() instanceof BlockItem
         && aimingAtNothing(client)) {
         this.shape.block(this.target(player, partialTick), this.color.argb());
      }
   }
}
