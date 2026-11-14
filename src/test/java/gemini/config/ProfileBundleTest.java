package gemini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ProfileBundleTest {
   private static JsonObject modules() {
      JsonObject flight = new JsonObject();
      flight.addProperty("id", "flight");
      flight.addProperty("enabled", true);
      flight.addProperty("keybind", 10);
      JsonObject settings = new JsonObject();
      settings.addProperty("Speed", 1.5);
      flight.add("settings", settings);
      JsonObject waypoints = new JsonObject();
      waypoints.addProperty("enabled", true);
      waypoints.add("settings", new JsonObject());
      waypoints.add("waypoints", JsonFiles.GSON.fromJson("[{\"name\":\"base\",\"x\":120,\"y\":64,\"z\":-340}]", com.google.gson.JsonElement.class));
      JsonObject home = new JsonObject();
      home.addProperty("homeX", 12.5);
      home.addProperty("deathX", 99);
      JsonObject all = new JsonObject();
      all.add("Flight", flight);
      all.add("Waypoints", waypoints);
      all.add("Home", home);
      all.addProperty("junk", "ignored");
      return all;
   }

   @Test
   void exportCarriesSettingsButNothingTiedToAWorld() {
      JsonObject bundle = ProfileBundle.wrap("pvp", "3.2.0", "2026-11-11T10:00:00Z", modules());
      assertEquals(ProfileBundle.FORMAT, bundle.get("format").getAsString());
      assertEquals("pvp", bundle.get("name").getAsString());
      JsonObject exported = bundle.getAsJsonObject("modules");
      assertEquals(1.5, exported.getAsJsonObject("Flight").getAsJsonObject("settings").get("Speed").getAsDouble());
      assertTrue(exported.getAsJsonObject("Flight").get("enabled").getAsBoolean());
      assertFalse(exported.getAsJsonObject("Waypoints").has("waypoints"), "pins are personal and belong to one world");
      assertFalse(exported.getAsJsonObject("Home").has("homeX"));
      assertFalse(exported.getAsJsonObject("Home").has("deathX"));
      assertFalse(exported.has("junk"));
      assertFalse(bundle.toString().contains("-340"), "no coordinate may leak into an export");
      assertFalse(bundle.has("panels"));
      assertFalse(bundle.has("sandboxMode"));
   }

   @Test
   void exportDoesNotAliasTheLiveConfig() {
      JsonObject live = modules();
      JsonObject bundle = ProfileBundle.wrap("pvp", "3.2.0", "now", live);
      bundle.getAsJsonObject("modules").getAsJsonObject("Flight").getAsJsonObject("settings").addProperty("Speed", 9.0);
      assertEquals(1.5, live.getAsJsonObject("Flight").getAsJsonObject("settings").get("Speed").getAsDouble());
   }

   @Test
   void importRoundTripsAnExport() throws ProfileBundle.FormatException {
      JsonObject profile = ProfileBundle.unwrap(ProfileBundle.wrap("pvp", "3.2.0", "now", modules()));
      assertTrue(ProfileBundle.isPortable(profile));
      assertEquals(ConfigMigrations.CURRENT_VERSION, ConfigMigrations.versionOf(profile));
      assertEquals(3, ProfileBundle.moduleCount(profile));
      assertEquals(10, profile.getAsJsonObject("modules").getAsJsonObject("Flight").get("keybind").getAsInt());
   }

   @Test
   void importAcceptsAPlainProfileAndUpgradesIt() throws ProfileBundle.FormatException {
      JsonObject old = new JsonObject();
      old.addProperty("version", 7);
      JsonObject reverse = new JsonObject();
      reverse.addProperty("enabled", true);
      JsonObject all = new JsonObject();
      all.add("ReverseStep", reverse);
      old.add("modules", all);
      old.addProperty("sandboxMode", false);
      JsonObject profile = ProfileBundle.unwrap(old);
      assertTrue(profile.getAsJsonObject("modules").has("Fast Fall"));
      assertFalse(profile.has("sandboxMode"), "an imported file can never flip the sandbox switch");
   }

   @Test
   void importRejectsWhatItCannotLoad() {
      JsonObject wrongFormat = new JsonObject();
      wrongFormat.addProperty("format", "someone-elses-client");
      wrongFormat.add("modules", new JsonObject());
      assertThrows(ProfileBundle.FormatException.class, () -> ProfileBundle.unwrap(wrongFormat));

      JsonObject newerFormat = ProfileBundle.wrap("x", "9.0.0", "now", new JsonObject());
      newerFormat.addProperty("formatVersion", ProfileBundle.FORMAT_VERSION + 1);
      assertThrows(ProfileBundle.FormatException.class, () -> ProfileBundle.unwrap(newerFormat));

      JsonObject newerConfig = ProfileBundle.wrap("x", "9.0.0", "now", new JsonObject());
      newerConfig.addProperty("version", ConfigMigrations.CURRENT_VERSION + 1);
      assertThrows(ProfileBundle.FormatException.class, () -> ProfileBundle.unwrap(newerConfig));

      assertThrows(ProfileBundle.FormatException.class, () -> ProfileBundle.unwrap(new JsonObject()));
      assertThrows(ProfileBundle.FormatException.class, () -> ProfileBundle.unwrap(null));
   }
}
