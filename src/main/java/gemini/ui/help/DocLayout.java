package gemini.ui.help;

import gemini.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Lays out parsed {@link Markdown} blocks into positioned, already wrapped pieces for a column of a given width.
 * The screen only draws the pieces, so all the line breaking, indentation and table column maths is here and is
 * unit tested (nothing may come out wider than its column).
 */
public final class DocLayout {
   /** Heading 1 is drawn this much larger, so its wrap width is divided by it. */
   public static final float H1_SCALE = Theme.HEADING_SCALE;
   private static final int BULLET_INDENT = 10;
   private static final int CELL_GAP = 8;

   /** What a piece is, which decides how it is drawn. */
   public enum Kind {
      H1,
      H2,
      H3,
      BODY,
      BULLET_MARK,
      BULLET,
      CELL_KEY,
      CELL,
      TABLE_HEADER,
      /** A thin divider spanning {@code width}. */
      RULE,
      /** A faint stripe behind a table row, {@code width} by {@code height}. */
      STRIPE
   }

   /** A piece at (x, y) relative to the column's top-left. {@code height} is used by stripes only. */
   public record Piece(Kind kind, int x, int y, int width, int height, String text) {
   }

   public record Result(List<Piece> pieces, int height) {
   }

   private DocLayout() {
   }

   public static Result layout(List<Markdown.Block> blocks, int width, ToIntFunction<String> measure) {
      List<Piece> out = new ArrayList<>();
      int w = Math.max(40, width);
      int y = 0;
      Markdown.Block previous = null;
      for (Markdown.Block block : blocks) {
         if (block instanceof Markdown.Heading h) {
            y += previous == null ? 0 : (h.level() <= 1 ? Theme.SPACE_XL : Theme.SPACE_L + Theme.SPACE_XS);
            if (h.level() <= 1) {
               int scaledWidth = (int)(w / H1_SCALE);
               for (String line : TextWrap.wrap(h.text(), scaledWidth, measure)) {
                  out.add(new Piece(Kind.H1, 0, y, measure.applyAsInt(line), 0, line));
                  y += Math.round(Theme.LINE * H1_SCALE);
               }

               y += Theme.SPACE_XS;
            } else {
               Kind kind = h.level() == 2 ? Kind.H2 : Kind.H3;
               for (String line : TextWrap.wrap(h.text(), w, measure)) {
                  out.add(new Piece(kind, 0, y, measure.applyAsInt(line), 0, line));
                  y += Theme.LINE;
               }

               if (kind == Kind.H2) {
                  out.add(new Piece(Kind.RULE, 0, y, w, 1, ""));
                  y += Theme.SPACE_S;
               }
            }
         } else if (block instanceof Markdown.Paragraph p) {
            y += previous == null || previous instanceof Markdown.Heading ? 0 : Theme.SPACE_M;
            for (String line : TextWrap.wrap(p.text(), w, measure)) {
               out.add(new Piece(Kind.BODY, 0, y, measure.applyAsInt(line), 0, line));
               y += Theme.LINE;
            }
         } else if (block instanceof Markdown.Bullet b) {
            y += previous instanceof Markdown.Bullet ? Theme.SPACE_XS : (previous == null || previous instanceof Markdown.Heading ? 0 : Theme.SPACE_M);
            int indent = Math.min(w / 3, b.depth() * BULLET_INDENT);
            int textX = indent + BULLET_INDENT;
            out.add(new Piece(Kind.BULLET_MARK, indent + 2, y, 0, 0, b.depth() == 0 ? "•" : "◦"));
            for (String line : TextWrap.wrap(b.text(), w - textX, measure)) {
               out.add(new Piece(Kind.BULLET, textX, y, measure.applyAsInt(line), 0, line));
               y += Theme.LINE;
            }
         } else if (block instanceof Markdown.Table t) {
            y += previous == null || previous instanceof Markdown.Heading ? Theme.SPACE_XS : Theme.SPACE_M;
            y = table(t, w, measure, y, out);
         } else if (block instanceof Markdown.Rule) {
            y += Theme.SPACE_M;
            out.add(new Piece(Kind.RULE, 0, y, w, 1, ""));
            y += Theme.SPACE_M;
         }

         previous = block;
      }

      return new Result(out, y);
   }

   private static int table(Markdown.Table t, int w, ToIntFunction<String> measure, int top, List<Piece> out) {
      int columns = Math.max(1, t.columns());
      int[] colX = new int[columns];
      int[] colW = new int[columns];
      if (columns == 2) {
         // Key/value tables: the first column as wide as its longest entry, up to two fifths of the width.
         int widest = measure.applyAsInt(t.header().get(0));
         for (List<String> row : t.rows()) {
            widest = Math.max(widest, measure.applyAsInt(row.get(0)));
         }

         colW[0] = Math.min(widest, w * 2 / 5);
         colX[1] = colW[0] + CELL_GAP;
         colW[1] = w - colX[1] - Theme.SPACE_XS;
         colX[0] = Theme.SPACE_XS;
         colW[0] = Math.max(10, colW[0] - Theme.SPACE_XS);
      } else {
         int share = (w - (columns - 1) * CELL_GAP) / columns;
         for (int c = 0; c < columns; c++) {
            colX[c] = c * (share + CELL_GAP);
            colW[c] = share;
         }
      }

      int y = top;
      y = row(t.header(), colX, colW, measure, y, out, true, false, w);
      out.add(new Piece(Kind.RULE, 0, y, w, 1, ""));
      y += Theme.SPACE_XS;
      boolean stripe = false;
      for (List<String> row : t.rows()) {
         y = row(row, colX, colW, measure, y, out, false, stripe, w);
         stripe = !stripe;
      }

      return y;
   }

   private static int row(List<String> cells, int[] colX, int[] colW, ToIntFunction<String> measure, int y, List<Piece> out, boolean header, boolean stripe, int w) {
      int lines = 1;
      List<List<String>> wrapped = new ArrayList<>();
      for (int c = 0; c < colX.length; c++) {
         String cell = c < cells.size() ? cells.get(c) : "";
         List<String> parts = TextWrap.wrap(header ? Markdown.strip(cell) : cell, colW[c], measure);
         wrapped.add(parts);
         lines = Math.max(lines, parts.size());
      }

      int height = lines * Theme.LINE + Theme.SPACE_XS;
      if (stripe) {
         out.add(new Piece(Kind.STRIPE, 0, y - 1, w, height, ""));
      }

      for (int c = 0; c < colX.length; c++) {
         int ly = y + 1;
         for (String line : wrapped.get(c)) {
            Kind kind = header ? Kind.TABLE_HEADER : (c == 0 && colX.length == 2 ? Kind.CELL_KEY : Kind.CELL);
            out.add(new Piece(kind, colX[c], ly, measure.applyAsInt(line), 0, line));
            ly += Theme.LINE;
         }
      }

      return y + height;
   }
}
