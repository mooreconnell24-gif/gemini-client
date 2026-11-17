package gemini.ui.mods;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Turns on a downloaded resource pack straight away, the way the vanilla Resource Packs screen does: rescan the pack
 * folder, select the pack on top, store the choice in options.txt and reload resources. Works in a world and on the
 * title screen. Client thread only.
 */
final class PackApplier {
   private PackApplier() {
   }

   /** The repository id of the pack in {@code zip}, or null when Minecraft did not pick it up. */
   static String find(Path zip) {
      PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
      repository.reload();
      String name = zip.getFileName().toString();
      for (Pack pack : repository.getAvailablePacks()) {
         String id = pack.getId();
         if (id.equals(name) || id.endsWith("/" + name)) {
            return id;
         }
      }

      return null;
   }

   /** Whether the pack says it was made for this game version (vanilla warns but still allows others). */
   static boolean compatible(String id) {
      Pack pack = Minecraft.getInstance().getResourcePackRepository().getPack(id);
      return pack != null && pack.getCompatibility().isCompatible();
   }

   /** Selects and applies the pack; returns an error for the user, or null when the reload started. */
   static String apply(Path zip) {
      Minecraft minecraft = Minecraft.getInstance();
      String id = find(zip);
      if (id == null) {
         return "Minecraft did not recognise " + zip.getFileName() + " as a resource pack";
      }

      PackRepository repository = minecraft.getResourcePackRepository();
      if (!repository.getSelectedIds().contains(id) && !repository.addPack(id)) {
         return "Could not turn on " + zip.getFileName();
      }

      // saves options.txt and starts Minecraft.reloadResourcePacks() when the selection changed
      minecraft.options.updateResourcePacks(repository);
      return null;
   }
}
