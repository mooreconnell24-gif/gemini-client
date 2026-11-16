package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Clean screenshots on one key: hides the HUD (and with it the hand and every Gemini overlay), waits for a frame
 * to be drawn without them, saves the screenshot the vanilla way, and puts the HUD back. Can take a short burst.
 * Runs once per press and switches itself off.
 */
public final class ScreenshotHelper extends Module {
   private final BooleanSetting hideHud = this.addSetting(
      new BooleanSetting("Hide HUD", "Hide the HUD, your hand and Gemini's overlays for the shot, like F1", true)
   );
   private final NumberSetting delay = this.addSetting(
      new NumberSetting("Delay", "Ticks to wait before the first shot, so a frame without the HUD is drawn first", 2.0, 1.0, 20.0, 1.0)
   );
   private final NumberSetting shots = this.addSetting(new NumberSetting("Shots", "How many screenshots to take in a row", 1.0, 1.0, 10.0, 1.0));
   private final NumberSetting interval = this.addSetting(
      new NumberSetting("Interval", "Ticks between shots in a burst", 10.0, 1.0, 100.0, 1.0).showIf(() -> this.shots.intValue() > 1)
   );
   private final BooleanSetting chatLink = this.addSetting(
      new BooleanSetting("Chat Link", "Post the usual clickable 'Saved screenshot' line in chat", true)
   );
   private boolean hidHud;
   private int wait;
   private int taken;

   public ScreenshotHelper() {
      super("Screenshot Helper", "Takes screenshots without the HUD, hand or overlays with one key, one at a time or in a burst.", Category.CLIENT);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   @Override
   public void onEnable() {
      Minecraft client = Minecraft.getInstance();
      this.taken = 0;
      this.wait = this.delay.intValue();
      this.hidHud = false;
      if (this.hideHud.get() && client.gui != null && client.gui.hud != null && !client.gui.hud.isHidden()) {
         client.gui.hud.toggle();
         this.hidHud = true;
      }
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (this.hidHud && client.gui != null && client.gui.hud != null && client.gui.hud.isHidden()) {
         client.gui.hud.toggle();
      }

      this.hidHud = false;
      this.wait = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || client.level == null) {
         this.setEnabled(false, false);
         return;
      }

      if (this.wait-- > 0) {
         return;
      }

      // Called between frames, like the vanilla screenshot key: the main target holds the last finished frame.
      Screenshot.grab(client.gameDirectory, client.gameRenderer.mainRenderTarget(), message -> {
         if (this.chatLink.get()) {
            client.execute(() -> {
               if (client.gui != null && client.gui.hud != null) {
                  client.gui.hud.getChat().addClientSystemMessage(message);
               }
            });
         }
      });
      this.taken++;
      if (this.taken >= this.shots.intValue()) {
         HudOverlay.toast("Screenshot Helper", this.taken == 1 ? "Screenshot saved" : this.taken + " screenshots saved", HudOverlay.Severity.SUCCESS);
         this.setEnabled(false, false);
      } else {
         this.wait = this.interval.intValue() - 1;
      }
   }
}
