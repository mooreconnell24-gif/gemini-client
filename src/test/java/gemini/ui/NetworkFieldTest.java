package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkFieldTest {
   @Test
   void wrapKeepsValuesInTheUnitInterval() {
      assertEquals(0.25F, NetworkField.wrap(0.25F), 1e-6F);
      assertEquals(0.1F, NetworkField.wrap(1.1F), 1e-5F);
      assertEquals(0.9F, NetworkField.wrap(-0.1F), 1e-5F);
      assertEquals(0.0F, NetworkField.wrap(1.0F), 1e-6F);
      assertEquals(0.0F, NetworkField.wrap(-1.0E-9F), 1e-6F, "a value that rounds to 1 wraps to 0");
      for (float value = -3.0F; value < 3.0F; value += 0.013F) {
         float wrapped = NetworkField.wrap(value);
         assertTrue(wrapped >= 0.0F && wrapped < 1.0F, value + " -> " + wrapped);
      }
   }

   @Test
   void wrappingHappensOutOfSightAndOutOfReach() {
      int[][] sizes = {{1280, 720}, {854, 480}, {1920, 1080}, {640, 360}, {720, 1280}};
      for (int[] size : sizes) {
         float w = size[0];
         float h = size[1];
         float margin = NetworkField.margin(w, h);
         float unit = Math.min(w, h);
         // The point wraps at the domain edges; there it is still outside the screen after the largest parallax
         // shift, and further from any on-screen point than the longest link.
         float shift = Math.abs(NetworkField.parallax(1.0F, 1.0F, unit));
         NetworkField field = new NetworkField(1L);
         field.resize(size[0], size[1]);
         float reach = 0.0F;
         for (int l = 0; l < NetworkField.LAYERS; l++) {
            reach = Math.max(reach, field.reach(l));
         }

         float leftEdge = NetworkField.toScreen(0.0F, w, margin) + shift;
         float rightEdge = NetworkField.toScreen(1.0F, w, margin) - shift;
         assertTrue(leftEdge + reach < 0.0F, "left wrap point out of reach at " + w + "x" + h);
         assertTrue(rightEdge - reach > w, "right wrap point out of reach at " + w + "x" + h);
         float top = NetworkField.toScreen(0.0F, h, margin) + shift;
         float bottom = NetworkField.toScreen(1.0F, h, margin) - shift;
         assertTrue(top + reach < 0.0F && bottom - reach > h, "vertical wrap out of reach at " + w + "x" + h);
      }
   }

   @Test
   void driftStaysInsideTheDomain() {
      NetworkField field = new NetworkField(7L);
      field.resize(854, 480);
      float margin = NetworkField.margin(854, 480);
      float shift = Math.abs(NetworkField.parallax(1.0F, 1.0F, 480));
      for (int frame = 0; frame < 20_000; frame++) {
         field.update(0.05F, 1.0F, -1.0F);
      }

      for (int i = 0; i < field.count(); i++) {
         assertTrue(field.x(i) >= -margin - shift && field.x(i) <= 854 + margin + shift, "x of " + i);
         assertTrue(field.y(i) >= -margin - shift && field.y(i) <= 480 + margin + shift, "y of " + i);
      }
   }

   @Test
   void zeroDeltaFreezesTheField() {
      NetworkField field = new NetworkField(3L);
      field.update(0.0F, 0.0F, 0.0F);
      float x = field.x(5);
      float y = field.y(5);
      for (int frame = 0; frame < 100; frame++) {
         field.update(0.0F, 0.0F, 0.0F);
      }

      assertEquals(x, field.x(5), 0.0F);
      assertEquals(y, field.y(5), 0.0F);
   }

   @Test
   void layersAreOrderedFarToNearAndSplitByShare() {
      int[] starts = NetworkField.layerStarts(100, new int[NetworkField.LAYERS + 1]);
      assertEquals(0, starts[0]);
      assertEquals(50, starts[1]);
      assertEquals(83, starts[2]);
      assertEquals(100, starts[3]);
      for (int count = 3; count <= NetworkField.MAX_POINTS; count++) {
         int[] s = NetworkField.layerStarts(count, new int[NetworkField.LAYERS + 1]);
         for (int l = 0; l < NetworkField.LAYERS; l++) {
            assertTrue(s[l + 1] > s[l], "every layer has a point when there are " + count);
         }

         assertEquals(count, s[NetworkField.LAYERS]);
      }

      NetworkField field = new NetworkField(1L);
      for (int i = 0; i < field.count(); i++) {
         int l = field.layer(i);
         assertTrue(i >= field.layerStart(l) && i < field.layerEnd(l));
      }
   }

   @Test
   void fartherLayersAreSlowerAndMoveLessWithTheMouse() {
      for (int l = 1; l < NetworkField.LAYERS; l++) {
         assertTrue(NetworkField.depth(l) > NetworkField.depth(l - 1));
         assertTrue(Math.abs(NetworkField.parallax(1.0F, NetworkField.depth(l), 720)) > Math.abs(NetworkField.parallax(1.0F, NetworkField.depth(l - 1), 720)));
      }

      assertEquals(0.0F, NetworkField.parallax(0.0F, 1.0F, 720), 0.0F, "no mouse offset, no shift");
      assertTrue(NetworkField.parallax(1.0F, 1.0F, 720) < 0.0F, "layers move against the mouse");
   }

   @Test
   void farLayersDriftSlower() {
      NetworkField field = new NetworkField(11L);
      field.resize(1280, 720);
      float[] moved = new float[NetworkField.LAYERS];
      int[] counted = new int[NetworkField.LAYERS];
      float[] x0 = new float[field.count()];
      float[] y0 = new float[field.count()];
      field.update(0.0F, 0.0F, 0.0F);
      for (int i = 0; i < field.count(); i++) {
         x0[i] = field.x(i);
         y0[i] = field.y(i);
      }

      field.update(0.5F, 0.0F, 0.0F);
      for (int i = 0; i < field.count(); i++) {
         float dx = field.x(i) - x0[i];
         float dy = field.y(i) - y0[i];
         float d = (float)Math.sqrt(dx * dx + dy * dy);
         if (d < 100.0F) {
            moved[field.layer(i)] += d;
            counted[field.layer(i)]++;
         }
      }

      for (int l = 1; l < NetworkField.LAYERS; l++) {
         assertTrue(moved[l] / counted[l] > moved[l - 1] / counted[l - 1], "layer " + l + " drifts faster than " + (l - 1));
      }
   }

   @Test
   void linksFadeSmoothlyToNothingAtTheReach() {
      assertEquals(1.0F, NetworkField.linkAlpha(0.0F, 100.0F), 1e-6F);
      assertEquals(0.0F, NetworkField.linkAlpha(100.0F, 100.0F), 0.0F);
      assertEquals(0.0F, NetworkField.linkAlpha(150.0F, 100.0F), 0.0F);
      assertEquals(0.0F, NetworkField.linkAlpha(10.0F, 0.0F), 0.0F);
      float previous = 1.0F;
      for (float d = 1.0F; d <= 100.0F; d += 1.0F) {
         float a = NetworkField.linkAlpha(d, 100.0F);
         assertTrue(a <= previous, "falls with distance");
         previous = a;
      }

      // No pop at the reach: the last step before it is tiny.
      assertTrue(NetworkField.linkAlpha(99.0F, 100.0F) < 0.001F);
   }

   @Test
   void calmZoneDimsPointsNearTheMenu() {
      assertEquals(0.25F, NetworkField.calm(50.0F, 50.0F, 0.0F, 0.0F, 100.0F, 100.0F, 40.0F, 0.25F), 1e-6F);
      assertEquals(1.0F, NetworkField.calm(200.0F, 50.0F, 0.0F, 0.0F, 100.0F, 100.0F, 40.0F, 0.25F), 1e-6F);
      float half = NetworkField.calm(120.0F, 50.0F, 0.0F, 0.0F, 100.0F, 100.0F, 40.0F, 0.25F);
      assertTrue(half > 0.25F && half < 1.0F);
      assertEquals(0.625F, half, 1e-5F, "smoothstep midpoint");
   }

   @Test
   void pointCountFollowsTheShapeNotTheResolution() {
      int hd = NetworkField.pointsFor(1280, 720);
      assertTrue(Math.abs(hd - NetworkField.pointsFor(854, 480)) <= hd / 30, "same picture at 854x480");
      assertTrue(Math.abs(hd - NetworkField.pointsFor(1920, 1080)) <= hd / 30, "same picture at 1920x1080");
      assertTrue(NetworkField.pointsFor(2560, 1080) > hd, "a wider window shows more of the field");
      assertTrue(hd <= NetworkField.MAX_POINTS);
      assertEquals(NetworkField.MAX_POINTS, NetworkField.pointsFor(7680, 720), "capped");
      assertTrue(NetworkField.pointsFor(1, 1) >= 12);
   }
}
