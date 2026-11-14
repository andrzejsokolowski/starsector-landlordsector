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
- `data/config/landlordsector/art.json` - exterior pictures per tier and planet type (`PlanetType`, 14 types,
  pictures only; traits, upgrades and comfort keep the 7 `Climate`s), with a "default" list. `Art` loads each
  texture at startup and drops paths that fail. 0.4.2: Andrzej's shacks for all 14 types; other tiers still
  placeholders. His originals go in `art-source/` (gitignored); trim to 1024x640 (centered 16:10 crop) into
  `graphics/landlordsector/exteriors/<tier>_<type>.png`. On load, `repairArtPaths` swaps a default picture for
  the planet type's own once one exists. Gemini prompts: `art-prompts.txt` / `.html` (not packaged).
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
- `Agents` - property agents (post `lls_property_agent`, named in `data/world/factions/default_ranks.json`)
  in market comm directories, saved in `Portfolio.agents`. Person memory `$lls_agent`. Share per market
  `Pricing.agentShare` (agentShare 0.25 at size 5, more on big markets), stay 3 to 6 months, arrival rate
  `Pricing.agentArrival` keeps the share steady. Player colonies always have one. Seeded on the first load
  of a save (`agentsSeeded`), then run monthly.
- rules.csv: `lls_agent_greeting` (PickGreeting, score 5), `lls_agent_option` (PopulateOptions with
  `$isPerson`, `$lls_agent`, `LLS_CanBuy`), `lls_open_selected` (`LLS_OpenBuyScreen`). `LLS_CanBuy.marketOf`
  takes the active person's market first.
- `PropertyStats` - comfort, decor, tenant happiness, security, location grade and reason, world name.
  The math is in `Pricing` (checked by `checkRules`). Happiness sets wear (`wearMult`) and tips, security
  cuts raid and disruption damage, decor raises value and reputation.
- `buy/BuyScreen` - the agent's screen: list left, chosen home right (`ui/PropertyView.sheet`).
- `manage/RealEstatePage` - the "Real estate" tab of the OddiszCommons Manage screen, same layout plus
  rent, repair and sell controls.
- `ui/Canvas` - draws and hit-tests its own buttons from the mouse in `advance`. Tooltip buttons inside
  custom panels can fail to report clicks (Gambling Den 0.1.0), so neither screen uses them. Escape is
  only consumed when `onEscape` returns true: the Manage page returns false so the Manage screen closes
  (0.1.0 swallowed it).

- 0.3.0 tenants: `Tenant` (saved: traits, shown/screened, wealth, mood), `Tenants.create` (name and
  portrait from the faction, traits from `data/TenantTraits` weighted by faction and market conditions),
  `TenantRules` (plain math, checked), `TenantDaily` (transient, once a day: moods, applicants, events at
  most every 5 days, moving out, finder deliveries, pool expiry), `Notify` (home log, fleet log, intel for
  big events), `Finders` (finders.json people placed in comm directories, `$lls_isFinder`,
  `LLS_FinderOptions` / `LLS_HireFinder`). House traits in `data/HouseTraits`, market condition effects in
  `data/ConditionTable`. New `Property` fields load as 0, false or null from older saves; `upgradeTo030`
  in the mod plugin rolls traits and gives pre-0.3.0 homes a tenant.

- conditions.json (0.3.1) is built from each condition's code: `tools/conditions` (README there). Entries may
  name `owners` / `ownersContain` (faction-only effects) and `others`. `Tenants.conditions` skips suppressed
  conditions, and every condition check goes through it.

