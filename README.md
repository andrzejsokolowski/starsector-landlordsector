# Landlordsector

A Starsector 0.98a mod by Oddisz. Buy homes from property agents on the worlds you visit, from shacks
to estates, and rent them out. Rent comes in every month. Keep the places in repair, set the rent and sell when the price is
right.

Requires LunaLib and OddiszCommons.

## Buying

- Homes are sold by **property agents**. Look for one in a market's comm directory and ask to see the
  homes for sale. About a quarter of markets of size 3 or more that are not pirate or Luddic Path have an
  agent at a time (more on big markets). Each stays 3 to 6 months, then moves on. Your own colonies
  always have one.
- The property screen lists the homes on the left and shows the chosen one large on the right, with its
  stat sheet.
- Bigger markets list more homes and better ones: shacks and studios from size 3, small houses from 4,
  houses from 5, villas from 6 and estates from 7. New homes come on the market every 90 days.
- Base prices: shack 1k to 10k, studio 30k to 50k, small house 100k to 300k, house 500k to 1M,
  villa 1.5M to 2.5M, estate 3M to 5M.
- The market stretches those prices from a quarter to four times: market size, stability and the planet
  all count. Habitable worlds with a mild climate and no pollution cost the most.
- Prices include the local tariff. On your own colonies homes cost half, with no tariff.

## Stats

- **Comfort** (0 to 100): the tier's base, more on habitable worlds with a mild climate, less on stations,
  hostile worlds, polluted worlds and high hazard. A worn home loses some.
- **Decor** (0 to 100): from the tier for now (furniture later). Adds up to a quarter to the value and
  raises the reputation its rent earns or costs.
- **Tenant happiness** (0 to 100): from comfort, decor, condition and the rent setting. Happy tenants wear
  a home slower and tip more often. Unhappy ones wear it up to 1.5 times as fast.
- **Location** (A to F): the market's price grade, from size, stability and the planet.
- **Security** (0 to 100): from stability, military buildings and ground defenses. Cuts the damage raids
  and disruption do.

## Rent

- Rent is paid every month, minus the local tariff, and shows in the income report as Real estate.
- At the Normal setting a home pays a share of its value each month: shack 3 percent, studio 2.5,
  small house 2, house 1.5, villa 1.2, estate 1. LunaLib has a rent multiplier.
- Five rent settings: Charity, Low, Normal, High and Greedy (half to 1.6 times the rent). Cheap rent
  slowly earns reputation with the market's owners and makes tenants happier. Greedy rent costs both.
- No rent while you are hostile with the owners.
- A tenant tips their landlord now and then.

## Upkeep and selling

- Homes wear out: about two cycles from new to condemned with average tenants. A worn home pays less
  rent.
- A condemned home has no tenants and is worth only its land. Repairs cost credits, up to 10 percent of
  the value for a full rebuild, and are paid from the Manage page.
- Raids and bombardments on the market damage your homes. Saturation bombardment, or a colony that dies,
  destroys them. If the market changes hands, you keep them.
- Sell any time from the Manage page for the value minus a 5 percent broker fee and the local tariff.
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
- The pictures are placeholders for now.

## For modders

`data/config/landlordsector/tiers.json` holds the tiers (with base comfort and decor) and every economy
number, agents included. `art.json` lists the pictures per tier and climate (station, temperate, arid,
cold, ocean, jungle, hostile), 1024 x 640. Both are read with
`getMergedJSON`, so other mods can add to them.

## Building

`./gradlew releaseZip` compiles against the game, LunaLib and `../OddiszCommons/jars/OddiszCommons.jar`,
runs the checks and writes `Landlordsector.zip`.
