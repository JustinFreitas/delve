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

See `../../.claude/plans/would-it-be-possible-wise-lantern.md` for the full milestone roadmap.

## Commands (current)

| Command | Description |
|---|---|
| `/ping` | Liveness check + gateway latency. |
| `/help` `[command]` | List commands, or detailed help for one. |
| `/roll-character <class> [name]` | Roll a new level-1 character (Cleric, Fighter, Magic-User, Thief, Dwarf, Elf, Halfling). |
| `/sheet` | Show your current character sheet. |

Commands also work via the message prefix (default `!`) or by mentioning the bot.
