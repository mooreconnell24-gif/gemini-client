package com.inferno.fabric.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class LiquidBounceCoverageTest {
   /** category \t module, extracted from LiquidBounce's ModuleManager.kt (nextgen branch). */
   private static List<String[]> modules() throws IOException {
      List<String[]> out = new ArrayList<>();

      try (InputStream in = LiquidBounceCoverageTest.class.getResourceAsStream("/liquidbounce-modules.tsv")) {
         assertNotNull(in);

         for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
               out.add(line.trim().split("\t"));
            }
         }
      }

      return out;
   }

   private static Set<String> registeredIds() {
      Set<String> ids = new HashSet<>();

      for (Check c : new CheckEngine().all()) {
         assertTrue(ids.add(c.id()), "duplicate check id " + c.id());
      }

      return ids;
   }

   @Test
   void everyLiquidBounceModuleIsListed() throws IOException {
      List<String[]> modules = modules();
      assertEquals(228, modules.size());
      Set<String> seen = new HashSet<>();

      for (String[] m : modules) {
         LiquidBounceCoverage.Entry e = LiquidBounceCoverage.find(m[1]);
         assertNotNull(e, "no coverage entry for LiquidBounce module " + m[1]);
         assertEquals(m[0], e.category(), "category of " + m[1]);
         seen.add(e.module());
      }

      assertEquals(modules.size(), LiquidBounceCoverage.all().size(), "coverage lists modules LiquidBounce doesn't have");
      assertEquals(seen.size(), LiquidBounceCoverage.all().size());
   }

   @Test
   void entriesAreConsistent() {
      Set<String> ids = registeredIds();

      for (LiquidBounceCoverage.Entry e : LiquidBounceCoverage.all()) {
         switch (e.status()) {
            case DETECTED -> assertFalse(e.checks().isEmpty(), e.module());
            case PARTIAL -> {
               assertFalse(e.checks().isEmpty(), e.module());
               assertFalse(e.note().isBlank(), e.module() + " needs a note");
            }
            case CLIENT_ONLY, NOT_COVERED -> {
               assertTrue(e.checks().isEmpty(), e.module());
               assertFalse(e.note().isBlank(), e.module() + " needs a reason");
            }
         }

         for (String id : e.checks()) {
            assertTrue(ids.contains(id), e.module() + " refers to unknown check " + id);
         }
      }
   }

   @Test
   void everyNewCheckCatchesSomeModule() {
      Set<String> used = new HashSet<>(LiquidBounceCoverage.CLIENT_WIDE_CHECKS);

      for (LiquidBounceCoverage.Entry e : LiquidBounceCoverage.all()) {
         used.addAll(e.checks());
      }

      for (Check c : new CheckEngine().all()) {
         String pkg = c.getClass().getName();
         if (pkg.contains(".Lb")) {
            assertTrue(used.contains(c.id()), c.id() + " is not mapped to any LiquidBounce module");
         }
      }
   }

   static String markdown() {
      Map<LiquidBounceCoverage.Status, Integer> counts = new LinkedHashMap<>();

      for (LiquidBounceCoverage.Status s : LiquidBounceCoverage.Status.values()) {
         counts.put(s, 0);
      }

      for (LiquidBounceCoverage.Entry e : LiquidBounceCoverage.all()) {
         counts.merge(e.status(), 1, Integer::sum);
      }

      Map<String, String> names = new LinkedHashMap<>();

      for (Check c : new CheckEngine().all()) {
         names.put(c.id(), c.displayName());
      }

      StringBuilder b = new StringBuilder();
      b.append("# LiquidBounce module coverage\n\n");
      b.append("Generated from `LiquidBounceCoverage.java` by `LiquidBounceCoverageTest` (run `./gradlew test -DupdateDocs=true` to refresh).\n\n");
      b.append("Every module in LiquidBounce's `ModuleManager` (nextgen branch, ").append(LiquidBounceCoverage.all().size()).append(" modules), and the Inferno checks that catch it.\n\n");
      b.append("| Status | Modules |\n|---|---|\n");
      b.append("| Detected | ").append(counts.get(LiquidBounceCoverage.Status.DETECTED)).append(" |\n");
      b.append("| Partly detected | ").append(counts.get(LiquidBounceCoverage.Status.PARTIAL)).append(" |\n");
      b.append("| Client-only (no server can see it) | ").append(counts.get(LiquidBounceCoverage.Status.CLIENT_ONLY)).append(" |\n");
      b.append("| Not covered | ").append(counts.get(LiquidBounceCoverage.Status.NOT_COVERED)).append(" |\n\n");
      b.append("A server only sees the packets a client sends. Render modules (ESP, FullBright, Zoom...) and client-side helpers don't change them, so no server-side anti-cheat can detect them.\n\n");
      b.append("Checks on the client as a whole: ");
      List<String> wide = new ArrayList<>();

      for (String id : LiquidBounceCoverage.CLIENT_WIDE_CHECKS) {
         wide.add(names.get(id) + " (`" + id + "`)");
      }

      b.append(String.join(", ", wide)).append(" looks for the LiquidBounce brand and cheat-client mod channels when a player joins.\n");
      Set<String> categories = new TreeSet<>();

      for (LiquidBounceCoverage.Entry e : LiquidBounceCoverage.all()) {
         categories.add(e.category());
      }

      for (String category : categories) {
         b.append("\n## ").append(Character.toUpperCase(category.charAt(0))).append(category.substring(1)).append("\n\n");
         b.append("| Module | Status | Inferno checks | Notes |\n|---|---|---|---|\n");
         List<LiquidBounceCoverage.Entry> rows = new ArrayList<>();

         for (LiquidBounceCoverage.Entry e : LiquidBounceCoverage.all()) {
            if (e.category().equals(category)) {
               rows.add(e);
            }
         }

         rows.sort((x, y) -> x.module().compareToIgnoreCase(y.module()));

         for (LiquidBounceCoverage.Entry e : rows) {
            List<String> checks = new ArrayList<>();

            for (String id : e.checks()) {
               checks.add(names.get(id) + " (`" + id + "`)");
            }

            String status = switch (e.status()) {
               case DETECTED -> "Detected";
               case PARTIAL -> "Partly";
               case CLIENT_ONLY -> "Client-only";
               case NOT_COVERED -> "Not covered";
            };
            b.append("| ").append(e.module()).append(" | ").append(status).append(" | ").append(String.join(", ", checks)).append(" | ").append(e.note()).append(" |\n");
         }
      }

      return b.toString();
   }

   @Test
   void docsAreUpToDate() throws IOException {
      Path doc = Path.of("docs/LIQUIDBOUNCE_COVERAGE.md");
      String md = markdown();
      if (Boolean.getBoolean("updateDocs")) {
         Files.writeString(doc, md);
      }

      assertTrue(Files.exists(doc), "run ./gradlew test -DupdateDocs=true");
      assertEquals(md, Files.readString(doc), "docs/LIQUIDBOUNCE_COVERAGE.md is stale: run ./gradlew test -DupdateDocs=true");
   }
}
