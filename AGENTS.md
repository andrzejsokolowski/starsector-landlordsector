# Landlordsector

These instructions apply to this repository, including when it is opened without the parent mod workspace.
The workspace `AGENTS.md` one folder up still governs releases, packaging, Git identity and communication.

## Notion

Tasks, history, test lists and the design answers: the **Landlordsector board** in Andrzej's Notion, under
**Starsector mods** (`collection://91a0728a-60a7-49ee-b045-f0cb3884957b`). Ticket ids start with LLS.
Read the board with `notion-query-data-sources` mode `view` on `view://41420ca7-5d20-81b5-b65e-000c5fe2fac9`.
LLS-1 "Design answers" holds every rule from the design interview (2026-11-10). Read it before changing
how the mod plays. Working rules and game API notes: **Claude Information**
(`collection://7bf9b1d6-b9e5-4b62-87f1-757bbaa51919`). Update the board during the work.

## Build

`./gradlew releaseZip` compiles against the game, LunaLib and `../OddiszCommons/jars/OddiszCommons.jar`
(build OddiszCommons first), runs the checks and writes `Landlordsector.zip`. Mod id `landlordsector`,
package `landlordsector`, staging folder `Landlordsector`. Plain Java 17, no Kotlin.

Checks: `checkRules` (tier table and price rules, plain Java), `checkRuntime` (the whole money loop on
Proxy stand-in markets, on the game's own Java with bytecode checks off, plus an XStream save round trip),
`checkReleaseFiles` (BOM, art paths, sprite keys, sound ids, LunaLib %, rule command classes),
`checkVersionMetadata`.

## Layout

- `data/config/landlordsector/tiers.json` - tiers (price range, monthly rent share, smallest market
  size, furniture slots, roll weight) and the "economy" numbers. `TierTable` reads it with getMergedJSON.
- `data/config/landlordsector/art.json` - exterior pictures per tier and climate, with a "default" list.
  `Art` loads each texture at startup and drops paths that fail. Placeholders until Andrzej's art.
- `MarketRules` - the hidden rule. Eligible: in economy, not hidden, size 3+, not pirate (also
  `Misc.isPirateFaction`) and not Luddic Path. `Pricing` holds the math as plain numbers: market multiplier
  0.25 to 4 from size, stability, hazard, habitable, mild climate and pollution.
- `Portfolio` (sector persistent data `landlordsector_portfolio`) - owned `Property` objects, per-market
  `MarketListings` (rolled again after `listingRefreshDays`), NPC homes on player colonies, last month's
  totals. These classes are in the save: keep field names, add fields with defaults.
- `MonthlyTick` (transient listener, added every load) - on the last economy tick of the month: drift,
  rent (minus tariff, frozen while hostile, 1 percent tip), reputation (capped at 2 points per faction per
  month), wear and condemning, NPC buyers and property tax on player colonies, report nodes under
  `landlordsector_real_estate`, one MessageIntel. It also damages homes on the player's own raids and
  bombardments.
- `HomeownerScript` (transient) - stability and growth on player colonies from NPC homes, through the
  OddiszCommons `Ledger` (released before every save).
- `buy/BuyScreen` - the custom visual dialog opened by rules.csv (`LLS_CanBuy`, `LLS_OpenBuyScreen`).
- `manage/RealEstatePage` - the "Real estate" tab of the OddiszCommons Manage screen.
- `ui/Canvas` - draws and hit-tests its own buttons from the mouse in `advance`. Tooltip buttons inside
  custom panels can fail to report clicks (Gambling Den 0.1.0), so neither screen uses them.

## Known limits

- The portfolio classes are in the save, so removing the mod mid-game breaks that save.
- Not played in game before 0.1.0 shipped. The two screens are the least checked part.
