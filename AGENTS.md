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

## Text review (Andrzej, 2026-11-16)

Every change that adds or rewords player-facing text (tooltips, messages, log lines, events, buttons, settings)
also publishes a text review artifact: one row per line with **Line | When it is used | Accept | Reject with
comment**, saved through the artifact `db` capability so his verdicts can be read back. Generator:
`tools/review-pages/` (`text_review.py`, lines in `text_lines.json`), published at
https://claude.ai/artifact/GyTt9U386E6H7vh5eg47MA (republish to that URL). Link it in the chat and on the ticket, and read the verdicts before
the next text change.

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
  texture at startup and drops paths that fail. 0.4.2: Andrzej's shacks for all 14 types. 0.7.1: all six tiers have
  two pictures per type (168); the placeholders stay as the "default". His originals go in `art-source/` (gitignored). 0.7.0: `tools/art-maker/export_art.py` crops
  them (centered 16:10) to 1024x640 JPG `graphics/landlordsector/exteriors/<tier>_<type>.jpg` + `_2.jpg` (the
  alt) and rewrites art.json. Exteriors load on demand: `Art.use` (called from `Canvas.sprite`) loads a picture
  when drawn and unloads the least recently drawn beyond 12 (never one drawn in the last second). On load, `repairArtPaths` swaps a default picture for
  the planet type's own once one exists. Gemini prompts: `art-prompts.txt` / `.html` (not packaged).
  1.1.0 / 1.1.1: art.json "rare" holds a third, unusual picture per tier and type (all 84, `<tier>_<type>_3.jpg`
  from art-source `<stem>_alt2.jpg`, never flipped), shown on "rareChance" (0.15) of new homes, never twice on one
  agent list (`Art.pick`). Prompts: `tools/art-maker/wild_prompts.py` (art-prompts-wild.*); review page:
  `tools/art-maker/wild_review.py` (map / build / verdicts).
  Picture maker (LLS ticket "Picture maker"): `tools/art-maker/make_art.py` runs Andrzej's own method through
  the Gemini API (variant 1 in a new chat, then only the VARIANT 2 text in the same chat, alt flipped left to
  right) into `art-source/` under his names (`shouse_`, `gasgiant`, `_alt`, .jpg). `gallery.py` (launch config
  `lls-art-gallery`, port 8765) reviews them into `art-source/review.json`; `make_art.py redo` remakes rejected
  ones from the comment (fix = edit that picture, start over = new picture). Key: GEMINI_API_KEY, his to set.
