package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.ClientState;
import gemini.util.Measurement;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A measuring tape. While on, middle-click a block to set the first point and another to set the second; until the
 * second is set, the measurement follows the block you look at. Shows the distance between the block centres and the
 * size and block count of the box between them. A third middle-click starts again.
 */
public final class Measure extends Module {
   private final NumberSetting reach = this.addSetting(
      new NumberSetting("Reach", "How far away a block can be to be picked as a point", 64.0, 4.0, 256.0, 4.0)
   );
   private final BooleanSetting box = this.addSetting(new BooleanSetting("Box", "Outline the whole box between the two points", true));
   private final BooleanSetting labels = this.addSetting(new BooleanSetting("Labels", "Show the distance and box size between the points", true));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Draw the points, line and labels in front of blocks", true));
   private final BooleanSetting chat = this.addSetting(new BooleanSetting("Chat", "Write the full measurement in chat when the second point is set", false));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Colour of the points, line, box and labels", 190));
   private boolean hasA;
   private boolean hasB;
   private int ax;
   private int ay;
   private int az;
   private int bx;
   private int by;
   private int bz;
   private Measurement measurement;
   private boolean middleWasDown;

   public Measure() {
      super("Measure", "Measures the distance, size and block count between two blocks you middle-click.", Category.WORLD);
      this.addSetting(new ActionSetting("Copy Result", "Copy the current measurement to the clipboard", this::copy));
      this.addSetting(new ActionSetting("Clear", "Remove both points", this::clear));
   }

   @Override
   public void onDisable() {
      this.clear();
      this.middleWasDown = false;
   }

   @Override
   public void onWorldChange() {
      this.clear();
   }

   private void clear() {
      this.hasA = false;
      this.hasB = false;
      this.measurement = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (!ClientState.ready(client) || player == null) {
         this.middleWasDown = false;
         return;
      }

      boolean picking = client.gui.screen() == null;
      boolean down = picking && client.mouseHandler.isMiddlePressed();
      boolean pressed = down && !this.middleWasDown;
      this.middleWasDown = down;
      boolean needLook = pressed || this.hasA && !this.hasB;
      if (!needLook) {
         return;
      }

      HitResult hit = player.pick(this.reach.get(), 1.0F, false);
      BlockPos looked = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
      if (pressed && looked != null) {
         if (!this.hasA || this.hasB) {
            this.hasA = true;
            this.hasB = false;
            this.ax = looked.getX();
            this.ay = looked.getY();
            this.az = looked.getZ();
         } else {
            this.hasB = true;
            this.bx = looked.getX();
            this.by = looked.getY();
            this.bz = looked.getZ();
            this.update(this.bx, this.by, this.bz);
            if (this.chat.get()) {
               CommandManager.feedback("§bMeasure §7" + this.measurement.summary());
            }
         }
      }

      if (this.hasA && !this.hasB) {
         // Until the second point is set, the far end follows the block under the crosshair.
         if (looked != null) {
            this.update(looked.getX(), looked.getY(), looked.getZ());
         } else {
            this.measurement = null;
         }
      }
   }

   /** Rebuilds the measurement only when an end point moved, so labels are formatted once per change. */
   private void update(int x, int y, int z) {
      if (this.measurement == null || !this.measurement.same(this.ax, this.ay, this.az, x, y, z)) {
         this.measurement = new Measurement(this.ax, this.ay, this.az, x, y, z);
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (!this.hasA) {
         return;
      }

      int argb = this.color.argb();
      boolean walls = this.throughWalls.get();
      Render3D.block(this.ax, this.ay, this.az, argb, true, true, 2.0F, 50, walls);
      Measurement m = this.measurement;
      if (m == null) {
         return;
      }

      Render3D.block(m.bx, m.by, m.bz, argb, true, this.hasB, 2.0F, 50, walls);
      double x1 = m.ax + 0.5;
      double y1 = m.ay + 0.5;
      double z1 = m.az + 0.5;
      double x2 = m.bx + 0.5;
      double y2 = m.by + 0.5;
      double z2 = m.bz + 0.5;
      Render3D.line(x1, y1, z1, x2, y2, z2, argb, 2.0F, walls);
      if (this.box.get() && m.volume() > 1L) {
         Render3D.box(
            Math.min(m.ax, m.bx), Math.min(m.ay, m.by), Math.min(m.az, m.bz),
            Math.max(m.ax, m.bx) + 1.0, Math.max(m.ay, m.by) + 1.0, Math.max(m.az, m.bz) + 1.0,
            argb, true, false, 1.0F, 0, walls
         );
      }

      if (this.labels.get()) {
         double mx = (x1 + x2) * 0.5;
         double my = (y1 + y2) * 0.5;
         double mz = (z1 + z2) * 0.5;
         Render3D.text(m.distanceLabel(), mx, my + 0.75, mz, argb, 0.45F, walls);
         Render3D.text(m.sizeLabel(), mx, my + 0.45, mz, argb, 0.35F, walls);
      }
   }

   private void copy() {
      Measurement m = this.measurement;
      if (m == null) {
         CommandManager.feedback("§7Nothing measured yet. Switch §fMeasure §7on and middle-click two blocks.");
      } else {
         Minecraft.getInstance().keyboardHandler.setClipboard(m.summary());
         CommandManager.feedback("§bMeasure §7copied: " + m.summary());
      }
   }

   @Override
   public boolean onCommand(String[] args) {
      if (args.length == 0) {
         return false;
      }

      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "clear", "reset" -> {
            this.clear();
            CommandManager.feedback("§bMeasure §7cleared.");
         }
         case "copy" -> this.copy();
         default -> {
            return false;
         }
      }

      return true;
   }

   @Override
   public String info() {
      Measurement m = this.measurement;
      return m == null ? null : m.distanceLabel();
   }
}
