package com.inferno.fabric.checks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public final class TamperChecks {
   static final List<Holder<Attribute>> WATCHED = List.of(
      Attributes.MOVEMENT_SPEED,
      Attributes.GRAVITY,
      Attributes.JUMP_STRENGTH,
      Attributes.STEP_HEIGHT,
      Attributes.SCALE,
      Attributes.FRICTION_MODIFIER,
      Attributes.AIR_DRAG_MODIFIER,
      Attributes.MOVEMENT_EFFICIENCY,
      Attributes.ENTITY_INTERACTION_RANGE,
      Attributes.BLOCK_INTERACTION_RANGE,
      Attributes.BLOCK_BREAK_SPEED,
      Attributes.SAFE_FALL_DISTANCE
   );

   private TamperChecks() {
   }

   public static double vanillaValue(ServerPlayer p, Holder<Attribute> attribute) {
      AttributeInstance inst = p.getAttribute(attribute);
      if (inst == null) {
         return ((Attribute)attribute.value()).getDefaultValue();
      }

      double value = inst.getBaseValue();

      for (AttributeModifier m : inst.getModifiers()) {
         if (vanilla(m) && m.operation() == Operation.ADD_VALUE) {
            value += m.amount();
         }
      }

      double result = value;

      for (AttributeModifier m : inst.getModifiers()) {
         if (vanilla(m) && m.operation() == Operation.ADD_MULTIPLIED_BASE) {
            result += value * m.amount();
         }
      }

      for (AttributeModifier m : inst.getModifiers()) {
         if (vanilla(m) && m.operation() == Operation.ADD_MULTIPLIED_TOTAL) {
            result *= 1.0 + m.amount();
         }
      }

      return ((Attribute)attribute.value()).sanitizeValue(result);
   }

   static boolean vanilla(AttributeModifier m) {
      return "minecraft".equals(m.id().getNamespace());
   }

   public static boolean typedByPlayer() {
      return StackWalker.getInstance()
         .walk(
            frames -> frames.anyMatch(
               f -> {
                  String c = f.getClassName();
                  String m = f.getMethodName();
                  return c.equals("net.minecraft.server.network.ServerGamePacketListenerImpl")
                        && (m.equals("performUnsignedChatCommand") || m.equals("performSignedChatCommand"))
                     || c.equals("net.minecraft.world.level.block.entity.SignBlockEntity")
                     || c.equals("net.minecraft.world.level.BaseCommandBlock")
                     || c.equals("net.minecraft.server.ServerFunctionManager");
               }
            )
         );
   }

   public static final class AttributeTamper extends Check {
      public AttributeTamper() {
         super(
            "attribute-tamper",
            "Attributes (Modded)",
            Check.Category.MOVEMENT,
            "Speed, step, jump, gravity, reach or mining changed by a mod's attribute modifier (Step, HighJump, Glide, Reach...)."
         );
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         if (p.tickCount % 10 == 0) {
            TamperChecks.AttributeTamper.Seen seen = this.data(s, TamperChecks.AttributeTamper.Seen::new);
            long now = System.currentTimeMillis();

            for (Holder<Attribute> attribute : TamperChecks.WATCHED) {
               AttributeInstance inst = p.getAttribute(attribute);
               if (inst != null) {
                  for (AttributeModifier m : inst.getModifiers()) {
                     String key;
                     Long last;
                     if (!TamperChecks.vanilla(m)
                        && ((last = seen.reported.get(key = attribute.getRegisteredName() + " " + m.id())) == null || now - last > 30000L)) {
                        seen.reported.put(key, now);
                        this.flag(p, attribute.getRegisteredName().replace("minecraft:", "") + " changed by " + m.id() + " (" + Check.round(m.amount()) + ")");
                     }
                  }
               }
            }
         }
      }

      private static final class Seen {
         final Map<String, Long> reported = new HashMap<>();
      }
   }

   public static final class CommandInjection extends Check {
      public CommandInjection() {
         super(
            "command-injection",
            "Commands (Not Typed)",
            Check.Category.WORLD,
            "A command ran as you that you never typed: a client mod running gamemode, tp, tick rate... on your world's server."
         );
      }

      public void onCommand(ServerPlayer p, String command, boolean typed) {
         if (!typed && this.enabled()) {
            String shown = command.length() > 60 ? command.substring(0, 60) + "..." : command;
            this.flag(p, "ran /" + shown + " without typing it");
         }
      }
   }

   public static final class FlightPermission extends Check {
      public FlightPermission() {
         super(
            "flight-permission",
            "Flight (Permission)",
            Check.Category.MOVEMENT,
            "Allowed to fly in Survival: flight switched on inside your world's server by a client mod (Fly 'creative' modes)."
         );
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         if (!p.isCreative() && !p.isSpectator() && (p.getAbilities().mayfly || p.getAbilities().flying)) {
            TamperChecks.FlightPermission.Last last = this.data(s, TamperChecks.FlightPermission.Last::new);
            long now = System.currentTimeMillis();
            if (now - last.time > 10000L) {
               last.time = now;
               this.flag(p, "can fly in " + p.gameMode().getName() + " mode" + (p.getAbilities().flying ? " and is flying" : ""));
            }
         }
      }

      private static final class Last {
         long time;
      }
   }
}
