package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import gemini.world.NotesText;
import gemini.world.WorldNotes;
import java.util.ArrayList;
import java.util.List;

/** Shows the open world's notes (by default only the open to-dos) from World Notes. Rebuilt only when they change. */
public final class NotesHud extends Ticked {
   private static final int TITLE_COLOR = -6641995;
   private final BooleanSetting todosOnly = this.addSetting(
      new BooleanSetting("Only To-dos", "Show only the open to-dos ([ ] lines) instead of every line of the notes", true).onChange(this::invalidate)
   );
   private final NumberSetting maxLines = this.addSetting(
      new NumberSetting("Lines", "How many lines to show at most", 6.0, 1.0, 20.0, 1.0).onChange(this::invalidate)
   );
   private final NumberSetting maxChars = this.addSetting(
      new NumberSetting("Line Length", "Longer lines are cut to this many characters", 40.0, 12.0, 80.0, 1.0).onChange(this::invalidate)
   );
   private int builtVersion = -1;
   private List<TextHudWidget.Line> lines = List.of();

   public NotesHud() {
      super("Pinned Notes", "Shows the to-dos or notes you wrote for this world with World Notes.", "Left Center", 6, 0, 500);
   }

   private void invalidate() {
      this.builtVersion = -1;
   }

   @Override
   public void onEnable() {
      this.invalidate();
   }

   @Override
   public void onDisable() {
      this.lines = List.of();
      this.invalidate();
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      WorldNotes.ensureLoaded();
      int version = WorldNotes.version();
      if (version != this.builtVersion) {
         this.builtVersion = version;
         List<String> text = NotesText.hudLines(WorldNotes.text(), this.todosOnly.get(), this.maxLines.intValue(), this.maxChars.intValue());
         if (text.isEmpty()) {
            this.lines = List.of();
         } else {
            List<TextHudWidget.Line> built = new ArrayList<>(text.size() + 1);
            built.add(new TextHudWidget.Line(this.todosOnly.get() ? "To-do" : "Notes", TITLE_COLOR));
            for (String line : text) {
               built.add(TextHudWidget.Line.of(line));
            }

            this.lines = List.copyOf(built);
         }
      }

      return this.lines;
   }
}
