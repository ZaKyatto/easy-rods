# Easy Rods

Server-side Fabric mod for Minecraft 26.3. Cast a fishing bobber onto a
pressure plate to hold it pressed; pull the rod from anywhere (any
distance, any dimension) to release it. Holds survive bobber despawn,
relogs, chunk unloads and server restarts. Vanilla clients can join.

Works on dedicated servers (vanilla clients can join) and in
singleplayer.

## Configuration

`config/easy-rods.json` (created on first start):

| Key | Default | Meaning |
| --- | --- | --- |
| `durabilityCostPerPull` | `1` | Durability used each time a rod releases a plate (0-1024). |
| `ownerOnlyPull` | `false` | Only the player who cast a rod can pull it. |
| `releaseChunkLoadTicks` | `100` | Ticks the plate's chunk stays loaded after a release (20-72000). Requires restart. |

## Development

- Run dev server: `./gradlew runServer`
- Build jar: `./gradlew build` (output in `build/libs`)

## License

CC0-1.0
