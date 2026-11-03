package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public final class EntityCounter extends Ticked {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Only count entities this close", 48.0, 8.0, 256.0, 4.0));
   private final NumberSetting entries = this.addSetting(new NumberSetting("Entries", "Maximum number of rows", 6.0, 1.0, 20.0, 1.0));
   private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Count dropped items", false));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Count other players", false));

   public EntityCounter() {
      super("Entity Counter", "How many of each kind of entity are around you.", "Right Center", 6, -80, 500);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer self = hud.client.player;
      double limit = this.radius.get() * this.radius.get();
      Map<EntityType<?>, Integer> counts = new HashMap<>();

      for (Entity entity : hud.client.level.entitiesForRendering()) {
         if (entity != self
            && !(entity.distanceToSqr(self) > limit)
            && (!(entity instanceof Player) || this.players.get())
            && (!(entity instanceof ItemEntity) || this.items.get())) {
            counts.merge(entity.getType(), 1, Integer::sum);
         }
      }

      List<Entry<EntityType<?>, Integer>> sorted = new ArrayList<>(counts.entrySet());
      sorted.sort(Entry.<EntityType<?>, Integer>comparingByValue().reversed());
      List<TextHudWidget.Line> lines = new ArrayList<>();

      for (int i = 0; i < Math.min(sorted.size(), this.entries.intValue()); i++) {
         Entry<EntityType<?>, Integer> entry = sorted.get(i);
         lines.add(new TextHudWidget.Line(entry.getKey().getDescription().getString() + " x" + entry.getValue(), -1511689));
      }

      return lines;
   }
}
