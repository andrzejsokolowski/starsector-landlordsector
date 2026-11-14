package landlordsector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/** Checks the shipped tier table and the price and rent rules without the game. Run by Gradle check. */
public final class RulesCheck {

    private static final List<String> problems = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        loadAll(args[0]);

        List<Tier> tiers = TierTable.all();
        expect(tiers.size() == 6, "six tiers, found " + tiers.size());
        expect(tiers.get(0).minSize <= MarketRules.MIN_SIZE, "the cheapest tier must be listed on size 3 markets");
        for (int i = 0; i < tiers.size(); i++) {
            Tier t = tiers.get(i);
            expect(t.min > 0 && t.min < t.max, t.id + ": min must be above 0 and below max");
            expect(t.rentShare > 0 && t.rentShare < 0.2f, t.id + ": rentShare out of range");
            expect(t.slots > 0 && t.weight > 0, t.id + ": slots and weight must be positive");
            if (i > 0) {
                Tier prev = tiers.get(i - 1);
                expect(t.min > prev.max * 0.99f || t.min >= prev.min, t.id + ": tiers must get dearer");
                expect(t.rentShare < prev.rentShare, t.id + ": cheaper tiers must yield more (Andrzej, LLS-1)");
                expect(t.minSize >= prev.minSize, t.id + ": minSize must not fall");
            }
        }
        Tier estate = TierTable.get("estate");
        expect(estate != null, "an estate tier");

        // Andrzej's scale (LLS-2, 2026-11-12).
        float prime = Pricing.marketMult(9, 10f, 0.25f, true, true, false, false, false);
        float worst = Pricing.marketMult(3, 0f, 3f, false, false, true, true, true);
        float typical = Pricing.marketMult(5, 5f, 1f, false, false, false, false, false);
        float earth = Pricing.marketMult(6, 7f, 0.5f, true, true, false, false, false);
        float lunamun = Pricing.marketMult(5, 10f, 1.65f, false, false, false, false, false);
        float magma = Pricing.marketMult(5, 4f, 2.75f, false, false, false, true, false);
        expect(Math.abs(prime - 4f) < 1e-4, "the best possible world reaches 4x, got " + prime);
        expect(Math.abs(worst - 0.25f) < 1e-4, "the worst world drops to 0.25x, got " + worst);
        expect(Math.abs(typical - 0.95f) < 0.06f, "a standard world sits near 1x, got " + typical);
        expect("S".equals(Pricing.grade(earth)), "an Earth-like world is grade S (Andrzej), got " + Pricing.grade(earth));
        String lg = Pricing.grade(lunamun);
        expect("B".equals(lg) || "C".equals(lg), "Lunamun is grade B or C (Andrzej), got " + lg);
        expect("F".equals(Pricing.grade(magma)), "a 275 percent magma world with pirates is F, got "
                + Pricing.grade(magma));
        String tg = Pricing.grade(typical);
        expect("C".equals(tg), "a standard world is grade C, got " + tg);
        expect("S+".equals(Pricing.grade(prime)), "the best possible world is S+, got " + Pricing.grade(prime));
        expect(Math.abs(Pricing.planetMult(1f, false, false, false) - 1f) < 1e-6, "hazard 100 percent is standard");
        expect(Math.abs(Pricing.planetMult(0.5f, false, false, false) - 1.5f) < 1e-6, "hazard 50 percent is 1.5");
        expect(Pricing.planetMult(1.65f, false, false, false) > 0.8f, "hazard 165 percent is still fine");
        expect(Math.abs(Pricing.sizeMult(5) - 1f) < 0.06f && Math.abs(Pricing.sizeMult(6) - 1f) < 0.06f,
                "size 5 and 6 are standard");
        expect(Pricing.sizeMult(8) > Pricing.sizeMult(7) && Pricing.sizeMult(7) > Pricing.sizeMult(6) + 0.2f,
                "size 7 is big, 8 bigger");
        expect(Pricing.marketMult(6, 6f, 1f, true, true, false, false, false)
                > Pricing.marketMult(6, 6f, 1f, false, false, true, false, false),
                "mild, habitable, clean worlds cost more than polluted ones");
        expect(Pricing.dangerMult(true, false) < 1f && Pricing.dangerMult(false, true) < 1f, "pirates and Pathers cost");
        expect(Pricing.stabilityMult(9) > Pricing.stabilityMult(2), "stable markets cost more");

        // Rent: a 4 million estate at Normal and full condition pays 1 percent a month.
        float estateRent = Pricing.rent(4_000_000f, estate.rentShare, 1f, 1f, 1f);
        expect(Math.abs(estateRent - 40_000f) < 1f, "a 4 million estate rents for 40k, got " + estateRent);
        expect(Pricing.rent(100f, 0.03f, 1f, 0f, 1f) == 1.5f, "a worn-out home pays half rent");
        expect(Pricing.rent(100f, 0.03f, 1f, 1f, 2f) == 6f, "the LunaLib multiplier scales rent");

        // Value and repairs.
        Economy eco = TierTable.economy();
        expect(Pricing.value(1000f, 0f, true, eco.landValue) == 200f, "a condemned home keeps its land value");
        expect(Pricing.value(1000f, 1f, false, eco.landValue) == 1000f, "a new home is worth its full value");
        expect(Math.abs(Pricing.repairCost(1000f, 0f, eco.repairFullCost) - 100f) < 1e-3,
                "a full rebuild costs 10 percent of the value");
        expect(Pricing.repairCost(1000f, 1f, eco.repairFullCost) == 0f, "a new home needs no repairs");
        expect(Math.abs(eco.wearMonths - 24f) < 1e-3, "about 2 cycles from new to condemned (Andrzej, LLS-1)");

