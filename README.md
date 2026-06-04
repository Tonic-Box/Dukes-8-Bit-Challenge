# Duke's Descent

A procedural dungeon crawler starring Duke, the Java mascot, built for the 8-bit
challenge. Descend through randomly generated floors, fight Java-themed enemies,
loot effect-bearing gear of escalating rarity, crack open sealed vaults, dodge
mimic chests, topple floor bosses, and see how deep you can get before you fall.

![img.png](img.png)

## Build & Run

Requires **JDK 25**.

```
./gradlew run     # play
./gradlew size    # print the measured runtime size
./gradlew build   # compile + package
```

## Controls

| Action | Key |
| --- | --- |
| Move | WASD |
| Attack (also smashes breakable scenery) | Space |
| Interact - open shop, buy potion, open adjacent chest, equip selected item, confirm menu | E |
| Cancel - drink potion (in world), leave shop, close inventory | Q |
| Open inventory | I |
| Drop selected item (in inventory) | D |
| Navigate menus | W / S |
| Open a sealed vault door | Walk into it holding a key |
| Descend / ascend | Walk onto the gold / red stairs |
| Mute / unmute all audio | M |
| Mute / unmute music only | T |
| Pause / back out | Esc |

## Gameplay

- **Goal:** descend as far as possible. Score is the deepest floor reached.
- **Fog of war:** only tiles in line of sight are visible; explored tiles stay dimly remembered and a minimap in the top corner tracks the layout, stairs, vault doors, and enemies you have uncovered.
- **Enemies** (Bug, NullPointer, MemoryLeak, ForkBomb, Deadlock) wake only once they enter your light, then pursue in real time, pulsing as they strike. Numbers and stats scale gently with depth; a ForkBomb splits into Bugs on death and a Deadlock is a slow, heavy brute.
- **Gear, effects & rarity:** weapons, armor, and trinkets drop as loot and from chests. Beyond raw attack and defense they carry effects: lifesteal, crit, reach, poison, knockback, thorns, dodge, heal-on-kill, and trinket perks - that flash visibly when they proc. Every drop also rolls a rarity - Common, Rare, or Legendary - that scales both its stats and its effect strength; higher rarities are scarce early and grow more common with depth (and bosses roll with boosted odds). The inventory screen shows your stats and the exact +/- of equipping each item.
- **Chests & mimics:** chests are opened with **E** when you stand beside them - but one in seven is a **mimic**, a demi-boss that springs to life and attacks. Mimics hit harder than the floor's regular enemies and are guaranteed to drop at least a Rare when slain.
- **Breakable scenery:** pots, crates, and vases sit in rooms and corridors, blocking the way until a sword swing smashes them. A broken prop occasionally coughs up loot.
- **Pits:** clusters of black pit tiles open in room interiors (never blocking a corridor). Step into one and Duke tumbles 1–3 floors down, taking fall damage and landing somewhere random - the floors he falls past are generated and remembered, so climbing back up stays consistent.
- **Keys & vaults:** sealed vault rooms, gated by a locked door, hide one to three chests. A key dropped elsewhere on the floor opens one, and vaults grow more common the deeper you go.
- **Bosses:** every fifth floor is an arena guarding the way down - a large multi-tile boss with a telegraphed area slam (its reach shown as highlighted danger tiles), an enrage at half health that summons minions, and a stairway that unseals only once it falls.
- **Progression:** kills grant XP and gold; leveling raises max HP and attack. HP also regenerates slowly while exploring.
- **Merchant:** a shopkeeper spawns on each floor and sells potions for gold.
- **Persistent floors:** a floor remembers itself - climb back up and your cleared enemies, opened doors, looted chests, smashed scenery, and felled bosses stay as you left them.
- **Audio:** procedural sound effects (slash, hit, footstep, stairs, key, chest break, mimic, pit fall, boss slam) and a looping chiptune track, all synthesized at runtime. Mute everything with **M** or just the music with **T**.

## Architecture

Four classes, each with a single responsibility:

| Class | Responsibility |
| --- | --- |
| `Main` | Window, render loop, and keyboard input. |
| `Game` | All simulation: map generation, field of view, entities, combat, progression, and state. |
| `Renderer` | All drawing; stateless and derived entirely from `Game`. |
| `Sound` | Procedural MIDI: sound effects and a looping music track via the JDK synthesizer. |

