# WildTp

A random "wild" teleport (`/wild`) for Paper 1.21+. Sends players to a random **safe** spot
in the world — never into lava, water, or the void — with a configurable radius and cooldown.

## Download

**[Download the latest release »](https://github.com/Standardan/wildtp/releases/latest)**

Drop the `.jar` into your server's `plugins/` folder and restart. Requires Paper 1.21+ (Java 21).

## Usage

- `/wild` (aliases `/rtp`, `/wilderness`) — teleport to a random safe location.

Permissions: `wildtp.use` (default: all), `wildtp.cooldown.bypass` (op).

## Configuration (`config.yml`)

```yaml
world: ""          # empty = player's current world
min-radius: 200
max-radius: 5000
max-attempts: 25
cooldown-seconds: 60
unsafe-blocks: [LAVA, WATER, FIRE, CACTUS, MAGMA_BLOCK, ...]
```

## Design notes

- **Fully asynchronous search.** Each candidate location's chunk is loaded with
  `getChunkAtAsync` so unexplored terrain generates **off the main thread**. The safety check
  (block reads, which must be on the main thread) is hopped back via the scheduler, and the
  final move uses `teleportAsync`. The server never freezes — even if it takes 25 tries to find
  solid ground far out in ungenerated terrain.
- **Real safety checks.** A spot is only accepted if the ground is solid and not in the unsafe
  list, with two passable blocks above for the player to stand in.
- **One search at a time per player**, with a per-player cooldown.

## Building

JDK 21 + Maven. `mvn clean package` → `target/wildtp-1.0.0.jar`.
