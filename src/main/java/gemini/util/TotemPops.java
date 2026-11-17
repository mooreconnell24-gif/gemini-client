package gemini.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;

/**
 * Counts Totem of Undying pops per living entity.
 *
 * <p>The module manager feeds it every incoming entity-event packet and ticks it, whether or not any module that
 * shows the counts is on, so Nametags, TargetHUD, Totem Pop Counter and Pop Chams all read the same numbers.
 * Counts are kept per entity UUID in a {@link Ledger}; when they are forgotten is the {@link #setPolicy policy}
 * (by default when the entity dies), and always when the world changes.
 *
 * <p>Packets can arrive off the client thread, so a pop is handed to the client thread before anything is
 * counted; everything else here is client-thread only.
 */
public final class TotemPops {
   /** Forget an entity's pops when it dies. */
   public static final int RESET_ON_DEATH = 0;
   /** Forget them when it dies or leaves the loaded area. */
   public static final int RESET_WHEN_GONE = 1;
   /** Keep them until the world changes. */
   public static final int RESET_ON_WORLD_CHANGE = 2;
   private static final Ledger LEDGER = new Ledger();
   private static final List<Listener> LISTENERS = new ArrayList<>();
   private static IntSupplier policy = () -> RESET_ON_DEATH;

   private TotemPops() {
   }

   /** Told about pops and deaths on the client thread. */
   public interface Listener {
      /** {@code count} is the entity's total including this pop. */
      void onPop(LivingEntity entity, int count);

      /** An entity that had popped at least once died. */
      void onDeath(LivingEntity entity, int pops);
   }

   /** The bookkeeping, free of game classes so it can be unit-tested. */
   public static final class Ledger {
      private final Map<UUID, Entry> entries = new HashMap<>();

      /** One entity's record. */
      public static final class Entry {
         int count;
         int entityId;
         boolean dead;

         public int count() {
            return this.count;
         }

         public int entityId() {
            return this.entityId;
         }
      }

      /** Records a pop and returns the new total. A pop also means the entity is alive again. */
      public int pop(UUID id, int entityId) {
         Entry entry = this.entries.get(id);
         if (entry == null) {
            entry = new Entry();
            this.entries.put(id, entry);
         }

         entry.entityId = entityId;
         entry.dead = false;
         return ++entry.count;
      }

      public int count(UUID id) {
         Entry entry = this.entries.get(id);
         return entry == null ? 0 : entry.count;
      }

      /**
       * Notes that the entity is dead. Returns its pop count the first time this is reported for a death, and -1
       * when the entity never popped or the death was already reported. With {@code forget} the record is dropped.
       */
      public int died(UUID id, boolean forget) {
         Entry entry = this.entries.get(id);
         if (entry == null) {
            return -1;
         }

         boolean first = !entry.dead;
         entry.dead = true;
         if (forget) {
            this.entries.remove(id);
         }

         return first ? entry.count : -1;
      }

      /** The entity was seen alive: a later death is reported again. */
      public void alive(UUID id) {
         Entry entry = this.entries.get(id);
         if (entry != null) {
            entry.dead = false;
         }
      }

      public void forget(UUID id) {
         this.entries.remove(id);
      }

      public void reset() {
         this.entries.clear();
      }

      public int size() {
         return this.entries.size();
      }

      public boolean isEmpty() {
         return this.entries.isEmpty();
      }

      Map<UUID, Entry> entries() {
         return this.entries;
      }
   }

   public static void addListener(Listener listener) {
      if (listener != null && !LISTENERS.contains(listener)) {
         LISTENERS.add(listener);
      }
   }

   /** Supplies one of the {@code RESET_*} constants; read every tick so a setting change applies at once. */
   public static void setPolicy(IntSupplier source) {
      policy = source == null ? () -> RESET_ON_DEATH : source;
   }

   /** Pops recorded for the entity (0 when none). */
   public static int count(Entity entity) {
      return entity == null || LEDGER.isEmpty() ? 0 : LEDGER.count(entity.getUUID());
   }

   public static int count(UUID id) {
      return id == null ? 0 : LEDGER.count(id);
   }

   /** Number of entities with at least one recorded pop. */
   public static int tracked() {
      return LEDGER.size();
   }

   public static void reset() {
      LEDGER.reset();
   }

   /** Called for every incoming packet; only the totem entity event is looked at. */
   public static void onPacket(Packet<?> packet) {
      if (packet instanceof ClientboundEntityEventPacket event && event.getEventId() == EntityEvent.PROTECTED_FROM_DEATH) {
         ClientThread.run(() -> handle(event));
      }
   }

   private static void handle(ClientboundEntityEventPacket event) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null || !(event.getEntity(level) instanceof LivingEntity living)) {
         return;
      }

      int count = LEDGER.pop(living.getUUID(), living.getId());
      for (int i = 0; i < LISTENERS.size(); i++) {
         try {
            LISTENERS.get(i).onPop(living, count);
         } catch (RuntimeException e) {
            gemini.Gemini.LOGGER.warn("A totem pop listener failed", e);
         }
      }
   }

   /** Once per client tick: notices deaths and applies the reset policy. Costs nothing while nobody has popped. */
   public static void tick(ClientLevel level) {
      if (LEDGER.isEmpty() || level == null) {
         return;
      }

      int mode = policy.getAsInt();
      Iterator<Map.Entry<UUID, Ledger.Entry>> it = LEDGER.entries().entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, Ledger.Entry> slot = it.next();
         Ledger.Entry entry = slot.getValue();
         Entity entity = level.getEntity(entry.entityId);
         if (entity instanceof LivingEntity living && living.getUUID().equals(slot.getKey())) {
            if (living.isDeadOrDying()) {
               if (!entry.dead) {
                  entry.dead = true;
                  for (int i = 0; i < LISTENERS.size(); i++) {
                     try {
                        LISTENERS.get(i).onDeath(living, entry.count);
                     } catch (RuntimeException e) {
                        gemini.Gemini.LOGGER.warn("A totem pop listener failed", e);
                     }
                  }
               }

               if (mode != RESET_ON_WORLD_CHANGE) {
                  it.remove();
               }
            } else {
               entry.dead = false;
            }
         } else if (mode == RESET_WHEN_GONE) {
            it.remove();
         }
      }
   }
}
