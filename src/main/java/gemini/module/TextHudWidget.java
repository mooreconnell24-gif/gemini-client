package gemini.module;

import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.List;
import net.minecraft.client.gui.Font;

/**
 * A HUD card made of text rows. A row is either plain text, or a <b>label</b> (regular weight, dim), a <b>value</b>
 * (medium weight, the theme's primary text unless a threshold colours it) and an optional <b>unit</b> (faint):
 * the hierarchy is carried by weight and brightness, not by colour. Labels share one column; values are
 * right-aligned in the next so digits line up from row to row, and units follow in a third column.
 *
 * <p>Rows are kept between frames, so they hold {@link HudStyle#PRIMARY}, {@link HudStyle#SECONDARY} and
 * {@link HudStyle#TERTIARY} rather than theme colours; the painter resolves them, so a theme change restyles a row
 * that was built before it.
 *
 * <p>Widgets hand back the same list until their text changes, so it is measured once per change; a frame only
 * draws from the measured columns.
 */
public abstract class TextHudWidget extends HudWidget {
   private List<TextHudWidget.Line> cached;
   private int measured = -1;
   /** {@link UiFont#generation()} at the last measurement: the font or GUI scale changing makes widths stale. */
   private int measuredGeneration = -1;
   /** Column widths of the cached rows: labels, values, units, and the widest plain row. */
   private int labelW;
   private int valueW;
   private int unitW;
   private int plainW;
   private int[] valueWidths = new int[8];
   private boolean anyLabel;
   /** Weight of a row's value; labels, units and plain lines are regular. */
   private static final FontWeight VALUE = FontWeight.MEDIUM;

   protected TextHudWidget(String name, String description, String defaultCorner, int defaultX, int defaultY) {
      super(name, description, defaultCorner, defaultX, defaultY);
   }

   protected abstract List<TextHudWidget.Line> lines(HudContext var1);

   /**
    * Whether values are right-aligned in their column (numbers line up). Widgets whose values are words or several
    * numbers in a row left-align them instead.
    */
   protected boolean alignValuesRight() {
      return true;
   }

   private void measureLines(Font font, List<TextHudWidget.Line> lines) {
      this.labelW = 0;
      this.valueW = 0;
      this.unitW = 0;
      this.plainW = 0;
      this.anyLabel = false;
      int n = lines == null ? 0 : lines.size();
      if (this.valueWidths.length < n) {
         this.valueWidths = new int[Math.max(n, this.valueWidths.length * 2)];
      }

      for (int i = 0; i < n; i++) {
         TextHudWidget.Line line = lines.get(i);
         int vw = line.isRow() ? UiFont.width(line.text(), FontSize.BODY, VALUE) : UiFont.width(line.text());
         this.valueWidths[i] = vw;
         if (line.isRow()) {
            this.anyLabel |= !line.label().isEmpty();
            this.labelW = Math.max(this.labelW, UiFont.width(line.label()));
            this.valueW = Math.max(this.valueW, vw);
            if (!line.unit().isEmpty()) {
               this.unitW = Math.max(this.unitW, HudStyle.UNIT_GAP + UiFont.width(line.unit()));
            }
         } else {
            this.plainW = Math.max(this.plainW, vw);
         }
      }
   }

   private int rowsWidth() {
      int w = this.valueW + this.unitW;
      return this.anyLabel ? this.labelW + HudStyle.LABEL_GAP + w : w;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      List<TextHudWidget.Line> lines = this.lines(hud);
      int generation = UiFont.generation();
      if (lines != this.cached || this.measured < 0 || generation != this.measuredGeneration) {
         this.measuredGeneration = generation;
         this.cached = lines;
         this.measureLines(hud.font, lines);
         this.measured = Math.max(this.plainW, this.rowsWidth());
      }

      if (this.cached != null && !this.cached.isEmpty()) {
         return this.measured + 2 * HudStyle.PAD_X;
      } else {
         return 0;
      }
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.cached != null && !this.cached.isEmpty() ? (this.cached.size() - 1) * HudStyle.LINE + HudStyle.TEXT_H + 2 * HudStyle.PAD_Y : 0;
   }

   @Override
   protected void paint(HudContext hud, int width, int height) {
      Font font = hud.font;
      int backdrop = this.backdrop();
      int labelColor = HudStyle.label(backdrop);
      int unitColor = HudStyle.unit(backdrop);
      int x = HudStyle.PAD_X;
      int valueX = this.anyLabel ? x + this.labelW + HudStyle.LABEL_GAP : x;
      boolean right = this.alignValuesRight();
      int y = HudStyle.PAD_Y;
      List<TextHudWidget.Line> lines = this.cached;
      for (int i = 0; i < lines.size(); i++) {
         TextHudWidget.Line line = lines.get(i);
         int vw = this.valueWidths[i];
         if (!line.isRow()) {
            UiFont.draw(hud.graphics, line.text(), x, y, HudStyle.themed(line.color(), backdrop), hud.shadow);
         } else {
            if (!line.label().isEmpty()) {
               UiFont.draw(hud.graphics, line.label(), x, y, labelColor, hud.shadow);
            }

            int vx = right ? valueX + this.valueW - vw : valueX;
            UiFont.draw(hud.graphics, line.text(), vx, y, HudStyle.themed(line.color(), backdrop), FontSize.BODY, VALUE, hud.shadow);
            if (!line.unit().isEmpty()) {
               // Units follow their own value when values are left-aligned, and form a column otherwise.
               int ux = right ? valueX + this.valueW + HudStyle.UNIT_GAP : vx + vw + HudStyle.UNIT_GAP;
               UiFont.draw(hud.graphics, line.unit(), ux, y, unitColor, hud.shadow);
            }
         }

         y += HudStyle.LINE;
      }
   }

   /**
    * One row. {@code text} is the value (or the whole line for a plain row), {@code color} its colour (an ARGB colour,
    * or one of the {@link HudStyle} text levels so it follows the theme); a row made
    * with {@link #row} also has a dim {@code label} before it and a faint {@code unit} after it.
    */
   public record Line(String text, int color, String label, String unit) {
      /** A plain line of text in one colour. */
      public Line(String text, int color) {
         this(text, color, null, null);
      }

      public static TextHudWidget.Line of(String text) {
         return new TextHudWidget.Line(text, HudStyle.PRIMARY);
      }

      /** A label / value / unit row; {@code label} and {@code unit} may be empty. */
      public static TextHudWidget.Line row(String label, String value, String unit, int color) {
         return new TextHudWidget.Line(value, color, label == null ? "" : label, unit == null ? "" : unit);
      }

      /** A value in the theme's primary text colour with a label and unit. */
      public static TextHudWidget.Line row(String label, String value, String unit) {
         return row(label, value, unit, HudStyle.PRIMARY);
      }

      /** Whether this is a label / value / unit row rather than a plain line. */
      public boolean isRow() {
         return this.label != null;
      }
   }
}
