package gemini.module.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingCodecTest {
   @Test
   void everyKindSurvivesASaveAndLoad() {
      BooleanSetting bool = new BooleanSetting("Flag", "", false);
      bool.set(true);
      NumberSetting number = new NumberSetting("Speed", "", 0.55, 0.2, 5.0, 0.1);
      number.set(2.3);
      ModeSetting mode = new ModeSetting("Mode", "", "A", "A", "B", "C");
      mode.set("C");
      ListSetting list = new ListSetting("Blocks", "", ListSetting.Kind.BLOCK, "stone", "minecraft:dirt");
      ColorSetting color = new ColorSetting("Color", "", 120);
      color.set(300);

      for (Setting<?> setting : List.<Setting<?>>of(bool, number, mode, list, color)) {
         assertNull(SettingCodec.roundTrip(setting), setting.name());
      }

      assertEquals(true, bool.get());
      assertEquals(2.3, number.get(), 1.0E-9);
      assertEquals("C", mode.get());
      assertEquals(List.of("minecraft:stone", "minecraft:dirt"), list.get());
      assertEquals(300, color.get());
   }

   @Test
   void aNumberBetweenStepsKeepsItsDefault() {
      NumberSetting number = new NumberSetting("Speed", "", 0.55, 0.2, 5.0, 0.1);
      SettingCodec.read(number, SettingCodec.write(number));
      assertEquals(0.55, number.get(), 1.0E-12);
   }

   @Test
   void anUncustomisedColourSavesAsABareHue() {
      ColorSetting color = new ColorSetting("Color", "", 42);
      JsonElement saved = SettingCodec.write(color);
      assertTrue(saved.isJsonPrimitive());
      assertEquals(42, saved.getAsInt());
   }

   @Test
   void theColourObjectFormRoundTripsEveryComponent() {
      ColorSetting color = new ColorSetting("Color", "", 200);
      assertNull(SettingCodec.colorObjectRoundTrip(color));
      // The check puts the original back.
      assertTrue(color.isDefault());

      color.setSaturation(0.25F);
      color.setBrightness(0.8F);
      color.setAlpha(0.4F);
      color.setSync(ColorSetting.RAINBOW);
      JsonElement saved = SettingCodec.write(color);
      ColorSetting loaded = new ColorSetting("Color", "", 200);
      SettingCodec.read(loaded, saved);
      assertEquals(0.25F, loaded.saturation());
      assertEquals(0.8F, loaded.brightness());
      assertEquals(0.4F, loaded.alpha());
      assertEquals(ColorSetting.RAINBOW, loaded.sync());
      assertEquals(saved, SettingCodec.write(loaded));
   }

   @Test
   void anOldHueOnlyConfigStillLoadsIntoAColour() {
      ColorSetting color = new ColorSetting("Color", "", 10);
      SettingCodec.read(color, JsonParser.parseString("77"));
      assertEquals(77, color.get());
      assertTrue(color.alpha() == 1.0F && ColorSetting.STATIC.equals(color.sync()));
   }

   @Test
   void aValueOfTheWrongShapeIsRejected() {
      NumberSetting number = new NumberSetting("Speed", "", 1.0, 0.0, 5.0, 0.5);
      assertThrows(RuntimeException.class, () -> SettingCodec.read(number, JsonParser.parseString("[1,2]")));
      assertEquals(1.0, number.get(), 1.0E-12);
   }

   @Test
   void actionButtonsAreNotSaved() {
      assertNull(SettingCodec.write(new ActionSetting("Go", "", () -> {})));
      assertNull(SettingCodec.roundTrip(new ActionSetting("Go", "", () -> {})));
   }
}
