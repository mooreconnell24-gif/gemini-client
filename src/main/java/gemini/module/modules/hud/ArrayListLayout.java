package gemini.module.modules.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The state behind the animated module list: which entries are showing, in what order, how wide each is and how
 * far each has slid in or out. It knows nothing about the game, so it can be unit-tested.
 *
 * <p><b>Polling.</b> A poll is {@link #beginPoll()}, one {@link #offer} per entry that should be showing, then
 * {@link #endPoll()}. An entry that was not offered starts sliding out; it keeps its row (and its place in the
 * order) until {@link #advance} has run its animation down to zero, and only then is it removed.
 *
 * <p><b>Caching.</b> Text decoration, width measurement and sorting only happen when a poll actually changed
 * something (an entry appeared, returned, or its label, variant or info text changed) or after
 * {@link #invalidate()}. A poll that changes nothing, and every {@link #advance}, allocates nothing.
 *
 * @param <K> the entry identity (a module); compared by reference
 */
public final class ArrayListLayout<K> {
   public static final int SORT_WIDTH = 0;
   public static final int SORT_ALPHABETICAL = 1;
   public static final int SORT_GROUP = 2;
   /** Pixels between the label and its info suffix. */
   public static final int INFO_GAP = 4;

   /** Measures a string in pixels. */
   @FunctionalInterface
   public interface Measure {
      int width(String text);
   }

   /** Turns an entry's raw name and info into what is drawn (key bind suffix, lower case, tag brackets...). */
   public interface Decorator<K> {
      String label(K key, String rawLabel);

      /** May return null or empty to draw no suffix. */
      String info(K key, String rawInfo);
   }

   /** One animation step: where {@code current} should be after {@code deltaSeconds} on its way to {@code target}. */
   @FunctionalInterface
   public interface Glide {
      float step(float current, float target, float deltaSeconds);
   }

   /** One line of the list. Fields are read by the renderer and must not be written outside this class. */
   public static final class Row<K> {
      public final K key;
      /** Decorated texts, ready to draw; {@link #info} is null when there is no suffix. */
      public String label;
      public String info;
      public int labelWidth;
      /** Full width of the text: label, gap and info. */
      public int width;
      /** 0 = hidden, 1 = fully shown. */
      public float anim;
      /** False once the entry stopped being offered; the row then slides out. */
      public boolean active;
      public int group;
      String rawLabel;
      String rawInfo;
      int variant;
      int seen;
      boolean dirty = true;
      String sortName;

      Row(K key) {
         this.key = key;
      }
   }

   private final List<Row<K>> rows = new ArrayList<>();
   private final Map<K, Row<K>> byKey = new IdentityHashMap<>();
   private final Measure measure;
   private final Measure infoMeasure;
   private final Decorator<K> decorator;
   private final Comparator<Row<K>> byWidth = (a, b) -> {
      int c = Integer.compare(b.width, a.width);
      return c != 0 ? c : a.sortName.compareTo(b.sortName);
   };
   private final Comparator<Row<K>> byName = (a, b) -> a.sortName.compareTo(b.sortName);
   private final Comparator<Row<K>> byGroup = (a, b) -> {
      int c = Integer.compare(a.group, b.group);
      return c != 0 ? c : this.byWidth.compare(a, b);
   };
   private int sortMode = SORT_WIDTH;
   private int poll;
   private boolean changed = true;
   private boolean allDirty;
   private int maxWidth;
   private int sorts;

   public ArrayListLayout(Measure measure, Decorator<K> decorator) {
      this(measure, measure, decorator);
   }

   /** As the two-argument form, with the info suffix measured separately (it may be drawn in another weight). */
   public ArrayListLayout(Measure measure, Measure infoMeasure, Decorator<K> decorator) {
      this.measure = measure;
      this.infoMeasure = infoMeasure;
      this.decorator = decorator;
   }

   /** Rows in display order, including the ones still sliding out. Do not modify. */
   public List<Row<K>> rows() {
      return this.rows;
   }

   public int size() {
      return this.rows.size();
   }

   /** Width of the widest row currently held. */
   public int maxWidth() {
      return this.maxWidth;
   }

   /** How many times the list has been re-sorted; lets tests prove an idle poll does no work. */
   public int sortCount() {
      return this.sorts;
   }

   public void setSortMode(int mode) {
      if (mode != this.sortMode) {
         this.sortMode = mode;
         this.changed = true;
      }
   }

   /** The decoration or the font changed: every row is re-decorated, re-measured and re-sorted at the next poll. */
   public void invalidate() {
      this.allDirty = true;
      this.changed = true;
   }

   public void beginPoll() {
      this.poll++;
   }

   /**
    * Declares an entry as showing. {@code variant} is anything else the decorated label depends on (the key bind);
    * {@code group} is the sort bucket for {@link #SORT_GROUP}.
    */
   public void offer(K key, String rawLabel, int variant, String rawInfo, int group) {
      Row<K> row = this.byKey.get(key);
      if (row == null) {
         row = new Row<>(key);
         row.active = true;
         this.byKey.put(key, row);
         this.rows.add(row);
         this.changed = true;
      } else if (!row.active) {
         row.active = true;
         this.changed = true;
      }

      if (row.variant != variant || row.group != group || !same(row.rawLabel, rawLabel) || !same(row.rawInfo, rawInfo)) {
         row.rawLabel = rawLabel;
         row.rawInfo = rawInfo;
         row.variant = variant;
         row.group = group;
         row.dirty = true;
         this.changed = true;
      }

      row.seen = this.poll;
   }

   private static boolean same(String a, String b) {
      return a == b || a != null && a.equals(b);
   }

   /** Finishes a poll. Returns true when the list was re-measured and re-sorted. */
   public boolean endPoll() {
      for (int i = 0; i < this.rows.size(); i++) {
         Row<K> row = this.rows.get(i);
         if (row.active && row.seen != this.poll) {
            row.active = false;
         }
      }

      if (!this.changed) {
         return false;
      }

      for (int i = 0; i < this.rows.size(); i++) {
         Row<K> row = this.rows.get(i);
         if (row.dirty || this.allDirty) {
            this.decorate(row);
         }
      }

      this.allDirty = false;
      this.changed = false;
      this.rows.sort(this.sortMode == SORT_ALPHABETICAL ? this.byName : (this.sortMode == SORT_GROUP ? this.byGroup : this.byWidth));
      this.sorts++;
      this.remeasureMax();
      return true;
   }

   private void decorate(Row<K> row) {
      String label = this.decorator.label(row.key, row.rawLabel == null ? "" : row.rawLabel);
      String info = row.rawInfo == null || row.rawInfo.isEmpty() ? null : this.decorator.info(row.key, row.rawInfo);
      row.label = label == null ? "" : label;
      row.info = info == null || info.isEmpty() ? null : info;
      row.labelWidth = this.measure.width(row.label);
      row.width = row.labelWidth + (row.info == null ? 0 : INFO_GAP + this.infoMeasure.width(row.info));
      row.sortName = row.label.toLowerCase(Locale.ROOT);
      row.dirty = false;
   }

   private void remeasureMax() {
      int widest = 0;
      for (int i = 0; i < this.rows.size(); i++) {
         widest = Math.max(widest, this.rows.get(i).width);
      }

      this.maxWidth = widest;
   }

   /**
    * Moves every row toward shown or hidden and drops the rows that have finished sliding out. With
    * {@code glide == null} rows snap straight to their target.
    */
   public void advance(float deltaSeconds, Glide glide) {
      boolean removed = false;
      for (int i = this.rows.size() - 1; i >= 0; i--) {
         Row<K> row = this.rows.get(i);
         float target = row.active ? 1.0F : 0.0F;
         row.anim = glide == null ? target : clamp01(glide.step(row.anim, target, deltaSeconds));
         if (!row.active && row.anim <= 0.0F) {
            this.rows.remove(i);
            this.byKey.remove(row.key);
            removed = true;
         }
      }

      if (removed) {
         this.remeasureMax();
      }
   }

   private static float clamp01(float value) {
      return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
   }

   /** Height of the whole list when each row takes {@code rowHeight} at full size: rows collapse as they slide. */
   public float height(float rowHeight) {
      float total = 0.0F;
      for (int i = 0; i < this.rows.size(); i++) {
         total += rowHeight * this.rows.get(i).anim;
      }

      return total;
   }

   /**
    * How many rows, from the top of the order, fit in {@code maxHeight} when each takes {@code rowHeight} at full
    * size: the list is cut there instead of running off the screen. Rows that are sliding in or out count for the
    * part of their height that is open. Always at least one row when there is one.
    */
   public int fitCount(float rowHeight, float maxHeight) {
      float total = 0.0F;
      for (int i = 0; i < this.rows.size(); i++) {
         total += rowHeight * this.rows.get(i).anim;
         if (total > maxHeight && i > 0) {
            return i;
         }
      }

      return this.rows.size();
   }

   /** Height of the first {@code count} rows (see {@link #fitCount}). */
   public float height(float rowHeight, int count) {
      float total = 0.0F;
      int n = Math.min(count, this.rows.size());
      for (int i = 0; i < n; i++) {
         total += rowHeight * this.rows.get(i).anim;
      }

      return total;
   }

   /** Forgets everything, with no slide-out. */
   public void clear() {
      this.rows.clear();
      this.byKey.clear();
      this.maxWidth = 0;
      this.changed = true;
   }
}
