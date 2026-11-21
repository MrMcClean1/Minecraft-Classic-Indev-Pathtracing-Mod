# Classic PT

A path tracer for two survival versions of early Minecraft: **Classic 0.30 (survival, `c0.30-s-1858`)** and
**Indev 20100223**, the last Indev. It is a sibling of the Alpha, Beta and Tekkit path tracers, cut down to what
these versions need: Classic and Indev keep the whole level in one array, so the level goes to the GPU as one
3D texture and one compute shader traces it.

Off when the game starts. **F7** turns it on and off.

What it traces: every block with the game's own terrain.png and face textures (read from the game's tile classes),
sunlight and its shadows, light from torches, fire and lava, two bounces of light ending in the sky, see-through
leaves and glass, plants, slabs, torches, water that reflects and refracts, the water and ground around the map.
Frames add up while the view stays still.

## NVIDIA RTX (opt-in)

On an RTX card, as in the Alpha, Beta and Tekkit tracers, all off by default and set in `.minecraft\classicpt.properties`
(these versions have no mod menu): `upscaler=dlss` (DLSS super resolution, its mode from `renderScale`: 1 DLAA,
0.67 Quality, 0.58 Balanced, 0.5 Performance, lower Ultra Performance), `rayReconstruction=true` (with DLSS:
NVIDIA's denoiser replaces the tracer's own), `frameGeneration=true` (with DLSS), `lowLatency=2` (Reflex).
They need NVIDIA's Streamline DLLs in `.minecraft\betapt-nvidia` (the same folder and files as the other tracers;
NVIDIA's files, not in this repo). The tracer stays OpenGL: its pictures go to a small Vulkan side (`vk/`, LWJGL 3
in a class loader of its own, packed into the jar) through memory both APIs share, Streamline runs there, and the
result comes back as an OpenGL texture.

## No loader, no other mods

Both versions run through MCPHackers LaunchWrapper in MultiMC. Classic PT is a LaunchWrapper tweak
(`--tweakClass`): it hands everything to the tweak LaunchWrapper would use anyway and adds two calls to the game's
bytecode (before the HUD, and each frame). MultiMC passes it from the instance's own component, so nothing else is
installed and the instance's JVM arguments stay as they are.

```
.\build.ps1                      # build\classicpt-1.0.0.jar (javac --release 8; libraries from MultiMC's folder)
.\install.ps1 c0.30-s-1858       # adds the component to that MultiMC instance
.\install.ps1 in-20100223
.\install.ps1 <instance> -Remove
```

Needs OpenGL 4.3 (compute shaders). The instances run Java 8.

## Test

An empty file `.minecraft\classicpt-test\run` arms one launch. The test needs no input and never takes the mouse
(it runs behind other windows): Indev makes a small level from its title screen; then it takes the game's own
picture, turns the tracer on, lets it add up and takes the traced one, places a block and checks it reaches the
GPU, and checks the toggle. With the word `rtx` in the run file it then runs DLSS (Performance) and Ray
Reconstruction and checks each against the native traced picture (`dlss.png`, `rr.png`). Report and pictures in
`classicpt-test\<date>\`, then the game closes.

## Classic Bots (Indev; Classic 0.30 to come)

The same bots as Alpha PT, Beta PT and the Beta modpack, in Indev 20100223: `/bot on`, `/bot spawn [name]`, then
talk to them ("Tess follow me", "Tess bring me 16 wood", "Tess set up a base here"...). Indev has no chat, so **T**
(or **/**) opens the bots' own chat box and their lines are drawn over the game. Bots are saved in the level
(`.mclevel`, entity id `ClassicBot`) and come back when it loads. Settings in `.minecraft\config\classicbots.properties`.

How the sources stay one set: the bots are Alpha PT's files (`bots\sync.ps1` copies them into `bots\src`, package
renamed), compiled against the game jar renamed to Alpha's Feather names (`tools\Remap.java named` with
`bots\indev.mappings`, hand-made from javap), then turned back to the game's own names (`Remap back`). What Indev lacks
or keeps differently (float positions, no block metadata setter with the id, squared `distanceTo`, a block-entity
getter that casts any block) is a `STUB` in the mappings, answered by `bots\indev\Compat.java`. The per-game files
(`Bots`, `Names`, `BotsClient`, `IndevBot`, `Entry`, chat, `BotTest`) live in `bots\indev`. The build fails if any
game reference doesn't resolve. Classic PT reaches the bots by reflection (`Bots.java`); `tweak\Hook` also lets the
level loader ask for unknown entity ids.

Test: an empty file `.minecraft\classicbots-test\run` arms one launch (in the background, muted, the mouse never
taken, the player can't be hurt): makes a level, spawns a bot, orders it to follow and moves the player 12 blocks
(the bot has to walk there, a teleport fails), saves the level with the game's own writer, reads it back (bot, owner,
things), opens it and checks the bot runs. Report in `classicbots-test\<date>\report.txt`; an empty
`classicbots-test\debug` adds the bots' debug log (`log.txt` while a test runs).

## Where things are in the games (javap)

| | Classic 0.30 survival | Indev 20100223 |
|---|---|---|
| Minecraft | `com.mojang.minecraft.l` | `net.minecraft.client.d` |
| hooked method | `l.run()`, HUD call `e.m.a(FZII)V` | renderer `net.minecraft.client.a.e.a(F)V`, HUD `c.l.a(F)V` |
| level | field `d`: `Level` (`width`, `depth` = y, `height` = z, `blocks`, `waterLevel`) | field `d`: `net.minecraft.a.a.g` (`a` x, `c` y, `b` z, `d` blocks) |
| player | field `f`; `x y z`, `xo yo zo`, `yRot xRot` | field `f`; `h i j`, `e f g`, `n o` (and `p q` old) |
| tiles | `level.tile.a.b[]`, face texture `b(int)` | `net.minecraft.a.a.b.x.c[]`, face texture `a(int)` |

Block index in both: `(y * zSize + z) * xSize + x`.

MIT licence.
