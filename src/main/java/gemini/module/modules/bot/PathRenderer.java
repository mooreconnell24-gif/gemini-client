package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ColorSetting;
import gemini.util.BuiltInBaritone;

public final class PathRenderer extends Module {
   private final ColorSetting lineColor = this.addSetting(new ColorSetting("Path Color", "Color of the 3D Baritone path line", 198));
   private final ColorSetting goalColor = this.addSetting(new ColorSetting("Goal Color", "Color of the destination box", 280));

   public PathRenderer() {
      super("Path Renderer", "Render 3D path lines and goal boxes for Baritone.", Category.RENDER);
      this.loadState(true, this.getKeybind());
   }

   @Override
   public void onTick() {
      BuiltInBaritone.configureRender(this.isEnabled(), this.lineColor.argb(), this.goalColor.argb());
   }

   @Override
   public void onDisable() {
      BuiltInBaritone.configureRender(false, this.lineColor.argb(), this.goalColor.argb());
   }
}
