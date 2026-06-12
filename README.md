# Duke's Descent

A procedural dungeon crawler starring Duke, the Java mascot, built for the 8-bit
challenge. Descend through randomly generated floors, fight Java-themed enemies,
loot effect-bearing gear of escalating rarity, crack open sealed vaults, dodge
mimic chests, topple floor bosses, and see how deep you can get before you fall.

This branch adds an **autopilot**: launch it and Duke plays himself — exploring,
fighting, looting, shopping, healing, and descending, restarting on death.

## Build & Run

Requires **JDK 25**.

This branch is autopilot-only — there is no manual play (and no `run` task).

```
./gradlew runBot     # watch the autopilot play in a window
./gradlew runBotSim  # fast-forward runs with a live stats panel, for tuning from the logs
./gradlew test       # game + bot unit tests
./gradlew clearLog   # fresh logs/autoplay.log
```

`runBot` paces menu screens so equips and purchases are readable, and ignores the
keyboard except **T** / **M** (mute music / all). `runBotSim` runs as fast as the CPU
allows and stops after `-Pruns=N` completed runs (default 10) or `-PsimMinutes=M`
simulated minutes (default 240). Bot tunables pass through the same way, e.g.
`-Pautoplay.kite=false`, `-Pautoplay.healAt=0.4`, `-Pautoplay.menuDelay=0`.

## The autopilot

The brain lives in the `autoplay` package and never touches game classes; it sees the
world through two small ports, implemented by one adapter in the default package
(`BotBridge`), which reads the game state and injects key events.

- **`WorldSnapshot`** — an immutable copy of the game state, captured once per frame:
  tiles and fog, player, enemies, loot, merchant, boss, inventory.
- **Behaviors** — one class per rule (`behavior/`), asked in priority order each frame;
  the first that claims the tick wins: restart on death, shop, manage gear, drink
  potion, dodge the boss slam, fight (kiting between swings), flee, hunt, open chests,
  collect loot, unlock vaults, visit the merchant, explore, descend.
- **Intents** — behaviors return declarative decisions (`MoveTo`, `AttackToward`,
  `Press`, ...); an executor turns the winning intent into held and pressed keys, with
  a BFS **navigator** that avoids traps and pits, smashes blocking scenery, and enters
  stairs only on purpose.
- **Safety nets** — a watchdog re-plans when no progress is made, unreachable targets
  are blacklisted per floor, and a failed tick can never crash the game loop.
- **Logging** — every decision, event (kills, pickups, equips, slams, descents), and
  run summary is written to `logs/autoplay.log` as grep-friendly `key=value` lines;
  tuning is a loop of run, read the log, adjust, rerun.

`BotMain` starts the windowed run, `BotSim` the headless one — same brain, the sim
just ticks the game with synthetic 16 ms frames as fast as it can, audio muted.