        // Rent settings run from cheap and kind to greedy and hard on the home.
        RentSetting[] s = RentSetting.values();
        for (int i = 1; i < s.length; i++) {
            expect(s[i].rentMult > s[i - 1].rentMult, s[i] + ": rent must rise");
            expect(s[i].wearMult >= s[i - 1].wearMult, s[i] + ": wear must not fall");
            expect(s[i].repPerMonth < s[i - 1].repPerMonth, s[i] + ": reputation must fall");
        }
        expect(RentSetting.FREE.lower() == RentSetting.FREE && RentSetting.GREEDY.higher() == RentSetting.GREEDY,
                "rent settings stop at both ends");
        expect(RentSetting.FREE.rentMult == 0f && RentSetting.CHARITY.lower() == RentSetting.FREE, "rent can be free");
        // 0.3.9 (Andrzej): "Free should be free" - anyone affords it, the rich go one size further down, never a shack.
        expect(TenantRules.fits(5, RentSetting.FREE, 1, 0) && TenantRules.pricePenalty(5, RentSetting.FREE, 1, 0) == 0f,
                "a poor tenant takes a free estate");
        expect(TenantRules.beneathPenalty(3, 6, 0, RentSetting.FREE) == 0f
                && TenantRules.beneathPenalty(3, 6, 0, RentSetting.NORMAL) == TenantRules.BENEATH,
                "an S tenant takes a free house without complaint");
        expect(!TenantRules.fits(0, RentSetting.FREE, 6, 0) && !TenantRules.fits(0, RentSetting.FREE, 5, 0),
                "rich tenants never live in a shack, even for free");
        // 0.3.8: taste traits, decor, security.
        expect(TenantRules.pricePenalty(4, RentSetting.NORMAL, 4, 1) == 0f
                && TenantRules.pricePenalty(4, RentSetting.NORMAL, 4, 0) == TenantRules.STRETCH,
                "a Social climber of wealth B is happy in a villa that others find too big");
        expect(TenantRules.beneathPenalty(2, 4, 1) == TenantRules.BENEATH && TenantRules.beneathPenalty(2, 4, 0) == 0f,
                "a Social climber finds a small house too small a tier sooner");
        expect(TenantRules.wouldLiveIn(2, 6, -1) && !TenantRules.wouldLiveIn(2, 6, 0),
                "a Frugal S tenant lives in a small house that other S tenants refuse");
        expect(!TenantRules.canAfford(5, RentSetting.NORMAL, 3) && !TenantRules.fits(5, RentSetting.NORMAL, 3, 1),
                "taste never changes what a tenant can pay");
        expect(Pricing.decorHappiness(0f) < -5f && Pricing.decorHappiness(Pricing.DECOR_PLAIN) == 0f
                && Pricing.decorHappiness(100f) > 20f, "bare walls cost happiness, plain is neutral, furnished adds");
        expect(Pricing.securityHappiness(90f, false) > 0f && Pricing.securityHappiness(90f, true) < 0f
                && Pricing.securityHappiness(10f, false) < 0f, "law-abiding tenants like security, criminals don't");
        landlordsector.data.TenantEvents.Def tipEv = landlordsector.data.TenantEvents.get("tip");
        expect(tipEv != null && tipEv.requires.contains("generous"), "only Generous tenants tip");
        // 0.3.9: big events carry a line in the tenant's own voice; trait lines win over faction and plain ones.
        for (landlordsector.data.TenantEvents.Def ev : landlordsector.data.TenantEvents.all()) {
            if (ev.big) expect(landlordsector.data.TenantEvents.hasMessages(ev.id), ev.id + " is big but has no tenant message");
        }
        java.util.Random sayRnd = new java.util.Random(3);
        for (int i = 0; i < 20; i++) {
            String v = landlordsector.data.TenantEvents.say("trashed", java.util.Arrays.asList("vandal"), "hegemony", sayRnd);
            String plain = landlordsector.data.TenantEvents.say("trashed", java.util.Arrays.asList("tidy"), "hegemony", sayRnd);
            expect(v != null && (v.startsWith("Try kicking") || v.startsWith("Place looked") || v.startsWith("Walls were")),
                    "a Vandal speaks as a vandal: " + v);
            expect(plain != null && (plain.startsWith("Sorry about") || plain.startsWith("It was a bad")),
                    "an ordinary tenant gets the plain lines: " + plain);
        }
        expect(landlordsector.data.TenantEvents.say("smashed_window", java.util.Arrays.asList("vandal"), "pirates", sayRnd) == null,
                "small events stay quiet");
        expect(landlordsector.data.TenantTraits.get("generous") != null
                && landlordsector.data.TenantTraits.get("social_climber").homeShift == 1
                && landlordsector.data.TenantTraits.get("frugal").homeShift == -1
                && landlordsector.data.TenantTraits.get("shady").criminal, "new traits load");

