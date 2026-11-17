package gemini.ui.clickgui;

import gemini.Gemini;
import gemini.input.Keys;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import gemini.ui.Fuzzy;
import gemini.ui.Glass;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/**
 * Editor for a {@link ListSetting}: a searchable grid of every block or item (with their icons), a list of
 * every entity type (with its spawn egg), or a free-text list. Typing filters instantly with {@link Fuzzy}
 * matching on both the id and the in-game name; chips above the grid narrow it to a category whose membership
 * comes from the game's own tags, data components and block classes. Selected entries always sort first.
 */
public final class ListEditorScreen extends Screen {
   private static final int CELL = 20;
   private static final int PAD = 10;
   private static final int CHIP_H = 13;
   private final Screen parent;
   private final Module module;
   private final ListSetting setting;
   private final List<ListEditorScreen.Entry> all = new ArrayList<>();
   private final List<ListEditorScreen.Entry> shown = new ArrayList<>();
   private final List<ListEditorScreen.Filter> filters = new ArrayList<>();
   private final List<int[]> chipBounds = new ArrayList<>();
   private ListEditorScreen.Filter filter;
   private String search = "";
   private String lastSearch = null;
   private ListEditorScreen.Filter lastFilter;
   private int lastSize = -1;
   private float scroll;
   private float scrollTarget;
   private float open;
   private long lastFrame = Util.getMillis();
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private int bodyTop;
   private int bodyBottom;
   private int columns;

   public ListEditorScreen(Screen parent, Module module, ListSetting setting) {
      super(Component.literal(setting.name()));
      this.parent = parent;
      this.module = module;
      this.setting = setting;
      this.buildEntries();
      this.buildFilters();
   }

   private boolean grid() {
      return this.setting.kind() == ListSetting.Kind.BLOCK || this.setting.kind() == ListSetting.Kind.ITEM;
   }

   /** Row height of the list layouts: entities get room for their spawn egg. */
   private int rowHeight() {
      return this.setting.kind() == ListSetting.Kind.ENTITY ? 18 : 13;
   }

