package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.util.AttributeBoost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

/**
 * The natural spawning caps of the dimension you are in, as the server's spawner saw them on its last pass: how
 * many mobs of each kind count toward the cap and how many are allowed. A full monster cap is why a mob farm stops
 * producing; this shows it without the debug screen.
 */
public final class MobCaps extends Ticked {
   /** {@code NaturalSpawner.MAGIC_NUMBER}: the 17x17 chunk area one player's spawning radius covers. */
   static final int CHUNK_AREA = 289;
   private static final MobCategory[] SHOWN = {
      MobCategory.MONSTER,
      MobCategory.CREATURE,
      MobCategory.AMBIENT,
      MobCategory.WATER_CREATURE,
      MobCategory.WATER_AMBIENT,
      MobCategory.UNDERGROUND_WATER_CREATURE,
      MobCategory.AXOLOTLS
   };
   private static final String[] LABELS = {"Monsters", "Animals", "Ambient", "Water", "Fish", "Glow Squid", "Axolotls"};
   private final BooleanSetting monsters = this.addSetting(new BooleanSetting("Monsters", "Show the hostile mob cap", true));
   private final BooleanSetting animals = this.addSetting(new BooleanSetting("Animals", "Show the passive animal cap", true));
   private final BooleanSetting ambient = this.addSetting(new BooleanSetting("Ambient", "Show the bat cap", false));
   private final BooleanSetting water = this.addSetting(
      new BooleanSetting("Water", "Show the squid, fish, glow squid and axolotl caps", false)
   );
   private final BooleanSetting chunks = this.addSetting(new BooleanSetting("Chunk Count", "Show how many chunks around you count for spawning", false));
   private volatile Snapshot snapshot;
   private int poll;

   public MobCaps() {
      super("Mob Caps", "Shows the natural spawning cap for each kind of mob, to help work out why a farm stops.", "Top Right", 6, 120, 250);
   }

   /** {@code counts[i]} and {@code caps[i]} line up with {@link #SHOWN}; {@code chunks} is -1 while spawning has not run. */
   private record Snapshot(int[] counts, int[] caps, int chunks) {
   }

   /** The cap for one mob kind: its per-chunk limit spread over the spawnable chunks, as the spawner works it out. */
   static int globalCap(int perChunk, int spawnableChunks) {
      return perChunk * spawnableChunks / CHUNK_AREA;
   }

   @Override
   public void onEnable() {
      this.snapshot = null;
      this.poll = 0;
   }

   @Override
   public void onDisable() {
      this.snapshot = null;
   }

   @Override
   public void onWorldChange() {
      this.snapshot = null;
      this.poll = 0;
   }

   @Override
   public void onTick() {
      if (this.poll-- <= 0) {
         this.poll = 20;
         AttributeBoost.onServer(this::read);
      }
   }

   /** Runs on the server thread. */
   private void read(ServerPlayer player) {
      NaturalSpawner.SpawnState state = player.level().getChunkSource().getLastSpawnState();
      if (state == null) {
         this.snapshot = new Snapshot(new int[SHOWN.length], new int[SHOWN.length], -1);
         return;
      }

      int spawnable = state.getSpawnableChunkCount();
      int[] counts = new int[SHOWN.length];
      int[] caps = new int[SHOWN.length];
      for (int i = 0; i < SHOWN.length; i++) {
         counts[i] = state.getMobCategoryCounts().getInt(SHOWN[i]);
         caps[i] = globalCap(SHOWN[i].getMaxInstancesPerChunk(), spawnable);
      }

      this.snapshot = new Snapshot(counts, caps, spawnable);
   }

   private boolean shown(int index) {
      return switch (index) {
         case 0 -> this.monsters.get();
         case 1 -> this.animals.get();
         case 2 -> this.ambient.get();
         default -> this.water.get();
      };
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      Snapshot s = this.snapshot;
      if (s == null) {
         return List.of();
      }

      if (s.chunks() < 0) {
         return List.of(new TextHudWidget.Line("Mob caps: spawning has not run here", Glass.TEXT_FAINT));
      }

      List<TextHudWidget.Line> lines = new ArrayList<>(SHOWN.length + 1);
      for (int i = 0; i < SHOWN.length; i++) {
         if (this.shown(i)) {
            int count = s.counts()[i];
            int cap = s.caps()[i];
            int color = count >= cap ? Glass.ERROR : (count * 4 >= cap * 3 ? Glass.WARN : Glass.TEXT);
            lines.add(new TextHudWidget.Line(LABELS[i] + " " + count + "/" + cap, color));
         }
      }

      if (this.chunks.get()) {
         lines.add(new TextHudWidget.Line(s.chunks() + " spawnable chunks", Glass.TEXT_DIM));
      }

      return lines;
   }
}