- 0.3.2 (Andrzej's notes after 0.3.1): one permanent agent per eligible market (`Agents.update`), 1 to 4
  listings (`Pricing.listingCount`), 0 to 3 house traits with one-word names, property names (`data/Names`,
  names.json, `Listing.name`, `Property.name`), headhunters (headhunters.json, `FinderDefs`; the code still
  says finders) on fixed markets with perks (wealthShift, traitBoost), wealth E to S (`TenantRules.wealthLetter`),
  hover tooltips (`Canvas.tip`, `ui/Tips`), colored event log on the right of the Manage page (log entries are
  flag, date, colored part, rest, split by tabs).

- 0.4.0 (LLS-4, Andrzej's "make everything feel connected"): four layers. The place (market conditions, house
  traits) sets home levels (`data/Levels`, levels.json: heat, cold, air, noise, light, 0 to 3, `better` levels flip
  the written sign), the levels cost people happiness and health (`PropertyStats.levels` / `health`, tenant trait
  `levels` multipliers and `healthCost`, added up per trait), and health changes what happens (`sickOnly` /
  `sickMult` events, `TenantRules.treatmentChance`). Upgrades (`data/Upgrades`, upgrades.json) carry a
  `HouseTraits.Def` of effects, and `HouseTraits.get` falls back to them, so an installed upgrade counts wherever a
  trait does: `Property.features()` / `Listing.features()` (traits plus finished upgrades) feed the stats, price,
  wear, tags and event odds. `Installed` (saved: id, startedAt, days, furniture) in `Property.upgrades`;
  `Listing.upgrades` for homes for sale. Furniture items are `lls_<id>` rows in special_items.csv
  (`items/FurnitureItemPlugin`); `FurnitureSources` (transient listener) stocks open markets and adds loot.
  `TenantDaily.finishWork` finishes renovations and one-time `fixes`. `LandlordsectorModPlugin.upgradeTo040` moves
  any trait id that is now an upgrade into `Property.upgrades` (same id, built). Manage page: Upgrades tab (G).
  Space (family size) waits for 0.5.0; Loud tenants make no noise for others until homes hold more people.
  0.4.1: furniture icons are Andrzej's, 80x80 in `graphics/icons/cargo/landlordsector/<id>.png`, cut from his sheet
  (first view of each piece, background already transparent). A new piece needs an icon there and its csv row.

## Known limits

- The portfolio classes are in the save, so removing the mod mid-game breaks that save.
- Andrzej plays each version and sends notes with screenshots: 0.1.0, 0.2.0, 0.3.1, 0.3.2, 0.3.3, 0.3.4,
  0.3.5 and 0.3.7 so far. His notes count as the play test; name only the changes he hasn't seen yet.

- 0.3.3 (Andrzej's notes after 0.3.2): wealth fits a tier (`TenantRules.fitTier`, E shack to S estate). A tenant
  never moves in two tiers above their wealth or three below (`TenantRules.fits`, `Actions.refusal`); the rent
  setting shifts the price level by at most a tier. Applicants are rolled for the home's tier
  (`TenantRules.applicantWealth`) and kept only if they fit. Headhunter returns post an intel message
  (`Notify.news`). The location grade's factors and conditions are a tooltip (`PropertyStats.gradeLines`).

- 0.3.4 (Andrzej's notes after 0.3.3): game text uses the % sign, never the word "percent" (only LunaSettings.csv
  must avoid a bare %). Every event in tenant_events.json has a `kind` named in `"kinds"`; tenant trait tooltips
  say "More likely to have good/bad house events about <kind>" (`Tips.eventKinds`) and never name an event.

- 0.3.5 (Andrzej: "use normal english words"): every name and line the player reads uses common everyday words.
  No "dearer", "a stretch", "beneath them", no rare words in trait names (Sporous, Storied, Mycophile) or home
  names (Hovel, Grange, Rime). Trait ids stay the same (saves keep ids), only the names changed.

- 0.3.6 (Andrzej's notes after 0.3.5): the buy screen sizes itself to the screen (`BuyScreen.screenW/screenH`).
  `PropertyStats` keeps a breakdown per stat (`comfortLines`, `decorLines`, `happinessLines`, `securityLines`);
  `gradeLines` holds only price factors. Tooltip lines may carry colored parts: {text, color, part, color, ...}
  (`Canvas.highlight`); trait likes/hates lines color only the verb and each trait name by its own good/bad.
  Tenant names use `Tenants.factionColor` (the faction's base UI color) instead of "From X". House traits may
  `cancel` conditions with their hazard (Geothermal, Air-conditioned): `HouseTraits.hazardOffset`,
  `MarketRules.traitPrice`. Wealth notes read "Desired home size: Villa. Matteo thinks a shack is too small".

- 0.3.7 (Andrzej: new players may not know faction colors): tenant names read "Name - Faction" with only the
  faction name in its color (`PropertyView.nameAndFaction` / `factionColored`). The pool list moves the faction to
  the second line when the name is too long.

- 0.3.8 (Andrzej's notes after 0.3.7): `RentSetting.FREE` (shift -1.5, so the 0.3.3 rule "the poor never afford a
  villa" holds). Taste: tenant trait `homeShift` (Social climber +1, Frugal -1) through the `TenantRules` overloads
  with a shift; `criminal` traits flip `Pricing.securityHappiness`. `Pricing.decorHappiness` (20 is neutral). No
  monthly tips: the tip event `requires` Generous (`TenantEvents.Def.requires`). Happiness 60+ repairs 1% a month,
  80+ may decorate (`TenantRules.MAINTAIN_*`, `IMPROVE_*`, in `MonthlyTick`). Advert applicants get trait odds from
  the home (`Tenants.fitBoost`). `PropertyStats.valueLines` / `graded`. Manage page hotkeys via `Canvas.onKey` and
  `hotkeyButton`. LunaLib `lls_kaysaarsDemise` (`Settings.kaysaarsDemise`, unused for now).

- 0.3.9 (Andrzej: "Free should be free"): on `RentSetting.FREE` anyone can afford any home (`canAfford`,
  `pricePenalty`) and `belowWanted` counts a home one size closer, so an S tenant takes a house but never a shack.
  This replaces the 0.3.3 "no villa for the poor" rule for Free only. Big events carry a tenant message:
  `"messages"` in tenant_events.json (groups by traits, then factions, then plain), `TenantEvents.say`,
  `Notify.event(..., who, said)` puts it in the home log, fleet log and the intel message.