   private void buildEntries() {
      switch (this.setting.kind()) {
         case BLOCK:
            for (Block block : BuiltInRegistries.BLOCK) {
               Identifier key = BuiltInRegistries.BLOCK.getKey(block);
               if (key != null && block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.VOID_AIR) {
                  Item item = block.asItem();
                  this.all.add(new ListEditorScreen.Entry(key.toString(), item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item), name(block.getName()), block));
               }
            }
            break;
         case ITEM:
            for (Item item : BuiltInRegistries.ITEM) {
               Identifier key = BuiltInRegistries.ITEM.getKey(item);
               if (key != null && item != Items.AIR) {
                  ItemStack stack = new ItemStack(item);
                  this.all.add(new ListEditorScreen.Entry(key.toString(), stack, name(stack.getHoverName()), item));
               }
            }
            break;
         case ENTITY:
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
               Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
               if (key != null) {
                  ItemStack egg = SpawnEggItem.byId(type).map(ItemStack::new).orElse(ItemStack.EMPTY);
                  this.all.add(new ListEditorScreen.Entry(key.toString(), egg, name(type.getDescription()), type));
               }
            }
            break;
         default:
      }

      this.all.sort(Comparator.comparing(ListEditorScreen.Entry::id));
   }

   private static String name(Component component) {
      try {
         return component.getString();
      } catch (RuntimeException e) {
         return "";
      }
   }

   // ---- category chips -------------------------------------------------------------------------------------

   private void buildFilters() {
      if (this.setting.kind() == ListSetting.Kind.TEXT) {
         return;
      }

      this.filters.add(new ListEditorScreen.Filter("All", entry -> true));
      this.filters.add(new ListEditorScreen.Filter("Selected", entry -> this.setting.contains(entry.id())));
      switch (this.setting.kind()) {
         case BLOCK -> {
            this.blockFilter("Ores", block -> is(block, BlockTags.ORES));
            this.blockFilter(
               "Storage",
               block -> block instanceof AbstractChestBlock<?>
                  || block instanceof BarrelBlock
                  || block instanceof ShulkerBoxBlock
                  || block instanceof HopperBlock
                  || block instanceof DispenserBlock
                  || block instanceof AbstractFurnaceBlock
                  || block instanceof BrewingStandBlock
                  || block instanceof CrafterBlock
                  || block instanceof ChiseledBookShelfBlock
                  || block instanceof DecoratedPotBlock
                  || block instanceof ShelfBlock
                  || is(block, BlockTags.SHULKER_BOXES)
                  || is(block, BlockTags.COPPER_CHESTS)
            );
            this.blockFilter(
               "Redstone",
               block -> block.defaultBlockState().isSignalSource()
                  || block instanceof DiodeBlock
                  || block instanceof PistonBaseBlock
                  || block instanceof RedstoneLampBlock
                  || block instanceof NoteBlock
                  || block instanceof TntBlock
                  || block instanceof DispenserBlock
                  || block instanceof HopperBlock
                  || block instanceof CrafterBlock
                  || block == Blocks.REDSTONE_WIRE
                  || is(block, BlockTags.RAILS)
            );
            this.blockFilter(
               "Building",
               block -> is(block, BlockTags.PLANKS)
                  || is(block, BlockTags.SLABS)
                  || is(block, BlockTags.STAIRS)
                  || is(block, BlockTags.WALLS)
                  || is(block, BlockTags.FENCES)
                  || is(block, BlockTags.FENCE_GATES)
                  || is(block, BlockTags.DOORS)
                  || is(block, BlockTags.TRAPDOORS)
                  || is(block, BlockTags.STONE_BRICKS)
                  || is(block, BlockTags.WOOL)
                  || is(block, BlockTags.CONCRETE)
                  || is(block, BlockTags.TERRACOTTA)
                  || is(block, BlockTags.GLAZED_TERRACOTTA)
                  || is(block, BlockTags.CONCRETE_POWDERS)
            );
            this.blockFilter(
               "Natural",
               block -> block instanceof VegetationBlock
                  || is(block, BlockTags.BASE_STONE_OVERWORLD)
                  || is(block, BlockTags.BASE_STONE_NETHER)
                  || is(block, BlockTags.DIRT)
                  || is(block, BlockTags.SAND)
                  || is(block, BlockTags.LEAVES)
                  || is(block, BlockTags.OVERWORLD_NATURAL_LOGS)
                  || is(block, BlockTags.FLOWERS)
                  || is(block, BlockTags.SAPLINGS)
                  || is(block, BlockTags.CROPS)
                  || is(block, BlockTags.ICE)
                  || is(block, BlockTags.SNOW)
                  || is(block, BlockTags.CORALS)
                  || is(block, BlockTags.NYLIUM)
                  || is(block, BlockTags.MOSS_BLOCKS)
                  || is(block, BlockTags.MUD)
                  || is(block, BlockTags.ORES)
            );
         }
         case ITEM -> {
            this.itemFilter(
               "Tools",
               item -> is(item, ItemTags.PICKAXES) || is(item, ItemTags.AXES) || is(item, ItemTags.SHOVELS) || is(item, ItemTags.HOES)
                  || item.components().has(DataComponents.TOOL)
            );
            this.itemFilter(
               "Weapons",
               item -> is(item, ItemTags.SWORDS)
                  || is(item, ItemTags.SPEARS)
                  || is(item, ItemTags.BOW_ENCHANTABLE)
                  || is(item, ItemTags.CROSSBOW_ENCHANTABLE)
                  || is(item, ItemTags.TRIDENT_ENCHANTABLE)
                  || is(item, ItemTags.MACE_ENCHANTABLE)
                  || item.components().has(DataComponents.WEAPON)
            );
            this.itemFilter(
               "Armour",
               item -> is(item, ItemTags.HEAD_ARMOR) || is(item, ItemTags.CHEST_ARMOR) || is(item, ItemTags.LEG_ARMOR) || is(item, ItemTags.FOOT_ARMOR)
                  || item.components().has(DataComponents.GLIDER)
            );
            this.itemFilter("Food", item -> item.components().has(DataComponents.FOOD));
            this.itemFilter("Potions", item -> item.components().has(DataComponents.POTION_CONTENTS));
            this.itemFilter("Blocks", item -> item instanceof BlockItem);
         }
         case ENTITY -> {
            this.entityFilter("Hostile", type -> type.getCategory() == MobCategory.MONSTER);
            this.entityFilter(
               "Passive",
               type -> type.getCategory() != MobCategory.MONSTER && type.getCategory() != MobCategory.MISC
            );
            this.entityFilter("Undead", type -> is(type, EntityTypeTags.UNDEAD));
            this.entityFilter("Aquatic", type -> is(type, EntityTypeTags.AQUATIC) || is(type, EntityTypeTags.CAN_BREATHE_UNDER_WATER));
            this.entityFilter("Projectiles", type -> is(type, EntityTypeTags.IMPACT_PROJECTILES) || is(type, EntityTypeTags.ARROWS));
            this.entityFilter(
               "Other",
               type -> type.getCategory() == MobCategory.MISC && !is(type, EntityTypeTags.IMPACT_PROJECTILES) && !is(type, EntityTypeTags.ARROWS)
            );
         }
         default -> {
         }
      }

      this.filter = this.filters.get(0);
   }

   private void blockFilter(String label, Predicate<Block> test) {
      this.filters.add(new ListEditorScreen.Filter(label, entry -> entry.source() instanceof Block block && test.test(block)));
   }

   private void itemFilter(String label, Predicate<Item> test) {
      this.filters.add(new ListEditorScreen.Filter(label, entry -> entry.source() instanceof Item item && test.test(item)));
   }

   private void entityFilter(String label, Predicate<EntityType<?>> test) {
      this.filters.add(new ListEditorScreen.Filter(label, entry -> entry.source() instanceof EntityType<?> type && test.test(type)));
   }

   // Tags only exist once a world has sent them; before that every tag test is simply false.
   private static boolean is(Block block, TagKey<Block> tag) {
      return block.builtInRegistryHolder().is(tag);
   }

   private static boolean is(Item item, TagKey<Item> tag) {
      return item.builtInRegistryHolder().is(tag);
   }

   private static boolean is(EntityType<?> type, TagKey<EntityType<?>> tag) {
      return type.builtInRegistryHolder().is(tag);
   }

   // ---- layout and filtering -------------------------------------------------------------------------------

   protected void init() {
      // Minecraft 26.3 reads keys through SDL, which only sends typed characters to a screen that asks for them.
      this.minecraft.textInputManager().startTextInput(this);
      this.panelW = Math.min(this.width - 24, 480);
      this.panelH = Math.min(this.height - 24, 360);
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = (this.height - this.panelH) / 2;
      this.layoutChips();
      int chipRows = this.chipBounds.isEmpty() ? 0 : this.chipBounds.get(this.chipBounds.size() - 1)[1] + CHIP_H + 4 - (this.panelY + 50);
      this.bodyTop = this.panelY + 52 + Math.max(0, chipRows);
      this.bodyBottom = this.panelY + this.panelH - 24;
      this.columns = Math.max(1, (this.panelW - PAD * 2 - 6) / CELL);
      this.lastSearch = null;
   }

   private void layoutChips() {
      this.chipBounds.clear();
      int x = this.panelX + PAD;
      int y = this.panelY + 50;
      for (ListEditorScreen.Filter f : this.filters) {
         int w = UiFont.width(f.label()) + 12;
         if (x + w > this.panelX + this.panelW - PAD) {
            x = this.panelX + PAD;
            y += CHIP_H + 3;
         }

         this.chipBounds.add(new int[]{x, y, w});
         x += w + 4;
      }
   }

   private void refilter() {
      if (this.search.equals(this.lastSearch) && this.setting.size() == this.lastSize && this.filter == this.lastFilter) {
         return;
      }

      boolean filterChanged = this.filter != this.lastFilter || !this.search.equals(this.lastSearch);
      this.lastSearch = this.search;
      this.lastSize = this.setting.size();
      this.lastFilter = this.filter;
      this.shown.clear();
      String needle = this.search.trim();
      if (this.setting.kind() == ListSetting.Kind.TEXT) {
         for (String value : this.setting.get()) {
            if (needle.isEmpty() || Fuzzy.score(needle, value) > 0) {
               this.shown.add(new ListEditorScreen.Entry(value, ItemStack.EMPTY, value, null));
            }
         }
      } else {
         String idNeedle = needle.toLowerCase(Locale.ROOT).replace(' ', '_');
         List<ListEditorScreen.Scored> selected = new ArrayList<>();
         List<ListEditorScreen.Scored> rest = new ArrayList<>();
         for (ListEditorScreen.Entry entry : this.all) {
            if (this.filter != null && !this.filter.test().test(entry)) {
               continue;
            }

            int score = 1;
            if (!needle.isEmpty()) {
               String path = entry.id().substring(entry.id().indexOf(':') + 1);
               score = Math.max(Math.max(Fuzzy.score(idNeedle, path), Fuzzy.score(needle, entry.name())), Fuzzy.substringScore(idNeedle, entry.id()));
               if (score <= 0) {
                  continue;
               }
            }

            (this.setting.contains(entry.id()) ? selected : rest).add(new ListEditorScreen.Scored(entry, score));
         }

         Comparator<ListEditorScreen.Scored> order = Comparator.comparingInt(ListEditorScreen.Scored::score).reversed();
         selected.sort(order);
         rest.sort(order);
         selected.forEach(scored -> this.shown.add(scored.entry()));
         rest.forEach(scored -> this.shown.add(scored.entry()));
      }

      if (filterChanged) {
         this.scrollTarget = 0.0F;
         this.scroll = 0.0F;
      }
   }

   private int contentHeight() {
      return this.grid() ? (this.shown.size() + this.columns - 1) / this.columns * CELL : this.shown.size() * this.rowHeight();
   }

   private float maxScroll() {
      return Math.max(0, this.contentHeight() - (this.bodyBottom - this.bodyTop));
   }

   // ---- drawing --------------------------------------------------------------------------------------------

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      float dt = Math.min(0.05F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.open = Glass.approach(this.open, 1.0F, 14.0F, dt);
      this.refilter();
      this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget));
      this.scroll = Glass.approach(this.scroll, this.scrollTarget, 18.0F, dt);
      float fade = this.open;
      int accent = Glass.accent();
      int accent2 = Glass.accent2();
      graphics.fill(0, 0, this.width, this.height, Glass.withAlpha(0x050810, Math.round(190.0F * fade)));
      int lift = Math.round((1.0F - fade) * 12.0F);
      int px = this.panelX;
      int py = this.panelY + lift;
      Glass.panel(graphics, px, py, px + this.panelW, py + this.panelH, 6, Glass.surface(0), accent, fade, 0.35F);
      Glass.gradientBar(graphics, px + 6, py + 24, px + this.panelW - 6, py + 25, Glass.fade(accent, fade), Glass.fade(accent2, fade));
      Controls.text(graphics, this.font, this.module.getName() + "  ·  " + this.setting.name(), px + PAD, py + 8, Glass.text(), fade);
      String count = this.setting.size() + " selected";
      Controls.text(graphics, this.font, count, px + this.panelW - PAD - UiFont.width(count), py + 8, accent2, fade);

      int fieldTop = py + 30;
      int doneW = UiFont.width("Done") + 16;
      int clearW = UiFont.width("Clear") + 16;
      int fieldRight = px + this.panelW - PAD - doneW - clearW - 8;
      Glass.roundRect(graphics, px + PAD, fieldTop, fieldRight, fieldTop + 16, 4, Glass.fade(0xE0101520, fade));
      Glass.roundOutline(graphics, px + PAD, fieldTop, fieldRight, fieldTop + 16, 4, Glass.fade(Glass.withAlpha(accent, 200), fade));
      boolean blink = now / 420L % 2L == 0L;
      String hint = this.setting.kind() == ListSetting.Kind.TEXT ? "Type text and press Enter to add" : "Search " + this.setting.kind().noun() + "s by id or name...";
      String query = this.search.isEmpty() ? hint : this.search + (blink ? "|" : "");
      while (UiFont.width(query) > fieldRight - px - PAD - 12 && query.length() > 1) {
         query = query.substring(1);
      }

      Controls.text(graphics, this.font, query, px + PAD + 5, fieldTop + 4, this.search.isEmpty() ? Glass.textFaint() : Glass.text(), fade);
      int clearX = fieldRight + 4;
      int doneX = clearX + clearW + 4;
      this.button(graphics, clearX, fieldTop, clearW, "Clear", mouseX, mouseY, accent, fade, false);
      this.button(graphics, doneX, fieldTop, doneW, "Done", mouseX, mouseY, accent, fade, true);

      for (int i = 0; i < this.filters.size(); i++) {
         int[] b = this.chipBounds.get(i);
         ListEditorScreen.Filter f = this.filters.get(i);
         boolean active = f == this.filter;
         boolean over = inside(mouseX, mouseY, b[0], b[1] + lift, b[2], CHIP_H);
         int cy = b[1] + lift;
         Glass.roundRect(graphics, b[0], cy, b[0] + b[2], cy + CHIP_H, 4, Glass.fade(active ? Glass.withAlpha(accent2, 140) : (over ? Glass.withAlpha(accent, 90) : 0xC0161C28), fade));
         Glass.roundOutline(graphics, b[0], cy, b[0] + b[2], cy + CHIP_H, 4, Glass.fade(Glass.withAlpha(active ? accent2 : (over ? accent : 0xFFFFFF), active || over ? 220 : 40), fade));
         Controls.text(graphics, this.font, f.label(), b[0] + 6, cy + 3, active || over ? Glass.text() : Glass.textDim(), fade);
      }

      int top = this.bodyTop + lift;
      int bottom = this.bodyBottom + lift;
      graphics.enableScissor(px, top, px + this.panelW, bottom);
      ListEditorScreen.Entry hovered = null;
      int offset = Math.round(this.scroll);
      if (this.grid()) {
         int startRow = Math.max(0, offset / CELL);
         int endRow = (offset + (bottom - top)) / CELL + 1;

         for (int row = startRow; row <= endRow; row++) {
            for (int col = 0; col < this.columns; col++) {
               int index = row * this.columns + col;
               if (index >= this.shown.size()) {
                  break;
               }

               ListEditorScreen.Entry entry = this.shown.get(index);
               int x = px + PAD + col * CELL;
               int y = top + row * CELL - offset;
               boolean selected = this.setting.contains(entry.id());
               boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= Math.max(y, top) && mouseY < Math.min(y + CELL, bottom);
               int fill = selected ? Glass.withAlpha(accent, 110) : (over ? 0x40FFFFFF : 0x50202838);
               Glass.roundRect(graphics, x + 1, y + 1, x + CELL - 1, y + CELL - 1, 3, Glass.fade(fill, fade));
               if (selected || over) {
                  Glass.roundOutline(graphics, x + 1, y + 1, x + CELL - 1, y + CELL - 1, 3, Glass.fade(selected ? accent2 : 0x90FFFFFF, fade));
               }

               if (entry.icon().isEmpty()) {
                  this.letter(graphics, entry, x + 7, y + 6, fade);
               } else if (fade > 0.6F) {
                  graphics.fakeItem(entry.icon(), x + 2, y + 2);
               }

               if (over) {
                  hovered = entry;
               }
            }
         }
      } else {
         int rowH = this.rowHeight();
         int startRow = Math.max(0, offset / rowH);
         int endRow = (offset + (bottom - top)) / rowH + 1;
         boolean icons = this.setting.kind() == ListSetting.Kind.ENTITY;

         for (int row = startRow; row <= endRow && row < this.shown.size(); row++) {
            ListEditorScreen.Entry entry = this.shown.get(row);
            int y = top + row * rowH - offset;
            boolean selected = this.setting.contains(entry.id());
            boolean over = mouseX >= px + PAD && mouseX < px + this.panelW - PAD && mouseY >= Math.max(y, top) && mouseY < Math.min(y + rowH, bottom);
            if (selected || over) {
               Glass.roundRect(graphics, px + PAD - 2, y, px + this.panelW - PAD, y + rowH - 1, 3, Glass.fade(selected ? Glass.withAlpha(accent, 90) : 0x30FFFFFF, fade));
            }

            int textX = px + PAD + 2;
            if (icons) {
               if (entry.icon().isEmpty()) {
                  this.letter(graphics, entry, textX + 5, y + 5, fade);
               } else if (fade > 0.6F) {
                  graphics.fakeItem(entry.icon(), textX, y + 1);
               }

               textX += 20;
            }

            int textY = y + (rowH - 8) / 2;
            String label = icons && !entry.name().isEmpty() ? entry.name() : entry.id();
            Controls.text(graphics, this.font, label, textX, textY, selected ? Glass.text() : Glass.textDim(), fade);
            if (icons) {
               Controls.text(graphics, this.font, entry.id(), textX + UiFont.width(label) + 8, textY, Glass.textFaint(), fade);
            }

            if (over) {
               hovered = entry;
            }
         }
      }

      graphics.disableScissor();
      if (this.contentHeight() > bottom - top) {
         int trackH = bottom - top;
         int thumbH = Math.max(14, Math.round(trackH * ((float)trackH / this.contentHeight())));
         int thumbY = top + Math.round((trackH - thumbH) * (this.scroll / Math.max(1.0F, this.maxScroll())));
         Glass.roundRect(graphics, px + this.panelW - 6, top, px + this.panelW - 3, bottom, 1, Glass.fade(0x40000000, fade));
         Glass.roundRect(graphics, px + this.panelW - 6, thumbY, px + this.panelW - 3, thumbY + thumbH, 1, Glass.fade(Glass.withAlpha(accent, 190), fade));
      }

      if (this.shown.isEmpty()) {
         String empty = this.setting.kind() == ListSetting.Kind.TEXT
            ? (this.search.isEmpty() ? "Nothing here yet." : "Press Enter to add \"" + this.search + "\"")
            : (this.search.isEmpty() ? "Nothing in " + this.filter.label() + " (category tags load with a world)" : "No match for \"" + this.search + "\"");
         Controls.text(graphics, this.font, empty, px + PAD, top + 6, Glass.textFaint(), fade);
      }

      String footer = this.grid() || this.setting.kind() == ListSetting.Kind.ENTITY
         ? this.shown.size() + " shown  ·  click to toggle  ·  type to search  ·  selected entries come first"
         : "Click an entry to remove it  ·  Enter adds the typed text  ·  Esc to close";
      Controls.text(graphics, this.font, footer, px + PAD, py + this.panelH - 15, Glass.textFaint(), fade);
      if (hovered != null) {
         String tip = hovered.name().isEmpty() || hovered.name().equals(hovered.id()) ? hovered.id() : hovered.name() + "  (" + hovered.id() + ")";
         graphics.setTooltipForNextFrame(this.font, UiFont.literal(tip), mouseX, mouseY);
      }

      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   private void letter(GuiGraphicsExtractor graphics, ListEditorScreen.Entry entry, int x, int y, float fade) {
      int colon = entry.id().indexOf(':');
      String path = entry.id().substring(colon + 1);
      if (!path.isEmpty()) {
         Controls.text(graphics, this.font, path.substring(0, 1).toUpperCase(Locale.ROOT), x, y, Glass.textDim(), fade);
      }
   }

   private void button(GuiGraphicsExtractor graphics, int x, int y, int w, String label, int mouseX, int mouseY, int accent, float fade, boolean primary) {
      boolean over = inside(mouseX, mouseY, x, y, w, 16);
      int fill = over ? Glass.withAlpha(accent, 130) : (primary ? Glass.withAlpha(accent, 70) : 0xC0161C28);
      Glass.roundRect(graphics, x, y, x + w, y + 16, 4, Glass.fade(fill, fade));
      Glass.roundOutline(graphics, x, y, x + w, y + 16, 4, Glass.fade(Glass.withAlpha(over || primary ? accent : 0xFFFFFF, over ? 240 : (primary ? 170 : 50)), fade));
      Controls.text(graphics, this.font, label, x + (w - UiFont.width(label)) / 2, y + 4, over ? Glass.text() : Glass.textDim(), fade);
   }

   // ---- input ----------------------------------------------------------------------------------------------

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      double mx = event.x();
      double my = event.y();
      int button = Keys.glfwButton(event.button());
      int fieldTop = this.panelY + 30;
      int doneW = UiFont.width("Done") + 16;
      int clearW = UiFont.width("Clear") + 16;
      int fieldRight = this.panelX + this.panelW - PAD - doneW - clearW - 8;
      int clearX = fieldRight + 4;
      int doneX = clearX + clearW + 4;
      if (button == 0 && my >= fieldTop && my < fieldTop + 16) {
         if (mx >= doneX && mx < doneX + doneW) {
            this.onClose();
            return true;
         }

         if (mx >= clearX && mx < clearX + clearW) {
            this.setting.clear();
            this.save();
            return true;
         }
      }

      if (button == 0) {
         for (int i = 0; i < this.filters.size(); i++) {
            int[] b = this.chipBounds.get(i);
            if (inside(mx, my, b[0], b[1], b[2], CHIP_H)) {
               this.filter = this.filters.get(i);
               return true;
            }
         }
      }

      if (mx < this.panelX || mx > this.panelX + this.panelW || my < this.panelY || my > this.panelY + this.panelH) {
         this.onClose();
         return true;
      }

      if (my >= this.bodyTop && my < this.bodyBottom && (button == 0 || button == 1)) {
         int offset = Math.round(this.scroll);
         ListEditorScreen.Entry clicked = null;
         if (this.grid()) {
            int col = (int)((mx - this.panelX - PAD) / CELL);
            int row = (int)((my - this.bodyTop + offset) / CELL);
            if (mx >= this.panelX + PAD && col >= 0 && col < this.columns) {
               int index = row * this.columns + col;
               if (index >= 0 && index < this.shown.size()) {
                  clicked = this.shown.get(index);
               }
            }
         } else {
            int row = (int)((my - this.bodyTop + offset) / this.rowHeight());
            if (row >= 0 && row < this.shown.size()) {
               clicked = this.shown.get(row);
            }
         }

         if (clicked != null) {
            this.setting.toggle(clicked.id());
            this.save();
            return true;
         }
      }

      return super.mouseClicked(event, doubled);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      float step = this.grid() ? CELL * 2 : this.rowHeight() * 3;
      this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget - (float)scrollY * step));
      return true;
   }

   public boolean keyPressed(KeyEvent event) {
      int key = event.key();
      if (key == Keys.ESCAPE) {
         if (!this.search.isEmpty()) {
            this.search = "";
         } else {
            this.onClose();
         }

         return true;
      } else if (key == Keys.BACKSPACE && !this.search.isEmpty()) {
         this.search = event.hasControlDown() ? "" : this.search.substring(0, this.search.offsetByCodePoints(this.search.length(), -1));
         return true;
      } else if (event.isPaste()) {
         String pasted = Controls.clipboard().replaceAll("[\\r\\n\\t]", " ").trim();
         this.search = (this.search + pasted).substring(0, Math.min(48, (this.search + pasted).length()));
         return true;
      } else if (Keys.isEnter(key)) {
         this.enter();
         return true;
      }

      return super.keyPressed(event);
   }

   /**
    * Enter adds typed text to a text list; for registry lists it toggles the only remaining match, or adds an id
    * typed in full that the search could not show.
    */
   private void enter() {
      if (this.search.isBlank()) {
         return;
      }

      if (this.setting.kind() == ListSetting.Kind.TEXT) {
         this.setting.add(this.search);
      } else if (this.shown.size() == 1) {
         this.setting.toggle(this.shown.get(0).id());
      } else if (this.shown.isEmpty() && this.setting.isKnown(this.search)) {
         this.setting.add(this.search);
      } else {
         return;
      }

      this.search = "";
      this.save();
   }

   public boolean charTyped(CharacterEvent event) {
      if (event.isAllowedChatCharacter() && this.search.length() < 48) {
         this.search = this.search + event.codepointAsString();
         return true;
      }

      return super.charTyped(event);
   }

   private void save() {
      Gemini.getInstance().config().save();
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void removed() {
      this.minecraft.textInputManager().stopTextInput(this);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   /** One selectable registry entry: its id, icon, in-game name and the block, item or entity type it stands for. */
   private record Entry(String id, ItemStack icon, String name, Object source) {
   }

   private record Scored(ListEditorScreen.Entry entry, int score) {
   }

   private record Filter(String label, Predicate<ListEditorScreen.Entry> test) {
   }
}
