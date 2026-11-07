package com.inferno.fabric.checks;

public final class Move {
   public final double fromX;
   public final double fromY;
   public final double fromZ;
   public final double toX;
   public final double toY;
   public final double toZ;
   public final double dx;
   public final double dy;
   public final double dz;
   public final double horizontal;
   public final float yawDelta;
   public final float pitchDelta;
   public final float yaw;
   public final float pitch;
   public final boolean moved;
   public final boolean rotated;
   public final boolean ground;
   public final boolean lastGround;
   public final boolean clientGround;
   public final boolean special;
   public final boolean exempt;
   public final boolean liquid;
   public final boolean climbing;
   public final boolean sticky;
   public final boolean ice;
   public final boolean soul;
   public final boolean headBlocked;
   public final long time = System.currentTimeMillis();
   private boolean setback;

   Move(
      PlayerState s,
      double toX,
      double toY,
      double toZ,
      float yaw,
      float pitch,
      boolean ground,
      boolean clientGround,
      boolean special,
      boolean exempt,
      boolean liquid,
      boolean climbing,
      boolean sticky,
      boolean ice,
      boolean soul,
      boolean headBlocked
   ) {
      this.fromX = s.fromX;
      this.fromY = s.fromY;
      this.fromZ = s.fromZ;
      this.toX = toX;
      this.toY = toY;
      this.toZ = toZ;
      this.dx = toX - this.fromX;
      this.dy = toY - this.fromY;
      this.dz = toZ - this.fromZ;
      this.horizontal = Math.sqrt(this.dx * this.dx + this.dz * this.dz);
      this.yaw = yaw;
      this.pitch = pitch;
      this.yawDelta = wrap(yaw - s.fromYaw);
      this.pitchDelta = pitch - s.fromPitch;
      this.moved = this.dx != 0.0 || this.dy != 0.0 || this.dz != 0.0;
      this.rotated = this.yawDelta != 0.0F || this.pitchDelta != 0.0F;
      this.ground = ground;
      this.lastGround = s.lastGround;
      this.clientGround = clientGround;
      this.special = special;
      this.exempt = exempt;
      this.liquid = liquid;
      this.climbing = climbing;
      this.sticky = sticky;
      this.ice = ice;
      this.soul = soul;
      this.headBlocked = headBlocked;
   }

   public boolean checkable() {
      return this.moved && !this.special && !this.exempt;
   }

   public boolean airborne() {
      return this.checkable() && !this.ground && !this.lastGround && !this.liquid && !this.climbing && !this.sticky;
   }

   public void setback() {
      this.setback = true;
   }

   public boolean wantsSetback() {
      return this.setback;
   }

   public static float wrap(float d) {
      if ((d = d % 360.0F) >= 180.0F) {
         d -= 360.0F;
      }

      if (d < -180.0F) {
         d += 360.0F;
      }

      return d;
   }
}
