# delve

A text-based **B/X (Basic/Expert) Dungeons & Dragons** dungeon-crawler Discord bot.

Patterned on the [ukulele](../ukulele) music bot's skeleton — a Spring-DI command framework
with dual prefix + slash support, JDA event dispatch, and a cached persistence layer — with the
audio domain replaced by a deterministic B/X rules engine and a per-player game-session state
machine.

## Design at a glance

- **Solo play**, with the ability to recruit **retainers & hirelings** to form a party.
- **Deterministic rules engine** — no LLM/external API; B/X random tables + template text.
- **Full B/X ruleset** (classes, levels, XP, spells, saves, morale, encumbrance, treasure).
- **Procedural dungeons + authored set-pieces**.

## Stack

- Java 25 (virtual threads for command execution)
- Spring Boot 4 (DI, config, Spring Data JDBC persistence)
- JDA 6 (Discord gateway + slash commands; no audio module)
- H2 (file mode) + Flyway migrations + Caffeine cache

## Running

Set the bot token in the environment, then run:

```sh
DISCORD_TOKEN=your-token-here ./gradlew bootRun
```

Configuration lives in `src/main/resources/delve.properties` (token, prefix, activity, datasource).

## Build & test

```sh
./gradlew test        # unit + Spring context tests
./gradlew bootJar     # produces delve.jar
```

## Status

- **Milestone 1** — bootable skeleton (command framework, persistence + Flyway, `/ping`, `/help`).
- **Milestone 2** — character creation & sheet with full B/X mechanics (abilities + modifiers, the
  seven classes with requirements, HP from hit dice, AC, saving throws, starting gold/gear/spells),
  persisted per Discord user as a JSON save blob.
- **Milestone 3** — deterministic combat engine (no Discord), unit-tested: `AttackResolver`
  (THAC0 vs descending AC, nat-20/nat-1), `CombatTables` (class & monster THAC0 progressions),
  full per-level `SavingThrows`, `Advancement` (per-class XP tables), `DamageRoll`, and the
  `Monster`/`MonsterType` model with a starter `Bestiary`.
- **Milestone 4** — procedural exploration: `DungeonGenerator` (multi-level connected room graphs,
  Moldvay-style stocking, depth-appropriate monsters, secret doors, stairs) and `ExplorationService`
  (the state machine for `/enter` `/look` `/move` `/search` `/open`), with 10-minute dungeon turns,
  torch burn, wandering-monster checks, and trap springing.
- **Milestone 5** — combat loop: `CombatService` runs B/X side-initiative rounds (`/attack`, `/flee`),
  monster retaliation, morale checks, and 2d6 reaction rolls on encounter; victory awards XP and
  auto-levels the character (HP gain), defeat ends the delve.
- **Milestone 6** — retainers & hirelings: `/hire` `/party` `/dismiss`. Charisma caps the roster and
  sets loyalty; retainers fight alongside the PC (shared `Combatant`/`Advanceable` contracts), take
  hits, earn half-shares of XP, run a loyalty check when bloodied, and may desert in a rout.
- **Milestone 7** — full B/X depth: spells (`/cast`, `/prepare`) with the B/X slot tables, Magic
  Missile / Sleep / Cure Light Wounds; `/quaff` healing potions; encumbrance & movement rates on the
  sheet; richer treasure (potions in hoards); authored set-pieces stamped into generated levels
  (`content/setpieces.json`); and `/town` to rest, heal the party, pay retainer upkeep, and
  re-prepare spells between delves.

All seven milestones are complete. See `../../.claude/plans/would-it-be-possible-wise-lantern.md`
for the original roadmap.

## Commands (current)

| Command | Description |
|---|---|
| `/ping` | Liveness check + gateway latency. |
| `/help` `[command]` | List commands, or detailed help for one. |
| `/roll-character <class> [name]` | Roll a new level-1 character (Cleric, Fighter, Magic-User, Thief, Dwarf, Elf, Halfling). |
| `/sheet` | Show your current character sheet. |
| `/enter` | Begin a dungeon delve. |
| `/look` | Describe the current room (free). |
| `/move <dir>` | Move north/south/east/west, or up/down at stairs (one dungeon turn). |
| `/search` | Search for secret doors, traps and treasure (one dungeon turn). |
| `/open <dir>` | Open or force a door. |
| `/attack [n]` | Strike in combat (one round); optionally target enemy #n. |
| `/flee` | Flee combat to an adjacent room. |
| `/hire <class> [name]` | Recruit a retainer in town. |
| `/party` | List your character and retainers. |
| `/dismiss <name>` | Release a retainer. |
| `/cast <spell> [target]` | Cast a prepared spell (combat or utility). |
| `/prepare <spell>` | Memorize a spell into a free slot. |
| `/quaff` | Drink a potion of healing. |
| `/town` | Return to town: rest, heal, pay upkeep, re-prepare spells. |

Commands also work via the message prefix (default `!`) or by mentioning the bot.
