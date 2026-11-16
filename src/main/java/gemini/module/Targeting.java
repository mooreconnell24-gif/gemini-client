package gemini.module;

import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.render.Render3D;
import gemini.util.Damage;
import gemini.util.TargetPriority;
import gemini.util.Targets;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The shared "who to target" settings and the box drawn around the current target, for every module that picks a
 * target. Add one to a module the same way as {@link gemini.render.ShapeSettings}:
 *
 * <pre>
 * private final Targeting targeting = Targeting.builder(this).build();
 * ...
 * LivingEntity target = this.targeting.pick(player, level.entitiesForRendering(), range, extraCheck);
 * ...
 * public void onRender3D(float partialTick) { this.targeting.render(partialTick); }
 * public void onDisable() { this.targeting.clear(); }
 * public void onWorldChange() { this.targeting.clear(); }
 * </pre>
 *
 * <p>Settings it adds (names are stable config keys): {@code Players}, {@code Hostiles}, {@code Passives},
 * {@code Invisible} (unless the module supplies its own filter settings through the builder), {@code Priority},
 * {@code Prefer}, {@code Stick To Target}, {@code Target Box}, {@code Box Color}, {@code Health Color} and
 * {@code Box Through Walls}.
 *
 * <p>{@link #pick} makes one pass over the entities with no list and no sort.
 */
public final class Targeting {
   private final BooleanSupplier players;
   private final BooleanSupplier hostiles;
   private final BooleanSupplier passives;
   private final BooleanSupplier invisible;
   private final ModeSetting priority;
   private final ModeSetting prefer;
   private final BooleanSetting stick;
   private final ModeSetting box;
   private final ColorSetting boxColor;
   private final BooleanSetting healthColor;
   private final BooleanSetting boxThroughWalls;
   private LivingEntity target;

   private Targeting(Builder b) {
      Module m = b.module;
      BooleanSupplier show = b.visible;
      this.players = b.players != null ? b.players : m.addSetting(new BooleanSetting("Players", "Target other players (including the Fake Player dummy)", b.defPlayers).showIf(show))::get;
      this.hostiles = b.hostiles != null ? b.hostiles : m.addSetting(new BooleanSetting("Hostiles", "Target hostile mobs", b.defHostiles).showIf(show))::get;
      this.passives = b.passives != null ? b.passives : m.addSetting(new BooleanSetting("Passives", "Target passive animals (never your own pets)", b.defPassives).showIf(show))::get;
      this.invisible = b.invisible != null ? b.invisible : m.addSetting(new BooleanSetting("Invisible", "Also target invisible entities", true).showIf(show))::get;
      this.priority = m.addSetting(
         new ModeSetting(
               b.priorityName,
               "Which target goes first: the closest, the weakest or strongest, the one nearest your crosshair, by armour, or the one most dangerous to you right now",
               b.defPriority,
               TargetPriority.MODES
            )
            .showIf(show)
      );
      this.prefer = m.addSetting(
         new ModeSetting("Prefer", "Try players before mobs (or the other way round) before Priority orders the rest", TargetPriority.PREFER_ANY, TargetPriority.PREFER)
            .showIf(show)
      );
      this.stick = m.addSetting(
         new BooleanSetting("Stick To Target", "Keep the current target while it stays valid instead of switching whenever a better one appears", b.defStick)
            .showIf(show)
      );
      this.box = m.addSetting(
         new ModeSetting("Target Box", "Box drawn around the current target", b.defBox, "Off", "Outline", "Fill", "Both").showIf(show)
      );
      this.boxColor = m.addSetting(
         new ColorSetting("Box Color", "Colour of the target box", b.defHue).showIf(() -> show.getAsBoolean() && this.fixedColorShown())
      );
      this.healthColor = m.addSetting(
         new BooleanSetting("Health Color", "Colour the box by the target's health, green to red", false).showIf(() -> show.getAsBoolean() && !this.box.is("Off"))
      );
      this.boxThroughWalls = m.addSetting(
         new BooleanSetting("Box Through Walls", "Draw the target box on top of terrain", true).showIf(() -> show.getAsBoolean() && !this.box.is("Off"))
      );
   }

   /** Box Color only matters while the box is drawn and not coloured by health. */
   private boolean fixedColorShown() {
      return !this.box.is("Off") && !this.healthColor.get();
   }

   public static Builder builder(Module module) {
      return new Builder(module);
   }

   /** Whether {@code entity} passes the kind filters (players/hostiles/passives/invisible, teams, own pets). */
   public boolean accepts(LocalPlayer self, Entity entity) {
      return Targets.isCandidate(self, entity, this.players.getAsBoolean(), this.hostiles.getAsBoolean(), this.passives.getAsBoolean(), this.invisible.getAsBoolean());
   }

   /**
    * Picks the best target within {@code range} blocks (measured from the eyes to the hit box) that passes the kind
    * filters and {@code extra} (may be null), remembers it for {@link #render} and returns it, or null. With
    * Stick To Target the previous target is kept while it still qualifies.
    */
   public LivingEntity pick(LocalPlayer self, Iterable<Entity> entities, double range, Predicate<LivingEntity> extra) {
      if (self == null) {
         this.target = null;
         return null;
      }

      LivingEntity current = this.target;
      if (this.stick.get() && current != null && this.valid(self, current, range, extra)) {
         return current;
      }

      Vec3 look = self.getViewVector(1.0F);
      Vec3 eye = self.getEyePosition();
      String mode = this.priority.get();
      String preferMode = this.prefer.get();
      LivingEntity best = null;
      double bestScore = Double.POSITIVE_INFINITY;
      for (Entity entity : entities) {
         if (entity instanceof LivingEntity living && this.valid(self, living, range, extra)) {
            double score = this.score(self, living, eye, look, mode, preferMode);
            if (score < bestScore) {
               bestScore = score;
               best = living;
            }
         }
      }

      this.target = best;
      return best;
   }

   /** For modules that choose a target some other way (the crosshair, a hit): shows the box around it. */
   public void setTarget(LivingEntity entity) {
      this.target = entity;
   }

   public LivingEntity target() {
      LivingEntity current = this.target;
      return current != null && current.isAlive() && !current.isRemoved() ? current : null;
   }

   /** Forget the target: call from onDisable and onWorldChange. */
   public void clear() {
      this.target = null;
   }

   /** Draws the target box, if one is chosen and Target Box is not Off. Call from onRender3D. */
   public void render(float partialTick) {
      LivingEntity current = this.target();
      if (current == null || this.box.is("Off")) {
         return;
      }

      int color;
      if (this.healthColor.get()) {
         float max = current.getMaxHealth();
         color = TargetPriority.healthColor(max <= 0.0F ? 0.0 : current.getHealth() / max);
      } else {
         color = this.boxColor.argb() | 0xFF000000;
      }

      boolean lines = this.box.is("Outline") || this.box.is("Both");
      boolean fill = this.box.is("Fill") || this.box.is("Both");
      Render3D.entityBox(current, partialTick, 0.05, color, lines, fill, 2.0F, 40, this.boxThroughWalls.get());
   }

   private boolean valid(LocalPlayer self, LivingEntity living, double range, Predicate<LivingEntity> extra) {
      return this.accepts(self, living) && Targets.reach(self, living) <= range && (extra == null || extra.test(living));
   }

   private double score(LocalPlayer self, LivingEntity living, Vec3 eye, Vec3 look, String mode, String preferMode) {
      double dx = living.getX() - eye.x;
      double dy = living.getY() + living.getBbHeight() * 0.5 - eye.y;
      double dz = living.getZ() - eye.z;
      double distanceSq = dx * dx + dy * dy + dz * dz;
      double angle = TargetPriority.angleTo(look.x, look.y, look.z, dx, dy, dz);
      double threat = TargetPriority.MOST_DANGEROUS.equals(mode) ? this.threatOf(self, living, Math.sqrt(distanceSq)) : 0.0;
      return TargetPriority.score(mode, preferMode, distanceSq, Damage.health(living), living.getArmorValue(), angle, living instanceof Player, threat);
   }

   private double threatOf(LocalPlayer self, LivingEntity living, double distance) {
      boolean targetingYou;
      if (living instanceof Mob mob) {
         targetingYou = mob.getTarget() == self;
      } else if (living instanceof Player) {
         // A player looking straight at you (within 12 degrees) is treated as aiming at you.
         Vec3 their = living.getViewVector(1.0F);
         targetingYou = TargetPriority.angleTo(their.x, their.y, their.z, self.getX() - living.getX(), self.getEyeY() - living.getEyeY(), self.getZ() - living.getZ()) < 12.0;
      } else {
         targetingYou = false;
      }

      double damage = living.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? living.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
      boolean armed = Targets.isHostile(living) || living instanceof Player && damage > 1.0;
      return TargetPriority.threat(targetingYou, armed, damage, distance);
   }

   /** Options for {@link Targeting}; every one has a sensible default. */
   public static final class Builder {
      private final Module module;
      private BooleanSupplier players;
      private BooleanSupplier hostiles;
      private BooleanSupplier passives;
      private BooleanSupplier invisible;
      private boolean defPlayers = true;
      private boolean defHostiles = true;
      private boolean defPassives = false;
      private boolean defStick = false;
      private String defPriority = TargetPriority.CLOSEST;
      private String priorityName = "Priority";
      private String defBox = "Outline";
      private int defHue = 0;
      private BooleanSupplier visible = () -> true;

      private Builder(Module module) {
         this.module = module;
      }

      /** Use the module's existing filter settings instead of adding Players/Hostiles/Passives/Invisible (keeps old config keys). */
      public Builder filters(BooleanSupplier players, BooleanSupplier hostiles, BooleanSupplier passives, BooleanSupplier invisible) {
         this.players = players;
         this.hostiles = hostiles;
         this.passives = passives;
         this.invisible = invisible;
         return this;
      }

      /** Defaults for the added filter settings. */
      public Builder defaults(boolean players, boolean hostiles, boolean passives) {
         this.defPlayers = players;
         this.defHostiles = hostiles;
         this.defPassives = passives;
         return this;
      }

      public Builder priority(String defaultMode) {
         this.defPriority = defaultMode;
         return this;
      }

      /** A different setting name for the priority mode, for a module that already used "Priority" for something else. */
      public Builder priorityName(String name) {
         this.priorityName = name;
         return this;
      }

      public Builder stick(boolean defaultStick) {
         this.defStick = defaultStick;
         return this;
      }

      /** Default Target Box mode ("Off", "Outline", "Fill", "Both") and colour hue (0-360). */
      public Builder box(String defaultMode, int hue) {
         this.defBox = defaultMode;
         this.defHue = hue;
         return this;
      }

      /** Shows the targeting settings only while {@code visible} holds (for modules where targeting is optional). */
      public Builder showIf(BooleanSupplier visible) {
         this.visible = visible;
         return this;
      }

      public Targeting build() {
         return new Targeting(this);
      }
   }
}