        // Property stats.
        for (int i = 1; i < tiers.size(); i++) {
            expect(tiers.get(i).comfort > tiers.get(i - 1).comfort, tiers.get(i).id + ": comfort must rise with the tier");
            expect(tiers.get(i).decor >= tiers.get(i - 1).decor, tiers.get(i).id + ": decor must not fall");
        }
        float mildHome = Pricing.comfort(60f, Climate.TEMPERATE, true, true, false, 0.5f, 1f);
        float toxicHome = Pricing.comfort(60f, Climate.HOSTILE, false, false, true, 2f, 1f);
        expect(mildHome > toxicHome + 30f, "a mild habitable world is far more comfortable than a toxic rock");
        expect(Pricing.comfort(60f, Climate.TEMPERATE, false, false, false, 1f, 0f) < 60f, "a ruin is less comfortable");
        float happyKind = Pricing.happiness(50f, 10f, RentSetting.CHARITY.happiness, 0.5f);
        float happyGreedy = Pricing.happiness(50f, 10f, RentSetting.GREEDY.happiness, 0.5f);
        expect(Math.abs(happyKind - happyGreedy - 60f) < 1e-3, "Charity to Greedy moves happiness by 60");
        expect(Pricing.wearMult(100f) == 0.5f && Pricing.wearMult(0f) == 1.5f, "wear runs from half to 1.5 times");
        expect(Pricing.security(10f, 30f, 15f, false, false) == 100f && Pricing.security(0f, 0f, 0f, false, false) == 0f,
                "security range");
        expect(Pricing.security(5f, 0f, 0f, true, true) == 5f, "pirates and Pathers cut security");
        expect(Pricing.damageMult(100f) > 0.3f && Pricing.damageMult(0f) == 1f, "security cuts damage, never to zero");
        expect("S+".equals(Pricing.grade(4f)) && "S".equals(Pricing.grade(2f)) && "A".equals(Pricing.grade(1.6f))
                && "B".equals(Pricing.grade(1.1f)) && "C".equals(Pricing.grade(1f)) && "D".equals(Pricing.grade(0.6f))
                && "E".equals(Pricing.grade(0.45f)) && "F".equals(Pricing.grade(0.25f)), "location grades S+ to F");
        expect(Math.abs(Pricing.decorValueMult(100f) - 1.25f) < 1e-6, "decor adds up to a quarter to the value");
        for (int i = 1; i < s.length; i++) {
            expect(s[i].happiness < s[i - 1].happiness, s[i] + ": happiness must fall as rent rises");
        }

        // 0.3.2: names, 0 to 3 house traits, wealth letters, headhunters.
        for (Tier t : tiers) expect(landlordsector.data.Names.nounCount(t.id) >= 3, t.id + " has name nouns");
        for (landlordsector.data.HouseTraits.Def d : landlordsector.data.HouseTraits.all()) {
            expect(landlordsector.data.Names.hasWordsFor(d.id), "house trait " + d.id + " has name words");
            expect(d.name.indexOf(' ') < 0 || d.name.contains("-"), "house trait " + d.id + " has a one-word name: " + d.name);
        }
        String named = landlordsector.data.Names.make("small_house", java.util.Arrays.asList("garden"), new java.util.Random(3));
        expect(named.split(" ").length == 2, "a name is an adjective and a noun: " + named);
        java.util.Random tr = new java.util.Random(5);
        int[] counts = new int[4];
        java.util.Set<String> hab = new java.util.HashSet<>(java.util.Arrays.asList("habitable", "mild_climate"));
        for (int i = 0; i < 2000; i++) {
            int n = landlordsector.data.HouseTraits.roll(hab, "temperate", false, 3, tr).size();
            expect(n >= 0 && n <= 3, "0 to 3 house traits, got " + n);
            if (n >= 0 && n <= 3) counts[n]++;
        }
        expect(counts[0] > 200 && counts[3] > 100, "homes with no traits and with three both happen: " + java.util.Arrays.toString(counts));
        expect("E".equals(TenantRules.wealthLetter(1)) && "C".equals(TenantRules.wealthLetter(3))
                && "S".equals(TenantRules.wealthLetter(6)), "wealth letters E to S");
        String[] hhMarkets = {"chicomoztoc", "volturn", "sindria", "kantas_den", "uaf_new_auroria", "eochu_bres", "mairaath",
            "port_tse", "kazeron", "gilead", "jangala"};
        expect(landlordsector.data.FinderDefs.all().size() == hhMarkets.length, "one headhunter per market Andrzej named");
        for (String m : hhMarkets) {
            boolean found = false;
            for (landlordsector.data.FinderDefs.Def d : landlordsector.data.FinderDefs.all()) found |= d.markets.contains(m);
            expect(found, "a headhunter lives on " + m);
        }
        landlordsector.data.FinderDefs.Def kanta = landlordsector.data.FinderDefs.get("hh_kantas_den");
        landlordsector.data.FinderDefs.Def eochu = landlordsector.data.FinderDefs.get("hh_eochu_bres");
        expect(kanta != null && kanta.wealthShift < 0 && kanta.traitBoost.containsKey("hardy"),
                "Kanta's Den brings cheap tenants used to rough living (Andrzej)");
        expect(eochu != null && eochu.wealthShift > 0 && eochu.traitBoost.containsKey("prosperous"),
                "Eochu Bres brings rich tenants through connections (Andrzej)");
        for (landlordsector.data.FinderDefs.Def d : landlordsector.data.FinderDefs.all()) {
            for (String t : d.traitBoost.keySet()) expect(landlordsector.data.TenantTraits.get(t) != null, d.id + " boosts a real trait: " + t);
            expect(!d.perk.isEmpty() && d.perk.indexOf('%') < 0, d.id + " has a perk line");
        }

