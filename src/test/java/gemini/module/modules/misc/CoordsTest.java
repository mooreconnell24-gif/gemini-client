package gemini.module.modules.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.util.Coords;
import org.junit.jupiter.api.Test;

/** The shared {@link Coords} helpers and the clipboard text Coords Copier builds from them. */
class CoordsTest {
   @Test
   void dimensionNames() {
      assertEquals("Overworld", Coords.dimensionName(Coords.OVERWORLD));
      assertEquals("Nether", Coords.dimensionName(Coords.NETHER));
      assertEquals("End", Coords.dimensionName(Coords.END));
      assertEquals("deep_dark_dim", Coords.dimensionName("somemod:deep_dark_dim"));
   }

   @Test
   void netherLinkScales() {
      assertEquals(0.125, Coords.scale(Coords.OVERWORLD, Coords.NETHER));
      assertEquals(8.0, Coords.scale(Coords.NETHER, Coords.OVERWORLD));
      assertEquals(1.0, Coords.scale(Coords.END, Coords.END));
      assertTrue(Double.isNaN(Coords.scale(Coords.END, Coords.OVERWORLD)));
   }

   @Test
   void distanceAcrossTheLink() {
      assertEquals(5.0, Coords.distance(Coords.OVERWORLD, 0.0, 0.0, Coords.OVERWORLD, 3.0, 4.0), 1e-9);
      // A bed at Nether 100, 0 is Overworld 800, 0.
      assertEquals(800.0, Coords.distance(Coords.OVERWORLD, 0.0, 0.0, Coords.NETHER, 100.0, 0.0), 1e-9);
      // Overworld 800 seen from the Nether is 100.
      assertEquals(100.0, Coords.distance(Coords.NETHER, 0.0, 0.0, Coords.OVERWORLD, 800.0, 0.0), 1e-9);
      assertTrue(Double.isNaN(Coords.distance(Coords.END, 0.0, 0.0, Coords.OVERWORLD, 1.0, 1.0)));
   }

   @Test
   void headingsFollowMinecraftAxes() {
      assertEquals("S", Coords.heading(0.0, 10.0));
      assertEquals("N", Coords.heading(0.0, -10.0));
      assertEquals("E", Coords.heading(10.0, 0.0));
      assertEquals("W", Coords.heading(-10.0, 0.0));
      assertEquals("NE", Coords.heading(5.0, -5.0));
      assertEquals("SW", Coords.heading(-5.0, 5.0));
      assertEquals("-", Coords.heading(0.0, 0.0));
   }

   @Test
   void formats() {
      assertEquals("10 64 -3", Coords.format("Plain", Coords.OVERWORLD, 10.7, 64.0, -2.2, false));
      assertEquals("10.70, 64.00, -2.20", Coords.format("Comma", Coords.OVERWORLD, 10.7, 64.0, -2.2, true));
      assertEquals("/tp @s 10 64 -3", Coords.format("Teleport", Coords.OVERWORLD, 10.7, 64.0, -2.2, false));
      assertEquals("/execute in minecraft:the_nether run tp @s 1 70 2", Coords.format("Execute", Coords.NETHER, 1.5, 70.0, 2.5, false));
   }

   @Test
   void copierText() {
      assertEquals("16 64 -32", CoordsCopier.text("Plain", false, Coords.OVERWORLD, null, false, false, 16.4, 64.0, -31.5));
      assertEquals(
         "16 64 -32 (Overworld) [plains] | Nether: 2 64 -4",
         CoordsCopier.text("Plain", false, Coords.OVERWORLD, "minecraft:plains", true, true, 16.4, 64.0, -31.5)
      );
      assertEquals("8 40 8 | Overworld: 64 40 64", CoordsCopier.text("Plain", false, Coords.NETHER, null, false, true, 8.0, 40.0, 8.0));
      // The execute format already names the dimension; the End has no linked coordinates.
      assertEquals(
         "/execute in minecraft:the_end run tp @s 0 60 0",
         CoordsCopier.text("Execute", false, Coords.END, null, true, true, 0.2, 60.0, 0.9)
      );
   }
}
