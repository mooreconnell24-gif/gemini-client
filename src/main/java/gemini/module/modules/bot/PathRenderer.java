package gemini.module.modules.bot;

import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ColorSetting;
import gemini.util.BaritoneBridge;
import gemini.util.BuiltInBaritone;

public final class PathRenderer extends Module {
   private final ColorSetting lineColor = this.addSetting(new ColorSetting("Path Color", "Color of the 3D Baritone path line", 198));
   private final ColorSetting goalColor = this.addSetting(new ColorSetting("Goal Color", "Color of the destination box", 280));
   private boolean mirroredEnabled;
   private int mirroredLine;
   private int mirroredGoal;
   private boolean mirrored;

   public PathRenderer() {
      super("Path Renderer", "Render 3D path lines and goal boxes for Baritone.", Category.RENDER);
      this.loadState(true, this.getKeybind());
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.BOT;
   }

   @Override
   public void onTick() {
      this.apply(this.isEnabled());
   }

   @Override
   public void onDisable() {
      this.apply(false);
   }

   /** Keeps both engines in step: the built-in renderer directly, the real Baritone through its own settings. */
   private void apply(boolean enabled) {
      int line = this.lineColor.argb();
      int goal = this.goalColor.argb();
      BuiltInBaritone.configureRender(enabled, line, goal);
      if (!this.mirrored || enabled != this.mirroredEnabled || line != this.mirroredLine || goal != this.mirroredGoal) {
         if (BaritoneBridge.setExternalSetting("renderPath", enabled)) {
            BaritoneBridge.setExternalSetting("renderGoal", enabled);
            BaritoneBridge.setExternalSetting("colorCurrentPath", new java.awt.Color(line, true));
            BaritoneBridge.setExternalSetting("colorGoalBox", new java.awt.Color(goal, true));
            this.mirrored = true;
            this.mirroredEnabled = enabled;
            this.mirroredLine = line;
            this.mirroredGoal = goal;
         }
      }
   }
}