        // ---- 0.3.0 data files ----
        expect(landlordsector.data.ConditionTable.size() > 100, "the condition table covers the installed mods");
        expect(landlordsector.data.ConditionTable.get("US_shrooms").happiness > 0f, "psychoactive fungus cheers tenants");
        expect(landlordsector.data.ConditionTable.get("rogue_ai_core").price < 1f, "a rogue AI core lowers prices");
        for (String id : new String[] {"US_shrooms", "rat_rampant_military_core", "aar_crowned_world", "ix_killsats"}) {
            expect(landlordsector.data.ConditionTable.get(id) != null, "condition table has " + id);
        }
        // From the real effects (2026-11-12): conditions that only move stability or do nothing are left out.
        for (String id : new String[] {"trade_center", "organized_crime", "urbanized_polity", "DE_Titanlober", "yunru_gaia"}) {
            expect(landlordsector.data.ConditionTable.get(id) == null, id + " has no lived effect of its own");
        }
        // Owner-only effects.
        landlordsector.data.ConditionTable.Effect doctrine = landlordsector.data.ConditionTable.get("ii_imperialdoctrine");
        expect(doctrine != null && doctrine.forOwner("interstellarimperium") != null && doctrine.forOwner("hegemony") == null,
                "the Imperial doctrine only works for the Imperium");
        landlordsector.data.ConditionTable.Effect tdf = landlordsector.data.ConditionTable.get("istl_peremohy_condition");
        expect(tdf != null && tdf.forOwner("dassault_mikoyan").security > 0 && tdf.forOwner("hegemony").security < 0,
                "the Territorial Defense Force helps free factions and hurts anyone else");
        landlordsector.data.ConditionTable.Effect legio = landlordsector.data.ConditionTable.get("tahlan_legiotyranny");
        expect(legio != null && legio.forOwner("tahlan_legioinfernalis") != null && legio.forOwner("pirates") == null,
                "Legio Entrenchment matches any Legio faction id");
        expect(landlordsector.data.ConditionTable.matching(new java.util.HashSet<>(java.util.Arrays.asList("ii_imperialdoctrine",
                "US_shrooms")), "hegemony").size() == 1, "matching skips owner-only effects of other owners");
        java.util.Set<String> houseTags = new java.util.HashSet<>();
        for (landlordsector.data.Upgrades.Def u : landlordsector.data.Upgrades.all()) houseTags.addAll(u.effects.tags);
        for (landlordsector.data.HouseTraits.Def d : landlordsector.data.HouseTraits.all()) {
            houseTags.addAll(d.tags);
            expect(d.weight > 0f && d.price > 0.5f && d.price < 1.5f && d.wear > 0.5f && d.wear < 1.5f,
                    "house trait " + d.id + " has sane numbers");
            for (String c : d.conflicts) expect(landlordsector.data.HouseTraits.get(c) != null, d.id + " conflicts with a real trait");
        }
        expect(landlordsector.data.HouseTraits.get("flowers") != null && landlordsector.data.HouseTraits.get("lava_lake") != null,
                "Andrzej's examples: field of flowers and lava lake");
        expect(landlordsector.data.HouseTraits.get("flowers").good && !landlordsector.data.HouseTraits.get("lava_lake").good,
                "flowers are good, lava is bad");
        java.util.Set<String> eventIds = new java.util.HashSet<>();
        for (landlordsector.data.TenantEvents.Def e : landlordsector.data.TenantEvents.all()) {
            eventIds.add(e.id);
            expect(!e.text.isEmpty() && e.text.indexOf('%') < 0, "event " + e.id + " has plain text");
            expect(landlordsector.data.TenantEvents.hasKind(e.kind),
                    "event " + e.id + " has a kind with words in \"kinds\"");
        }
        // 0.3.4 (Andrzej): trait tooltips name the kind of event, never the event.
        java.util.List<String[]> pros = landlordsector.ui.Tips.tenantTrait(landlordsector.data.TenantTraits.get("prosperous"));
        boolean money = false;
        for (String[] line : pros) {
            if (line[0].equals("More likely to have good house events about money") && "+".equals(line[1])) money = true;
            expect(!line[0].contains("furniture") && !line[0].contains("times as likely"), "prosperous tooltip names no event: " + line[0]);
        }
        expect(money, "Prosperous: more likely to have good house events about money");
        java.util.List<String[]> vandalTip = landlordsector.ui.Tips.tenantTrait(landlordsector.data.TenantTraits.get("vandal"));
        boolean damage = false;
        for (String[] line : vandalTip) if (line[0].equals("More likely to have bad house events about damage to the home") && "-".equals(line[1])) damage = true;
        expect(damage, "Vandal: more likely to have bad house events about damage to the home, in red");
        // 0.3.6 (Andrzej): only the verb and the trait names are colored, each name by its own good or bad.
        int verbLines = 0;
        for (landlordsector.data.TenantTraits.Def d : landlordsector.data.TenantTraits.all()) {
            for (String[] line : landlordsector.ui.Tips.tenantTrait(d)) {
                boolean likes = line[0].startsWith("Likes homes"), hates = line[0].startsWith("Hates homes");
                if (!likes && !hates) continue;
                verbLines++;
                expect(line[1] == null && line.length >= 6, d.id + ": the likes/hates line itself is plain");
                expect((likes ? "Likes" : "Hates").equals(line[2]) && (likes ? "+" : "-").equals(line[3]),
                        d.id + ": the verb is colored green or red");
                for (int i = 4; i + 1 < line.length; i += 2) {
                    Boolean g = landlordsector.data.HouseTraits.byName(line[i]);
                    // Upgrades are always green (0.4.0).
                    if (g == null && line[0].contains(" homes with ")) g = Boolean.TRUE;
                    expect(g != null && (g ? "+" : "-").equals(line[i + 1]), d.id + ": " + line[i] + " has its own color");
                }
            }
        }
        expect(verbLines > 5, "tenant traits show likes and hates lines");
        java.util.Set<String> cold = new java.util.HashSet<>(java.util.Arrays.asList("cold"));
        java.util.Set<String> veryCold = new java.util.HashSet<>(java.util.Arrays.asList("very_cold", "habitable"));
        java.util.List<String> geo = java.util.Collections.singletonList("geothermal");
        expect(Math.abs(landlordsector.data.HouseTraits.hazardOffset(geo, cold) - 0.25f) < 0.001f
                && Math.abs(landlordsector.data.HouseTraits.hazardOffset(geo, veryCold) - 0.5f) < 0.001f,
                "Geothermal takes the cold's 25% (extreme cold 50%) hazard off its home");
        expect(landlordsector.data.HouseTraits.hazardOffset(geo, new java.util.HashSet<>()) == 0f,
                "Geothermal does nothing on a world that is not cold");
        expect(landlordsector.data.HouseTraits.get("geothermal").conflicts.contains("frozen_pipes")
                && landlordsector.data.HouseTraits.get("frozen_pipes").conflicts.contains("geothermal"),
                "a Geothermal home is never also Freezing");
        expect(Math.abs(landlordsector.data.HouseTraits.hazardOffset(
                java.util.Collections.singletonList("air_conditioned"),
                new java.util.HashSet<>(java.util.Arrays.asList("very_hot"))) - 0.5f) < 0.001f,
                "Air-conditioned takes the extreme heat's 50% hazard off its home");
        java.util.Random geoRoll = new java.util.Random(7);
        for (int i = 0; i < 2000; i++) {
            java.util.List<String> r = landlordsector.data.HouseTraits.roll(cold, "cold", false, 3, geoRoll);
            expect(!(r.contains("geothermal") && (r.contains("frozen_pipes") || r.contains("heated_floors"))),
                    "Geothermal never rolls with Freezing or Toasty");
        }
        for (landlordsector.data.TenantTraits.Def d : landlordsector.data.TenantTraits.all()) {
            for (String t : d.likes) expect(houseTags.contains(t), d.id + " likes " + t + ", which no house trait or upgrade has");
            for (String t : d.dislikes) expect(houseTags.contains(t), d.id + " dislikes " + t + ", which no house trait or upgrade has");
            for (String ev : d.events.keySet()) expect(eventIds.contains(ev), d.id + " names event " + ev + ", which does not exist");
        }
        expect(landlordsector.data.TenantTraits.get("vandal") != null && landlordsector.data.TenantTraits.get("filthy") != null
                && landlordsector.data.TenantTraits.get("prosperous") != null && landlordsector.data.TenantTraits.get("pollen_allergy") != null,
                "Andrzej's examples: vandal, filthy, prosperous, pollen allergy");
        // Pirates find vandals, Tri-Tachyon finds the prosperous.
        java.util.Set<String> none = java.util.Collections.emptySet();
        landlordsector.data.TenantTraits.Def vandal = landlordsector.data.TenantTraits.get("vandal");
        landlordsector.data.TenantTraits.Def rich = landlordsector.data.TenantTraits.get("prosperous");
        expect(landlordsector.data.TenantTraits.weight(vandal, "pirates", none, 1f)
                > 3f * landlordsector.data.TenantTraits.weight(vandal, "tritachyon", none, 1f), "pirates bring vandals");
        expect(landlordsector.data.TenantTraits.weight(rich, "tritachyon", none, 1f)
                > 3f * landlordsector.data.TenantTraits.weight(rich, "pirates", none, 1f), "Tri-Tachyon brings the prosperous");
        java.util.Random rnd = new java.util.Random(1);
        int pirateBad = 0, ttBad = 0;
        for (int i = 0; i < 400; i++) {
            for (String id : landlordsector.data.TenantTraits.roll("pirates", none, 1f, rnd)) {
                if (Boolean.FALSE.equals(landlordsector.data.TenantTraits.get(id).good)) pirateBad++;
            }
            for (String id : landlordsector.data.TenantTraits.roll("tritachyon", none, 1f, rnd)) {
                if (Boolean.FALSE.equals(landlordsector.data.TenantTraits.get(id).good)) ttBad++;
            }
        }
        expect(pirateBad > 2 * ttBad, "pirate tenants roll far more bad traits than Tri-Tachyon ones (" + pirateBad + " vs " + ttBad + ")");
        for (landlordsector.data.FinderDefs.Def f : landlordsector.data.FinderDefs.all()) {
            expect(!f.first.isEmpty() && f.hire > 0f && f.search > 0f && f.days > 0f && f.tenants > 0, "headhunter " + f.id + " is complete");
            expect(f.pitch.indexOf('%') < 0 && f.pitch.length() < 80, "headhunter " + f.id + " has a short plain pitch");
        }