Key decisions:

- **Data-oriented state.** The world is flat primitive arrays (`int[] map`, parallel enemy arrays) rather than an object hierarchy, no per-entity classes or allocations.
- **Real-time over discrete logic.** Player, enemy, and attack actions run on independent millisecond clocks and are interpolated each frame.
- **Fully procedural content.** Floors use rectangular rooms joined by corridors; visibility uses per-tile ray casting; sprites are composed of primitive shapes.
- **Procedural audio.** Effects are short synthesized blips; the music is a sequencer loop routed into the same JDK synthesizer on its own channels. Nothing is loaded from disk, so the resources directory stays empty.
- **Seeded then persistent floors.** A floor's first layout, enemies, and merchant are produced from a per-floor seed (`baseSeed + floor`); after that the floor's state is snapshotted and restored on return, so revisiting preserves your changes rather than regenerating.

## Code design notes

A few implementation choices and the reasoning behind them:

- **Input as flags drained by the loop.** Key events (AWT's event thread) only set held/request booleans; the game loop reads and acts on them. Simulation stays on a single thread, and discrete actions (potion, buy, pause, stairs) use edge detection so a held key fires once instead of repeating with the OS key-repeat.
- **Single integer game state.** `PLAYING` / `SHOP` / `PAUSED` / `DEAD` is one `int` switched on in update and render; a minimal state machine that needs no extra classes or enums.
- **Swap-remove entity arrays.** A dead enemy is removed by copying the last active entry over its slot and shrinking the count. Removal is O(1), order is irrelevant, and no objects are allocated or garbage-collected mid-run.
- **Ray-cast field of view.** Visibility casts a straight line to each tile within radius and stops at the first wall. Chosen for compactness over heavier shadow-casting; it only recomputes on movement, so the cost is negligible.
- **Integer-rounded camera.** The camera follows Duke's interpolated sub-pixel position but is rounded to whole pixels before drawing, so the scrolling dungeon stays crisp instead of shimmering.
- **Placement sanity checks.** The merchant is only placed where the surrounding 3×3 block is floor, guaranteeing it sits in open room space and can never wall off a corridor.
- **Directional avatar.** Duke is drawn as four facing sprites (front / back / left / right); the right profile is the left one mirrored with a transform, so only one side is hand-built.
- **Bosses.** A floor boss occupies a 3x3 footprint and lives in its own fields rather than the per-tile enemy arrays, keeping its multi-tile collision, telegraphed slam, and enrage phase cleanly separate from the lightweight regular enemies.
- **Per-floor state snapshots.** Leaving a floor copies its tiles, fog memory, enemies, loot, and boss into a cache keyed by depth; returning restores that snapshot. First visits still generate from the seed, so determinism and persistence coexist. Pit falls warp several floors at once, generating and caching each skipped floor on the way down so climbing back up is consistent.
- **Packed item instances.** A carried item is a single `int`: the template id in the low bits and the rolled rarity tier in the high bits. Loot, inventory, and equipment slots all stay primitive, rarity scaling is applied where stats are read, and the per-floor snapshot stores items as-is.
- **Smooth fog via an overlay table.** Each tile holds a light level that eases toward lit or unlit every frame. The renderer draws every tile in its lit form, then lays one of a small lookup table of translucent-black steps over it sized by that level - so lighting glides with the player with no per-frame allocation and no duplicate dim/lit color sets.

## Optimization strategies

Size is measured as the compiled `.class` files under `build/classes/java/main`.

- **Debug info stripped** (`-g:none`): line-number, local-variable, and source-file tables are removed from the bytecode while source remains fully readable.
- **No asset files:** both graphics and audio are generated at runtime.
- **Compact data:** enemy stats use small lookup tables, trivial one-call helpers are inlined, and the music score is packed into strings instead of array-init bytecode.
- **No per-frame allocation:** colors and the fog-overlay palette are hoisted into constants and lookup tables, polygon scratch buffers are shared, and entities are reused in fixed arrays, keeping peak memory low.
- Other minor optimizations explained inline with comments.
