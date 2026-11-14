# Landlordsector

A Starsector 0.98a mod by Oddisz. Buy homes from property agents on the worlds you visit, from shacks
to estates, and rent them out. Rent comes in every month. Keep the places in repair, set the rent and sell when the price is
right.

Requires LunaLib and OddiszCommons.

## Buying

- Homes are sold by **property agents**: every market of size 3 or more that is not pirate or Luddic
  Path has one in its comm directory. Each lists 1 to 4 homes (1 at size 3 and 4, up to 4 from size 9).
- The property screen lists the homes on the left and shows the chosen one large on the right, with its
  stat sheet.
- Bigger markets list better homes: shacks and studios from size 3, small houses from 4, houses from 5,
  villas from 6 and estates from 7. New homes come on the market every 90 days.
- Every home has a name, often taken from its traits (names.json), for example Leafy Cottage.
- Base prices: shack 1k to 10k, studio 30k to 50k, small house 100k to 300k, house 500k to 1M,
  villa 1.5M to 2.5M, estate 3M to 5M.
- The market stretches those prices from a quarter to four times. Size 5 and 6 markets with 100%
  hazard are the standard. Bigger markets (7 and 8), stability, low hazard, a habitable world and a mild
  climate raise the price. Small markets, high hazard, pollution, pirate activity and Pather cells lower
  it.
- Prices include the local tariff. On your own colonies homes cost half, with no tariff.

## Tenants

- A new home stands empty and advertised. Applicants turn up over the days, faster with a good location,
  comfort and low rent. Pick one of up to three from the home's Tenant tab on the Manage page, or turn
  them away.
- Each tenant has 2 or 3 traits that fit where they come from (tenant_traits.json): pirates bring vandals,
  filthy tenants and late payers, Tri-Tachyon brings the prosperous and the tidy.
- Wealth (E to S) fits one tier: E a shack, D a studio, C a small house, B a house, A a villa, S an estate.
  A home one tier above their wealth is hard to pay for (-20 happiness), two or more
  above they can't afford. A home two tiers below is not good enough for them (-15), three or more below they
  refuse. Low rent makes a home easier to afford: Charity makes it cost like a home one tier cheaper, Greedy like
  one tier more expensive, Low and High half as much. Applicants for an advert fit the home; the market's faction tilts their wealth.
- You see one trait of an applicant until you pay to screen them. A monthly screening service makes bad
  traits rarer.
- Homes roll 0 to 3 traits of their own (house_traits.json). Geothermal (cold worlds) and the Air conditioning renovation (hot
  worlds) take the cold or the heat off one home: its comfort and price use the hazard without it. Tenants like (+8 happiness) or hate (-15)
  some of them. Hover over a trait to see what it does. Tenant traits tell which kind of house event
  (money, damage, crime and safety, the neighbors, cleaning and repairs, decorating) they make likelier, not the events themselves.
- Tenant events (tenant_events.json): at most one per home every 5 days. For the big ones the tenant also
  sends you a line in their own words ("messages" in the same file, by trait or faction). Bad ones get likelier with
  unhappy tenants, greedy rent, cheap homes (for damage) and low security (for crime). Good ones get
  likelier with happy tenants. They go into the fleet log and the home's log.
- Tenants below 15 happiness may move out (loyal ones never do). You can evict.
- Headhunters (headhunters.json): eleven people, each in the comm directory of their own world (Chicomoztoc,
  Volturn, Sindria, Kanta's Den, New Auroria with United Aurora Federation, Eochu Bres, Mairaath, Port Tse,
  Kazeron, Gilead, Jangala), each with a perk (cheap hardy tenants, rich ones, loyalists and so on). Hire one,
  send them on a search from the Headhunters tab, and move the screened tenants they bring into your empty homes.
  An intel message tells you when they are back.

## Stats

- **Comfort** (0 to 100): the tier's base, plus or minus with the hazard rating, a little more on
  habitable worlds with a mild climate, less on stations and polluted worlds. A worn home loses some.
- **Decor** (0 to 100): from the tier, furniture and what tenants do. Adds up to a quarter to the value and
  raises the reputation its rent earns or costs.
- **Home levels** (0.4.0, levels.json): Heat, Cold, Air, Noise and Light, 0 to 3, from market conditions,
  buildings and market size, moved by house traits and upgrades. Each step costs tenants happiness and health;
  tenant traits change how much ("levels" and "healthCost" in tenant_traits.json).
