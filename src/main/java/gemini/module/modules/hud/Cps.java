package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class Cps extends Ticked {
   private final ArrayDeque<Long> left = new ArrayDeque<>();
   private final ArrayDeque<Long> right = new ArrayDeque<>();
   private boolean wasLeft;
   private boolean wasRight;

   public Cps() {
      super("CPS", "Shows your clicks per second for both mouse buttons.", "Bottom Right", 6, 114, 100);
   }

   /**
    * Sampled every frame rather than every tick: a quick click (pressed and released within one 50 ms tick) never
    * shows up in a once-a-tick check, so the tick-based count read low.
    */
   @Override
   public void onRenderHud(HudContext hud) {
      this.sample();
      super.onRenderHud(hud);
   }

   @Override
   public void onTick() {
      this.sample();
   }

   private void sample() {
      Minecraft client = Minecraft.getInstance();
      long now = System.currentTimeMillis();
      boolean l = client.mouseHandler.isLeftPressed();
      boolean r = client.mouseHandler.isRightPressed();
      if (l && !this.wasLeft) {
         this.left.addLast(now);
      }

      if (r && !this.wasRight) {
         this.right.addLast(now);
      }

      this.wasLeft = l;
      this.wasRight = r;
      trim(this.left, now);
      trim(this.right, now);
   }

   private static void trim(ArrayDeque<Long> clicks, long now) {
      while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
         clicks.removeFirst();
      }
   }

   int leftCps() {
      trim(this.left, System.currentTimeMillis());
      return this.left.size();
   }

   int rightCps() {
      trim(this.right, System.currentTimeMillis());
      return this.right.size();
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      return List.of(TextHudWidget.Line.row("", this.leftCps() + " / " + this.rightCps(), "cps"));
   }
}
