package gemini.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BackupNamesTest {
   private static final LocalDateTime T = LocalDateTime.of(2026, 11, 17, 14, 3, 22);

   @Test
   void fileNamesRoundTripTheirTime() {
      assertEquals("2026-11-17_14-03-22.zip", BackupNames.fileName(T, 1));
      assertEquals("2026-11-17_14-03-22-2.zip", BackupNames.fileName(T, 2));
      assertEquals(T, BackupNames.time("2026-11-17_14-03-22.zip"));
      assertEquals(T, BackupNames.time("2026-11-17_14-03-22-2.zip"));
      assertNull(BackupNames.time("my world.zip"));
      assertNull(BackupNames.time("2026-13-40_99-99-99.zip"), "an impossible date is not a backup time");
      assertFalse(BackupNames.isBackup("2026-11-17_14-03-22.zip.part"));
   }

   @Test
   void rotationKeepsTheNewestAndNeverTouchesOtherFiles() {
      List<String> names = List.of(
         "2026-11-15_10-00-00.zip", "keep me.zip", "2026-11-17_09-00-00.zip", "2026-11-16_10-00-00.zip", "2026-11-17_09-00-00-2.zip", "notes.txt"
      );
      assertEquals(List.of("2026-11-16_10-00-00.zip", "2026-11-15_10-00-00.zip"), BackupNames.toDelete(names, 2));
      assertEquals(List.of(), BackupNames.toDelete(names, 4));
      assertEquals(List.of(), BackupNames.toDelete(names, 10));
      assertEquals(3, BackupNames.toDelete(names, 0).size(), "at least one backup is always kept");
      assertEquals("2026-11-17_09-00-00-2.zip", BackupNames.newestFirst(names).get(0), "same second: the later suffix is newer");
      assertEquals(4, BackupNames.newestFirst(names).size());
   }

   @Test
   void restoredFoldersAreSafeAndNeverReuseAName() {
      Set<String> taken = Set.of("Base (backup 2026-11-17 14-03)", "Base (backup 2026-11-17 14-03) 2");
      assertEquals("Base (backup 2026-11-17 14-03) 3", BackupNames.restoredFolder("Base", T, taken::contains));
      assertEquals("Other (backup 2026-11-17 14-03)", BackupNames.restoredFolder("Other", T, taken::contains));
      assertEquals("World (backup restored)", BackupNames.restoredFolder("..", null, name -> false));
      assertEquals("Base (backup 2026-11-17 14:03)", BackupNames.restoredDisplayName("Base", T));
   }

   @Test
   void safeFolderRemovesSeparatorsAndDots() {
      assertEquals("a_b_c", BackupNames.safeFolder("a/b\\c"));
      assertEquals("World", BackupNames.safeFolder(".."));
      assertEquals("World", BackupNames.safeFolder("   "));
      assertEquals("World", BackupNames.safeFolder(null));
      assertEquals("_x", BackupNames.safeFolder("../x"));
      assertEquals("C_ drive", BackupNames.safeFolder("C: drive"));
      assertEquals(60, BackupNames.safeFolder("w".repeat(200)).length());
   }

   @Test
   void sizesUseADecimalPointEverywhere() {
      Locale before = Locale.getDefault();
      try {
         Locale.setDefault(Locale.GERMANY);
         assertEquals("512 B", BackupNames.size(512));
         assertEquals("2 KB", BackupNames.size(2048));
         assertEquals("12.5 MB", BackupNames.size(13_107_200L));
         assertEquals("1.50 GB", BackupNames.size(1_610_612_736L));
      } finally {
         Locale.setDefault(before);
      }

      assertTrue(BackupNames.shownTime("2026-11-17_14-03-22.zip").equals("2026-11-17 14:03"));
   }
}
