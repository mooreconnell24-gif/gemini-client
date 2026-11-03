# Inferno Bypass

**Inferno Bypass** (Misc) gets Gemini's modules past the Inferno anti-cheat (`inferno-anticheat-3.0.3.jar`) in a single-player world. Gemini still refuses servers and Realms, so this only matters in your own worlds with Inferno installed.

This page goes through every check in Inferno 3.0.3, what it looks at, and what happens to it with the bypass on.

## How Inferno works

Inferno is a Fabric mod that runs inside the same game as Gemini. It has three ways in:

1. **A packet hook.** A mixin on `ClientCommonPacketListenerImpl.send` (priority 2000) passes every outgoing packet to `InfernoMod.onOutboundC2SPacket`. Every movement, timer, packet-order, velocity, sprint and block-break check runs from there.
2. **An end-of-tick sweep.** On every client tick it strips Gemini's attribute boosts by their exact ids, takes flight away in survival, puts back fall distance that was zeroed, and clears `noPhysics`.
3. **Fabric callbacks.** `UseBlockCallback` (block clicks), `UseEntityCallback` (right-clicking entities) and `ServerEntityEvents.ENTITY_LOAD` (arrows).

## The three bypasses

### 1. Packet Tunnel

Gemini hooks the same `send` method. If a hook cancels `send`, every hook after it is skipped, so whichever hook runs first decides what the others see. Mixin priority sets that order. To avoid depending on which way it goes, Gemini hooks `send` twice, at priority 1000 and 3000, one either side of Inferno's 2000. The first Gemini hook to run lets Gemini's modules see the packet, then hands it straight to the network `Connection` and cancels `send`.

The packet still reaches the server unchanged. Inferno never sees it, so nothing it does from that hook can fire.

### 2. Hide Boosts

Inferno's integrity guard only knows five ids: `gemini:step`, `gemini:highjump`, `gemini:reach`, `gemini:entity_reach` and `gemini:fastbreak`. With Hide Boosts on, Gemini stores the same boosts as `gemini:boost_step` and so on, which the guard doesn't recognise.

### 3. Arrow re-apply and safe block reach

- Inferno resets every arrow's damage to 2 when it loads. Arrow Damage now sets its damage again on the next server tick, after every load listener has run.
- Inferno rejects any block click from more than 5.35 blocks away. With the bypass on, Reach keeps block range at 5.3.

## Every Inferno check

| Inferno check | What it catches | With Inferno Bypass on |
|---|---|---|
| `Movement.SimulationHorizontal` | Speed, NoSlow, LongJump | **Bypassed** (Packet Tunnel) |
| `Movement.Step` | Step | **Bypassed** (Packet Tunnel; Hide Boosts keeps the step attribute) |
| `Movement.NoFallSpoof`, `Movement.NoFallPacket` | NoFall | **Bypassed** (Packet Tunnel) |
| `Integrity.FallReset` | Zeroed fall distance (NoFall, Clutch, AntiVoid) | **Bypassed**: Inferno works out fall distance from move packets, so it never sees a fall to put back |
| `Movement.Jesus` | Jesus | **Bypassed** (Packet Tunnel) |
| `Movement.HighJump` | High Jump, Flight | **Bypassed** (Packet Tunnel; Hide Boosts keeps jump strength) |
| `Movement.AirAscend`, `Movement.GlideHover`, `Movement.FastFall` | Flight, Glide, Spider, FastFall | **Bypassed** (Packet Tunnel) |
| `Movement.FastLadder` | Fast Ladder | **Bypassed** (Packet Tunnel) |
| `PacketOrder.Timer` | Timer | **Bypassed** (Packet Tunnel) |
| `PacketOrder.MultiPacketBurst` | Click TP, Burrow | **Bypassed** (Packet Tunnel) |
| `PacketOrder.InvalidPitch` | Derp | **Bypassed** (Packet Tunnel) |
| `Combat.PacketCriticals`, `Combat.MaceKill` | Criticals | **Bypassed** (Packet Tunnel) |
| `Combat.SuperKnockback` | Super Knockback, W-Tap | **Bypassed** (Packet Tunnel) |
| `Combat.Velocity` | Velocity | **Bypassed** (Packet Tunnel). Without the bypass, Inferno skips movement checks for 10 ticks after you're hit anyway. |
| `World.InstantMine` | Instant Mine | **Bypassed** (Packet Tunnel) |
| `PacketOrder.InventoryAttack` | Attacking with the inventory open | **Bypassed** (Packet Tunnel) |
| `Player.NoRotateSet` | No Rotate Set | Never fires: Inferno's `verifyPostSetbackPacket` always returns true |
| `Integrity.AttributeBoost` | Step, High Jump, Reach, Fast Break | **Bypassed** (Hide Boosts) |
| `World.BlockReach` | Reach on blocks | **Bypassed** (Reach capped at 5.3 blocks) |
| Arrow sanitiser | Arrow Damage | **Bypassed** (re-applied a tick later) |
| `Combat.Reach`, `Combat.HitboxMiss` | Reach, KillAura, Hitboxes | Never runs in game: `validateAttack` is only called from Inferno's test suite, and `UseEntityCallback` only sees right-clicks, not attacks |
| `World.ScaffoldHitVec`, block-placement `World.AirPlace`, `World.Burrow` | Scaffold, AirPlace, Burrow | Never runs in game: `onInteractBlock` is only called from Inferno's test suite |
| `World.AirPlace` (UseBlockCallback) | AirPlace | **Inferno wins.** It refuses any click on an air block before the packet is built. |
| `Integrity.AbilitiesSpoof` | Flight in Auto or Creative mode | **Inferno wins.** It takes flight away every tick. Use Flight's Velocity, Glide or Jetpack mode instead; they don't need flight abilities. |
| `Movement.Phase` (`noPhysics`) | Phase | **Inferno wins.** It clears `noPhysics` every tick and pulls you back. |
| `Combat.Reach` on `UseEntityCallback` | Right-clicking an entity from more than 3.35 blocks | **Inferno wins.** It only affects right-clicks; attacks aren't checked. |

## Testing it

1. Put `inferno-anticheat-3.0.3.jar` and `gemini-3.0.0.jar` in `mods` and open a single-player world in survival.
2. Type `!inferno alerts` so Inferno posts its flags in chat.
3. With **Inferno Bypass** off, turn on Speed, Step or NoFall. Inferno flags them and pulls you back.
4. Turn **Inferno Bypass** on and repeat. The flags stop.
5. `!inferno` shows Inferno's total violation level. With the bypass on it stays at 0, except for the checks marked **Inferno wins**.
