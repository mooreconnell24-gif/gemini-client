package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.mixin.FishingBobberEntityAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ExtraChecks {
   private ExtraChecks() {
   }

   public static final class AutoFish extends Check {
      public AutoFish() {
         super("autofish", "AutoFish", Check.Category.WORLD, "Reeling in fish faster than a human can react, every time (AutoFish).");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         ExtraChecks.AutoFish.Fish f = this.data(s, ExtraChecks.AutoFish.Fish::new);
         FishingHook hook = p.fishing;
         if (hook != f.hook) {
            f.hook = hook;
            f.lastCountdown = 0;
            f.bite = 0L;
         }

         if (hook != null) {
            int countdown = ((FishingBobberEntityAccessor)hook).inferno$getHookCountdown();
            if (countdown > 0 && f.lastCountdown <= 0) {
               f.bite = InfernoMod.ENGINE.tickStart();
            } else if (countdown <= 0) {
               f.bite = 0L;
            }

            f.lastCountdown = countdown;
         }
      }

      @Override
      public void onUseItem(ServerPlayer p, PlayerState s, InteractionHand hand) {
         FishingHook hook = p.fishing;
         if (hook != null && p.getItemInHand(hand).getItem() instanceof FishingRodItem) {
            ExtraChecks.AutoFish.Fish f = this.data(s, ExtraChecks.AutoFish.Fish::new);
            int countdown = ((FishingBobberEntityAccessor)hook).inferno$getHookCountdown();
            long bite = f.bite;
            f.bite = 0L;
            if (hook != f.hook || countdown <= 0 || bite == 0L) {
               f.fast = 0;
            } else if (!InfernoMod.ENGINE.serverLagging()) {
               long reaction = System.currentTimeMillis() - bite;
               if (reaction < InfernoConfig.get().limit("autofish.max-reaction-ms")) {
                  if (++f.fast >= 8) {
                     this.flag(p, "reeled in " + reaction + " ms after the bite, " + f.fast + " catches in a row");
                  }
               } else {
                  f.fast = 0;
               }
            }
         }
      }

      private static final class Fish {
         FishingHook hook;
         int lastCountdown;
         long bite;
         int fast;
      }
   }

   public static final class AutoTotem extends Check {
      public AutoTotem() {
         super("autototem", "AutoTotem", Check.Category.COMBAT, "Refilling the off-hand totem instantly after it pops.");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         if (s.totemPop != 0L) {
            long elapsed = System.currentTimeMillis() - s.totemPop;
            if (elapsed > 2000L) {
               s.totemPop = 0L;
            } else if (isTotem(p.getOffhandItem())) {
               s.totemPop = 0L;
               long limit = 100 + Math.min(p.connection.latency(), 200) / 4;
               if (elapsed < limit) {
                  if (this.buffer(s, 1.0) >= 2.0) {
                     this.flag(p, "new totem in the off-hand " + elapsed + " ms after one popped");
                  }
               } else {
                  this.decay(s, 0.5);
               }
            }
         }
      }

      public static boolean isTotem(ItemStack stack) {
         return stack != null && String.valueOf(stack.getItem()).contains("totem_of_undying");
      }
   }

   public static final class BadPackets extends Check {
      public BadPackets() {
         super(
            "badpackets",
            "BadPackets (Rotation)",
            Check.Category.MOVEMENT,
            "Impossible head angle past straight up or down (Derp, broken KillAura/Scaffold rotations)."
         );
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (s.rawLook && Math.abs(s.rawPitch) > 90.001F && this.buffer(s, 1.0) >= 2.0) {
            this.buffer(s, -1.0);
            this.flag(p, "head pitch " + round(s.rawPitch) + " (the game only allows -90 to 90)");
         }
      }
   }

   public static final class InteractWalls extends Check {
      private static final String[] CONTAINERS = new String[]{"chest", "barrel", "shulker_box", "furnace", "smoker", "hopper", "dispenser", "dropper"};

      public InteractWalls() {
         super("interact-walls", "Interact (Walls)", Check.Category.WORLD, "Opening chests and containers through walls (GhostHand, ChestAura).");
      }

      @Override
      public boolean onUseBlock(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         BlockPos pos = hit.getBlockPos();
         ServerLevel w = p.level();
         String name = CheckEngine.blockId(w.getBlockState(pos));
         boolean container = false;

         for (String c : CONTAINERS) {
            if (name.contains(c)) {
               container = true;
               break;
            }
         }

         long now = System.currentTimeMillis();
         ExtraChecks.InteractWalls.Last last = this.data(s, ExtraChecks.InteractWalls.Last::new);
         if (container && (!pos.equals(last.pos) || now - last.time >= 100L)) {
            last.pos = pos;
            last.time = now;
            Vec3 eye = p.getEyePosition();
            Vec3 clicked = hit.getLocation();
            double[][] eyes = new double[][]{{eye.x, eye.y, eye.z}, {p.getX(), p.getY() + 1.62, p.getZ()}, {p.getX(), p.getY() + 1.27, p.getZ()}};
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            double[][] targets = new double[][]{
               {clicked.x, clicked.y, clicked.z},
               {x + 0.5, y + 0.5, z + 0.5},
               {x + 0.5, y + 0.9, z + 0.5},
               {x + 0.5, y + 0.1, z + 0.5},
               {x + 0.1, y + 0.5, z + 0.5},
               {x + 0.9, y + 0.5, z + 0.5},
               {x + 0.5, y + 0.5, z + 0.1},
               {x + 0.5, y + 0.5, z + 0.9}
            };

            for (double[] e : eyes) {
               for (double[] t : targets) {
                  if (clear(w, e, t, x, y, z)) {
                     this.decay(s, 0.5);
                     return false;
                  }
               }
            }

            if (this.buffer(s, 1.0) >= 2.0) {
               this.flag(p, "opened a container through a wall");
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }

      private static boolean clear(ServerLevel w, double[] eye, double[] target, int tx, int ty, int tz) {
         double dx = target[0] - eye[0];
         double dy = target[1] - eye[1];
         double dz = target[2] - eye[2];
         double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (length > 10.0) {
            return true;
         }

         int steps = (int)Math.ceil(length / 0.05);

         for (int i = 1; i < steps; i++) {
            double t = (double)i / steps;
            double px = eye[0] + dx * t;
            double py = eye[1] + dy * t;
            double pz = eye[2] + dz * t;
            int bx = (int)Math.floor(px);
            int by = (int)Math.floor(py);
            int bz = (int)Math.floor(pz);
            if (bx != tx || by != ty || bz != tz) {
               double fx = px - bx;
               double fy = py - by;
               double fz = pz - bz;
               if (!(fx < 0.05)
                  && !(fx > 0.95)
                  && !(fy < 0.05)
                  && !(fy > 0.95)
                  && !(fz < 0.05)
                  && !(fz > 0.95)
                  && w.getBlockState(new BlockPos(bx, by, bz)).isSolidRender()) {
                  return false;
               }
            }
         }

         return true;
      }

      private static final class Last {
         BlockPos pos;
         long time;
      }
   }

   public static final class NoSwingMining extends Check {
      public NoSwingMining() {
         super("noswing-mining", "NoSwing (Mining)", Check.Category.WORLD, "Breaking blocks without swinging the arm (NoSwing, packet miners).");
      }

      @Override
      public boolean onBreak(ServerPlayer p, PlayerState s, BlockPos pos) {
         long now = System.currentTimeMillis();
         if (s.digPos != null && s.digPos.equals(pos) && now - s.digTime <= 60000L) {
            s.digPos = null;
            if (s.lastSwing >= s.digTime - 100L) {
               this.decay(s, 0.5);
               return false;
            }

            ExtraChecks.NoSwingMining.Data d = this.data(s, ExtraChecks.NoSwingMining.Data::new);
            if (d.breaks.size() < 20) {
               d.breaks.add(new long[]{now, s.digTime});
            }

            return false;
         } else {
            return false;
         }
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         ExtraChecks.NoSwingMining.Data d = this.data(s, ExtraChecks.NoSwingMining.Data::new);
         long now = System.currentTimeMillis();

         while (!d.breaks.isEmpty()) {
            long[] br = d.breaks.get(0);
            if (s.lastSwing >= br[1] - 100L) {
               d.breaks.remove(0);
               this.decay(s, 0.5);
            } else {
               if (now - br[0] < 1500L) {
                  return;
               }

               d.breaks.remove(0);
               if (!InfernoMod.ENGINE.serverLagging() && this.buffer(s, 1.0) >= 3.0) {
                  this.buffer(s, -1.0);
                  this.flag(p, "broke a block without swinging");
               }
            }
         }
      }

      private static final class Data {
         final List<long[]> breaks = new ArrayList<>();
      }
   }

   public static final class NoWeb extends Check {
      public NoWeb() {
         super("noweb", "NoWeb", Check.Category.MOVEMENT, "Moving through cobwebs at normal speed (NoWeb, NoSlow).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.moved) {
            ExtraChecks.NoWeb.Last last = this.data(s, ExtraChecks.NoWeb.Last::new);
            AABB from = p.getBoundingBox().move(-m.dx, -m.dy, -m.dz).inflate(-0.01);
            boolean in = CheckEngine.blocksNear(p.level(), from, "cobweb");
            boolean judge = in && last.inWeb;
            last.inWeb = in;
            if (judge && m.checkable() && !m.liquid && !weaving(p)) {
               double d = Math.sqrt(m.dx * m.dx + m.dy * m.dy + m.dz * m.dz);
               if (d > 0.12) {
                  if (this.buffer(s, 1.0) >= 3.0) {
                     this.buffer(s, -2.0);
                     this.flag(p, round(d) + " blocks in one tick inside a cobweb (legit at most about 0.09)");
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.5);
               }
            }
         }
      }

      private static boolean weaving(ServerPlayer p) {
         for (MobEffectInstance effect : p.getActiveEffects()) {
            if (effect.getDescriptionId().toLowerCase(Locale.ROOT).contains("weaving")) {
               return true;
            }
         }

         return false;
      }

      private static final class Last {
         boolean inWeb;
      }
   }
}
