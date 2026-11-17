package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudContext;
import gemini.util.AttributeBoost;
import gemini.util.InfoText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * What you did this session: blocks mined and placed, mobs killed, deaths, damage dealt and taken, items crafted
 * and experience gained. The numbers are the game's own statistics, read from the integrated server once a second,
 * with the first reading as the zero point ({@link SessionTally}), so nothing is missed between ticks and nothing
 * is double counted.
 */
public final class SessionStats extends Ticked {
   private static final int MINED = 0;
   private static final int PLACED = 1;
   private static final int KILLS = 2;
   private static final int DEATHS = 3;
   private static final int DEALT = 4;
   private static final int TAKEN = 5;
   private static final int CRAFTED = 6;
   private static final int XP = 7;
   private static final int COUNTERS = 8;
   /** Items that place a block; built once on the server thread, then only read there. */
   private static Item[] blockItems;
   private final BooleanSetting mined = this.addSetting(new BooleanSetting("Blocks Mined", "Show blocks you broke this session", true));
   private final BooleanSetting placed = this.addSetting(new BooleanSetting("Blocks Placed", "Show blocks you placed this session", true));
   private final BooleanSetting kills = this.addSetting(new BooleanSetting("Mob Kills", "Show mobs you killed this session", true));
   private final BooleanSetting deaths = this.addSetting(new BooleanSetting("Deaths", "Show how often you died this session", false));
   private final BooleanSetting damage = this.addSetting(new BooleanSetting("Damage", "Show hearts of damage you dealt and took this session", false));
   private final BooleanSetting crafted = this.addSetting(new BooleanSetting("Crafted", "Show items you crafted this session", false));
   private final BooleanSetting xp = this.addSetting(new BooleanSetting("Experience", "Show experience points you collected this session", true));
   private final BooleanSetting rates = this.addSetting(new BooleanSetting("Per Hour", "Add the hourly rate after each count (after the first minute)", false));
   private final SessionTally tally = new SessionTally(COUNTERS);
   private final InfoText infoText = new InfoText();
   private volatile long[] reading;
   private int poll;

   public SessionStats() {
      super("Session Stats", "Shows blocks mined and placed, kills, deaths, damage, crafts and experience gained this session.", "Bottom Right", 6, 130, 500);
      this.addSetting(new ActionSetting("Reset", "Start counting from zero again", this::restart));
   }

   private void restart() {
      this.tally.reset(System.currentTimeMillis());
      this.reading = null;
      this.poll = 0;
   }

   @Override
   public void onEnable() {
      this.restart();
   }

   @Override
   public void onDisable() {
      this.reading = null;
   }

   /**
    * A dimension change keeps the session (the statistics belong to the save, not the dimension); only a reading
    * still in flight is dropped. Leaving the world switches the module off, and joining another starts afresh.
    */
   @Override
   public void onWorldChange() {
      this.reading = null;
   }

   @Override
   public void onTick() {
      long[] values = this.reading;
      if (values != null) {
         this.reading = null;
         this.tally.update(values);
      }

      if (this.poll-- <= 0) {
         this.poll = 20;
         AttributeBoost.onServer(this::read);
      }
   }

   /** Runs on the server thread. */
   private void read(ServerPlayer player) {
      ServerStatsCounter stats = player.getStats();
      Item[] blocks = blockItems;
      if (blocks == null) {
         List<Item> list = new ArrayList<>();
         for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem) {
               list.add(item);
            }
         }

         blocks = list.toArray(new Item[0]);
         blockItems = blocks;
      }

      long[] values = new long[COUNTERS];
      for (Block block : BuiltInRegistries.BLOCK) {
         values[MINED] += stats.getValue(Stats.BLOCK_MINED, block);
      }

      for (Item item : blocks) {
         values[PLACED] += stats.getValue(Stats.ITEM_USED, item);
      }

      for (Item item : BuiltInRegistries.ITEM) {
         values[CRAFTED] += stats.getValue(Stats.ITEM_CRAFTED, item);
      }

      values[KILLS] = stats.getValue(Stats.CUSTOM, Stats.MOB_KILLS);
      values[DEATHS] = stats.getValue(Stats.CUSTOM, Stats.DEATHS);
      values[DEALT] = stats.getValue(Stats.CUSTOM, Stats.DAMAGE_DEALT);
      values[TAKEN] = stats.getValue(Stats.CUSTOM, Stats.DAMAGE_TAKEN);
      values[XP] = player.totalExperience;
      this.reading = values;
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      if (!this.tally.primed()) {
         return List.of();
      }

      long now = System.currentTimeMillis();
      List<TextHudWidget.Line> lines = new ArrayList<>(6);
      this.add(lines, this.mined.get(), "Mined", MINED, now);
      this.add(lines, this.placed.get(), "Placed", PLACED, now);
      this.add(lines, this.kills.get(), "Kills", KILLS, now);
      this.add(lines, this.deaths.get(), "Deaths", DEATHS, now);
      if (this.damage.get() && (this.tally.gained(DEALT) > 0L || this.tally.gained(TAKEN) > 0L)) {
         // Both damage statistics count tenths of a health point; two points make a heart.
         lines.add(TextHudWidget.Line.row("Dealt", InfoText.format(this.tally.gained(DEALT) / 20.0, 1), "hearts"));
         lines.add(TextHudWidget.Line.row("Taken", InfoText.format(this.tally.gained(TAKEN) / 20.0, 1), "hearts"));
      }

      this.add(lines, this.crafted.get(), "Crafted", CRAFTED, now);
      this.add(lines, this.xp.get(), "XP", XP, now);
      return lines;
   }

   private void add(List<TextHudWidget.Line> lines, boolean shown, String label, int index, long now) {
      // A counter still at zero says nothing yet; its line appears with the first block, kill or point.
      if (!shown || this.tally.gained(index) <= 0L) {
         return;
      }

      // Label, the count right-aligned so the digits line up, and the hourly rate as a faint unit.
      String rate = "";
      if (this.rates.get()) {
         double perHour = this.tally.perHour(index, now);
         if (!Double.isNaN(perHour)) {
            rate = HudFormat.grouped(Math.round(perHour)) + "/h";
         }
      }

      lines.add(TextHudWidget.Line.row(label, HudFormat.grouped(this.tally.gained(index)), rate));
   }

   @Override
   public String info() {
      return this.tally.primed() ? this.infoText.whole(this.tally.gained(MINED), " mined") : null;
   }
}
