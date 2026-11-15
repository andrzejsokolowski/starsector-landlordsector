package landlordsector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.util.Misc;

import landlordsector.data.TenantTraits;
import landlordsector.data.Upgrades;

/**
 * 1.2.0 (LLS-5, Andrzej's design of 2026-11-15): the player's own home, a partner who lives there, marriage, kids,
 * divorce and alimony. The partner is the home's tenant with {@link Tenant#partner} set, so happiness, health and
 * events work as for any tenant.
 */
public final class Family {

    private static final Logger log = Global.getLogger(Family.class);

    /** Person memory: true on the partner and on each child in the comm directory, with the ids next to them. */
    public static final String PARTNER_FLAG = "$lls_partner";
    public static final String HOME_KEY = "$lls_homeId";
    public static final String CHILD_FLAG = "$lls_child";
    public static final String CHILD_KEY = "$lls_childId";
    /** Person memory: set once an officer's or administrator's relationship got its starting value. */
    public static final String BOND_FLAG = "$lls_bondSeen";

    /** Relationship needed to move in together (Andrzej: "+70 rep or more"). */
    public static final int MIN_BOND = 70;
    /** Claude's numbers (officers and admins have no relationship in the base game, so the mod grows it). */
    public static final float BOND_PER_LEVEL = 10f;
    public static final float ADMIN_BOND_START = 30f;
    public static final float BOND_PER_MONTH = 10f;
    public static final float BOND_PER_BATTLE = 2f;

    /** "Last visited: X days ago": no cost for this long, then half a point a day, at most 50. */
    public static final float VISIT_GRACE_DAYS = 30f;
    public static final float VISIT_PENALTY_PER_DAY = 0.5f;
    public static final float VISIT_PENALTY_MAX = 50f;

    public static final float CHILD_CHANCE = 0.25f;
    /** Andrzej: pregnant 20 days after trying, born 10 days later. */
    public static final float TELL_DAYS = 20f;
    public static final float BIRTH_DAYS = 30f;
    /** Andrzej: "a small 2% chance of her rejecting you with a 'Actually, I'm cheating on you'". */
    public static final float CHEAT_CHANCE = 0.02f;
    public static final float DIVORCE_SHARE = 0.5f;
    /** Kids are happier with their parent as your partner, more so as your wife or husband. */
    public static final float KID_HAPPY_PARTNER = 5f;
    public static final float KID_HAPPY_SPOUSE = 15f;
    /** A wife or husband you visit has more good events. */
    public static final float SPOUSE_GOOD_EVENTS = 1.5f;
    /** Grown kids: mine pay a month and colony growth, times 1 + half their childhood quality (0 to 4). */
    public static final float WORKER_PAY = 2000f;
    public static final float GROWTH = 1f;
    public static final float DAYS_PER_CYCLE = 365f;

    /** Tenant events that make no sense for someone you live with (rent, tips, a new partner, a baby of their own). */
    public static final Set<String> PARTNER_SKIP_EVENTS = new HashSet<>(Arrays.asList("late_rent", "skipped_rent",
            "tip", "partner_moves_in", "child_born"));
    /** Tenant traits a partner never has: they live with you, pay no rent and have no other family. */
    public static final Set<String> PARTNER_SKIP_TRAITS = new HashSet<>(Arrays.asList("couple", "family", "elderly",
            "late_payer", "social_climber", "frugal", "generous"));
    /** Tenant traits a child never has. */
    public static final Set<String> CHILD_SKIP_TRAITS = new HashSet<>(Arrays.asList("couple", "family", "elderly",
            "late_payer", "social_climber", "frugal", "generous", "long_hours", "remote_worker", "prosperous",
            "smoker", "gambler", "smuggler", "shady"));

    private static final String[] ROMAN = {"", "", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI",
        "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};

    private Family() {
    }

    // ---- Who you can move in with ----

    /** Officers and administrators of the opposite sex, best relationship first. */
    public static List<PersonAPI> candidates() {
        seedBonds();
        List<PersonAPI> out = new ArrayList<>();
        PersonAPI me = Global.getSector().getPlayerPerson();
        for (PersonAPI p : service()) if (fits(me, p) && !out.contains(p)) out.add(p);
        out.sort((a, b) -> Integer.compare(bond(b), bond(a)));
        return out;
    }

    /** Everyone in the player's service: fleet officers, then administrators. */
    private static List<PersonAPI> service() {
        List<PersonAPI> out = new ArrayList<>();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null && fleet.getFleetData() != null) {
            for (OfficerDataAPI o : fleet.getFleetData().getOfficersCopy()) {
                if (o != null && o.getPerson() != null) out.add(o.getPerson());
            }
        }
        try {
            for (AdminData a : Global.getSector().getCharacterData().getAdmins()) {
                if (a != null && a.getPerson() != null && !out.contains(a.getPerson())) out.add(a.getPerson());
            }
        } catch (Throwable ignored) {
            // No character data (headless check).
        }
        return out;
    }

