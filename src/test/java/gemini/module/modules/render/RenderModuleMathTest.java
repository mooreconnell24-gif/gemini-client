package gemini.module.modules.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pure parts of the render modules: curves, label text and the label cache. */
class RenderModuleMathTest {
   private static final float EPS = 1.0E-5F;

   @Test
   void breakEspStagesFillTheBoxInTenths() {
      assertEquals(0.1F, BreakEsp.stageProgress(0), EPS);
      assertEquals(0.5F, BreakEsp.stageProgress(4), EPS);
      assertEquals(1.0F, BreakEsp.stageProgress(9), EPS, "the last crack stage is a full block");
      assertEquals(1.0F, BreakEsp.stageProgress(12), EPS, "clamped above");
      assertEquals(0.0F, BreakEsp.stageProgress(-3), EPS, "clamped below");
   }

   @Test
   void breakEspBoxGrowsOrShrinks() {
      assertEquals(0.1F, BreakEsp.boxSize(0.0F, false), EPS);
      assertEquals(1.0F, BreakEsp.boxSize(1.0F, false), EPS);
      assertEquals(1.0F, BreakEsp.boxSize(0.0F, true), EPS);
      assertEquals(0.1F, BreakEsp.boxSize(1.0F, true), EPS);
      assertEquals(0.55F, BreakEsp.boxSize(0.5F, false), EPS);
      assertEquals(BreakEsp.boxSize(1.0F, false), BreakEsp.boxSize(3.0F, false), EPS, "progress past 1 is clamped");
   }

   @Test
   void breakEspFadeIsLinearAndIgnoresBackwardsTime() {
      float half = BreakEsp.faded(1.0F, 0.225F);
      assertEquals(0.5F, half, 1.0E-4F, "half of the 0.45 s fade");
      assertEquals(1.0F, BreakEsp.faded(1.0F, -1.0F), EPS, "a negative frame time does not brighten the entry");
      assertTrue(BreakEsp.faded(0.2F, 0.45F) < 0.0F, "a full fade time takes any entry below zero");
   }

   @Test
   void popChamsFadeEasesOutAndEnds() {
      assertEquals(1.0F, PopChams.fade(0.0F, 2.0F), EPS);
      assertEquals(0.25F, PopChams.fade(1.0F, 2.0F), EPS, "half way through, (1 - 0.5)^2");
      assertEquals(0.0F, PopChams.fade(2.0F, 2.0F), EPS);
      assertEquals(0.0F, PopChams.fade(5.0F, 2.0F), EPS);
      assertEquals(0.0F, PopChams.fade(0.5F, 0.0F), EPS, "a zero life never divides by zero");
      float previous = 1.0F;
      for (float age = 0.1F; age < 2.0F; age += 0.1F) {
         float now = PopChams.fade(age, 2.0F);
         assertTrue(now < previous, "fades monotonically");
         previous = now;
      }
   }

   @Test
   void popChamsGrowsLinearly() {
      assertEquals(1.0F, PopChams.scale(0.0F, 0.15F), EPS);
      assertEquals(1.3F, PopChams.scale(2.0F, 0.15F), EPS);
      assertEquals(1.0F, PopChams.scale(-1.0F, 0.15F), EPS, "never shrinks below its spawn size");
   }

   @Test
   void totemCountsReadNaturally() {
      assertEquals("1 totem", TotemPopCounter.amount(1, "totem"));
      assertEquals("3 totems", TotemPopCounter.amount(3, "totem"));
      assertEquals("0 pops", TotemPopCounter.amount(0, "pop"));
   }

   @Test
   void zoomEasingIsFrameRateIndependent() {
      float oneStep = ZoomModule.ease(1.0F, 4.0F, 0.05F);
      float twoSteps = ZoomModule.ease(ZoomModule.ease(1.0F, 4.0F, 0.025F), 4.0F, 0.025F);
      assertEquals(oneStep, twoSteps, 1.0E-4F, "two half frames land where one whole frame does");
      assertTrue(oneStep > 1.0F && oneStep < 4.0F);
      assertEquals(1.0F, ZoomModule.ease(1.0F, 4.0F, 0.0F), EPS, "no time, no movement");
      float zoom = 4.0F;
      for (int frame = 0; frame < 120; frame++) {
         zoom = ZoomModule.ease(zoom, 1.0F, 1.0F / 60.0F);
      }

      assertEquals(1.0F, zoom, EPS, "easing out reaches exactly 1 within two seconds, so the camera can stop zooming");
   }

   @Test
   void soundLocatorImportantOnlyHidesEverythingElse() {
      assertEquals("Container", SoundLocator.classifySound("block.chest.open", true));
      assertEquals("Explosion", SoundLocator.classifySound("entity.generic.explode", true));
      assertEquals("Teleport", SoundLocator.classifySound("entity.enderman.teleport", true));
      assertEquals("Totem Pop", SoundLocator.classifySound("item.totem.use", true));
      assertNull(SoundLocator.classifySound("entity.generic.drink", true), "drinking is not important");
      assertNull(SoundLocator.classifySound("entity.player.burp", true));
      assertNull(SoundLocator.classifySound("block.stone.step", true));
      assertEquals("Consumable", SoundLocator.classifySound("entity.generic.drink", false));
      assertEquals("Movement", SoundLocator.classifySound("block.stone.step", false));
      assertNull(SoundLocator.classifySound("ambient.cave", false));
   }

   @Test
   void labelCacheRebuildsOnlyWhenWhatItShowsChanges() {
      LabelCache cache = new LabelCache();
      Object stack = new Object();
      cache.beginFrame();
      assertNull(cache.get(7, stack, 3L));
      String text = cache.put(7, stack, 3L, "Dirt x3");
      assertSame(text, cache.get(7, stack, 3L), "same source and signature: the same string, nothing built");
      assertNull(cache.get(7, stack, 4L), "the count changed");
      assertNull(cache.get(7, new Object(), 3L), "another stack");
      assertNull(cache.get(8, stack, 3L), "another entity");
   }

   @Test
   void labelCacheDropsEntitiesNotAskedForAnyMore() {
      LabelCache cache = new LabelCache();
      Object kept = new Object();
      Object gone = new Object();
      cache.beginFrame();
      cache.put(1, kept, 0L, "kept");
      cache.put(2, gone, 0L, "gone");
      for (int frame = 0; frame < LabelCache.STALE_FRAMES * 2; frame++) {
         cache.beginFrame();
         assertEquals("kept", cache.get(1, kept, 0L));
      }

      assertEquals(1, cache.size(), "the entity that stopped being drawn was swept");
      assertNull(cache.get(2, gone, 0L));
   }
}