        // Tenant math: Andrzej's vandal is far worse in a greedy shack than in a charity estate.
        float vandalShack = TenantRules.eventWeight(false, true, false, 1f, 4f, 30f, RentSetting.GREEDY, 0, 50f);
        float vandalEstate = TenantRules.eventWeight(false, true, false, 1f, 4f, 80f, RentSetting.CHARITY, 5, 50f);
        expect(vandalShack > 8f * vandalEstate, "a vandal wrecks a greedy shack far more than a charity estate ("
                + vandalShack + " vs " + vandalEstate + ")");
        expect(TenantRules.eventWeight(true, false, false, 1f, 1f, 90f, RentSetting.NORMAL, 3, 50f)
                > TenantRules.eventWeight(true, false, false, 1f, 1f, 10f, RentSetting.NORMAL, 3, 50f), "happy tenants bring good events");
        expect(TenantRules.eventWeight(false, false, true, 1f, 1f, 50f, RentSetting.NORMAL, 3, 0f)
                > 2f * TenantRules.eventWeight(false, false, true, 1f, 1f, 50f, RentSetting.NORMAL, 3, 100f), "security stops crime");
        // 0.3.3: wealth fits a tier (Andrzej's examples: S wants a villa or an estate, never a shack; a pirate never
        // affords a villa, even on cheap rent). The rent setting moves the price by at most one tier.
        expect(TenantRules.fitTier(1) == 0 && TenantRules.fitTier(6) == 5, "E fits a shack, S an estate");
        expect(!TenantRules.fits(0, RentSetting.NORMAL, 6) && !TenantRules.fits(0, RentSetting.GREEDY, 6),
                "S wealth won't live in a shack at any rent");
        expect(TenantRules.fits(4, RentSetting.GREEDY, 6) && TenantRules.fits(5, RentSetting.GREEDY, 6)
                && TenantRules.pricePenalty(4, RentSetting.GREEDY, 6) == 0f && TenantRules.beneathPenalty(4, 6) == 0f,
                "S wealth lives happily in a villa or an estate, even on Greedy rent");
        for (RentSetting rs : RentSetting.values()) {
            if (rs == RentSetting.FREE) continue;
            expect(!TenantRules.fits(4, rs, 1) && !TenantRules.fits(4, rs, 2), "E and D wealth can't pay for a villa on " + rs.label);
        }
        expect(TenantRules.fits(1, RentSetting.CHARITY, 1) && TenantRules.pricePenalty(1, RentSetting.CHARITY, 1) == 0f,
                "Charity rent brings a studio in reach of E wealth");
        expect(TenantRules.pricePenalty(2, RentSetting.NORMAL, 3) == 0f
                && TenantRules.pricePenalty(2, RentSetting.HIGH, 3) == 0.5f * TenantRules.STRETCH
                && TenantRules.pricePenalty(2, RentSetting.GREEDY, 3) == TenantRules.STRETCH,
                "rent above the tenant's tier is a stretch, half a tier at a time");
        expect(TenantRules.beneathPenalty(2, 5) == TenantRules.BENEATH && TenantRules.fits(2, RentSetting.NORMAL, 5),
                "two tiers down is beneath them but they still live there");
        expect(TenantRules.applicantWealth(4, 3, 0) == 5 && TenantRules.applicantWealth(0, 1, 0) == 0,
                "applicants answer an advert for their kind of home, tilted by the faction");
        expect(TenantRules.tenantHappiness(60f, 0f, 0, 1, 0f, 0f) == 45f, "a disliked tag costs 15 happiness");
        expect(TenantRules.tenantHappiness(60f, 0f, 1, 0, 0f, 0f) == 68f, "a liked tag adds 8 happiness");
        expect(TenantRules.moveOutChance(10f, true) == 0f && TenantRules.moveOutChance(10f, false) > 0f
                && TenantRules.moveOutChance(50f, false) == 0f, "only unhappy, disloyal tenants move out");
        expect(TenantRules.arrivalChance(1f, 50f, RentSetting.CHARITY) > TenantRules.arrivalChance(1f, 50f, RentSetting.GREEDY),
                "cheap rent draws applicants faster");
        expect(landlordsector.data.TenantEvents.minDaysBetween() >= 5f, "at most one event per home every 5 days (Andrzej)");

