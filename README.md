# Landlordsector

A Starsector 0.98.5a mod by Oddisz. Buy homes from property agents on the worlds you visit, from shacks
to estates, and rent them out. Rent comes in every month. Keep the places in repair, set the rent and sell when the price is
right.

Requires LunaLib and OddiszCommons.

## Buying

- Homes are sold by **property agents**: every market of size 3 or more that is not Luddic Path
  has one in its comm directory (a new colony gets one the next day). A market has room for more homes the bigger it is (4 at size 3, 13 at size 6, 24 at size 8; owned and for
  sale together, times the world's own number in worlds.json) and
  lists half to all of the free ones.
- The property screen lists the homes on the left and shows the chosen one large on the right, with its
  stat sheet.
- Bigger markets list better homes: shacks, studios and small houses from size 3, houses from 4, villas
  from 5 and estates from 6. A list with room for every size starts with one of each.
- Worlds with their own housing (worlds.json, from each world's description in the game): crowded hive cities
  on Sindria, the Tri-Tachyon capital Eochu Bres from shanties to estates, the paradise world Gilead and more. Their homes get house traits that fit the lore
  (every home in Sindria's hive cities is Underground). New homes come on the market every 90 days.
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
- Homes roll 0 to 3 traits of their own (house_traits.json). Geothermal (cold worlds) takes the cold off one home:
  its comfort and price use the hazard without it. Marks (Graffiti, Dirty) stay until repairs clean them off. Tenants like (+8 happiness) or hate (-15)
  some of them. Hover over a trait to see what it does. Tenant traits tell which kind of house event
  (money, damage, crime and safety, the neighbors, cleaning and repairs, decorating) they make likelier, not the events themselves.
- Events (tenant_events.json, 0.5.0): house events (at most one per home every 5 days) and tenant events (the
  tenant's own life, at most one every 7 days, steered by health, security and traits; tenants can die). For the big ones the tenant also
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
- **Services** (0.6.0, services.json): hired per home on the Services tab (S), paid monthly as a share of the
  Normal rent: security patrol, cleaning crew (cleans graffiti and dirt), maintenance contract (repairs), gardener,
  concierge, pest control plan and the screening service. 1.7.0: the tenant manager (flat 5,000 a month,
  Manager.java) fills an empty home with the best applicant and moves the rent between Low and High.
- **House flipping** (0.6.0): about a third of homes for sale come worn, some with graffiti or dirt. Worn price plus
  repairs stays under the price new, and the repaired home sells for more than the worn price before tariffs.
- **Tenant happiness and health** (0.6.0) are the tenant's own bars, in the Tenant section of the home sheet.
- **Environment** (0.5.0, environment.json): every home sits next to one building on its colony, or next to
  nothing (never a building in orbit or one with no effect, 1.0.1). Commute, Air and Noise are scores from 0 to
  100 shown as letter grades, set by the home's spot on the colony (commute 0 to 50, 1.0.1), the station, market
  conditions, the neighbour, house traits and upgrades; they move tenant happiness and health. Home levels
  (levels.json): Heat, Cold and Radiation, 0 to 3. Tenant traits change how much each matters ("levels" and
  "healthCost" in tenant_traits.json). Criminals hate police and spying buildings on their colony.
- **Tenant health** (0 to 100, 0.4.0): base 70, minus home levels and a worn home, plus traits and upgrades
  (kids' effects only for Family with kids). Below 60 tenants can fall sick and miss rent more often; below 20
  they may move out to get treatment.
- **Upgrades** (upgrades.json): furniture (items `lls_<id>` in special_items.csv, sold in open markets with a
  property agent and found in salvage; right-click one to put it in the furniture storage, then drag it into a
  home's slots on the Furniture tab (F)) and upgrades (credits, built at once, no slots, `conflicts` only; Upgrades
  tab (G)). Both use the house trait effects plus `levels`, `env`, `kids` and `events`; `price` raises the value by
  a percent, `valueShare` by a flat share of the cost.
- **Tenant happiness** (0 to 100): from comfort, decor (below 20 costs happiness), condition, security, the
  environment and the rent setting. Below 15 a tenant may move out. It tilts house events toward good ones. Happy tenants wear a home slower; unhappy ones up to 1.5 times as fast. Only
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

## Family

- Declare one of your homes your own from its Tenants tab. You live there: no rent and no adverts.
- Propose to one of your officers or administrators of the opposite sex from your home's Family tab. Officers
  say yes at their highest level, administrators after governing one of your colonies for 12 months. They
  retire and live with you as your wife or husband. 2% are cheating on you and never say yes.
- With AoTD Vaults of Knowledge and Kaysaar's Demise on, Sophia Anderson can marry you once she is on your
  research council. She always says yes, keeps her research post and never dies in a tenant event.
- Your spouse lives like any tenant, with their own traits, happiness, health and events, and pays no rent.
  After 30 days without a visit (dock at that market) they lose half a point of happiness a day, up to 50.
  An unhappy spouse divorces you.
- Talk to your spouse in that market's comm directory: try for a child (a 1 in 4 chance, once a day), give a
  gift or ask for a divorce. Gifts are sold and the money goes into upgrades for the house, then repairs.
- Each child at home raises what the next one needs: 50 happiness and 40 comfort for the second child, 10 more
  of each for every child after that, up to 95 and 90.
- Married: no tariffs on that market, happier kids, and good events half again as likely while you visit.
- Divorce: the house is sold and you get half. Your ex takes the kids. You pay alimony for each child until
  they grow up.
- A child is born 30 days after a lucky try, with your name and Junior, and lives in the comm directory.
  The Romantic bedroom upgrade (your own home only) raises the chance per try from 25% to 50%.
- Kids grow up in five stages, one cycle each with the default 5 cycles: Baby, Toddler, Child, Teen, Adult.
  At home they cost 500 a month as babies and toddlers, 1,500 as a child, 2,500 as a teen and nothing as adults.
  Your spouse loses 5 happiness per baby, 8 per toddler and 3 per child; a teen helps (+4) or is a handful (-4).
- Up to the Teen stage a kid's picture shows them from behind (no face): in a crib, with toy blocks, at a window,
  on a walkway at night; blue for boys, red for girls. From the Adult stage they show the random portrait from your
  faction picked at birth.
- From the Child stage, talk to them to steer what they become (officer, administrator, miner, Nexerelin agent,
  local celebrity). As a teen the wish is locked. Teens take lessons (25,000, up to 3, a month apart) and spend a
  day with you once a week (every 4 days count as a lesson, up to 2). Adults go to an Academy on your colony
  (Industrial Evolution, officers and administrators, 100,000) or take extra classes (250,000).
- Traits come at the Child stage: from the home (E or F air, noise, commute or location, a shack or studio; S+
  scores and villas give good ones) and one or two at random, good ones likelier the happier the family. Teens get
  one or two more. Kid events (first words, broken arms, bullying, rarely a death) come about every two months.
- At the end of the Adult stage they start their life. Officers start at level 1 + childhood quality (0 to 4) +
  what they learned, at most 5; administrators get a second skill at 3 or more. Bad traits and a hard childhood
  (below 35 and 20 average happiness) give bad skills (officers ADHD, Lazy, Weak lungs, Hard of hearing, Hothead,
  Timid; administrators Wasteful, Slacker, Unpopular). The Academy gives Academy graduate, extra classes give
  Private tutors or Business school. Miners, agents and celebrities do 25% better per lesson point and 20% worse
  per bad mark; with extra classes miners sometimes send home furniture, agents learn a second specialty and
  celebrities add 1 stability.
- Give a tenant a nickname from their Tenants tab: it shows everywhere instead of the name.

## Your colonies

- Homes you do not buy on your own colonies are bought by NPC buyers over time. The colony earns half
  the price of each sale, a monthly property tax, and a little growth and stability (up to 1).

## The Manage page

Press the Manage (Y) button on the campaign bar and open the Real estate tab: your homes on the left, the
chosen one large on the right with its stat sheet, rent setting, Repair and Sell.

## Settings (LunaLib)

- Rent multiplier.
- Most properties you can own (default 5, 0 for no limit).
- Cycles for kids to grow up (default 5).
- Alimony per child each month (default 5,000).
- Kaysaar's Demise: with AoTD Vaults of Knowledge, Sophia Anderson can marry you (default off).

## Compatibility

- Can be added to a game in progress. Removing it from a game in progress breaks that save.
- Every home size has two pictures for each planet type.

## For modders

`data/config/landlordsector/tiers.json` holds the tiers (with base comfort and decor) and every economy
number, agents included. `art.json` lists the pictures per tier and planet type (station, terran, jungle,
water, arid, desert, tundra, frozen, cryovolcanic, barren, toxic, volcanic, irradiated, gas_giant), 1024 x 640,
with a "default" list for the rest. Both are read with
`getMergedJSON`, so other mods can add to them.

## Building

`./gradlew releaseZip` compiles against the game, LunaLib and `../OddiszCommons/jars/OddiszCommons.jar`,
runs the checks and writes `Landlordsector.zip`.
