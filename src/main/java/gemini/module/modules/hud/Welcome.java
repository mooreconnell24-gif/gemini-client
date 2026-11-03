package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.ListSetting;
import gemini.ui.HudContext;
import java.util.List;

public final class Welcome extends Ticked {
   private final ListSetting message = this.addSetting(
      new ListSetting("Message", "Greeting text; {player} becomes your name", ListSetting.Kind.TEXT, "Welcome to Gemini, {player}")
   );

   public Welcome() {
      super("Welcome", "A greeting with your name.", "Top Center", 0, 28, 1000);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      String text = this.message.isEmpty() ? "Welcome, {player}" : this.message.get().getFirst();
      return List.of(new TextHudWidget.Line(text.replace("{player}", hud.client.player.getName().getString()), hud.accent));
   }
}