        // Listings by size.
        expect(Pricing.listingCount(3) == 1 && Pricing.listingCount(5) == 2 && Pricing.listingCount(7) == 3
                && Pricing.listingCount(12) == 4, "fewer listings now that every market has an agent");

        // Climates from vanilla planet types.
        expect(Climate.ofType("terran") == Climate.TEMPERATE, "terran is temperate");
        expect(Climate.ofType("terran-eccentric") == Climate.TEMPERATE, "terran-eccentric is temperate");
        expect(Climate.ofType("arid") == Climate.ARID && Climate.ofType("desert1") == Climate.ARID, "arid, desert");
        expect(Climate.ofType("tundra") == Climate.COLD && Climate.ofType("frozen2") == Climate.COLD, "cold");
        expect(Climate.ofType("cryovolcanic") == Climate.COLD, "cryovolcanic is cold");
        expect(Climate.ofType("water") == Climate.OCEAN, "water is ocean");
        expect(Climate.ofType("jungle") == Climate.JUNGLE, "jungle");
        checkUpgrades040(java.nio.file.Paths.get(args[0]).getParent());
        expect(Climate.ofType("barren") == Climate.HOSTILE && Climate.ofType("toxic") == Climate.HOSTILE
                && Climate.ofType("lava") == Climate.HOSTILE && Climate.ofType("rocky_metallic") == Climate.HOSTILE,
                "barren, toxic, lava and rocky worlds are hostile");
        checkPlanetTypes042(java.nio.file.Paths.get(args[0]).getParent());

