package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.module.setting.ModeSetting;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThemePaletteTest {
   @Test
   void contrastMatchesTheWcagDefinition() {
      assertEquals(21.0, Theme.contrast(0xFF000000, 0xFFFFFFFF), 1.0E-6);
      assertEquals(1.0, Theme.contrast(0xFF336699, 0xFF336699), 1.0E-9);
      assertEquals(Theme.contrast(0xFF123456, 0xFFABCDEF), Theme.contrast(0xFFABCDEF, 0xFF123456), 1.0E-9);
      // the textbook value for mid grey #777 on white
      assertEquals(4.48, Theme.contrast(0xFF777777, 0xFFFFFFFF), 0.01);
      assertEquals(0.0, Theme.luminance(0xFF000000), 1.0E-9);
      assertEquals(1.0, Theme.luminance(0x00FFFFFF), 1.0E-9, "alpha is ignored");
   }

   @Test
   void everyPresetIsReadable() {
      assertTrue(Theme.PRESETS.size() >= 8, "about eight themes");
      for (Theme.Palette p : Theme.PRESETS) {
         for (int level = 0; level <= 3; level++) {
            int surface = p.surface(level);
            assertEquals(0xFF, surface >>> 24, p.name() + " surfaces are opaque");
            String where = p.name() + " on surface " + level;
            assertTrue(Theme.contrast(p.text, surface) >= 4.5, "text " + where + ": " + Theme.contrast(p.text, surface));
            assertTrue(Theme.contrast(p.textDim, surface) >= 4.5, "dim text " + where + ": " + Theme.contrast(p.textDim, surface));
            assertTrue(Theme.contrast(p.textFaint, surface) >= 3.0, "faint text " + where + ": " + Theme.contrast(p.textFaint, surface));
            assertTrue(Theme.contrast(p.accent, surface) >= 3.0, "accent " + where + ": " + Theme.contrast(p.accent, surface));
            assertTrue(Theme.contrast(p.accent2, surface) >= 3.0, "second accent " + where + ": " + Theme.contrast(p.accent2, surface));
            for (int status : new int[] {p.ok, p.warn, p.error, p.info}) {
               assertTrue(Theme.contrast(status, surface) >= 3.0, "status colour " + where);
            }
         }

         // the hierarchy reads in the right order
         assertTrue(Theme.luminance(p.text) > Theme.luminance(p.textDim), p.name());
         assertTrue(Theme.luminance(p.textDim) > Theme.luminance(p.textFaint), p.name());
         // surfaces rise with their level, and stay dark
         for (int level = 1; level <= 3; level++) {
            assertTrue(Theme.luminance(p.surface(level)) > Theme.luminance(p.surface(level - 1)), p.name() + " level " + level);
         }

         assertTrue(Theme.luminance(p.surface(3)) < 0.05, p.name() + " is a dark theme");
         assertEquals(p.surface(0), p.surface(-4));
         assertEquals(p.surface(3), p.surface(9));
         // text on an accent-filled control is readable too
         assertTrue(Theme.contrast(Theme.onColor(p.accent), p.accent) >= 4.5, p.name() + " label on accent");
      }
   }

   @Test
   void presetNamesAreDistinctAndListedBeforeCustom() {
      String[] names = Theme.themeNames();
      assertEquals(Theme.PRESETS.size() + 1, names.length);
      assertEquals(Theme.CUSTOM, names[names.length - 1]);
      assertEquals(Theme.DEFAULT, names[0]);
      Set<String> seen = new HashSet<>();
      for (String name : names) {
         assertTrue(seen.add(name.toLowerCase()), name);
      }

      assertNotNull(Theme.preset(Theme.DEFAULT));
      assertEquals("Ember", Theme.preset("ember").name());
      assertNull(Theme.preset(Theme.CUSTOM));
      assertNull(Theme.preset("Nope"));
      assertNull(Theme.preset(null));
   }

   @Test
   void savedThemeNamesFromOlderVersionsStillResolve() {
      // names that still exist keep their meaning
      for (String kept : new String[] {"Aurora", "Ocean", "Ember", "Violet", "Mono", "Midnight", "Sunset", "Mint", "Rose", "Custom"}) {
         assertEquals(kept, Theme.canonicalTheme(kept));
      }

      assertEquals("Ocean", Theme.canonicalTheme(" ocean "));
      assertEquals("Custom", Theme.canonicalTheme("CUSTOM"));
      // retired names map to a theme that exists
      assertEquals("Gemini", Theme.canonicalTheme("Blue"));
      assertEquals("Violet", Theme.canonicalTheme("Purple"));
      assertEquals("Violet", Theme.canonicalTheme("Twin"));
      assertEquals("Violet", Theme.canonicalTheme("Dracula"));
      assertEquals("Ember", Theme.canonicalTheme("Gold"));
      assertEquals("Ocean", Theme.canonicalTheme("Nord"));
      assertEquals("Rose", Theme.canonicalTheme("Sakura"));
      for (String retired : Theme.retiredThemeNames()) {
         assertNull(Theme.preset(retired), retired + " is really retired");
         assertNotNull(Theme.preset(Theme.canonicalTheme(retired)), retired);
      }

      assertEquals(Theme.DEFAULT, Theme.canonicalTheme(null));
      assertEquals(Theme.DEFAULT, Theme.canonicalTheme("something else"));
   }

   /** The Theme setting is built like this in the ClickGUI module: an old config value must select the new theme. */
   @Test
   void aModeSettingAcceptsRetiredValuesThroughAliases() {
      ModeSetting setting = new ModeSetting("Theme", "test", Theme.DEFAULT, Theme.themeNames());
      for (String retired : Theme.retiredThemeNames()) {
         setting.alias(retired, Theme.canonicalTheme(retired));
      }

      setting.set("Mint");
      assertEquals("Mint", setting.get());
      setting.set("Sakura");
      assertEquals("Rose", setting.get());
      setting.set("nord");
      assertEquals("Ocean", setting.get());
      setting.set("Blue");
      assertEquals("Gemini", setting.get());
      setting.set("Unknown theme");
      assertEquals("Gemini", setting.get(), "unknown values are still ignored");
      setting.set(null);
      assertEquals("Gemini", setting.get());
      assertThrows(IllegalArgumentException.class, () -> setting.alias("Old", "Not a mode"));
   }

   @Test
   void mixBlendsEveryChannel() {
      assertEquals(0xFF000000, Theme.mix(0xFF000000, 0xFFFFFFFF, 0.0F));
      assertEquals(0xFFFFFFFF, Theme.mix(0xFF000000, 0xFFFFFFFF, 1.0F));
      assertEquals(0xFF808080, Theme.mix(0xFF000000, 0xFFFFFFFF, 0.5F));
      assertEquals(0x80FF0000, Theme.mix(0x00FF0000, 0xFFFF0000, 0.5F));
      assertEquals(0xFFFFFFFF, Theme.mix(0xFF000000, 0xFFFFFFFF, 7.0F), "t is clamped");
      assertEquals(0xFF000000, Theme.mix(0xFF000000, 0xFFFFFFFF, -1.0F));
   }

   @Test
   void aCrossfadeStartsAtTheOldThemeAndEndsAtTheNewOne() {
      Theme.Palette from = Theme.preset("Gemini");
      Theme.Palette to = Theme.preset("Ember");
      Theme.Palette live = new Theme.Palette("live");
      live.blend(from, to, 0.0F);
      assertEquals(from.accent, live.accent);
      assertEquals(from.surface(2), live.surface(2));
      assertEquals(from.textDim, live.textDim);
      live.blend(from, to, 1.0F);
      assertEquals(to.accent, live.accent);
      assertEquals(to.accent2, live.accent2);
      assertEquals(to.surface(0), live.surface(0));
      assertEquals(to.text, live.text);
      assertEquals("Ember", live.name());
      live.blend(from, to, 0.5F);
      assertEquals(Theme.mix(from.accent, to.accent, 0.5F), live.accent);
      assertEquals(Theme.mix(from.surface(3), to.surface(3), 0.5F), live.surface(3));

      assertEquals(0.0F, Theme.fadeProgress(0L, false));
      assertEquals(0.5F, Theme.fadeProgress(Theme.FADE_MS / 2, false), 1.0E-4F);
      assertEquals(1.0F, Theme.fadeProgress(Theme.FADE_MS, false));
      assertEquals(1.0F, Theme.fadeProgress(Theme.FADE_MS * 10, false));
      assertEquals(1.0F, Theme.fadeProgress(0L, true), "no fade in progress");
      assertEquals(0.0F, Theme.fadeProgress(-5L, false));
      float previous = 0.0F;
      for (long ms = 0; ms <= Theme.FADE_MS; ms += 10) {
         float t = Theme.fadeProgress(ms, false);
         assertTrue(t >= previous, "monotonic");
         previous = t;
      }

      assertTrue(Theme.FADE_MS >= 120L && Theme.FADE_MS <= 400L, "a short crossfade");
   }

   @Test
   void aCustomPaletteIsDerivedFromTwoAccentsAndASurfaceRange() {
      Theme.Palette custom = new Theme.Palette(Theme.CUSTOM);
      custom.derive(0x00FF8800, 0xFF00CCFF, 0xFF101010, 0xFF202020);
      assertEquals(0xFFFF8800, custom.accent, "accents are made opaque");
      assertEquals(0xFF00CCFF, custom.accent2);
      assertEquals(0xFF101010, custom.surface(0));
      assertEquals(0xFF202020, custom.surface(3));
      assertTrue(Theme.contrast(custom.text, custom.surface(3)) >= 4.5);
      assertTrue(Theme.contrast(custom.textDim, custom.surface(3)) >= 4.5);
   }

   @Test
   void withoutARunningClientTheLivePaletteIsTheDefaultTheme() {
      Theme.Palette live = Theme.current();
      Theme.Palette preset = Theme.preset(Theme.DEFAULT);
      assertEquals(preset.accent, live.accent);
      assertEquals(preset.surface(1), live.surface(1));
      assertEquals(preset.accent, Glass.accent());
      assertEquals(preset.accent2, Glass.accent2());
      assertEquals(preset.surface(2), Glass.surface(2));
      assertEquals(preset.text, Glass.text());
      assertEquals(preset.textDim, Glass.textDim());
      assertEquals(preset.textFaint, Glass.textFaint());
   }
}
