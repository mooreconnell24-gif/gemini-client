package gemini.module.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ListSetting extends Setting<List<String>> {
   private final ListSetting.Kind kind;
   private volatile Set<Block> blockCache;
   private volatile Set<Item> itemCache;
   private volatile Set<EntityType<?>> entityCache;

   public ListSetting(String name, String description, ListSetting.Kind kind, String... defaults) {
      super(name, description, normalizeAll(kind, List.of(defaults)));
      this.kind = kind;
   }

   public ListSetting.Kind kind() {
      return this.kind;
   }

   public static String normalize(ListSetting.Kind kind, String raw) {
      if (raw == null) {
         return null;
      } else {
         String text = raw.trim();
         if (text.isEmpty()) {
            return null;
         } else if (kind == ListSetting.Kind.TEXT) {
            return text;
         } else {
            text = text.toLowerCase(Locale.ROOT).replace(' ', '_');
            return text.indexOf(58) < 0 ? "minecraft:" + text : text;
         }
      }
   }

   private static List<String> normalizeAll(ListSetting.Kind kind, Iterable<String> raw) {
      LinkedHashSet<String> clean = new LinkedHashSet<>();

      for (String entry : raw) {
         String normalized = normalize(kind, entry);
         if (normalized != null) {
            clean.add(normalized);
         }
      }

      return Collections.unmodifiableList(new ArrayList<>(clean));
   }

   public void set(List<String> value) {
      List<String> normalized = normalizeAll(this.kind, value == null ? List.of() : value);
      if (!normalized.equals(this.get())) {
         this.blockCache = null;
         this.itemCache = null;
         this.entityCache = null;
         super.set(normalized);
      }
   }

   public int size() {
      return this.get().size();
   }

   public boolean isEmpty() {
      return this.get().isEmpty();
   }

   public boolean contains(String entry) {
      String normalized = normalize(this.kind, entry);
      if (normalized == null) {
         return false;
      } else if (this.kind == ListSetting.Kind.TEXT) {
         for (String stored : this.get()) {
            if (stored.equalsIgnoreCase(normalized)) {
               return true;
            }
         }

         return false;
      } else {
         return this.get().contains(normalized);
      }
   }

   public boolean add(String entry) {
      String normalized = normalize(this.kind, entry);
      if (normalized != null && !this.contains(normalized)) {
         List<String> next = new ArrayList<>(this.get());
         next.add(normalized);
         this.set(next);
         return true;
      } else {
         return false;
      }
   }

   public boolean remove(String entry) {
      String normalized = normalize(this.kind, entry);
      if (normalized == null) {
         return false;
      } else {
         List<String> next = new ArrayList<>(this.get());
         boolean removed = next.removeIf(stored -> this.kind == ListSetting.Kind.TEXT ? stored.equalsIgnoreCase(normalized) : stored.equals(normalized));
         if (removed) {
            this.set(next);
         }

         return removed;
      }
   }

   public boolean toggle(String entry) {
      if (this.contains(entry)) {
         this.remove(entry);
         return false;
      } else {
         this.add(entry);
         return true;
      }
   }

   public void clear() {
      this.set(List.of());
   }

   public Set<Block> blocks() {
      Set<Block> cached = this.blockCache;
      if (cached == null) {
         Set<Block> resolved = new LinkedHashSet<>();

         for (String id : this.get()) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null) {
               BuiltInRegistries.BLOCK.getOptional(identifier).ifPresent(resolved::add);
            }
         }

         cached = Collections.unmodifiableSet(resolved);
         this.blockCache = cached;
      }

      return cached;
   }

   public Set<Item> items() {
      Set<Item> cached = this.itemCache;
      if (cached == null) {
         Set<Item> resolved = new LinkedHashSet<>();

         for (String id : this.get()) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null) {
               BuiltInRegistries.ITEM.getOptional(identifier).ifPresent(resolved::add);
            }
         }

         cached = Collections.unmodifiableSet(resolved);
         this.itemCache = cached;
      }

      return cached;
   }

   public Set<EntityType<?>> entityTypes() {
      Set<EntityType<?>> cached = this.entityCache;
      if (cached == null) {
         Set<EntityType<?>> resolved = new LinkedHashSet<>();

         for (String id : this.get()) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null) {
               BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).ifPresent(resolved::add);
            }
         }

         cached = Collections.unmodifiableSet(resolved);
         this.entityCache = cached;
      }

      return cached;
   }

   public boolean isKnown(String entry) {
      String normalized = normalize(this.kind, entry);
      if (normalized == null) {
         return false;
      } else if (this.kind == ListSetting.Kind.TEXT) {
         return true;
      } else {
         Identifier identifier = Identifier.tryParse(normalized);
         if (identifier == null) {
            return false;
         } else {
            return switch (this.kind) {
               case BLOCK -> BuiltInRegistries.BLOCK.containsKey(identifier);
               case ITEM -> BuiltInRegistries.ITEM.containsKey(identifier);
               case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(identifier);
               default -> true;
            };
         }
      }
   }

   public ListSetting showIf(BooleanSupplier supplier) {
      this.setVisibleWhen(supplier);
      return this;
   }

   public ListSetting onChange(Runnable listener) {
      super.onChange(listener);
      return this;
   }

   public static enum Kind {
      BLOCK("block"),
      ITEM("item"),
      ENTITY("entity"),
      TEXT("entry");

      private final String noun;

      private Kind(String noun) {
         this.noun = noun;
      }

      public String noun() {
         return this.noun;
      }
   }
}
