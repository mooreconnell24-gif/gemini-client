package com.inferno.fabric.checks;

import com.inferno.fabric.Alerts;
import com.inferno.fabric.InfernoConfig;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public abstract class Check {
   private final String id;
   private final String displayName;
   private final Check.Category category;
   private final String description;

   protected Check(String id, String displayName, Check.Category category, String description) {
      this.id = id;
      this.displayName = displayName;
      this.category = category;
      this.description = description;
   }

   public String id() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public Check.Category category() {
      return this.category;
   }

   public String description() {
      return this.description;
   }

   public boolean enabled() {
      return InfernoConfig.get().enabled(this.id);
   }

   protected void flag(ServerPlayer player, String detail) {
      Alerts.flag(player, this, detail);
   }

   protected double buffer(PlayerState s, double add) {
      double v = Math.max(0.0, s.buffers.getOrDefault(this.id, 0.0) + add);
      s.buffers.put(this.id, v);
      return v;
   }

   protected void decay(PlayerState s, double amount) {
      this.buffer(s, -amount);
   }

   protected <T> T data(PlayerState s, Supplier<T> create) {
      return (T)s.data.computeIfAbsent(this.id, k -> create.get());
   }

   public void onMove(ServerPlayer p, PlayerState s, Move m) {
   }

   public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
      return false;
   }

   public boolean onUseBlock(ServerPlayer p, PlayerState s, BlockHitResult hit) {
      return false;
   }

   public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
      return false;
   }

   public boolean onBreak(ServerPlayer p, PlayerState s, BlockPos pos) {
      return false;
   }

   public void onTick(ServerPlayer p, PlayerState s) {
   }

   public void onUseItem(ServerPlayer p, PlayerState s, InteractionHand hand) {
   }

   public void onClickSlot(ServerPlayer p, PlayerState s) {
   }

   public void onVelocity(ServerPlayer p, PlayerState s, Vec3 velocity) {
   }

   /** A movement-key packet arrived. {@code before} is the input the server held until now. */
   public void onInput(ServerPlayer p, PlayerState s, Input before, Input now) {
   }

   /** Dig / drop / swap / release packet ({@link ServerboundPlayerActionPacket}). */
   public void onAction(ServerPlayer p, PlayerState s, ServerboundPlayerActionPacket.Action action, BlockPos pos) {
   }

   /** Sprint / elytra / sleep command packet. */
   public void onPlayerCommand(ServerPlayer p, PlayerState s, ServerboundPlayerCommandPacket.Action action) {
   }

   /** Hotbar slot change. {@code from} is the slot the server had selected. */
   public void onCarriedSlot(ServerPlayer p, PlayerState s, int from, int to) {
   }

   /** The client finished one of its ticks; {@code tick} holds what it sent during that tick. */
   public void onClientTick(ServerPlayer p, PlayerState s, ClientTick tick) {
   }

   public void onRespawnRequest(ServerPlayer p, PlayerState s) {
   }

   public void onBookEdit(ServerPlayer p, PlayerState s, int pages) {
   }

   public void onChat(ServerPlayer p, PlayerState s, String message) {
   }

   public void onJoin(ServerPlayer p, PlayerState s) {
   }

   public static double[] direction(double yaw, double pitch) {
      double ry = Math.toRadians(yaw);
      double rp = Math.toRadians(pitch);
      double xz = Math.cos(rp);
      return new double[]{-xz * Math.sin(ry), -Math.sin(rp), xz * Math.cos(ry)};
   }

   public static String round(double v) {
      return String.format(Locale.ROOT, "%.2f", v);
   }

   public enum Category {
      COMBAT,
      MOVEMENT,
      WORLD,
      PLAYER,
      EXPLOIT;
   }
}
