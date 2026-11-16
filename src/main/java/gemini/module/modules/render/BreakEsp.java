package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.ui.Glass;
import gemini.util.InfoText;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.SortedSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Boxes on every block someone is breaking, read from the block-destruction progress the client level keeps (the
 * same table that draws the crack texture, filled by {@code ClientboundBlockDestructionPacket} for other players
 * and by your own game mode for your own mining). Progress arrives in ten stages; the box animates smoothly between
 * them. Entries and labels are pooled, so a steady scene allocates nothing of its own per frame.
 */
public final class BreakEsp extends Module {
   private static final float FADE_SECONDS = 0.45F;
   private final ModeSetting growth = this.addSetting(new ModeSetting("Box", "Grow from the centre as the block breaks, or shrink towards it", "Grow", "Grow", "Shrink"));
   private final ColorSetting startColor = this.addSetting(new ColorSetting("Start Color", "Colour at the first crack", 0));
   private final ColorSetting doneColor = this.addSetting(new ColorSetting("Done Color", "Colour as the block is about to break", 120));
   private final NumberSetting fillOpacity = this.addSetting(new NumberSetting("Fill Opacity", "Opacity of the box faces", 60.0, 0.0, 200.0, 5.0));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest block shown, in blocks", 48.0, 8.0, 160.0, 4.0));
   private final BooleanSetting labels = this.addSetting(new BooleanSetting("Labels", "Show who is breaking it and how far along", true));
   private final BooleanSetting self = this.addSetting(new BooleanSetting("Self", "Also show the block you are mining", true));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Draw on top of terrain", true));
   private final Long2ObjectOpenHashMap<Entry> byPos = new Long2ObjectOpenHashMap<>();
   private final ArrayList<Entry> active = new ArrayList<>();
   private final ArrayList<Entry> pool = new ArrayList<>();
   private long lastFrame;
   private final InfoText infoText = new InfoText();

   public BreakEsp() {
      super("Break ESP", "Shows a growing box on blocks being mined, with who is mining them and how far along they are.", Category.RENDER);
   }

   private static final class Entry {
      long key;
      int x;
      int y;
      int z;
      int breaker;
      String name;
      float target;
      float shown;
      /** 1 while the breaker is still at it, falling to 0 once the entry left the table. */
      float alpha;
      boolean seen;
      boolean finishing;
      int labelPercent = -1;
      String label;
      Vec3 labelPos;
   }

   /** How far along a block is at a destruction stage (0..9 from the game): a tenth per stage, 1 at the last. */
   static float stageProgress(int stage) {
      return Math.max(0.0F, Math.min(1.0F, (stage + 1) / 10.0F));
   }

   /** Edge length of the box at {@code progress} (0..1): 0.1 growing to a full block, or a full block shrinking to 0.1. */
   static float boxSize(float progress, boolean shrink) {
      float p = Math.max(0.0F, Math.min(1.0F, progress));
      return shrink ? 1.0F - p * 0.9F : 0.1F + p * 0.9F;
   }

   /** The opacity of a finished entry after {@code dt} more seconds of its linear fade-out. */
   static float faded(float alpha, float dt) {
      return alpha - Math.max(0.0F, dt) / FADE_SECONDS;
   }

   @Override
   public String info() {
      return this.active.isEmpty() ? null : this.infoText.whole(this.active.size(), "");
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onWorldChange() {
      this.reset();
   }

   private void reset() {
      for (Entry entry : this.active) {
         this.pool.add(entry);
      }

      this.active.clear();
      this.byPos.clear();
      this.lastFrame = 0L;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      LocalPlayer player = client.player;
      if (level == null || player == null) {
         this.reset();
         return;
      }

      for (int i = 0; i < this.active.size(); i++) {
         this.active.get(i).seen = false;
      }

      double maxSq = this.range.get() * this.range.get();
      for (Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> slot : level.destructionProgress().long2ObjectEntrySet()) {
         SortedSet<BlockDestructionProgress> set = slot.getValue();
         if (set == null || set.isEmpty()) {
            continue;
         }

         BlockDestructionProgress progress = set.last();
         if (progress.getId() == player.getId() && !this.self.get()) {
            continue;
         }

         BlockPos pos = progress.getPos();
         if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxSq) {
            continue;
         }

         long key = slot.getLongKey();
         Entry entry = this.byPos.get(key);
         if (entry == null) {
            entry = this.pool.isEmpty() ? new Entry() : this.pool.remove(this.pool.size() - 1);
            entry.key = key;
            entry.x = pos.getX();
            entry.y = pos.getY();
            entry.z = pos.getZ();
            entry.breaker = Integer.MIN_VALUE;
            entry.shown = 0.0F;
            entry.labelPercent = -1;
            entry.labelPos = new Vec3(entry.x + 0.5, entry.y + 1.35, entry.z + 0.5);
            this.byPos.put(key, entry);
            this.active.add(entry);
         }

         if (entry.breaker != progress.getId()) {
            entry.breaker = progress.getId();
            Entity breaker = level.getEntity(entry.breaker);
            entry.name = breaker == null ? "Someone" : breaker.getName().getString();
            entry.labelPercent = -1;
         }

         entry.target = stageProgress(progress.getProgress());
         entry.seen = true;
         entry.finishing = false;
         entry.alpha = 1.0F;
      }

      for (int i = 0; i < this.active.size(); i++) {
         Entry entry = this.active.get(i);
         if (!entry.seen && !entry.finishing) {
            // Gone from the table: either broken (air now, show it complete) or abandoned; fade either way.
            entry.finishing = true;
            if (level.getBlockState(BlockPos.of(entry.key)).isAir()) {
               entry.target = 1.0F;
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.active.isEmpty()) {
         this.lastFrame = 0L;
         return;
      }

      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      boolean shrink = this.growth.is("Shrink");
      boolean walls = this.throughWalls.get();
      int start = this.startColor.argb();
      int done = this.doneColor.argb();
      for (int i = this.active.size() - 1; i >= 0; i--) {
         Entry entry = this.active.get(i);
         entry.shown = Glass.approach(entry.shown, entry.target, 10.0F, dt);
         if (entry.finishing) {
            entry.alpha = faded(entry.alpha, dt);
            if (entry.alpha <= 0.0F) {
               this.byPos.remove(entry.key);
               this.active.set(i, this.active.get(this.active.size() - 1));
               this.active.remove(this.active.size() - 1);
               this.pool.add(entry);
               continue;
            }
         }

         float p = Math.max(0.0F, Math.min(1.0F, entry.shown));
         float size = boxSize(p, shrink);
         double half = size * 0.5;
         double cx = entry.x + 0.5;
         double cy = entry.y + 0.5;
         double cz = entry.z + 0.5;
         int rgb = Glass.lerp(start, done, p) & 0xFFFFFF;
         int alpha = Math.round(255.0F * entry.alpha);
         int argb = (alpha << 24) | rgb;
         Render3D.boxArgb(
            cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, argb, true, true, 1.8F, Math.round(this.fillOpacity.floatValue() * entry.alpha), walls
         );
         if (this.labels.get()) {
            int percent = Math.round(p * 100.0F);
            if (percent != entry.labelPercent) {
               entry.labelPercent = percent;
               entry.label = entry.name + " " + percent + "%";
            }

            Render3D.textArgb(entry.label, entry.labelPos, argb, 0.8F, walls);
         }
      }
   }
}
