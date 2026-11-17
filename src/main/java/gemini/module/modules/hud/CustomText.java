package gemini.module.modules.hud;

import gemini.ui.HudStyle;
import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.ui.HudContext;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Map;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class CustomText extends Ticked {
   private final ListSetting entries = this.addSetting(
      new ListSetting(
         "Lines",
         "Lines of text; {fps} {x} {y} {z} {chunk} {speed} {facing} {biome} {health} {food} {saturation} {xp} {slots} {memory} {time} are replaced with live values",
         ListSetting.Kind.TEXT,
         "Gemini {version}",
         "{fps} FPS  ·  {x} {y} {z}"
      )
   );
   private final BooleanSetting accentColor = this.addSetting(new BooleanSetting("Accent Color", "Colour the text with the theme accent", false));

   public CustomText() {
      super("Custom Text", "Shows your own lines of text, with placeholders for live values.", "Left Center", 6, 0, 200);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      if (this.entries.isEmpty()) {
         return List.of();
      } else {
         LocalPlayer player = hud.client.player;
         Map<String, String> values = new HashMap<>();
         values.put("{version}", gemini.Gemini.VERSION);
         values.put("{fps}", Integer.toString(hud.client.getFps()));
         values.put("{x}", Integer.toString(Mth.floor(player.getX())));
         values.put("{y}", Integer.toString(Mth.floor(player.getY())));
         values.put("{z}", Integer.toString(Mth.floor(player.getZ())));
         values.put("{player}", player.getName().getString());
         values.put("{health}", String.format(Locale.ROOT, "%.1f", player.getHealth()));
         values.put("{food}", Integer.toString(player.getFoodData().getFoodLevel()));
         values.put("{saturation}", String.format(Locale.ROOT, "%.1f", player.getFoodData().getSaturationLevel()));
         values.put("{xp}", Integer.toString(player.experienceLevel));
         values.put("{slots}", Integer.toString(freeSlots(player)));
         values.put("{chunk}", (Mth.floor(player.getX()) >> 4) + " " + (Mth.floor(player.getZ()) >> 4));
         values.put("{memory}", Long.toString((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576L));
         values.put("{facing}", HudHelpers.pretty(Direction.fromYRot(player.getYRot()).getName()));
         values.put("{speed}", String.format(Locale.ROOT, "%.1f", Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) * 20.0));
         values.put("{time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
         values.put(
            "{biome}", hud.client.level.getBiome(player.blockPosition()).unwrapKey().map(key -> HudHelpers.pretty(key.identifier().getPath())).orElse("?")
         );
         ServerData server = hud.client.getCurrentServer();
         values.put("{server}", server == null ? "Single-player" : server.ip);
         values.put("{online}", hud.client.getConnection() == null ? "0" : Integer.toString(hud.client.getConnection().getOnlinePlayers().size()));
         PlayerInfo info = hud.client.getConnection() == null ? null : hud.client.getConnection().getPlayerInfo(player.getUUID());
         // Ping is only real on a remote connection; in single player it is always zero, so it stays empty.
         values.put("{ping}", info == null || hud.client.isLocalServer() ? "" : Integer.toString(info.getLatency()));
         List<TextHudWidget.Line> lines = new ArrayList<>();

         for (String entry : this.entries.get()) {
            String text = entry;

            for (Entry<String, String> value : values.entrySet()) {
               text = text.replace(value.getKey(), value.getValue());
            }

            lines.add(new TextHudWidget.Line(text, this.accentColor.get() ? hud.accent : HudStyle.PRIMARY));
         }

         return lines;
      }
   }

   /** Empty slots in the main inventory and hotbar. */
   private static int freeSlots(LocalPlayer player) {
      int free = 0;
      for (int i = 0; i < 36; i++) {
         if (player.getInventory().getItem(i).isEmpty()) {
            free++;
         }
      }

      return free;
   }
}
