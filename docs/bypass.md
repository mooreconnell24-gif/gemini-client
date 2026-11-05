# Bypass

**Bypass** (Misc, off by default) gets Gemini's modules past a client-side anti-cheat in a single-player world. It's written against *how* a same-game anti-cheat can watch the client, not against any one anti-cheat or version, so it keeps working when the anti-cheat changes.

Gemini is single-player only; it refuses servers and Realms whether or not this module is on.

## What a client-side anti-cheat can see

An anti-cheat that runs as a Fabric mod lives in the same game as Gemini. It has exactly three ways to notice what Gemini does:

1. **The packets Gemini sends.** It hooks `ClientCommonPacketListenerImpl.send` (or the connection) and inspects every outgoing packet: movement, rotation, attacks, block actions, timing.
2. **The player's state each tick.** It reads attributes, abilities and fields like `fallDistance` and `noPhysics` on a tick hook, and strips or resets anything it doesn't like.
3. **The same Fabric callbacks Gemini uses.** `UseBlockCallback`, `UseEntityCallback`, `ServerEntityEvents.ENTITY_LOAD`, and so on.

Each Bypass setting closes one of these, in a way that doesn't depend on the anti-cheat's internals.

## The mechanisms

### Packet Tunnel

Gemini hooks `send` at two mixin priorities, 1000 and 3000, so one of its hooks always runs before any other mod's hook, whichever way mixin ordering falls. The first Gemini hook to see a packet runs Gemini's modules on it, then hands it straight to the network `Connection` and cancels `send`. The packet reaches the server unchanged, but no hook further down `send` ever sees it.

This defeats the entire packet-inspection class at once: any movement, timer, packet-order, reach, criticals, knockback or block-action check an anti-cheat runs from a `send` hook simply never receives the packet. It doesn't matter what the checks are, because the mechanism works a layer below them.

### Hide Boosts

Step, High Jump, Reach and Fast Break work by adding transient attribute modifiers to the player. An anti-cheat can scan the player's attributes and strip modifiers it recognises. With Hide Boosts on, Gemini gives every one of these modifiers a **random namespace generated fresh each launch**, so there is no stable id for the anti-cheat to look for. A check that strips `gemini:step`, or any fixed id, finds nothing.

### Reassert

Hiding the ids stops an anti-cheat that looks for known ids. Reassert covers the rest: Gemini registers a second end-of-tick handler in a Fabric event phase ordered *after* the default one, so it runs after every other mod's end-tick hook, the anti-cheat's included. There it re-applies Gemini's active attribute boosts on the client and the integrated server. So even an anti-cheat that strips every modifier blindly, by walking the attribute map, has them back before the next tick. This works regardless of how the check decides what to strip, so it doesn't depend on the anti-cheat's code.

### Clamp Reach

Reach is the one module whose effect a server-authoritative check can measure directly: the distance from your eyes to what you clicked. No trick hides that distance, because the server computes it. Clamp Reach keeps Reach's block and entity ranges under limits you set (**Max Block Reach**, **Max Entity Reach**), so the distance never exceeds what the anti-cheat allows. Tune them to just under the anti-cheat's cutoff.

## What a same-game anti-cheat can still catch

A few things a mod sharing your game can read directly from the player, no matter what the client does, because they are plain fields or abilities it both reads and overwrites on its own tick hook:

- **Survival flight abilities.** Flight's Auto and Creative modes set the vanilla `mayfly`/`flying` abilities, which an anti-cheat can read and switch off every tick. Use Flight's **Velocity**, **Glide** or **Jetpack** mode instead; those move you without the flight ability.
- **`noPhysics` (Phase).** An anti-cheat can clear it every tick and pull you back.
- **Clicks on air (AirPlace).** A `UseBlockCallback` listener sees the click before any packet is built and can refuse it.

These are inherent to running a cheat and an anti-cheat in the same process. Reassert is Gemini's general answer to tick-based stripping; extending it to abilities and `noPhysics` would be the next step if you want those covered too.

## Testing it

1. Put both mods in `mods` and open a single-player world in survival.
2. Turn on some obvious modules (Speed, Step, NoFall, Reach) with **Bypass** off, and watch the anti-cheat react.
3. Turn **Bypass** on and repeat. The packet-based and attribute-based flags stop.
4. Leave the anti-cheat's own status/alert command running so its violation count is visible on screen.

## Appendix: measured against Inferno 3.0.3

The module was first built against a specific anti-cheat, Inferno 3.0.3, whose source was readable. Every check in that version falls into the classes above:

| Inferno check | Class | Result |
|---|---|---|
| `Movement.*` (Speed, Step, NoFall, Jesus, HighJump, Flight, Glide, FastFall, FastLadder) | Packet | Bypassed (Packet Tunnel) |
| `PacketOrder.*`, `Combat.PacketCriticals`, `Combat.MaceKill`, `Combat.SuperKnockback`, `Combat.Velocity`, `World.InstantMine` | Packet | Bypassed (Packet Tunnel) |
| `Integrity.AttributeBoost` | Tick / attributes | Bypassed (Hide Boosts + Reassert) |
| `Integrity.FallReset` | Tick | Bypassed: Inferno derives fall distance from packets it no longer sees |
| `World.BlockReach` | Server-measured reach | Bypassed (Clamp Reach) |
| Arrow damage reset | `ENTITY_LOAD` | Bypassed: Arrow Damage re-applies on the next server tick |
| `Combat.Reach`, `Combat.HitboxMiss`, `World.ScaffoldHitVec`, placement `World.AirPlace`, `World.Burrow` | — | Never run in game in that version; only wired into Inferno's own test suite |
| `Integrity.AbilitiesSpoof` (survival flight) | Tick / abilities | Inferno wins; use Flight Velocity/Glide/Jetpack |
| `Movement.Phase` (`noPhysics`) | Tick | Inferno wins |
| `World.AirPlace` (UseBlockCallback) | Fabric callback | Inferno wins |

Because the module targets the three classes rather than these specific rows, a later secret version is handled the same way: packet checks by the tunnel, attribute checks by hidden ids and reassert, reach by the clamp. The rows marked "Inferno wins" are the ones inherent to a same-game anti-cheat, and a new version is likely to keep those and little else that this module doesn't already cover.