- **Tenant health** (0 to 100, 0.4.0): base 70, minus home levels and a worn home, plus traits and upgrades
  (kids' effects only for Family with kids). Below 60 tenants can fall sick and miss rent more often; below 20
  they may move out to get treatment.
- **Upgrades** (0.4.0, upgrades.json): furniture (cargo items `lls_<id>` in special_items.csv, sold in open
  markets with a property agent and found in salvage) and renovations (credits, build days, stay with the home).
  Both use the house trait effects plus `levels`, `kids` and `events`. Placed from the Upgrades tab (G) of the
  Manage page.
- **Tenant happiness** (0 to 100): from comfort, decor (below 20 costs happiness), condition, security and the
  rent setting. Below 15 a tenant may move out; above 60 they do small repairs (+1% condition a month); above 80
  they may decorate the home. Happy tenants wear a home slower; unhappy ones up to 1.5 times as fast. Only
  Generous tenants tip.
- **Location** (S+, S, A, B, C, D, E, F): the market's price grade. A standard world is C, an Earth-like
  one S, a high-hazard world with pirates F. Hovering over the grade lists every price factor and the market
  conditions that change the price. Hovering over a stat bar lists what makes up that stat.
- Market conditions from the base game and installed mods (conditions.json) change price, comfort,
  tenant happiness and security, from what each condition really does in the game. A condition's own
  stability and hazard changes already count through the market, so they are not repeated. Some only
  count under one faction. `tools/conditions` rebuilds the table when the mod list changes.
- **Security** (0 to 100): from stability, military buildings and ground defenses, lower with pirate
  activity or Pather cells. Law-abiding tenants are happier in a safe home, criminals less happy. Cuts
  break-ins, police raids and the damage raids and disruption do.
- Every stat bar and the Value row have a tooltip listing what makes them up.
- Rent settings: Free, Charity, Low, Normal, High, Greedy. On Free anyone can afford any home, and rich tenants
  take a home one size further down than usual (never a shack).

## Rent

- Rent is paid every month, minus the local tariff, and shows in the income report as Real estate.
- At the Normal setting a home pays a share of its value each month: shack 3%, studio 2.5%,
  small house 2%, house 1.5%, villa 1.2%, estate 1%. LunaLib has a rent multiplier.
- Five rent settings: Charity, Low, Normal, High and Greedy (half to 1.6 times the rent). Cheap rent
  slowly earns reputation with the market's owners and makes tenants happier. Greedy rent costs both.
- No rent while you are hostile with the owners.
- A tenant tips their landlord now and then.

## Upkeep and selling

- Homes wear out: about two cycles from new to condemned with average tenants. A worn home pays less
  rent.
- A condemned home has no tenants and is worth only its land. Repairs cost credits, up to 10% of
  the value for a full rebuild, and are paid from the Manage page.
- Raids and bombardments on the market damage your homes. Saturation bombardment, or a colony that dies,
  destroys them. If the market changes hands, you keep them.
- Sell any time from the Manage page for the value minus a 5% broker fee and the local tariff.
- Prices move with the market and drift a little each month.

## Your colonies

- Homes you do not buy on your own colonies are bought by NPC buyers over time. The colony earns half
  the price of each sale, a monthly property tax, and a little growth and stability (up to 1).

## The Manage page

Press the Manage (Y) button on the campaign bar and open the Real estate tab: your homes on the left, the
chosen one large on the right with its stat sheet, rent setting, Repair and Sell.

## Settings (LunaLib)

- Rent multiplier.
- Most properties you can own (0 for no limit).

## Compatibility

- Can be added to a game in progress. Removing it from a game in progress breaks that save.
- Shacks have their own picture for each planet type; the bigger homes still use placeholders.

## For modders

`data/config/landlordsector/tiers.json` holds the tiers (with base comfort and decor) and every economy
number, agents included. `art.json` lists the pictures per tier and planet type (station, terran, jungle,
water, arid, desert, tundra, frozen, cryovolcanic, barren, toxic, volcanic, irradiated, gas_giant), 1024 x 640,
with a "default" list for the rest. Both are read with
`getMergedJSON`, so other mods can add to them.

## Building

`./gradlew releaseZip` compiles against the game, LunaLib and `../OddiszCommons/jars/OddiszCommons.jar`,
runs the checks and writes `Landlordsector.zip`.
