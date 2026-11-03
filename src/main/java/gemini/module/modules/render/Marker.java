package gemini.module.modules.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.render.Render3D;
import gemini.render.ShapeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class Marker extends Module {
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Marker color", 280));
   private final BooleanSetting labels = this.addSetting(new BooleanSetting("Labels", "Number each marker", true));
   private final ShapeSettings shape = new ShapeSettings(this, "Both", true);
   private final List<AABB> boxes = new ArrayList<>();

   public Marker() {
      super("Marker", "Draw boxes you place yourself. Use .marker add / box.", Category.RENDER);
      this.addSetting(new ActionSetting("Mark Here", "Add a marker on your block", this::markHere));
      this.addSetting(new ActionSetting("Clear", "Remove every marker", this::clear));
   }

   private void markHere() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         BlockPos pos = player.blockPosition();
         this.boxes.add(new AABB(pos));
         CommandManager.feedback("§bMarker §7added #" + this.boxes.size() + " at §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
         if (!this.isSavedEnabled()) {
            this.setEnabled(true);
         } else {
            this.save();
         }
      }
   }

   private void clear() {
      this.boxes.clear();
      CommandManager.feedback("§bMarker §7cleared");
      this.save();
   }

   @Override
   public boolean onCommand(String[] args) {
      if (args.length == 0) {
         return false;
      } else {
         String var2 = args[0].toLowerCase(Locale.ROOT);
         switch (var2) {
            case "add":
               if (args.length >= 4) {
                  Integer x = parse(args[1]);
                  Integer y = parse(args[2]);
                  Integer z = parse(args[3]);
                  if (x == null || y == null || z == null) {
                     return false;
                  }

                  this.boxes.add(new AABB(new BlockPos(x, y, z)));
                  CommandManager.feedback("§bMarker §7added #" + this.boxes.size());
                  this.save();
               } else {
                  this.markHere();
               }
               break;
            case "box":
               if (args.length < 7) {
                  return false;
               }

               double[] v = new double[6];

               for (int i = 0; i < 6; i++) {
                  try {
                     v[i] = Double.parseDouble(args[i + 1]);
                  } catch (NumberFormatException var9) {
                     return false;
                  }
               }

               this.boxes.add(new AABB(v[0], v[1], v[2], v[3] + 1.0, v[4] + 1.0, v[5] + 1.0));
               CommandManager.feedback("§bMarker §7added #" + this.boxes.size());
               this.save();
               break;
            case "remove":
               Integer index = args.length > 1 ? parse(args[1]) : null;
               if (index == null || index < 1 || index > this.boxes.size()) {
                  CommandManager.feedback("§cNo marker with that number. Use §f.marker list");
                  return true;
               }

               this.boxes.remove(index - 1);
               this.save();
               CommandManager.feedback("§bMarker §7removed #" + index);
               break;
            case "clear":
               this.clear();
               break;
            case "list":
               if (this.boxes.isEmpty()) {
                  CommandManager.feedback("§7No markers yet.");
               }

               for (int i = 0; i < this.boxes.size(); i++) {
                  AABB box = this.boxes.get(i);
                  CommandManager.feedback(
                     "§7#"
                        + (i + 1)
                        + " §f"
                        + (int)box.minX
                        + " "
                        + (int)box.minY
                        + " "
                        + (int)box.minZ
                        + " §7to §f"
                        + (int)(box.maxX - 1.0)
                        + " "
                        + (int)(box.maxY - 1.0)
                        + " "
                        + (int)(box.maxZ - 1.0)
                  );
               }
               break;
            default:
               return false;
         }

         return true;
      }
   }

   private static Integer parse(String text) {
      try {
         return Integer.parseInt(text);
      } catch (NumberFormatException var2) {
         return null;
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      int argb = this.color.argb();

      for (int i = 0; i < this.boxes.size(); i++) {
         AABB box = this.boxes.get(i);
         this.shape.box(box, argb);
         if (this.labels.get()) {
            Render3D.text("#" + (i + 1), box.getCenter().add(0.0, box.getYsize() / 2.0 + 0.4, 0.0), argb, 0.4F, true);
         }
      }
   }

   @Override
   public void writeExtra(JsonObject object) {
      JsonArray array = new JsonArray();

      for (AABB box : this.boxes) {
         JsonArray entry = new JsonArray();
         entry.add(box.minX);
         entry.add(box.minY);
         entry.add(box.minZ);
         entry.add(box.maxX);
         entry.add(box.maxY);
         entry.add(box.maxZ);
         array.add(entry);
      }

      object.add("markers", array);
   }

   @Override
   public void readExtra(JsonObject object) {
      this.boxes.clear();
      if (object.has("markers") && object.get("markers").isJsonArray()) {
         for (JsonElement element : object.getAsJsonArray("markers")) {
            JsonArray entry = element.getAsJsonArray();
            if (entry.size() == 6) {
               this.boxes
                  .add(
                     new AABB(
                        entry.get(0).getAsDouble(),
                        entry.get(1).getAsDouble(),
                        entry.get(2).getAsDouble(),
                        entry.get(3).getAsDouble(),
                        entry.get(4).getAsDouble(),
                        entry.get(5).getAsDouble()
                     )
                  );
            }
         }
      }
   }
}
