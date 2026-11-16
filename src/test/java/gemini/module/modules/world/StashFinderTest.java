package gemini.module.modules.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Stash Finder through the API it already exposes: the saved stash list and each stash's totals and summary.
 * Cluster scoring and the per-chunk dedup are private instance logic that needs a scanned world, and the file
 * belongs to another lane, so they are not reached from here.
 */
class StashFinderTest {
   private static StashFinder.Stash stash(int x, int z, int... counts) {
      return new StashFinder.Stash("seed", "minecraft:overworld", x, 64, z, x - 3, 60, z - 3, x + 3, 70, z + 3, counts, 21.5, "2026-11-16 12:00:00");
   }

   @Test
   void totalsAndSummaryNameOnlyPresentTypes() {
      StashFinder.Stash found = stash(100, 200, 4, 0, 2, 3, 0, 1);
      assertEquals(10, found.total());
      assertEquals("chest=4 barrel=2 shulker=3 dispenser=1", found.summary());
      assertEquals("", stash(0, 0, 0, 0, 0, 0, 0, 0).summary());
   }

   @Test
   void savedStashesSurviveARoundTrip() {
      JsonObject saved = new JsonObject();
      JsonArray list = new JsonArray();
      list.add(entry(1000, -2000, "[5,1,0,2,0,0]"));
      list.add(entry(-48, 96, "[0,0,9,0,4,0]"));
      saved.add("stashes", list);

      StashFinder finder = new StashFinder();
      finder.readExtra(saved);
      JsonObject written = new JsonObject();
      finder.writeExtra(written);

      JsonArray out = written.getAsJsonArray("stashes");
      assertEquals(2, out.size());
      JsonObject first = out.get(0).getAsJsonObject();
      assertEquals(1000, first.get("x").getAsInt());
      assertEquals(-2000, first.get("z").getAsInt());
      assertEquals("minecraft:overworld", first.get("dimension").getAsString());
      assertEquals(JsonParser.parseString("[5,1,0,2,0,0]"), first.get("counts"));
      assertEquals(17.0, first.get("score").getAsDouble());
      assertEquals(-48, out.get(1).getAsJsonObject().get("x").getAsInt());
   }

   @Test
   void damagedEntryIsSkippedAndShortCountsArePadded() {
      JsonObject saved = new JsonObject();
      JsonArray list = new JsonArray();
      JsonObject broken = entry(0, 0, "[1]");
      broken.remove("world");
      list.add(broken);
      list.add(entry(16, 16, "[2,3]"));
      saved.add("stashes", list);

      StashFinder finder = new StashFinder();
      finder.readExtra(saved);
      JsonObject written = new JsonObject();
      finder.writeExtra(written);

      JsonArray out = written.getAsJsonArray("stashes");
      assertEquals(1, out.size());
      assertEquals(JsonParser.parseString("[2,3,0,0,0,0]"), out.get(0).getAsJsonObject().get("counts"));
   }

   @Test
   void readingReplacesWhatWasLoadedBefore() {
      JsonObject first = new JsonObject();
      JsonArray list = new JsonArray();
      list.add(entry(1, 1, "[1,0,0,0,0,0]"));
      list.add(entry(2, 2, "[1,0,0,0,0,0]"));
      first.add("stashes", list);
      StashFinder finder = new StashFinder();
      finder.readExtra(first);
      finder.readExtra(new JsonObject());
      JsonObject written = new JsonObject();
      finder.writeExtra(written);
      assertEquals(0, written.getAsJsonArray("stashes").size());
   }

   private static JsonObject entry(int x, int z, String counts) {
      JsonObject entry = new JsonObject();
      entry.addProperty("world", "seed");
      entry.addProperty("dimension", "minecraft:overworld");
      entry.addProperty("x", x);
      entry.addProperty("y", 64);
      entry.addProperty("z", z);
      entry.addProperty("minX", x - 2);
      entry.addProperty("minY", 60);
      entry.addProperty("minZ", z - 2);
      entry.addProperty("maxX", x + 2);
      entry.addProperty("maxY", 68);
      entry.addProperty("maxZ", z + 2);
      entry.add("counts", JsonParser.parseString(counts));
      entry.addProperty("score", 17.0);
      entry.addProperty("found", "2026-11-16 12:00:00");
      return entry;
   }
}
