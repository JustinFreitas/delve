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
- **Character seeding** — build table-ready PCs/NPCs at a target level (instantly, or by simulated
  delving) and export them, e.g. to seed a level-5 Desert of Desolation party. Output is pure B/X,
  so it drops into an OSE-compatible game as-is.

- **Web interface (optional)** — play and use the tools in a browser as well as Discord. Logs in with
  **Discord OAuth**, so the web session uses your Discord user id and shares the **same character/party
  /delve** as the bot. Off by default; see [Web interface](#web-interface).

## Stack

- Java 25 (virtual threads for command execution)
- Spring Boot 4 (DI, config, Spring Data JDBC persistence)
- JDA 6 (Discord gateway + slash commands; no audio module)
- Spring Web + Spring Security (optional web interface; OAuth2 login with Discord)
- H2 (file mode) + Flyway migrations + Caffeine cache

## Running

Set the bot token in the environment, then run:

```sh
DISCORD_TOKEN=your-token-here ./gradlew bootRun
```

Configuration lives in `src/main/resources/delve.properties` (token, prefix, activity, datasource).

## Web interface

Optional browser front-end over the **same services and saves** as the bot — it's a second front-end,
not a fork. Authentication is **Discord OAuth**: because Discord's `/users/@me` id is the same id JDA
uses, logging in on the web gives you the exact same character/party/delve as the bot (play on the web,
continue in Discord, seamlessly). Off by default (`config.web.enabled=false`) so the bot can run
headless.

To enable it:

1. Create a **Discord application** (Developer Portal → OAuth2), add a redirect URI
   `https://your-host/login/oauth2/code/discord` (or `http://localhost:8080/login/oauth2/code/discord`
   locally), and copy the client id/secret.
2. Run with the web env vars:
   ```sh
   DISCORD_TOKEN=… \
   WEB_ENABLED=true \
   DISCORD_OAUTH_CLIENT_ID=… DISCORD_OAUTH_CLIENT_SECRET=… \
   WEB_ALLOWED_USER_IDS=<your-discord-id>[,more]   # optional allowlist; empty = any Discord user \
   ./gradlew bootRun
   ```
3. Open the host, **Log in with Discord**, then play (roll/pregen a PC, enter a module, fight) and use
   the DM tools (pregen/roster/npc, export to text/JSON) from the sidebar.

The web UI is a lean, dependency-free static page (`src/main/resources/static/`) talking to a REST API
(`/api/**`) via a shared `GameFacade`. **The web app holds no Anthropic key** — PDF→module conversion
stays the offline `importModule` task; the web only lists and plays already-converted modules.

**Public deployment:** terminate TLS at a reverse proxy (the app sets `forward-headers-strategy` so
OAuth redirects resolve to `https://`), set `WEB_COOKIE_SECURE=true`, set `WEB_ALLOWED_USER_IDS` to the
people you actually want, and rely on the built-in CSRF protection and per-user rate limit
(`config.web.requests-per-minute`).

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
  hits, earn half-shares of XP, run a loyalty check when bloodied, and may permanently desert on
  `/flee` — but only after a genuinely bad fight (the PC bloodied, or a companion already down this
  delve), and only retainers who'd already broken and sat out from being bloodied themselves; anyone
  who held steady through the fight just retreats with the party, no roll.
- **Milestone 7** — full B/X depth: spells (`/cast`, `/prepare`) with the B/X slot tables, Magic
  Missile / Sleep / Cure Light Wounds; `/quaff` healing potions; encumbrance & movement rates on the
  sheet; richer treasure (potions in hoards); authored set-pieces stamped into generated levels
  (`content/setpieces.json`); and `/town` to rest, heal the party, pay retainer upkeep, and
  re-prepare spells between delves.

- **Milestones 8–11 — character seeding** (build PCs/NPCs up to a target level for a real campaign):
  - **M8** — `Leveling.advanceTo` + `PregenService` + `MagicItemTable`; `/pregen <class> [level] [name]`
    instantly builds a finished, table-ready character (rolls qualifying abilities, advances the level
    rolling HP per level, scales armor/wealth, grants a few B/X magic items, prepares spells).
  - **M9** — export: `/export [embed|text|json]` (Discord embed, copy-paste stat block, or a JSON file
    via the new `PregenExport` schema + `CommandContext.replyFile`).
  - **M10** — `/autodelve [level] [fast|bx]`: a headless autopilot that fast-forwards a character via
    simulated delves. **Default pace is by-the-book B/X OSE** (organic monster + 1-XP-per-gp treasure;
    authentic and slow, may end "exhausted" to be resumed); add `fast` for ~one level every 3–4 delves.
    The autopilot plays cautiously (avoids deadly rooms, flees losing fights) but the PC can still die.
  - **M11** — DM tools: `/roster <count> <level> [class]` mints a party of pregens (summary + combined
    JSON file); `/npc <class> <level> [name]` generates a single named NPC (embed + stat block + JSON).
    Both are stateless — they never overwrite your own character.

- **Modules from a PDF** — run a published B/X module instead of a generated dungeon. An **offline,
  one-time** converter (the `importModule` Gradle task) sends a module — a FineReader **searchable PDF**
  (corrected OCR text + maps, ingested natively by Claude) or a **TXT/Markdown** export — to the Claude
  API and writes an editable `content/modules/<name>.json` (rooms, exits, monsters, treasure, traps).
  `ModuleLoader` maps that JSON into the runtime `Dungeon` (exits made bidirectional, monsters resolved
  against the `Bestiary`), and `/enter <module>` plays it through the same exploration/combat engine.
  **The running bot has no API dependency** — the Anthropic SDK lives only in the importer source set.
  Process only your own legally-obtained PDFs; the extracted JSON stays local.

  ```sh
  ANTHROPIC_API_KEY=… ./gradlew importModule --args="--pdf=my-module.pdf --name=mymodule"
  # review content/modules/mymodule.json, then in the bot:  !loadmodule   →   !enter mymodule
  ```

- **Web interface** — optional browser front-end (Spring Web + Spring Security, **Discord OAuth**,
  shared saves) exposing play, DM tools, and module running over a REST API + lean static UI; a shared
  `GameFacade` backs both web and Discord. Public-deployment hardening (CSRF, per-user rate limit,
  Discord-id allowlist, forwarded-headers for TLS) included; the web app stays Anthropic-key-free.
  See [Web interface](#web-interface).

- **Milestone 12** — combat, formation & exploration overhaul:
  - **Marching order & ranked combat** — `/order` sets a party formation (front to back); corridor/room
    width (1-3 abreast, Gygax75-style) caps how many stand in a rank. Monsters can only strike the
    front-most living occupant of each file (`Formation`); a fallen or unfilled file exposes the next
    occupant behind it, independent of neighboring files. Reach weapons melee from rank 2 past a living
    rank-1 file-mate; missile weapons (`WeaponCatalog`, `/wield`) fire from rank 2+ with real B/X
    Short/Medium/Long range bands, closing each round at the monster's move rate.
  - **Surprise** (2-in-6 each side) and abstract engagement range on combat start; the front rank can
    `/pole` ahead for passive trap detection, at the cost of an AC penalty if surprised while poling.
  - **Reaction rolls** keep their existing 2d6+CHA roll for unscripted encounters, but authored modules
    can now script a fixed `HOSTILE`/`NEUTRAL`/`FRIENDLY` disposition that overrides it.
  - **Monster morale** is now edge-triggered (checked once on the first casualty, once more at half
    losses) instead of re-rolling every round after any loss.
  - **Corridor traps and treasure traps** extend the existing room-trap model: passages can be trapped
    and sprung on traversal, and treasure can be independently trapped, requiring a disarm attempt
    (`ThiefSkills.removeTraps`, a Thief-exclusive scaling chance; everyone else a flat low fallback) —
    giving the Thief class real mechanical identity. Dwarves and Elves gain a passive sense (automatic,
    no `/search` needed) for room/corridor traps and secret doors respectively, reusing their existing
    active-search bonuses.
  - **Progression tuning** — the character sheet now tracks total delves (manual or autodelve); dungeon
    treasure chance and value are bumped a notch above strict by-the-book odds (gold recovery is the
    main XP lever, and it was landing too slowly); treasure found is split PC/retainers the same way
    combat XP already is (a full share to the PC, a half-share per living retainer), so the PC's own
    gold/XP reflects their cut, not the whole hoard; and `/autodelve`'s milestone log now reports every
    engaged encounter and its outcome, not just the notable ones.
  - **Bulk hiring & toughness-based default order** — `/hire <class> all` / `/hire smart all` (also just
    `/hire all`, the default) / `/hire random all` fills every retainer slot you can afford in one
    command (single class, a front-loaded tankiest-first mix, or a uniform random mix). The default
    marching order now ranks the whole party — retainers *and* the PC — by measured toughness (AC
    ascending, then max HP descending, via the shared `Toughness` comparator) rather than always
    tacking the PC on last, so a heavily armored Fighter/Dwarf PC defaults toward the front and a
    Magic-User PC still defaults toward the back; `/order` still overrides this explicitly.
  - **Melee + missile starting kits** — every PC and retainer class gets a cheap missile backup
    alongside its melee weapon wherever B/X class fiction allows (a sling for most, matching the
    existing Elf short bow / Halfling sling) — only the dagger-only Magic-User is excluded. For the
    PC this just means the sling is in inventory for `/wield`; retainers (who have no `/wield`) now
    carry a `secondaryWeapon` and automatically loose it from rank 2+ before melee closes, instead of
    standing idle with a sword no one can swing yet.
  - **Two hands, and someone has to carry the light** — every combatant has two hands (`Hands`): a
    two-handed weapon, a shield, and physically holding the party's lit torch/lantern each cost one, so
    a sword-and-board Fighter adventuring alone has none left for their own torch — the classic B/X
    reason hirelings exist. `/light [torch|lantern]` lights (or checks the status of) the shared light
    source — a torch is cheap and burns 6 turns per unit, a lantern costs more but burns 24 turns per
    flask of oil; `/buy <torch|lantern|oil> [qty]` restocks both in town. The party auto-picks a
    free-handed retainer over the PC when nobody's carrying it, `/torchbearer [name]` reassigns it by
    hand, and `/wield shield`/`/wield unshield` adjusts your own shield — all rejected outright if it
    would leave nobody's hands free for what they're holding. `/autodelve` accounts for this too: if the
    whole rolled party would otherwise have no free hand for a torch (e.g. an all-Fighter roster), it
    has the PC drop their shield before descending rather than silently retreating in darkness on turn
    one, every delve, forever. Rolling a fresh character (`/roll-character`) also now starts a clean
    dungeon session instead of inheriting a previous character's in-progress delve/light state.
  - **`/autodelve`'s verbose log explains its XP math** — `LogDetail.VERBOSE` now shows the raw award
    and the prime-requisite percentage behind every XP line (e.g. `Gained 20 XP (23 × 90%, total 20).`),
    since the terse total alone (after a party gold-share split and a class's STR/INT/etc. adjustment)
    can look like a miscalculation. `MILESTONES` mode is unchanged — it never showed individual XP lines.
  - **`/autodelve` defaults to verbose, and shows the party afterward** — `verbose` is now the default
    log detail (pass `milestones` for the old curated-summary behavior instead), and every run now ends
    with the same party listing `/party` shows (rank, hands, light-bearer status for the PC and every
    retainer) — extracted into a shared `PartySummary` so both commands render it identically.

- **Milestone 13** — multi-PC party support, Phase 1 (foundation, roster, combat): `SaveGame` now holds
  up to **8 PCs** per save (`characters`, a list) instead of exactly one. `getCharacter()`/`setCharacter()`
  are kept permanently as the "primary/first PC" accessor — every not-yet-updated command still works
  unchanged. Old saves' singular `"character"` JSON key still loads correctly (a `@JsonIgnore` getter +
  `@JsonProperty("character")` setter bridge), so nobody's existing character was at risk; every save
  upgrades itself to the new `"characters"` shape on its next write. `/roll-character` is now additive —
  the first roll starts your party as before, every roll after that adds another PC (up to the cap)
  without disturbing an in-progress delve. `/attack`, `/cast`, and `/turn` all take an optional leading
  PC name (e.g. `/attack Bram 2`) to give that PC the explicit action for the round; every other living
  PC and all retainers still auto-attack. Combat only ends in defeat once every living PC is down, not
  just the first one to fall; XP and treasure shares now split across every living PC plus a half-share
  per retainer. See [Multi-PC party support](#multi-pc-party-support-phase-1) below for what's still
  Phase 2+.

- **Milestone 14** — best-effort auto-gear: `/roll-character` now spends a fresh PC's rolled gold for
  them by default (`Outfitter`), buying a class-appropriate weapon, armor, and shield — degrading
  tier-by-tier (chain mail → leather → none) rather than buying nothing on a poor gold roll — plus a
  few torches for light, using the same `GearCatalog` prices `/buy` does. Common flavor gear (rope,
  rations, a backpack) is skipped since it has no mechanical effect. A trailing `bare` token (e.g.
  `/roll-character fighter Bob bare`) opts back into the old manual-shopping flow for players who want
  to make their own gearing choices. A standalone `/outfit [pc-name]` command runs the same auto-gear
  pass against an already-rolled PC — for one made `bare`, one from before this feature existed, or one
  you only partly shopped for by hand.
- **Milestone 15** — `/hire` supports hiring to a specific PC: an optional leading PC-name argument
  (`/hire Bram fighter Conan`, matching `/attack`/`/cast`/`/turn`'s existing pattern) says whose gold
  pays the hiring fee and whose Charisma governs the loyalty roll and the retainer-count cap for that
  hire — no more being stuck with only the first-rolled PC's Charisma/purse in a multi-PC party. The
  hired retainer still joins the one shared, party-wide retainer pool afterward (combat, XP, upkeep,
  desertion are all unchanged). `/party`'s displayed Charisma cap now reflects the highest cap across
  every living PC, matching the new hiring behavior.
- **Milestone 16** — party-size visibility and a whole-party best-effort hire:
  - `/party` now shows each PC's own open retainer slots (their Charisma cap minus the party's current
    retainer count) right on their row, so it's no longer a guessing game which PC to name in `/hire`.
  - `/hire party [target]` best-effort fills the whole party toward a total headcount (PCs + retainers)
    — defaults to 9 (the wandering-monster penalty threshold), or a number like `/hire party 18`. Each
    hire is paid for by whichever living PC currently has the most gold among those who can still afford
    it and haven't hit their own Charisma cap, stopping short (rather than erroring) once nobody
    qualifies; the reply reports who paid for each new retainer.
  - Fixed a pre-existing bug: the wandering-monster check's party-size count was hardcoded to
    `1 + retainers` (assuming exactly one PC), silently undercounting any multi-PC party and delaying
    the penalty past its intended threshold. Now counts every living PC.
  - `/sheet [pc-name]`, `/quaff [pc-name]`, and `/prepare [pc-name] <spell>` — three of the ~9 remaining
    single-PC commands to get the multi-PC treatment: an optional PC-name argument (defaults to the
    first-rolled PC), same `save.resolve()` pattern as `/attack`/`/outfit`. Each PC already carries their
    own potions and memorized spells, so naming a specific PC for `/quaff`/`/prepare` closes a real gap,
    not just cosmetic parity.

All milestones are complete (225 tests green). See `../../.claude/plans/would-it-be-possible-wise-lantern.md`
for the roadmap.

- **House rules from `gygax75-rules`** — a separate house-ruled B/X reference (`DM Justin`'s own rules
  doc) was scanned against delve's implementation and ported in four passes: dungeon procedure gaps
  (listening at doors, rest-per-hour fatigue, party-size wandering-monster scaling), combat nuance
  (5-tier reaction table, energy drain via a new Wight monster, situational morale modifiers), retainer
  stakes & downtime economy (permadeath on flee, multi-day `/town [days]` rest at 1d3 hp/day, retainer
  starting gold), and character/class fixes (dual-prime-requisite XP averaging, a hit-die reroll floor,
  Thief backstab, Cleric `/turn` undead). Three more items were picked up afterward from the deferred
  backlog below: two-weapon fighting (`/wield offhand <item>`/`unoffhand` grants +1 to melee attack, no
  extra attack/damage — "no shield while dual-wielding" falls out of the existing two-hands budget for
  free), evasion/pursuit on `/flee` (the fleeing side auto-escapes if faster than the pursuer, per
  `Encumbrance`'s existing encounter-movement-rate math vs. `MonsterType.moveRate()` — no new
  movement-rate field needed after all; failing to be faster gets one flat 2-in-6 chance to still shake
  them, standing in for the rulebook's separate obstacle/dropped-loot/line-of-sight checks), and item
  costs + guided shopping + sell/haggle: a new `GearCatalog` gp price list for weapons/armor/gear;
  `/roll-character` now creates a bare, unequipped character underneath (spending their rolled gold via
  the extended `/buy <item>` — weapons/gear to inventory, armor/shield equipped directly — is still
  available and is what `bare` rolls fall back to), instead of the old free kit — pregen/roster/NPC
  generation keep the old instant-equip path untouched; and a new `/sell <item>` sells gear back at 10%
  of its price plus a haggle bonus (2d6 + CHA modifier, up to +25%). Gems/jewelry (already convert
  straight to gold at loot time) and scrolls (delve has no physical scroll item) aren't sellable, since
  neither is modeled as a distinct item. (Milestone 14 below layers a best-effort auto-gear default back
  on top of this bare foundation.)

### Known gaps (house rules not yet ported)

Each pass above deliberately deferred anything needing a subsystem delve doesn't have, rather than
half-build it:
- **Doors**: swing-shut-behind-you, a spike/wedge command, one-way doors — needs new `Exit`/`DoorState`
  states plus module-authoring support.
- **Combat**: attacking from behind (no flanking/facing concept in `Formation`); evasion's own running-
  exhaustion and obstacle/dropped-loot/line-of-sight sub-rules (see above — folded into one flat roll
  instead of modeled individually).
- **Economy**: Inn-tier resting costs and generosity-tier hiring, treasure storage, magical research,
  rune transferring — the last three have nothing to act on since delve has no magic-item system beyond
  healing potions.
- **Classes**: the custom demihuman classes (Barbarian, Druid, Knight, Warden, Gnome, Half-Orc, Wood
  Elf), expertise-point thief skills beyond Remove Traps (needs lockpicking/stealth subsystems that
  don't exist), alignment/languages (no mechanical hook to attach them to), true coin-weight
  encumbrance (would replace the existing tested `Encumbrance` model wholesale), and the one-level-per-
  session XP cap / alternate reroll-all-HD leveling method.

### Multi-PC party support (Phase 1)

Discussed alongside the sell/haggle pass: once starting gear costs real gold, a single new character
may be too fragile to survive to advance, so Justin asked for support for **up to 8 PCs per party**
with total party size (PCs + retainers + hirelings + mules) **under 18**. The wandering-monster penalty
above 9 party members was **already implemented** (`ExplorationService.wanderingMonsterTriggered`) and
already matched this. Phase 1 (see Milestone 13 above) shipped the rest of the foundation: the data
model, roster commands, and full combat integration. Still Phase 2+, deferred deliberately rather than
half-built:
- **~6 remaining commands** still act only on the first-rolled PC: `/wield`, `/buy`, `/sell`, `/enter`,
  `/export`, `/light`. Each needs the same optional-PC-name-argument treatment `/attack`/`/sheet`/
  `/quaff`/`/prepare` already got.
- **`/autodelve`** doesn't yet make multi-PC autopilot decisions (whose HP triggers a retreat? who
  quaffs a potion?) — its simulation loop threads one PC through every decision today.
- **The web interface** (`GameFacade`/`StateSnapshot`/`app.js`) still only shows/plays the first PC —
  unaffected by the data-model change, just not updated to expose PC #2+ yet.
- **Retainer ownership**: `/hire` (Milestone 15) lets any PC's Charisma/gold authorize a hire, but
  retainers still join one shared, undifferentiated party-wide pool afterward — there's no persistent
  "this retainer belongs to PC X" concept (per-PC upkeep, loyalty/desertion, or `/party` grouping).
  Switching persisted marching-order/light-bearer tokens from `"@you"` to real PC names once 2+ PCs
  exist is a separate, smaller refinement, not blocking.
- **"Mules"** were mentioned as part of the party-size cap but delve has no such entity modeled at all
  yet.

### Seeding a Desert of Desolation party (example)
```
!pregen fighter 5 Khalil        # instant level-5 PC, plate + a +1 sword + potions
!export json                    # download the B/X stat block to keep
!autodelve 5 fast               # or earn it: sim delves at ~3-4 per level
!roster 4 5                     # DM: a whole level-5 party as one JSON file
!npc cleric 6 Ahkmenrah         # DM: a named NPC
```

> **Planned:** the more generous **Gygax75 treasure-allotment tables** will be wired into the treasure
> economy as an option, with the default always remaining standard B/X OSE rules.

## Commands (current)

| Command | Description |
|---|---|
| `/ping` | Liveness check + gateway latency. |
| `/help` `[command]` | List commands, or detailed help for one. |
| `/roll-character <class> [name] [bare]` | Roll a new level-1 character (Cleric, Fighter, Magic-User, Thief, Dwarf, Elf, Halfling); auto-gears with a class-appropriate kit by default, or `bare` to shop for yourself with `/buy`. |
| `/outfit [pc-name]` | Best-effort auto-gear an already-rolled PC (town only) — the same thing `/roll-character` does by default. |
| `/sheet [pc-name]` | Show a character sheet — your first-rolled PC by default, or name a specific PC. |
| `/enter [module]` | Begin a delve — procedural, or run an authored module by name. |
| `/loadmodule` | List authored modules available to `/enter`. |
| `/look` | Describe the current room (free). |
| `/move <dir>` | Move north/south/east/west, or up/down at stairs (one dungeon turn). |
| `/search` | Search for secret doors, traps and treasure (one dungeon turn). |
| `/open <dir>` | Open or force a door. |
| `/attack [n]` | Strike in combat (one round); optionally target enemy #n. |
| `/flee` | Flee combat to an adjacent room. |
| `/hire [pc-name] <class> [name]` | Recruit a retainer in town, or bulk-hire with `<class> all` / `smart all` / `all` / `random all`; name a PC first to hire using their gold/Charisma. `hire party [target]` best-effort fills the whole party toward a headcount (default 9). |
| `/party` | List your character and retainers (rank, engagement, weapon class). |
| `/dismiss <name>` | Release a retainer. |
| `/order [name1 name2 ...]` | View or set your marching order (front to back). |
| `/wield <item name>` | Wield a recognized inventory item as your main weapon; `wield shield`/`wield unshield` adjusts your shield. |
| `/pole [on\|off]` | Toggle probing ahead with a 10-foot pole (passive trap sense; AC risk if surprised). |
| `/light [torch\|lantern]` | View the party's light status, or light a fresh torch/lantern. |
| `/torchbearer [name]` | View or reassign who's carrying the party's lit torch/lantern. |
| `/buy <torch\|lantern\|oil> [qty]` | Buy light supplies in town. |
| `/cast <spell> [target]` | Cast a prepared spell (combat or utility). |
| `/prepare [pc-name] <spell>` | Memorize a spell into a free slot — name a caster PC first in a multi-PC party. |
| `/quaff [pc-name]` | Drink a potion of healing — your first-rolled PC by default, or name a specific PC. |
| `/town` | Return to town: rest, heal, pay upkeep, re-prepare spells. |
| `/pregen <class> [level] [name]` | Instantly build a finished character at a level (default 5). |
| `/export [embed\|text\|json]` | Export your character (embed, stat block, or JSON file). |
| `/autodelve [level] [fast\|bx] [verbose\|milestones]` | Fast-forward your character via simulated delves (default B/X OSE pace, verbose log); shows the party afterward. |
| `/roster <count> <level> [class]` | DM: mint a party of pregens (+ JSON file). |
| `/npc <class> <level> [name]` | DM: generate a single named NPC (+ JSON file). |

Commands also work via the message prefix (default `!`) or by mentioning the bot.