    private static boolean fits(PersonAPI me, PersonAPI p) {
        if (p == null || p == me || p.isPlayer() || p.isAICore() || Misc.isUnremovable(p)) return false;
        Gender g = p.getGender();
        if (g != Gender.MALE && g != Gender.FEMALE) return false;
        Gender mine = me != null ? me.getGender() : Gender.ANY;
        if (mine == Gender.MALE) return g == Gender.FEMALE;
        if (mine == Gender.FEMALE) return g == Gender.MALE;
        return true;
    }

    public static boolean isAdmin(PersonAPI p) {
        try {
            for (AdminData a : Global.getSector().getCharacterData().getAdmins()) if (a.getPerson() == p) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** The relationship as the game shows it, -100 to 100. */
    public static int bond(PersonAPI p) {
        if (p == null || p.getRelToPlayer() == null) return 0;
        return Math.round(p.getRelToPlayer().getRel() * 100f);
    }

    private static void setBond(PersonAPI p, float points) {
        if (p == null || p.getRelToPlayer() == null) return;
        p.getRelToPlayer().setRel(Pricing.clamp(points, -100f, 100f) / 100f);
    }

    /**
     * Officers and administrators start where their service would have taken them: 10 per officer level, 30 for an
     * administrator. Each person once, the first time the mod sees them.
     */
    public static void seedBonds() {
        for (PersonAPI p : service()) {
            if (p.getMemoryWithoutUpdate() == null || p.getMemoryWithoutUpdate().getBoolean(BOND_FLAG)) continue;
            float start = isAdmin(p) && !inFleet(p) ? ADMIN_BOND_START
                    : BOND_PER_LEVEL * Math.max(1, p.getStats() != null ? p.getStats().getLevel() : 1);
            if (bond(p) < start) setBond(p, start);
            p.getMemoryWithoutUpdate().set(BOND_FLAG, true);
        }
    }

    /** Everyone in your service gets closer to you: each month, and officers after every battle you win. */
    public static void raiseBonds(float points, boolean officersOnly) {
        seedBonds();
        for (PersonAPI p : service()) {
            if (officersOnly && !inFleet(p)) continue;
            setBond(p, Math.min(100f, bond(p) + points));
        }
    }

    private static boolean inFleet(PersonAPI p) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return false;
        for (OfficerDataAPI o : fleet.getFleetData().getOfficersCopy()) if (o.getPerson() == p) return true;
        return false;
    }

    /** "Officer, level 5" or "Administrator". */
    public static String role(PersonAPI p) {
        if (inFleet(p)) return "Officer, level " + (p.getStats() != null ? p.getStats().getLevel() : 1);
        return "Administrator";
    }

    // ---- The player's home ----

    /** Null when the player can move into this home, or why not. */
    public static String cannotDeclare(Property p) {
        if (p.playerHome) return "You already live here.";
        if (p.market() == null) return "The world is gone.";
        if (p.condemned) return "Repair it first.";
        if (p.tenant != null) return p.tenant.name + " lives here.";
        return null;
    }

    public static String declareHome(Property p) {
        String no = cannotDeclare(p);
        if (no != null) return no;
        p.playerHome = true;
        p.advertising = false;
        p.applicants().clear();
        p.lastVisit = now();
        p.addLog("You moved in.", true, "You moved in.");
        return "You moved into " + p.displayName() + ".";
    }

    /** Null when the player can move out, or why not. */
    public static String cannotLeave(Property p) {
        if (!p.playerHome) return "You don't live here.";
        if (p.partner() != null) return p.partner().name + " lives here with you.";
        if (!p.children().isEmpty()) return "Your kids live here.";
        return null;
    }

    public static String stopLiving(Property p) {
        String no = cannotLeave(p);
        if (no != null) return no;
        p.playerHome = false;
        p.addLog("You moved out.");
        return "You moved out of " + p.displayName() + ".";
    }

    /** Null when this person would move in with you here, or why not. */
    public static String cannotMoveIn(Property p, PersonAPI who) {
        if (!p.playerHome) return "You don't live here.";
        if (p.tenant != null) return "Someone already lives here with you.";
        if (p.condemned) return "Repair it first.";
        if (p.market() == null) return "The world is gone.";
        if (bond(who) < MIN_BOND) return "Needs a relationship of +" + MIN_BOND;
        return null;
    }

    /** The officer or administrator leaves your service and moves in with you as your partner. */
    public static String moveInWith(Property p, PersonAPI who, Random random) {
        String no = cannotMoveIn(p, who);
        if (no != null) return no;
        MarketAPI m = p.market();
        leaveService(who);
        Tenant t = new Tenant();
        t.id = Portfolio.get().newId();
        t.name = who.getNameString();
        t.portrait = who.getPortraitSprite();
        t.factionId = Global.getSector().getPlayerFaction().getId();
        t.source = "partner";
        t.screened = true;
        t.wealth = 3;
        t.traits = rollTraits(m, PARTNER_SKIP_TRAITS, random);
        t.partner = 1;
        t.person = who;
        t.since = now();
        t.lastVisit = now();
        p.lastVisit = now();
        p.tenant = t;
        p.advertising = false;
        p.applicants().clear();
        placePerson(m, who, post(t));
        who.getMemoryWithoutUpdate().set(PARTNER_FLAG, true);
        who.getMemoryWithoutUpdate().set(HOME_KEY, p.id);
        p.addLog(t.name + " moved in with you.", true, t.name + " moved in with you.");
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        return t.name + " moved in with you at " + p.displayName() + ".";
    }

    /** Takes the person off their ship or colony and out of the officer and administrator lists. */
    private static void leaveService(PersonAPI who) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        try {
            if (fleet != null && fleet.getFleetData() != null) {
                for (FleetMemberAPI fm : fleet.getFleetData().getMembersListCopy()) {
                    if (fm.getCaptain() == who) fm.setCaptain(Global.getFactory().createPerson());
                }
                fleet.getFleetData().removeOfficer(who);
            }
        } catch (Throwable t) {
            log.warn("Landlordsector: could not take " + who.getNameString() + " off their ship", t);
        }
        try {
            if (isAdmin(who)) Global.getSector().getCharacterData().removeAdmin(who);
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (m.getAdmin() == who) m.setAdmin(Global.getSector().getPlayerPerson());
            }
        } catch (Throwable t) {
            log.warn("Landlordsector: could not take " + who.getNameString() + " off their colony", t);
        }
    }

    private static List<String> rollTraits(MarketAPI m, Set<String> skip, Random random) {
        String faction = m != null ? m.getFactionId() : "independent";
        List<String> traits = new ArrayList<>();
        for (String id : TenantTraits.roll(faction, Tenants.conditions(m), 1f, Collections.emptyMap(), random)) {
            if (!skip.contains(id)) traits.add(id);
        }
        return traits;
    }

    // ---- Names ----

    public static boolean female(Tenant t) {
        return t != null && t.person != null && t.person.getGender() == Gender.FEMALE;
    }

    private static boolean male(Tenant t) {
        return t != null && t.person != null && t.person.getGender() == Gender.MALE;
    }

    /** "girlfriend", "wife", "boyfriend", "husband", "partner" or "spouse". */
    public static String noun(Tenant t) {
        if (t == null) return "partner";
        if (t.isSpouse()) return female(t) ? "wife" : (male(t) ? "husband" : "spouse");
        return female(t) ? "girlfriend" : (male(t) ? "boyfriend" : "partner");
    }

    /** Comm directory post id, named in default_ranks.json. */
    private static String post(Tenant t) {
        return "lls_" + noun(t);
    }

    public static String he(Tenant t) {
        return female(t) ? "she" : (male(t) ? "he" : "they");
    }

    public static String his(Tenant t) {
        return female(t) ? "her" : (male(t) ? "his" : "their");
    }

    /** "Dad" or "Mom" for the player, as their kids say it. */
    public static String parentWord() {
        PersonAPI me = Global.getSector().getPlayerPerson();
        return me != null && me.getGender() == Gender.FEMALE ? "Mom" : "Dad";
    }

    /** "{playerName} Junior", then Junior II, Junior III... */
    static FullName juniorName(int n, Gender gender) {
        PersonAPI me = Global.getSector().getPlayerPerson();
        String first = me != null && me.getName() != null ? me.getName().getFirst() : "";
        String last = me != null && me.getName() != null ? me.getName().getLast() : "";
        String suffix = "Junior" + (n >= 2 ? " " + (n < ROMAN.length ? ROMAN[n] : String.valueOf(n)) : "");
        if (first == null) first = "";
        if (last == null) last = "";
        if (first.isEmpty() && last.isEmpty()) first = "Junior";
        else last = (last + " " + suffix).trim();
        if (first.isEmpty()) return new FullName(last, "", gender);
        return new FullName(first, last, gender);
    }

    // ---- Visits ----

    private static long now() {
        return Global.getSector().getClock().getTimestamp();
    }

    public static float daysSince(long when) {
        if (when <= 0L) return 0f;
        return Math.max(0f, Global.getSector().getClock().getElapsedDaysSince(when));
    }

    /** Happiness lost to "Last visited: X days ago". */
    public static float visitPenalty(float days) {
        return Pricing.clamp((days - VISIT_GRACE_DAYS) * VISIT_PENALTY_PER_DAY, 0f, VISIT_PENALTY_MAX);
    }

    /** The player docked at this market: everyone living in their homes there saw them. */
    public static void visit(MarketAPI m) {
        if (m == null) return;
        for (Property p : Portfolio.get().properties()) {
            if (!p.playerHome || !m.getId().equals(p.marketId)) continue;
            p.lastVisit = now();
            Tenant t = p.partner();
            if (t == null) continue;
            float gap = daysSince(t.lastVisit);
            if (gap >= 1f) t.lastGap = gap;
            t.lastVisit = now();
        }
    }

    // ---- Partner, proposal, children, gifts ----

    /** The home whose partner or child this person is, or null. */
    public static Property homeOf(PersonAPI person) {
        if (person == null || person.getMemoryWithoutUpdate() == null) return null;
        Object id = person.getMemoryWithoutUpdate().get(HOME_KEY);
        for (Property p : Portfolio.get().properties()) {
            if (id != null && p.id.equals(String.valueOf(id))) return p;
            if (p.partner() != null && p.partner().person == person) return p;
            for (Child c : p.children()) if (c.person == person) return p;
        }
        return null;
    }

    public static Child childOf(PersonAPI person) {
        if (person == null) return null;
        for (Property p : Portfolio.get().properties()) for (Child c : p.children()) if (c.person == person) return c;
        return null;
    }

    /** True: yes. False: the 2% who turn you down, and leave. */
    public static boolean propose(Property p, Random random) {
        Tenant t = p.partner();
        if (t == null || t.isSpouse()) return false;
        if (random.nextFloat() < CHEAT_CHANCE) {
            leaves(p, false, t.name + " turned down your proposal and left you. " + capital(he(t))
                    + (he(t).equals("they") ? " were" : " was") + " seeing someone else.");
            return false;
        }
        t.partner = 2;
        t.marriedAt = now();
        t.mood = Pricing.clamp(t.mood + 20f, -30f, 30f);
        if (t.person != null) {
            t.person.setRankId(post(t));
            t.person.setPostId(post(t));
        }
        Notify.event(p, t.name + " is now your " + noun(t) + ".", true, true);
        return true;
    }

    /** 0: tried today already, 1: a child is already on the way and you know it, 2: tried. */
    public static int tryForChild(Property p, Random random) {
        Tenant t = p.partner();
        if (t == null) return 0;
        if (t.lastTry > 0L && daysSince(t.lastTry) < 1f) return 0;
        if (t.conceivedAt > 0L && t.toldPregnant) return 1;
        t.lastTry = now();
        t.mood = Pricing.clamp(t.mood + 3f, -30f, 30f);
        if (t.conceivedAt <= 0L && random.nextFloat() < CHILD_CHANCE) {
            t.conceivedAt = now();
            t.toldPregnant = false;
        }
        return 2;
    }

    /** The player's special items that make a gift, as a cargo for the picker. Not furniture or quest items. */
    public static CargoAPI giftable() {
        CargoAPI out = Global.getFactory().createCargo(true);
        for (CargoStackAPI s : Global.getSector().getPlayerFleet().getCargo().getStacksCopy()) {
            if (!s.isSpecialStack() || s.getSpecialDataIfSpecial() == null) continue;
            String id = s.getSpecialDataIfSpecial().getId();
            if (id == null || id.startsWith(Upgrades.ITEM_PREFIX) || "wormhole_anchor".equals(id)) continue;
            if (s.getSpecialItemSpecIfSpecial() != null && (s.getSpecialItemSpecIfSpecial().hasTag("mission_item")
                    || s.getSpecialItemSpecIfSpecial().hasTag("mission"))) {
                continue;
            }
            out.addSpecial(s.getSpecialDataIfSpecial(), s.getSize());
        }
        return out;
    }

    /** What a gift did. */
    public static final class Gift {
        public float value;
        public final List<String> built = new ArrayList<>();
        public float repairs;
        public float kept;
        public String firstItem;
        public String firstName;
    }

    /**
     * The partner sells what you gave and spends the money on the house herself (Andrzej: "Item given sells for 80k =
     * wife buys 80k worth of upgrades"): upgrades the home can use, picked at random, then repairs. What is left she
     * keeps.
     */
    public static Gift gift(Property p, CargoAPI picked, Random random) {
        Gift g = new Gift();
        Tenant t = p.partner();
        if (t == null || picked == null) return g;
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        for (CargoStackAPI s : picked.getStacksCopy()) {
            if (!s.isSpecialStack() || s.getSpecialDataIfSpecial() == null) continue;
            float have = cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, s.getSpecialDataIfSpecial());
            float n = Math.min(have, s.getSize());
            if (n <= 0f) continue;
            if (g.firstItem == null) {
                g.firstItem = s.getSpecialDataIfSpecial().getId();
                g.firstName = s.getDisplayName();
            }
            g.value += s.getBaseValuePerUnit() * n;
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, s.getSpecialDataIfSpecial(), n);
        }
        if (g.value <= 0f) return g;
        float budget = g.value;
        List<Upgrades.Def> pool = new ArrayList<>(Upgrades.of(Upgrades.Kind.RENOVATION));
        Collections.shuffle(pool, random);
        boolean bought = true;
        while (bought) {
            bought = false;
            for (Upgrades.Def d : pool) {
                if (!wouldBuy(p, d)) continue;
                float cost = Actions.renovationCost(p, d);
                if (cost > budget) continue;
                budget -= cost;
                if (!d.fixes.isEmpty()) p.traits().removeAll(d.fixes);
                if (!d.oneTime) p.upgrades().add(Installed.done(d.id));
                g.built.add(d.name);
                p.addLog(t.name + " had " + d.name + " built.", true, t.name + " had " + d.name + " built.");
                bought = true;
                break;
            }
        }
        float repair = p.repairCost();
        if (repair > 0f && repair <= budget) {
            budget -= repair;
            g.repairs = repair;
            p.condition = 1f;
            p.condemned = false;
            p.traits().removeIf(id -> {
                landlordsector.data.HouseTraits.Def d = landlordsector.data.HouseTraits.get(id);
                return d != null && d.mark;
            });
        }
        g.kept = budget;
        t.mood = Pricing.clamp(t.mood + Math.min(30f, 5f + g.value / 20000f), -30f, 30f);
        return g;
    }

    private static boolean wouldBuy(Property p, Upgrades.Def d) {
        if (p.has(d.id) || !Upgrades.fits(d, p.tier().index, p.onStation(), p.traits())) return false;
        if (PropertyStats.noUse(d, p.market(), p.features(false))) return false;
        return Upgrades.clash(d, p.renovationIds()) == null;
    }

    // ---- Leaving, divorce, death ----

    /** The player ends it from the partner's comm link. */
    public static String breakUp(Property p) {
        Tenant t = p.partner();
        if (t == null) return "";
        return leaves(p, false, t.isSpouse() ? "You and " + t.name + " are divorced." : "You broke up with "
                + t.name + ".");
    }

    /**
     * The partner moves out or dies. Moving out takes their kids (Andrzej: "Your partner will also take the kids
     * seeing as you're leading a dangerous outlaw life in the persean sector"); a wife or husband also takes half the
     * home: it is sold and the player gets half. Kids who stay or leave young cost alimony until they grow up.
     *
     * @return the line shown to the player
     */
    public static String leaves(Property p, boolean dies, String text) {
        Tenant t = p.partner();
        if (t == null) return text;
        if (t.person != null) removePerson(t.person);
        p.tenant = null;
        if (dies) {
            Notify.event(p, text, false, true);
            return text;
        }
        boolean divorce = t.isSpouse();
        int took = 0;
        Iterator<Child> it = p.children().iterator();
        while (it.hasNext()) {
            Child c = it.next();
            if (!divorce && !t.id.equals(c.parentId)) continue;
            it.remove();
            took++;
            if (c.person != null) removePerson(c.person);
            c.person = null;
            if (!c.grown) Portfolio.get().exKids().add(c);
        }
        String line = text + (took > 0 ? " " + capital(he(t)) + " took the " + (took == 1 ? "kid" : "kids")
                + " with " + (he(t).equals("they") ? "them" : (female(t) ? "her" : "him")) + "." : "");
        if (divorce) {
            float sale = p.saleValue(true);
            float share = sale * DIVORCE_SHARE;
            release(p);
            Portfolio.get().properties().remove(p);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(share);
            line += " " + p.displayName() + " was sold. Your half: " + Misc.getDGSCredits(share) + ".";
            Notify.news(line, null, false);
            return line;
        }
        Notify.event(p, line, false, true);
        return line;
    }

    /** Takes everyone living in a home that is gone or sold out of the comm directories. */
    public static void release(Property p) {
        if (p.partner() != null && p.partner().person != null) removePerson(p.partner().person);
        for (Child c : p.children()) if (c.person != null) removePerson(c.person);
    }

    // ---- Children ----

    public static float ageDays(Child c) {
        return daysSince(c.bornAt);
    }

    public static float growUpDays() {
        return Settings.growUpCycles() * DAYS_PER_CYCLE;
    }

    /** "Newborn", "8 months old", "3 cycles old", "Grown up". */
    public static String age(Child c) {
        if (c.grown) return "Grown up";
        float d = ageDays(c);
        if (d < 30f) return "Newborn";
        if (d < DAYS_PER_CYCLE) {
            int months = Math.max(1, Math.round(d / 30f));
            return months + (months == 1 ? " month old" : " months old");
        }
        int cycles = (int) (d / DAYS_PER_CYCLE);
        return cycles + (cycles == 1 ? " cycle old" : " cycles old");
    }

    /** "an officer", "an administrator"... or null. */
    public static String pathName(String path) {
        if (path == null) return null;
        switch (path) {
            case Child.OFFICER: return "an officer";
            case Child.ADMIN: return "an administrator";
            case Child.WORKER: return "a miner";
            case Child.AGENT: return "an agent";
            case Child.GROWTH: return "a local celebrity";
            default: return null;
        }
    }

    /** A child's happiness today: the home, their parent as your partner, their traits, and how long you've been away. */
    public static float kidHappiness(Property p, Child c) {
        PropertyStats home = PropertyStats.withTenant(p, null);
        float h = home.happiness;
        Tenant t = p.partner();
        if (t != null) h += t.isSpouse() ? KID_HAPPY_SPOUSE : KID_HAPPY_PARTNER;
        for (String id : c.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null) h += d.happiness;
        }
        h -= visitPenalty(daysSince(p.lastVisit));
        return Pricing.clamp(h, 0f, 100f);
    }

    private static void birth(Property p, Tenant parent, Random random) {
        MarketAPI m = p.market();
        Child c = new Child();
        c.id = Portfolio.get().newId();
        c.bornAt = now();
        c.parentId = parent.id;
        c.parentName = parent.name;
        c.female = random.nextBoolean();
        Gender g = c.female ? Gender.FEMALE : Gender.MALE;
        int n = ++Portfolio.get().kidsBorn;
        FullName name = juniorName(n, g);
        c.name = (name.getFirst() + " " + name.getLast()).trim();
        c.traits = rollTraits(m, CHILD_SKIP_TRAITS, random);
        c.happiness = kidHappiness(p, c);
        PersonAPI kid = null;
        try {
            kid = Global.getSector().getPlayerFaction().createRandomPerson(g, random);
        } catch (Throwable ignored) {
        }
        if (kid == null) kid = Global.getFactory().createPerson();
        if (kid != null) {
            kid.setName(name);
            kid.setGender(g);
            c.portrait = kid.getPortraitSprite();
            kid.getMemoryWithoutUpdate().set(CHILD_FLAG, true);
            kid.getMemoryWithoutUpdate().set(CHILD_KEY, c.id);
            kid.getMemoryWithoutUpdate().set(HOME_KEY, p.id);
            if (m != null) placePerson(m, kid, "lls_" + c.noun());
        }
        c.person = kid;
        p.children().add(c);
        String text = female(parent) ? parent.name + " gave birth to your " + c.noun() + ", " + c.name + "."
                : "You had a " + c.noun() + " with " + parent.name + ": " + c.name + ".";
        Notify.event(p, text, true, true);
    }

    /** Renames a child (the player types a new first name, or a first and last name). */
    public static void rename(Child c, String typed) {
        if (c == null || typed == null || typed.trim().isEmpty()) return;
        String[] parts = typed.trim().split("\\s+", 2);
        PersonAPI me = Global.getSector().getPlayerPerson();
        String last = parts.length > 1 ? parts[1]
                : (me != null && me.getName() != null && me.getName().getLast() != null ? me.getName().getLast() : "");
        c.name = (parts[0] + " " + last).trim();
        if (c.person != null) c.person.setName(new FullName(parts[0], last, c.female ? Gender.FEMALE : Gender.MALE));
    }

    /**
     * A grown child starts their life: joins the fleet, takes an administrator's post, goes to the mines, becomes a
     * Nexerelin agent or moves to a colony. Returns false while they have to wait (no room in the fleet).
     */
    public static boolean startLife(Property p, Child c, Random random) {
        if (c.path == null) return false;
        int q = c.quality();
        try {
            switch (c.path) {
                case Child.OFFICER: {
                    CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
                    if (fleet.getFleetData().getOfficersCopy().size() >= Misc.getMaxOfficers(fleet)) return false;
                    PersonAPI o = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 1 + q,
                            OfficerManagerEvent.SkillPickPreference.ANY, random);
                    copyIdentity(c, o);
                    fleet.getFleetData().addOfficer(o);
                    news(c.name + " joined your fleet as an officer.");
                    break;
                }
                case Child.ADMIN: {
                    int max = Global.getSector().getPlayerStats().getAdminNumber().getModifiedInt();
                    if (Global.getSector().getCharacterData().getAdmins().size() >= max) return false;
                    PersonAPI a = OfficerManagerEvent.createAdmin(Global.getSector().getPlayerFaction(), q >= 3 ? 2 : 1,
                            random);
                    copyIdentity(c, a);
                    Global.getSector().getCharacterData().addAdmin(a);
                    news(c.name + " is one of your administrators now.");
                    break;
                }
                case Child.WORKER:
                    c.pay = WORKER_PAY * (1f + q * 0.5f);
                    Portfolio.get().adultKids().add(c);
                    news(c.name + " went to work in the mines and will send you " + Misc.getDGSCredits(c.pay)
                            + " a month.");
                    break;
                case Child.AGENT: {
                    if (!nexerelin() || c.person == null) return false;
                    removePerson(c.person);
                    NexAgents.add(c.person, 1 + q / 2, p.market());
                    news(c.name + " is your agent now.");
                    break;
                }
                case Child.GROWTH: {
                    MarketAPI colony = colonyFor(p);
                    if (colony == null) return false;
                    c.growth = GROWTH * (1f + q * 0.5f);
                    c.marketId = colony.getId();
                    Portfolio.get().adultKids().add(c);
                    news(c.name + " moved to " + colony.getName() + ". Everyone there wants a family now.");
                    break;
                }
                default:
                    return false;
            }
        } catch (Throwable t) {
            log.error("Landlordsector: " + c.name + " could not start as " + c.path, t);
            return false;
        }
        if (c.person != null) removePerson(c.person);
        if (!Child.AGENT.equals(c.path)) c.person = null;
        p.children().remove(c);
        return true;
    }

    /** True while Nexerelin is on (checked here so {@link NexAgents} is never loaded without it). */
    public static boolean nexerelin() {
        try {
            return Global.getSettings().getModManager().isModEnabled("nexerelin");
        } catch (Throwable t) {
            return false;
        }
    }

    /** The colony an inspiring child moves to: their own if it is yours, else your biggest. */
    private static MarketAPI colonyFor(Property p) {
        MarketAPI m = p.market();
        if (m != null && MarketRules.isOwn(m)) return m;
        MarketAPI best = null;
        for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!MarketRules.isOwn(o) || o.isHidden()) continue;
            if (best == null || o.getSize() > best.getSize()) best = o;
        }
        return best;
    }

    private static void copyIdentity(Child c, PersonAPI to) {
        Gender g = c.female ? Gender.FEMALE : Gender.MALE;
        if (c.person != null && c.person.getName() != null) to.setName(c.person.getName());
        else to.setName(new FullName(c.name, "", g));
        to.setGender(g);
        if (c.portrait != null) to.setPortraitSprite(c.portrait);
        if (to.getRelToPlayer() != null) to.getRelToPlayer().setRel(1f);
        to.getMemoryWithoutUpdate().set(BOND_FLAG, true);
    }

    private static void news(String text) {
        Notify.news(text, null, true);
    }

    // ---- Once a day and once a month ----

    /** Pregnancies, births, kids' happiness and growing up. Public for the headless check. */
    public static void daily(Random random) {
        seedBonds();
        for (Property p : new ArrayList<>(Portfolio.get().properties())) {
            if (!p.playerHome) continue;
            Tenant t = p.partner();
            if (t != null && t.conceivedAt > 0L) {
                float d = daysSince(t.conceivedAt);
                if (!t.toldPregnant && d >= TELL_DAYS) {
                    t.toldPregnant = true;
                    Notify.event(p, female(t) ? t.name + " is pregnant." : "You're pregnant. " + t.name
                            + " is the father.", true, true);
                }
                if (d >= BIRTH_DAYS) {
                    t.conceivedAt = 0L;
                    t.toldPregnant = false;
                    birth(p, t, random);
                }
            }
            for (Child c : new ArrayList<>(p.children())) {
                if (!c.grown) {
                    c.happiness = kidHappiness(p, c);
                    c.happySum += c.happiness;
                    c.happyDays++;
                    if (ageDays(c) >= growUpDays()) {
                        c.grown = true;
                        if (c.path == null || !startLife(p, c, random)) {
                            Notify.event(p, c.name + " is all grown up.", true, true);
                        }
                    }
                } else if (c.path != null) {
                    startLife(p, c, random);
                }
            }
            if (p.partner() != null) p.partner().kidsAtHome = p.children().size();
        }
    }

    /** Money from grown kids, alimony, and the month's closeness with your officers and administrators. */
    public static void monthly(MonthlyReport report, FDNode root) {
        Portfolio portfolio = Portfolio.get();
        float pay = 0f, alimony = 0f;
        for (Child c : portfolio.adultKids()) {
            if (c.pay <= 0f) continue;
            pay += c.pay;
            if (report != null) {
                FDNode n = report.getNode(root, "kid_" + c.id);
                n.name = c.name + ", mines";
                n.income += c.pay;
            }
        }
        float each = Settings.alimony();
        Iterator<Child> it = portfolio.exKids().iterator();
        while (it.hasNext()) {
            Child c = it.next();
            if (ageDays(c) >= growUpDays()) {
                it.remove();
                continue;
            }
            if (each <= 0f) continue;
            alimony += each;
            if (report != null) {
                FDNode n = report.getNode(root, "alimony_" + c.id);
                n.name = "Alimony for " + c.name;
                n.upkeep += each;
            }
        }
        portfolio.lastKidPay = pay;
        portfolio.lastAlimony = alimony;
        raiseBonds(BOND_PER_MONTH, false);
    }

    /** Markets where your wife or husband lives with you: no tariff there. */
    public static Set<String> spouseMarkets() {
        Set<String> out = new HashSet<>();
        for (Property p : Portfolio.get().properties()) {
            if (p.playerHome && p.partner() != null && p.partner().isSpouse()) out.add(p.marketId);
        }
        return out;
    }

    /** Growth each colony gets from your kids living there. */
    public static float growthAt(String marketId) {
        float g = 0f;
        for (Child c : Portfolio.get().adultKids()) if (marketId.equals(c.marketId)) g += c.growth;
        return g;
    }

    // ---- The comm directory ----

    private static void placePerson(MarketAPI m, PersonAPI who, String post) {
        try {
            who.setRankId(post);
            who.setPostId(post);
            who.setMarket(m);
            m.addPerson(who);
            CommDirectoryAPI dir = m.getCommDirectory();
            if (dir != null) dir.addPerson(who);
        } catch (Throwable t) {
            log.warn("Landlordsector: could not place " + who.getNameString() + " on " + m.getId(), t);
        }
    }

    private static void removePerson(PersonAPI who) {
        try {
            MarketAPI m = who.getMarket();
            if (m == null) return;
            CommDirectoryAPI dir = m.getCommDirectory();
            if (dir != null) dir.removePerson(who);
            m.removePerson(who);
        } catch (Throwable t) {
            log.warn("Landlordsector: could not remove " + who.getNameString(), t);
        }
        if (who.getMemoryWithoutUpdate() != null) {
            who.getMemoryWithoutUpdate().unset(PARTNER_FLAG);
            who.getMemoryWithoutUpdate().unset(CHILD_FLAG);
        }
    }

    static String capital(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
