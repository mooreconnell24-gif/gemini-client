package gemini.module.modules.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.module.modules.bot.BuildSchematic.Material;
import gemini.module.modules.bot.BuildSchematic.Placement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildSchematicTest {
   private static void assertNoDuplicates(List<Placement> list) {
      Set<String> seen = new HashSet<>();
      for (Placement placement : list) {
         assertTrue(seen.add(placement.right() + "," + placement.up() + "," + placement.forward()), "duplicate " + placement);
      }
   }

   @Test
   void portalWithCornersIsFourteenObsidianAndSupported() {
      List<Placement> portal = BuildSchematic.netherPortal(true);
      assertEquals(14, BuildSchematic.materials(portal).get(Material.OBSIDIAN));
      assertEquals(6, portal.stream().filter(p -> p.material() == Material.AIR).count());
      assertNoDuplicates(portal);
      assertTrue(BuildSchematic.supportedInOrder(portal, true));
   }

   @Test
   void portalWithoutCornersIsTenObsidianAndNeedsAirPlace() {
      List<Placement> portal = BuildSchematic.netherPortal(false);
      assertEquals(10, BuildSchematic.materials(portal).get(Material.OBSIDIAN));
      assertNoDuplicates(portal);
      // The side columns touch nothing placed before them: only air placement can build this.
      assertFalse(BuildSchematic.supportedInOrder(portal, true));
   }

   @Test
   void portalAirIsClearedFirstAndIgnitionBaseIsInTheFrame() {
      List<Placement> portal = BuildSchematic.netherPortal(true);
      for (int i = 0; i < 6; i++) {
         assertEquals(Material.AIR, portal.get(i).material());
      }

      assertTrue(portal.contains(BuildSchematic.portalIgnitionBase()));
   }

   @Test
   void witherPlacesSkullsLastWithTheMiddleOneFinal() {
      List<Placement> wither = BuildSchematic.wither();
      assertEquals(4, BuildSchematic.materials(wither).get(Material.SOUL_SAND));
      assertEquals(3, BuildSchematic.materials(wither).get(Material.WITHER_SKULL));
      int firstSkull = -1;
      for (int i = 0; i < wither.size(); i++) {
         if (wither.get(i).material() == Material.WITHER_SKULL && firstSkull < 0) {
            firstSkull = i;
         }

         if (firstSkull >= 0) {
            assertEquals(Material.WITHER_SKULL, wither.get(i).material(), "nothing but skulls after the first skull");
         }
      }

      assertEquals(new Placement(0, 2, 0, Material.WITHER_SKULL), wither.get(wither.size() - 1));
      assertTrue(BuildSchematic.supportedInOrder(wither, true));
      assertNoDuplicates(wither);
   }

   @Test
   void wallIsLengthTimesHeightBuiltBottomUp() {
      List<Placement> wall = BuildSchematic.wall(5, 3);
      assertEquals(15, wall.size());
      assertNoDuplicates(wall);
      for (int i = 1; i < wall.size(); i++) {
         assertTrue(wall.get(i).up() >= wall.get(i - 1).up());
      }

      assertTrue(BuildSchematic.supportedInOrder(wall, true));
   }

   @Test
   void platformGrowsOutwardSoEveryBlockTouchesAnEarlierOne() {
      for (int size = 1; size <= 9; size++) {
         List<Placement> platform = BuildSchematic.platform(size);
         assertEquals(size * size, platform.size());
         assertNoDuplicates(platform);
         // No ground assumed: a platform over a void must still be placeable block by block.
         assertTrue(BuildSchematic.supportedInOrder(platform, false), "size " + size);
      }

      assertEquals(new Placement(0, 0, 0, Material.FILL), BuildSchematic.platform(5).get(0));
   }

   @Test
   void pillarAndBridgeAreChains() {
      List<Placement> pillar = BuildSchematic.pillar(6);
      assertEquals(6, pillar.size());
      assertTrue(BuildSchematic.supportedInOrder(pillar, false));
      List<Placement> bridge = BuildSchematic.bridge(7);
      assertEquals(7, bridge.size());
      assertTrue(BuildSchematic.supportedInOrder(bridge, false));
   }

   @Test
   void sizesBelowOneStillBuildOneBlock() {
      assertEquals(1, BuildSchematic.wall(0, 0).size());
      assertEquals(1, BuildSchematic.platform(0).size());
      assertEquals(1, BuildSchematic.pillar(-3).size());
   }
}