        if (!problems.isEmpty()) {
            for (String p : problems) System.err.println("FAIL: " + p);
            System.exit(1);
        }
        System.out.println("Landlordsector rules verified: standard " + typical + "x " + tg + ", Earth-like " + earth
                + "x " + Pricing.grade(earth) + ", Lunamun " + lunamun + "x " + lg + ", magma with pirates " + magma
                + "x " + Pricing.grade(magma) + ", estate rent " + estateRent);
    }

    /** Reads a Starsector JSON file: # comments stripped. */
    static JSONObject json(java.nio.file.Path path) throws Exception {
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        for (String line : raw.split("\n")) {
            int hash = line.indexOf('#');
            text.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        return new JSONObject(text.toString());
    }

    /** Loads every data file next to tiers.json. */
    static void loadAll(String tiersPath) throws Exception {
        java.nio.file.Path dir = Paths.get(tiersPath).getParent();
        TierTable.load(json(dir.resolve("tiers.json")));
        landlordsector.data.ConditionTable.load(json(dir.resolve("conditions.json")));
        landlordsector.data.HouseTraits.load(json(dir.resolve("house_traits.json")));
        landlordsector.data.TenantTraits.load(json(dir.resolve("tenant_traits.json")));
        landlordsector.data.TenantEvents.load(json(dir.resolve("tenant_events.json")));
        landlordsector.data.FinderDefs.load(json(dir.resolve("headhunters.json")));
        landlordsector.data.Names.load(json(dir.resolve("names.json")));
        landlordsector.data.Upgrades.load(json(dir.resolve("upgrades.json")));
        landlordsector.data.Levels.load(json(dir.resolve("levels.json")));
    }

    /**
     * 0.4.2: every vanilla planet type maps to the picture type Andrzej drew it as, and art.json names only real
     * planet types and tiers (a typo would quietly fall back to the placeholder).
     */
    private static void checkPlanetTypes042(java.nio.file.Path dir) throws Exception {
        String[][] vanilla = {
                {"terran", "terran"}, {"terran-eccentric", "terran"}, {"jungle", "jungle"}, {"water", "water"},
                {"arid", "arid"}, {"desert", "desert"}, {"desert1", "desert"}, {"barren-desert", "desert"},
                {"tundra", "tundra"}, {"frozen", "frozen"}, {"frozen1", "frozen"}, {"frozen2", "frozen"},
                {"frozen3", "frozen"}, {"rocky_ice", "frozen"}, {"cryovolcanic", "cryovolcanic"},
                {"barren", "barren"}, {"barren2", "barren"}, {"barren3", "barren"}, {"barren_castiron", "barren"},
                {"barren-bombarded", "barren"}, {"rocky_metallic", "barren"}, {"rocky_unstable", "barren"},
                {"barren_venuslike", "toxic"}, {"toxic", "toxic"}, {"toxic_cold", "toxic"}, {"lava", "volcanic"},
                {"lava_minor", "volcanic"}, {"irradiated", "irradiated"}, {"gas_giant", "gas_giant"},
                {"ice_giant", "gas_giant"}};
        for (String[] v : vanilla) {
            expect(PlanetType.ofType(v[0]).id.equals(v[1]), "planet type " + v[0] + " shows the " + v[1] + " picture");
        }
        JSONObject ext = json(dir.resolve("art.json")).getJSONObject("exteriors");
        java.util.Set<String> types = new java.util.HashSet<>();
        for (PlanetType t : PlanetType.values()) types.add(t.id);
        types.add("default");
        java.util.Iterator<?> tiers = ext.keys();
        while (tiers.hasNext()) {
            String tier = String.valueOf(tiers.next());
            expect(TierTable.get(tier) != null, "art.json: " + tier + " is a tier");
            JSONObject byType = ext.getJSONObject(tier);
            expect(byType.has("default"), "art.json: " + tier + " has a default picture");
            java.util.Iterator<?> keys = byType.keys();
            while (keys.hasNext()) {
                String k = String.valueOf(keys.next());
                expect(types.contains(k), "art.json: " + tier + "." + k + " is a planet type");
            }
        }
        expect(ext.getJSONObject("shack").length() == PlanetType.values().length + 1,
                "art.json: a shack picture for every planet type");
    }

    /** 0.4.0: upgrades, home levels and health: every entry names real things, and the health math holds. */
    private static void checkUpgrades040(java.nio.file.Path dir) throws Exception {
        expect(landlordsector.data.Levels.all().size() == 5, "five home levels");
        expect(landlordsector.data.Upgrades.of(landlordsector.data.Upgrades.Kind.FURNITURE).size() >= 15,
                "at least 15 pieces of furniture");
        java.util.Set<String> levelIds = new java.util.HashSet<>();
        for (landlordsector.data.Levels.Def l : landlordsector.data.Levels.all()) {
            levelIds.add(l.id);
            expect(l.words.length == 4, l.id + ": four words, 0 to 3");
        }
        // The cargo items for furniture.
        java.nio.file.Path csv = dir.resolve("../../campaign/special_items.csv").normalize();
        java.util.Map<String, String[]> rows = new java.util.HashMap<>();
        for (String line : java.nio.file.Files.readAllLines(csv, java.nio.charset.StandardCharsets.UTF_8)) {
            String[] c = line.split(",", -1);
            if (c.length > 15 && !c[1].isEmpty() && !"id".equals(c[1])) rows.put(c[1], c);
        }
        java.util.Set<String> eventIds = new java.util.HashSet<>();
        for (landlordsector.data.TenantEvents.Def e : landlordsector.data.TenantEvents.all()) eventIds.add(e.id);
        java.util.Set<String> likedTags = new java.util.HashSet<>();
        for (landlordsector.data.TenantTraits.Def t : landlordsector.data.TenantTraits.all()) {
            likedTags.addAll(t.likes);
            likedTags.addAll(t.dislikes);
            for (String l : t.levels.keySet()) expect(levelIds.contains(l), t.id + " names unknown level " + l);
        }
        for (landlordsector.data.HouseTraits.Def h : landlordsector.data.HouseTraits.all()) {
            for (String l : h.levels.keySet()) expect(levelIds.contains(l), h.id + " names unknown level " + l);
            expect(landlordsector.data.Upgrades.get(h.id) == null, h.id + " is both a house trait and an upgrade");
        }
        for (landlordsector.data.Upgrades.Def d : landlordsector.data.Upgrades.all()) {
            for (String l : d.effects.levels.keySet()) expect(levelIds.contains(l), d.id + " names unknown level " + l);
            for (String e : d.effects.events.keySet()) expect(eventIds.contains(e), d.id + " names unknown event " + e);
            for (String t : d.effects.tags) expect(likedTags.contains(t), d.id + ": no tenant likes or hates tag " + t);
            for (String k : d.effects.kids.keySet()) {
                expect("happiness".equals(k) || "health".equals(k), d.id + ": kids effects are happiness and health");
            }
            for (String t : d.needs) expect(landlordsector.data.HouseTraits.isTrait(t), d.id + " needs unknown trait " + t);
            for (String t : d.fixes) expect(landlordsector.data.HouseTraits.isTrait(t), d.id + " fixes unknown trait " + t);
            if (d.furniture()) {
                String[] row = rows.get(d.itemId());
                expect(row != null, d.id + ": no special_items.csv row " + d.itemId());
                if (row != null) {
                    expect(row[0].equals(d.name), d.id + ": the item name must match " + d.name);
                    expect(row[2].contains(landlordsector.data.Upgrades.FURNITURE_TAG), d.id + ": item tag");
                    expect("landlordsector.items.FurnitureItemPlugin".equals(row[12]), d.id + ": item plugin");
                    expect(row[15].trim().matches("\\d+"), d.id + ": item order must be a number");
                }
            } else {
                expect(d.days > 0f && d.minCost > 0f && d.cost > 0f, d.id + ": a renovation has a cost and days");
            }
        }
        for (Tier t : TierTable.all()) {
            expect(landlordsector.data.Upgrades.renovationSlots(t.id) >= 1, t.id + ": renovation slots");
        }
        // The seven traits that became renovations (0.4.0) keep their ids as upgrades.
        for (String id : new String[] {"heated_floors", "air_conditioned", "new_plumbing", "reinforced", "bars",
                "lead_lined", "pool"}) {
            expect(!landlordsector.data.HouseTraits.isTrait(id) && landlordsector.data.Upgrades.get(id) != null,
                    id + " is a renovation now");
        }
        // Levels: Heat -1 cools, Air +1 cleans.
        landlordsector.data.Levels.Def heat = landlordsector.data.Levels.get("heat");
        landlordsector.data.Levels.Def air = landlordsector.data.Levels.get("air");
        expect(landlordsector.data.Levels.severity(heat, -1f) == -1f && landlordsector.data.Levels.severity(air, 1f) == -1f,
                "Heat -1 and Air +1 both make a home better");
        expect("Heat: Hot (1)".equals(landlordsector.data.Levels.label(heat, 1)), "levels read Heat: Hot (1)");
        expect(landlordsector.data.Levels.fromPlace(air, java.util.Set.of("pollution", "toxic_atmosphere"), 5, null) == 3,
                "pollution and a toxic atmosphere add up to Toxic air");
        landlordsector.data.Levels.Def noise = landlordsector.data.Levels.get("noise");
        expect(landlordsector.data.Levels.fromPlace(noise, java.util.Set.of(), 6, null) == 0
                && landlordsector.data.Levels.fromPlace(noise, java.util.Set.of(), 9, null) == 2,
                "big markets are noisy");
        // Health.
        float sick = landlordsector.data.Levels.sickBelow();
        expect(TenantRules.sickness(sick, sick) == 0f && TenantRules.sickness(0f, sick) == 1f, "sickness from 0 to 1");
        expect(TenantRules.sickWeight(true, 1f, 70f, sick) == 0f && TenantRules.sickWeight(true, 1f, 0f, sick) == 3f,
                "healthy tenants never fall sick, the sickest three times as often");
        expect(TenantRules.sickWeight(false, 2f, 0f, sick) == 2f && TenantRules.sickWeight(false, 2f, 80f, sick) == 1f,
                "sick tenants miss rent up to twice as often");
        expect(TenantRules.wornHealth(0.6f, 20f) == 0f && TenantRules.wornHealth(0f, 20f) == -20f,
                "a worn home costs health from 50% condition down");
        expect(TenantRules.treatmentChance(10f, 20f, false) > 0f && TenantRules.treatmentChance(10f, 20f, true) == 0f
                && TenantRules.treatmentChance(30f, 20f, false) == 0f, "only very sick, not loyal tenants leave for treatment");
        expect(landlordsector.data.TenantEvents.get("fell_sick") != null
                && landlordsector.data.TenantEvents.get("fell_sick").sickOnly, "the fell sick event is for sick tenants");
    }

    private static void expect(boolean ok, String what) {
        if (!ok) problems.add(what);
    }
}
