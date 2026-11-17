package gemini.module.modules.hud;

import gemini.ui.HudStyle;
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
   private final BooleanSetting chunks = this.addSetting(new BooleanSetting("Chunk Count", "Always show how many chunks around you count for spawning (shown anyway when fewer than 289 do, since that lowers the caps)", false));
   private volatile Snapshot snapshot;
   private int poll;

   public MobCaps() {
      super("Mob Caps", "Shows the natural spawning cap for each kind of mob, to help work out why a farm stops.", "Top Right", 6, 120, 250);
   }

   /**
    * {@code counts[i]} and {@code caps[i]} line up with {@link #SHOWN}; {@code chunks} is -1 while spawning has not
    * run. {@code spawning} is the Spawn Mobs game rule; {@code monsters} is false when monsters cannot spawn
    * naturally at all (Peaceful, or the Spawn Monsters game rule off).
    */
   private record Snapshot(int[] counts, int[] caps, int chunks, boolean spawning, boolean monsters) {
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
      net.minecraft.server.level.ServerLevel level = player.level();
      boolean spawning = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS);
      boolean monsters = level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
         && level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS);
      NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
      if (state == null) {
         this.snapshot = new Snapshot(new int[SHOWN.length], new int[SHOWN.length], -1, spawning, monsters);
         return;
      }

      int spawnable = state.getSpawnableChunkCount();
      int[] counts = new int[SHOWN.length];
      int[] caps = new int[SHOWN.length];
      for (int i = 0; i < SHOWN.length; i++) {
         counts[i] = state.getMobCategoryCounts().getInt(SHOWN[i]);
         caps[i] = globalCap(SHOWN[i].getMaxInstancesPerChunk(), spawnable);
      }

      this.snapshot = new Snapshot(counts, caps, spawnable, spawning, monsters);
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

      if (!s.spawning()) {
         return List.of(new TextHudWidget.Line("Mob spawning is off (game rule)", HudStyle.SECONDARY));
      }

      if (s.chunks() < 0) {
         return List.of();
      }

      List<TextHudWidget.Line> lines = new ArrayList<>(SHOWN.length + 1);
      for (int i = 0; i < SHOWN.length; i++) {
         if (this.shown(i)) {
            if (i == 0 && !s.monsters()) {
               lines.add(new TextHudWidget.Line("Monsters off (Peaceful or game rule)", HudStyle.SECONDARY));
               continue;
            }

            int count = s.counts()[i];
            int cap = s.caps()[i];
            // The spawner only tries while the count is below the cap; more than the cap is normal (spawners,
            // mobs walking in, animals from world generation) and just means none of that kind spawns naturally.
            boolean full = count >= cap;
            int color = full ? Glass.WARN : HudStyle.PRIMARY;
            lines.add(new TextHudWidget.Line(LABELS[i] + " " + count + " / " + cap + (full ? "  full" : ""), color));
         }
      }

      // The caps shrink with fewer spawnable chunks (a render distance below 8), so say so when that is happening.
      if (this.chunks.get() || s.chunks() < CHUNK_AREA) {
         lines.add(new TextHudWidget.Line(s.chunks() + " of " + CHUNK_AREA + " chunks can spawn mobs", HudStyle.SECONDARY));
      }

      return lines;
   }
}