- `MarketRules` - the hidden rule. Eligible: in economy, not hidden, size 3+ and not Luddic Path (pirate
  markets too since 0.7.2). `Pricing` holds the math as plain numbers: market multiplier
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
- 0.5.0 (LLS-3, LLS-4, LLS-12, LLS-13, Andrzej's 33 notes after 0.4.2): `data/Environment` (environment.json):
  Commute, Air and Noise scores 0 to 100 with letter grades, the neighbour each home sits next to
  (`Property.neighbor` / `Listing.neighbor`, an industry id or "none", rolled by `Surroundings.roll` from the
  colony's buildings; `byId`, then `byTag`, `skipTags` keeps orbital stations out) and colony tags (police,
  spying) that criminals hate. Levels are only heat, cold and radiation now (light gone, air and noise moved).
  `Surroundings.homeMult` is the location multiplier of one home (market times neighbour price). Upgrades: no
  slots, built at once (`Actions.build`), `conflicts`, `valueShare` (flat value, `Upgrades.flatValue`, added in
  `Property.fullValue` / `MarketRules.listingValue`). Furniture lives in `Portfolio.furniture` (id to count):
  `FurnitureItemPlugin` right-click stores it, the Furniture tab (F) uses `Canvas.draggable` / `dropZone` /
  `onDrop`. Events have a `type`: house events (`rollEvent`, happiness-weighted) and tenant events
  (`rollTenantEvent`, `TenantRules.tenantEventWeight`: health, security, traits; `dies`, `addTenantTrait`...).
  Marks (house traits with `mark`, Graffiti and Dirty) are cleaned by repairs. Homes per market:
  `MarketRules.housingSlots` = size, `listingRoll`. `upgradeTo050` rolls neighbours, finishes old renovations,
  moves duplicate furniture to storage and clears all listings once (`Portfolio.upgraded050`).
- 0.6.0 (LLS-3, LLS-4, LLS-12, the Services and House flipping tickets, Andrzej's 22 notes after 0.5.0): grades
  start at 50 (C), `Environment.letter` S+ to F, `colonyOverrides` (the AoTD `aotd_maglev` lifts commute to 95).
  `PropertyStats.area` / `nextTo` ("Mining nearby", "Residential area"), the sheet's Area, Size and Climate rows,
  upgrades and furniture as a count with a tooltip (`PropertyView.countItem`). Tenant happiness and health are the
  tenant's own bars (`Sheet.tenant`: null hides the section, "" shows None). `PropertyStats.uselessHere` greys
  out level upgrades a home does not need. Upgrade price = minCost + cost x value (`Upgrades.renovationCost`).
  Services (`data/Services`, services.json, `Property.services`, `HouseTraits.get` falls back to them, left out
  of `features(false)` so they never change the value; `MonthlyTick` charges them and runs `cleans` / `repairs`;
  Services tab (S); `upgradeTo060` turns the old `screeningService` flag into the service). House flipping:
  `Listing.wear` (0 on old listings), `MarketRules.listingWorth` / `WORN_CHANCE`, worn listings may roll Graffiti
  or Dirty; `checkRules` pins worn price + repairs < new and repaired sale > worn price. Picture variants:
  `<tier>_<type>_2.png` next to the first one in art.json; `Art.pick` already picks at random.
  0.4.1: furniture icons are Andrzej's, 80x80 in `graphics/icons/cargo/landlordsector/<id>.png`, cut from his sheet
  (first view of each piece, background already transparent). A new piece needs an icon there and its csv row.

- 1.2.0 family (LLS-5, Andrzej's design of 2026-11-15, numbers in `Family`): `Property.playerHome` (no rent:
  `isRented` is false, rent setting counts as Normal, no adverts), the partner is the home's `Tenant` with
  `partner` 1 (partner) or 2 (spouse) and `person` (the former officer or administrator, placed in the market's
  comm directory with `$lls_partner` / `$lls_homeId` and a post from default_ranks.json). 1.2.2 (Andrzej,
  2026-11-16): no relationship numbers and no girlfriend stage. `Family.propose(home, person, random)` from the
  Family tab marries at once: officers at `maxLevel` (the game's officerLevelUp plugin), administrators after 365
  days governing a player colony (`countGovernance`, person memory `$lls_governedDays`), Sophia Anderson
  (`aotd_sophia`, only with Kaysaar's Demise and AoTD `aotd_vok`, read through `AotdScience`, never killed by
  events, keeps her research post) always; 2% get "Actually... I'm cheating on you." (`$lls_cheated`, for good).
  `marryEveryPartner` on load. `childNeeds` / `cannotTry`: each kid at home raises the happiness and comfort for
  the next. `PropertyStats` gives
  partners "Last visited" (`Family.visitPenalty`) instead of the rent and wealth penalties; `TenantDaily` skips
  `Family.PARTNER_SKIP_EVENTS` for them and sends leaving and dying through `Family.leaves` (a spouse leaving =
  divorce: the home is sold, the player gets half, every kid goes, young ones go to `Portfolio.exKids` for
  alimony). `FamilyListener` counts docking as a visit. Kids:
  `Child` in `Property.children` (person in the comm directory, `$lls_child` / `$lls_childId`), born in
  `Family.daily`, grown after `Settings.growUpCycles`, then `Family.startLife` by `path` (officer, admin, worker
  pay and growth kids stay in `Portfolio.adultKids`). Dialogs: rules.csv `lls_partner_*`, `lls_child_*`,
  `lls_fam_*`, `lls_kid_*` call `LLS_Family <verb>`. Spouse markets get tariff x0 and growth kids immigration
  through `HomeownerScript` (ledger, never saved). Nexerelin agents only through `NexAgents` (compileOnly
  ExerelinCore.jar).

- 1.6.0 console commands (LLS-26): `data/console/commands.csv` lists 19 `lls_` commands (1.7.0: lls_furniture, lls_service, lls_sell and lls_evict removed; Andrzej keeps only what play can't do quickly), one class each in
  `landlordsector.console` (`Lls` holds the shared helpers: a home is its lls_homes number, its id or "here").
  Console Commands is optional: only that package touches `org.lazywizard.console` (compileOnly `lw_Console.jar`),
  and only Console Commands loads it. Commands reuse the game's code (`MarketRules.make`, `Actions.own`,
  `Family.birth`, `MonthlyTick.runMonth`); a new command adds its class, its csv row and its help line to
  `tools/review-pages/text_lines.json`.

- 1.8.0 (LLS-28 kids grow up, LLS-29 nicknames, LLS-30 kid portraits, LLS-5 changes): `Kids` holds the stages
  (`stageOf` from age, `Child.stage` the last one seen, `enter` runs once per new stage), childhood and teen traits
  (`rollChildhood` / `rollTeen`, kid-only traits `kidOnly` / `teenOnly` in tenant_traits.json with `officerSkill`,
  `adminSkill`, `training`), spouse burden (`burden`, read by `PropertyStats` for partners), monthly upkeep
  (`Family.monthly`, `Portfolio.lastKidCost`), training (lessons, days together, Academy `IndEvo_Academy`, classes),
  kid events (kid_events.json, `Kids.pick` / `apply`) and portraits (settings.json graphics `lls_kid_portraits`, key
  `<stage>_<f|m>_1` for baby to teen, `Kids.updatePortrait`, refreshed on every load). 1.8.1 (Andrzej: no faces, "I
  dont want to cause an uprising"): the kid seen from behind, made in the Gemini app from
  `tools/art-maker/kid_portraits.py` (prompts / build / sheet); from the Adult stage `Child.adultPortrait`, the random
  faction portrait picked at birth.
  1.9.0: kid health (`Kids.healthOf`, a stand-in Tenant with the kid's traits and kidsAtHome 1 through
  `PropertyStats.withTenant`; `Child.health` / `healthSum` / `healthDays`, `childhoodHealth`), illness events use it,
  `Kids.raised` (happiness and health, half each) sets the death chance, `SICKLY_CHILDHOOD` 40. Good traits name
  good skills in the same `officerSkill` / `adminSkill` fields (`Kids.goodSkills`, `goodTraits`); `Tips.kidTrait`
  shows the grown-up lines only on kids' traits. Kid skills: data/characters/skills (`lls_*`, tags npc_only and
  ai_core_only so random officers never roll them), effects in `landlordsector.skills.KidSkills`. `Family.startLife`
  scales by `Kids.training` and `Kids.badMarks`. `Kids.migrate` runs on load for older saves. Nicknames:
  `Tenant.realName` holds the real name while `name` is the nickname; `Canvas.startTyping` / `onTyped` take the
  typed line. Partner events read "Your wife, Name," (`Family.subject` / `who`, `TenantDaily.tenantWords`). The
  Romantic bedroom: upgrades.json `homeOnly` and `childChance`, `Upgrades.fits(d, property)`,
  `Family.childChance`.

- 1.10.0 (LLS-31, Andrzej: "did we make tenants use icons for their traits yet? If not, let's do it"): every house
  trait, Residential area, the climate levels and every tenant trait (kid traits too) have a painted icon, made in the
  Gemini app from `tools/art-maker/icons.py prompts` (Claude's queue, 3.1 Pro), saved as `art-source/icons/<key>.jpg`
  and shrunk by `icons.py build` to `graphics/landlordsector/icons/` (`tenant_<id>.png`, `tenant_hidden.png` by script).
  `PropertyView.traitRow` draws them with the tooltip and a green or red line under good and bad ones. Tenant icons
  ship all together or not at all: `icons.py build` writes none until every one is painted, and
  `tenantIconsReady` keeps the names until every icon loads (1.10.0 shipped the home icons only; Gemini's limit
  stopped the tenant set at 16 of 51; 1.11.0 shipped all 51 tenant icons and the hidden one). Applicant rows put the icons in their own column (`rowTraits`).
  `checkReleaseFiles` fails when a house trait has no icon or only some tenant icons are there.

- 1.12.0 (LLS-32 questions, LLS-33 leases, LLS-34 more events; Andrzej: "I really like interactive events! ... i cant
  pay rent > evict, or keep", "leases. Legally binding stuff ye?", "Invent some more"): events with `choices` in
  tenant_events.json are questions (`TenantEvents.Def.isQuestion`, the file's header lists the answer fields).
  `Choices.ask` stores a `Decision` on the home and posts a `QuestionIntel` (intel screen, answer buttons, tag "Real
  estate"); the Tenants tab shows it too (`questionBox`). `Choices.daily` takes the default after `answerDays` and runs
  `later` outcomes; the tenant manager answers at once (`managerPick`). Answers go through
  `TenantDaily.applyOutcome` (the old `apply` body). Leases: `Leases` (term per home `Property.leaseTerm()`, default
  12; `Tenant.leaseEnd`/`leaseMonths`/`leaseRent`/`breachAt`). `Property.setting()` is the rent paid now (the
  lease's), `askingRent()` the rent you set; the Manage page arrows and the manager use `askingRent`. Break fee in
  `TenantDaily.maybeMoveOut`, eviction fee in `Actions.evict`, lease end in `Leases.daily` (system question
  `lease_end`). `upgradeTo1120` gives tenants in old saves a lease. Checks: `checkLeases1120`, `checkChoices1120`.
  Every new text line is on the review page (areas New events, Questions, Tenant words, Leases).

- 1.12.1 (LLS-35 balance run, Andrzej: "make repairs less needed and cheaper"): `./gradlew balanceRun` simulates 40
  campaigns of 5 homes for 10 years (5 is the default limit; balance for 5, not 50). tiers.json economy:
  repairFullCost 0.10 to 0.05, `happyRepair` (0.03 a month at happiness 100, none at `happyRepairFrom` 60,
  `Pricing.happyRepair`, in `MonthlyTick` after the wear), `eventDamage` 0.5 (bad events' condition, in
  `TenantDaily.applyOutcome`). wearMonths stays 24 (LLS-1: an untended home falls apart in about 2 cycles).
  Turnover from 1.12.0: early_leave weight 0.3 to 0.03, `Leases.RENEW_HAPPINESS` = MOVE_OUT_BELOW (15, was 35),
  answer fails may need low happiness (`fail.below`, `Def.failBelow`). Result: tenants stay about 3.5 years, homes
  away from your colonies pay for themselves in 11-22 years, on your colonies in 2.5-6.
- 1.12.2 (LLS-37): rent x1.5 on other factions' worlds only. Andrzej: "our houses (our planets) are less valuable
  to live in. Why didn't we just cut tariffs?" Replaced by 1.12.3.
- 1.12.3 (LLS-37, Andrzej's decisions after the balance tables; they replace the LLS-1 price and wear rules): own
  colonies full price (`ownColonyPrice` 1.0), no tariff. Elsewhere `MarketRules.housingTariff` = market tariff x
  `housingTariff` 0.5 (15% at the usual 30%), paid once in `buyPrice`; rent and `Property.saleValue` pay no tariff
  (sale = value minus the 5% broker fee). rentShare table x1.5 (same rent for a home on any world). `wearMonths` 180
  (plain wear 10% a cycle at most, with an unhappy tenant), `repairFullCost` 0.10. Events unchanged. Andrzej reads
  balance as a per-month table for a 100,000-credit buy: money lost on buy, rent per month (after repairs and empty
  months), months till the loss is earned back, months until home value + rent = 2x. Balance run on 1.12.3: other
  worlds 13,000 lost, 1,240 a month, 11 and 91 months; own colony 0 lost, 1,255 a month, 0 and 80 months.

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

- 0.7.2 (Andrzej's notes after 0.7.1): `Agents.update` also runs daily from `TenantDaily.advance` (a new colony
  waited for the month end). `MarketListings.size` (0 in old saves) lets `MarketRules.grow` add one listing per
  size a market grew. Pirate markets sell homes. `FurnitureSources.special` skips loot from Omega, Threat and Dweller
  fleets or objects and fleets with `$noShipRecovery`. `Notify.news` and big `Notify.event`s post only the intel
  message (it already shows in the corner; the plain line doubled it). The sheet always shows the Upgrades row.

- 0.8.0 (Andrzej: estates impossible to find, too few homes, "Unique planets should matter"): `Pricing.housingRoom`
  (4, 6, 9, 13, 18, 24, then +6 per size) times `Worlds.Def.homes` is `MarketRules.housingSlots`. Tiers: small house
  from 3, house 4, villa 5, estate 6 (tiers.json). `MarketRules.add` lists one of each tier first when the count
  allows. `data/Worlds` (worlds.json, keyed by market id, written from the game's descriptions.csv: `planet_<id>`
  CUSTOM text1 and the GALLERY text2): homes, tier multipliers (0 = never), and an effect like a condition that
  `MarketRules.effects` adds after the conditions (named by the world's "name" in tooltips). `BuyScreen` shows the
  world's line, scrolls (`Canvas.onScroll`) and takes Up/Down. `upgradeTo080` re-rolls all listings once.
- 0.8.1 (Andrzej: "the underground trait ... for sindria's apartments"): worlds.json `traits` multiply house trait
  weights (`HouseTraits.roll` overload with the world map). A trait named with 1+ skips its anyCondition and climate
  (`fitsLore`: tier and station only); 10+ (`MARK_MULT`) is on every home there. `upgradeTo080` re-rolls once more
  (`upgraded081`).
- 1.13.0 (LLS-43, Andrzej: "Open-ended is earned ... I also like it being binding"): `Leases.TERMS` is 6/12/24 ("No
  lease" gone; `Property.leaseTerm()` turns an old 0 into 12). A running lease is binding: `TenantDaily.maybeMoveOut`
  returns while `Leases.running`. The `lease_end` question's second answer (`openEnded` in tenant_events.json,
  `Def.openEnded`) gives `Tenant.openEnded` when happiness is `OPEN_ENDED_HAPPINESS` (60)+ (`Choices.cannot` greys it
  out below): no end date, +6 happiness, free rent changes and evictions, moves out when unhappy with no fee.
  `upgradeTo1130` gives old no-lease tenants an open-ended lease. Balance run: 2.7 tenants per home in 10 years (was
  2.9), 69 empty days (was 74).
  Landlord achievements (LLS-42): draft names and hints on https://claude.ai/artifact/XUUiFtiZ2sV2SueNRpwGvc
  (`tools/review-pages/achievement_names.html`, db collection "names"); nothing built yet.
- 1.14.0 (LLS-44, Andrzej: "How about taking a mortgage through the property agent?", took the proposal as is):
  `Mortgage` (loan on `Property.loanLeft` / `loanPayment` / `loanMonthsLeft` / `loanMissed` / `loanInterest`, 0 on
  cash homes and old saves). tiers.json economy `mortgageDown` 0.2, `mortgageRate` 0.10 a cycle, `mortgageMissLimit` 3;
  terms `Mortgage.TERMS` 5 or 10 cycles. `Actions.buy(market, listing, cycles)` (BuyScreen: Loan length + Mortgage
  buttons), `payOff`, `repossess`; `sellFor` and the divorce sale pay the loan first. `MonthlyTick` pays it as an
  upkeep node "Mortgage, <home>" when credits (minus payments already due this month) cover it, else
  `Mortgage.miss` (interest still runs); at the limit the bank takes the home. Check: `checkMortgage1140`.
- 1.15.0 (LLS-42, Andrzej's names from https://claude.ai/artifact/XUUiFtiZ2sV2SueNRpwGvc): 23 achievements in
  `data/config/achievements_renewed/achievements.csv`, each read by Achievements - Renewed's built-in
  `memory:$lls_ach_<key>` script. `Feats` sets the sector memory keys (flags, and counts that only go up:
  homes, worlds, rent, tenantMonths, furniture, headhunters); `Feats.scan` runs on buying and each month. No code
  link to Achievements - Renewed. Names: Andrzej's, every word capitalized (his ACR rule); secret rows have no hint.
  `checkFeats150` in RulesCheck checks the file against `Feats.KEYS`. `Property.boughtWorn`, `Portfolio.headhuntersUsed`.
- 1.15.1 (Andrzej: Pather cells "remain static across Active, Disrupted, and Sleeper states"): `PatherCells.threat`
  reads the cells' `LuddicPathCellsIntel` (from the condition plugin, else `getCellsForMarket`): active 1, disrupted
  (sleeper with a timeout) `Pricing.PATHER_DISRUPTED` 0.5, sleeper 0 (the game's `isSleeper` is also true on player
  colonies with the Pather agreement). `Pricing.security` / `dangerMult` / `marketMult` take that as a float.
- 1.15.2 (LLS-47, found in the Landlordsector Renewed fork): `PropertyStats.tenantHappiness` counts each liked or hated
  source once per tenant trait (a set of source names, as Renewed does), so a feature with two matching tags (Leafy:
  garden + plants, Seaside: view + water, spy agencies: police + spying) is one like or hate. `checkRuntime` pins it.
