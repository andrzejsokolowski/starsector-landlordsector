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
    /** Person memory: days an administrator governed one of your colonies, counted from 1.2.2. */
    public static final String GOVERNED_KEY = "$lls_governedDays";
    /** Person memory: turned your proposal down (the 2%), for good. */
    public static final String CHEATED_KEY = "$lls_cheated";
    /** Andrzej, 2026-11-16: administrators say yes after governing for one cycle. */
    public static final float GOVERN_DAYS = 365f;
    /** AoTD Vaults of Knowledge: Sophia Anderson's important-person id, and the mod's id. */
    public static final String SOPHIA_ID = "aotd_sophia";
    public static final String VOK_MOD = "aotd_vok";

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

    // ---- Who you can marry (Andrzej, 2026-11-16) ----

    /**
     * Officers and administrators of the opposite sex, and Sophia Anderson (AoTD) with Kaysaar's Demise on. Those who
     * would say yes come first.
     */
    public static List<PersonAPI> candidates() {
        List<PersonAPI> out = new ArrayList<>();
        PersonAPI me = Global.getSector().getPlayerPerson();
        PersonAPI sophia = sophia();
        if (sophia != null && fits(me, sophia) && partnerHome(sophia) == null) out.add(sophia);
        for (PersonAPI p : service()) if (fits(me, p) && !out.contains(p)) out.add(p);
        out.sort((a, b) -> Boolean.compare(wouldRefuse(a) != null, wouldRefuse(b) != null));
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
        Gender g = genderOf(p);
        if (g != Gender.MALE && g != Gender.FEMALE) return false;
        Gender mine = playerGender();
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

    private static boolean inFleet(PersonAPI p) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return false;
        for (OfficerDataAPI o : fleet.getFleetData().getOfficersCopy()) if (o.getPerson() == p) return true;
        return false;
    }

    public static boolean isSophia(PersonAPI p) {
        return p != null && SOPHIA_ID.equals(p.getId());
    }

    /**
     * Sophia Anderson when Kaysaar's Demise is on and she sits on your AoTD research council, else null. AoTD's own
     * classes are only touched through {@link AotdScience}, and only while Vaults of Knowledge is on.
     */
    public static PersonAPI sophia() {
        if (!Settings.kaysaarsDemise()) return null;
        try {
            if (!Global.getSettings().getModManager().isModEnabled(VOK_MOD)) return null;
            PersonAPI s = Global.getSector().getImportantPeople().getPerson(SOPHIA_ID);
            return s != null && AotdScience.onCouncil(s) ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static int level(PersonAPI p) {
        return p != null && p.getStats() != null ? p.getStats().getLevel() : 1;
    }

    /** The highest level this officer can reach in your fleet (Officer Training and cryopod officers count). */
    public static int maxLevel(PersonAPI p) {
        try {
            Object plugin = Global.getSettings().getPlugin("officerLevelUp");
            if (plugin instanceof com.fs.starfarer.api.plugins.OfficerLevelupPlugin) {
                int m = ((com.fs.starfarer.api.plugins.OfficerLevelupPlugin) plugin).getMaxLevel(p);
                if (m > 0) return m;
            }
        } catch (Throwable ignored) {
        }
        try {
            float f = Global.getSettings().getFloat("officerMaxLevel");
            if (f > 0f) return (int) f;
        } catch (Throwable ignored) {
        }
        return 5;
    }

    public static float governedDays(PersonAPI p) {
        if (p == null || p.getMemoryWithoutUpdate() == null) return 0f;
        Object v = p.getMemoryWithoutUpdate().get(GOVERNED_KEY);
        return v instanceof Number ? ((Number) v).floatValue() : 0f;
    }

    /** A day of governance for every administrator running one of your colonies. Public for the headless check. */
    public static void countGovernance() {
        List<PersonAPI> seen = new ArrayList<>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m == null || !m.isPlayerOwned()) continue;
            PersonAPI a = m.getAdmin();
            if (a == null || a.isPlayer() || a.isAICore() || seen.contains(a) || !isAdmin(a)) continue;
            seen.add(a);
            if (a.getMemoryWithoutUpdate() != null) a.getMemoryWithoutUpdate().set(GOVERNED_KEY, governedDays(a) + 1f);
        }
    }

    /**
     * Why this person would turn you down, or null when they would say yes (Andrzej, 2026-11-16: officers only at
     * their highest level, administrators after a cycle of governing, Sophia always).
     */
    public static String wouldRefuse(PersonAPI who) {
        if (isSophia(who)) return null;
        if (who.getMemoryWithoutUpdate() != null && who.getMemoryWithoutUpdate().getBoolean(CHEATED_KEY)) {
            return "Seeing someone else.";
        }
        if (inFleet(who)) {
            int max = maxLevel(who);
            return level(who) >= max ? null : "Only says yes at level " + max + ".";
        }
        return governedDays(who) >= GOVERN_DAYS ? null : "Only says yes after governing a colony for 12 months.";
    }

    /** "Officer, level 5 of 6", "Administrator, governed 7 of 12 months", "Scientist on your research council". */
    public static String role(PersonAPI p) {
        if (isSophia(p)) return "Scientist on your research council";
        if (inFleet(p)) return "Officer, level " + level(p) + " of " + maxLevel(p);
        int months = Math.min(12, (int) (governedDays(p) / 30f));
        return "Administrator, governed " + months + " of 12 months";
    }

    /** The home where this person lives with you as your partner, or null. */
    public static Property partnerHome(PersonAPI who) {
        if (who == null) return null;
        for (Property p : Portfolio.get().properties()) {
            if (p.partner() != null && p.partner().person == who) return p;
        }
        return null;
    }

    // ---- The player's home ----

    /** Null when the player can move into this home, or why not. */
    public static String cannotDeclare(Property p) {
        if (p.playerHome) return "You already live here.";
        if (p.market() == null) return "The colony is gone.";
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

    /** Null when you can propose to this person here, or why not. */
    public static String cannotPropose(Property p, PersonAPI who) {
        if (!p.playerHome) return "You don't live here.";
        if (p.tenant != null) return "Someone already lives here with you.";
        if (p.condemned) return "Repair it first.";
        if (p.market() == null) return "The colony is gone.";
        if (partnerHome(who) != null) return "Already married to you.";
        return wouldRefuse(who);
    }

    /**
     * Andrzej, 2026-11-16: "Propose to officers to retire them and become your spouse." The one who says yes leaves
     * your service (Sophia keeps her research post) and lives here as your wife or husband. 2% are cheating on you
     * and say no for good; Sophia always says yes.
     */
    public static String propose(Property p, PersonAPI who, Random random) {
        String no = cannotPropose(p, who);
        if (no != null) return no;
        boolean sophia = isSophia(who);
        if (!sophia && random.nextFloat() < CHEAT_CHANCE) {
            if (who.getMemoryWithoutUpdate() != null) who.getMemoryWithoutUpdate().set(CHEATED_KEY, true);
            Feats.mark("cheated");
            return who.getNameString() + ": \"Actually... I'm cheating on you.\"";
        }
        MarketAPI m = p.market();
        if (!sophia) leaveService(who);
        Tenant t = new Tenant();
        t.id = Portfolio.get().newId();
        t.name = who.getNameString();
        t.portrait = who.getPortraitSprite();
        t.factionId = Global.getSector().getPlayerFaction().getId();
        t.source = "partner";
        t.screened = true;
        t.wealth = 3;
        t.traits = rollTraits(m, PARTNER_SKIP_TRAITS, random);
        t.partner = 2;
        t.marriedAt = now();
        t.person = who;
        t.since = now();
        t.lastVisit = now();
        p.lastVisit = now();
        p.tenant = t;
        p.advertising = false;
        p.applicants().clear();
        Feats.mark("married");
        if (sophia) Feats.mark("sophia");
        placePerson(m, who, post(t));
        who.getMemoryWithoutUpdate().set(PARTNER_FLAG, true);
        who.getMemoryWithoutUpdate().set(HOME_KEY, p.id);
        String now = t.name + " is now your " + noun(t) + ".";
        p.addLog(now, true, now);
        HomeownerScript.refresh();
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        String yes = random.nextBoolean() ? "Yes! Yes, of course!" : "I thought you'd never ask. Yes!";
        return t.name + ": \"" + yes + "\" " + now;
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
        return t != null && t.person != null && genderOf(t.person) == Gender.FEMALE;
    }

    private static boolean male(Tenant t) {
        return t != null && t.person != null && genderOf(t.person) == Gender.MALE;
    }

    /**
     * 1.7.0: AoTD's Sophia Anderson is stored as a man (Andrzej's screenshot: "Rank: Husband", "Meet him in person"),
     * so she counts as a woman here.
     */
    public static Gender genderOf(PersonAPI p) {
        if (p == null) return Gender.ANY;
        return isSophia(p) ? Gender.FEMALE : p.getGender();
    }

    /**
     * 1.7.0 (Andrzej: "a lunasettings override to treat your character as male or female ... They can select their
     * partner's sex by selecting their sex, but keep their portrait"): the player's sex for the family, the setting
     * first, else the character's.
     */
    public static Gender playerGender() {
        String s = Settings.playerSex();
        if ("Male".equals(s)) return Gender.MALE;
        if ("Female".equals(s)) return Gender.FEMALE;
        PersonAPI me = Global.getSector().getPlayerPerson();
        return me != null ? me.getGender() : Gender.ANY;
    }

    /** "girlfriend", "wife", "boyfriend", "husband", "partner" or "spouse". */
    public static String noun(Tenant t) {
        if (t == null) return "partner";
        if (t.isSpouse()) return female(t) ? "wife" : (male(t) ? "husband" : "spouse");
        return female(t) ? "girlfriend" : (male(t) ? "boyfriend" : "partner");
    }

    /**
     * 1.8.0 (Andrzej: "the notifications for your wife's name should start with "Your wife, {name}, did something"
     * in the intel log"): "Your wife, Mira Okoye," at the start of a line.
     */
    public static String subject(Tenant t) {
        if (t == null) return "";
        return "Your " + noun(t) + ", " + t.name + ",";
    }

    /** "your wife Mira Okoye" inside a line. */
    public static String who(Tenant t) {
        if (t == null) return "";
        return "your " + noun(t) + " " + t.name;
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
        return playerGender() == Gender.FEMALE ? "Mom" : "Dad";
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

    static long now() {
        return Global.getSector().getClock().getTimestamp();
    }

    public static float daysSince(long when) {
        if (when == 0L) return 0f; // 1.7.0: game timestamps are negative (cycle 206 is before 1970), 0 means never
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

    /**
     * 1.2.2: there is no girlfriend or boyfriend stage any more (Andrzej: officers "become your spouse"). Partners who
     * moved in under 1.2.0 or 1.2.1 are married on load. Public for the headless check.
     */
    public static void marryEveryPartner() {
        for (Property p : Portfolio.get().properties()) {
            Tenant t = p.partner();
            if (t == null) continue;
            if (!t.isSpouse()) {
                t.partner = 2;
                t.marriedAt = now();
            }
            // 1.7.0: Wife or Husband shown again on every load (Sophia showed as Husband).
            if (t.person != null) {
                if (isSophia(t.person) && t.person.getGender() != Gender.FEMALE) t.person.setGender(Gender.FEMALE);
                t.person.setRankId(post(t));
                t.person.setPostId(post(t));
            }
        }
    }

    /**
     * Andrzej, 2026-11-16: "the more you have, the higher the happiness and comfort requirements". The partner's
     * happiness and the home's comfort another child needs, {happiness, comfort}, or null for the first one.
     */
    public static float[] childNeeds(int kids) {
        if (kids <= 0) return null;
        return new float[] {Math.min(95f, 40f + 10f * kids), Math.min(95f, 30f + 10f * kids)};
    }

    /**
     * 1.8.0 (Andrzej: "make an upgrade for lived-in homes that increases pregnancy chance"): 25%, plus what the home's
     * upgrades add (the Romantic bedroom: +25%), at most 90%.
     */
    public static float childChance(Property p) {
        float c = CHILD_CHANCE;
        for (String id : p.renovationIds()) {
            Upgrades.Def d = Upgrades.get(id);
            if (d != null && d.childChance > 0f) c += d.childChance;
        }
        return Math.min(0.9f, c);
    }

    /** Why you can't try for another child yet, or null. */
    public static String cannotTry(Property p) {
        float[] need = childNeeds(p.children().size());
        if (need == null || p.partner() == null) return null;
        PropertyStats st = p.stats();
        if (st.happiness >= need[0] && st.comfort >= need[1]) return null;
        return "Another child needs happiness " + Math.round(need[0]) + " and comfort " + Math.round(need[1])
                + ". Now: " + Math.round(st.happiness) + " and " + Math.round(st.comfort) + ".";
    }

    /** 0: tried today already, 1: a child is already on the way and you know it, 2: tried, 3: not happy enough. */
    public static int tryForChild(Property p, Random random) {
        Tenant t = p.partner();
        if (t == null) return 0;
        if (t.conceivedAt != 0L && t.toldPregnant) return 1;
        if (cannotTry(p) != null) return 3;
        if (t.lastTry != 0L && daysSince(t.lastTry) < 1f) return 0;
        t.lastTry = now();
        t.mood = Pricing.clamp(t.mood + 3f, -30f, 30f);
        if (t.conceivedAt == 0L && random.nextFloat() < childChance(p)) {
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
                p.addLog(subject(t) + " had " + d.name + " built.", true, subject(t) + " had " + d.name + " built.");
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
        if (p.has(d.id) || !Upgrades.fits(d, p)) return false;
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
        if (divorce) Feats.mark("divorce");
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
            // 1.14.0: the sale pays off the mortgage first; you get half of what is left (or pay what it lacks).
            float loan = Mortgage.has(p) ? p.loanLeft : 0f;
            Mortgage.clear(p);
            float rest = p.saleValue(true) - loan;
            float share = rest >= 0f ? rest * DIVORCE_SHARE : rest;
            release(p);
            Portfolio.get().properties().remove(p);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(share);
            line += " " + p.displayName() + " was sold. " + (share >= 0f ? "Your half: " + Misc.getDGSCredits(share)
                    : "The sale did not cover the mortgage. You paid " + Misc.getDGSCredits(-share)) + ".";
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

    /** A child is born (public since 1.6.0 for the console's lls_baby). */
    public static void birth(Property p, Tenant parent, Random random) {
        MarketAPI m = p.market();
        Child c = new Child();
        c.id = Portfolio.get().newId();
        c.bornAt = now();
        c.parentId = parent.id;
        c.parentName = parent.name;
        Feats.mark("child");
        c.female = random.nextBoolean();
        Gender g = c.female ? Gender.FEMALE : Gender.MALE;
        int n = ++Portfolio.get().kidsBorn;
        FullName name = juniorName(n, g);
        c.name = (name.getFirst() + " " + name.getLast()).trim();
        // 1.8.0 (Andrzej: "only roll those traits on childhood stage"): babies have none.
        c.traits = new ArrayList<>();
        c.stage = Kids.BABY;
        c.look = random.nextInt(Kids.LOOKS);
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
            c.adultPortrait = c.portrait;
            c.person = kid;
            Kids.updatePortrait(c);
            kid.getMemoryWithoutUpdate().set(CHILD_FLAG, true);
            kid.getMemoryWithoutUpdate().set(CHILD_KEY, c.id);
            kid.getMemoryWithoutUpdate().set(HOME_KEY, p.id);
            if (m != null) placePerson(m, kid, "lls_" + c.noun());
        }
        c.person = kid;
        p.children().add(c);
        String text = female(parent) ? subject(parent) + " gave birth to your " + c.noun() + ", " + c.name + "."
                : "You had a " + c.noun() + " with " + who(parent) + ": " + c.name + ".";
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
        Feats.mark("grown");
        int q = c.quality();
        // 1.8.0 (LLS-28): what they learned makes them better, bad traits and a hard childhood worse.
        int learned = Kids.training(c);
        int bad = Kids.badMarks(c);
        float scale = (1f + q * 0.5f) * (1f + learned * 0.25f) * (float) Math.pow(0.8, bad)
                * (1f + Kids.GOOD_TRAIT_BONUS * Kids.goodTraits(c));
        String with = "";
        try {
            switch (c.path) {
                case Child.OFFICER: {
                    CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
                    if (fleet.getFleetData().getOfficersCopy().size() >= Misc.getMaxOfficers(fleet)) return false;
                    int level = Math.max(1, Math.min(OFFICER_MAX_START, 1 + q + learned));
                    PersonAPI o = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), level,
                            OfficerManagerEvent.SkillPickPreference.ANY, random);
                    copyIdentity(c, o);
                    List<String> skills = new ArrayList<>(Kids.goodSkills(c, true));
                    skills.addAll(Kids.badSkills(c, true));
                    if (c.academy) skills.add(Kids.ACADEMY_OFFICER);
                    if (c.classes) skills.add(Kids.TUTORED);
                    giveSkills(c, o, skills);
                    fleet.getFleetData().addOfficer(o);
                    with = skillNames(skills);
                    news(c.name + " joined your fleet as an officer, level " + level + "." + with);
                    break;
                }
                case Child.ADMIN: {
                    int max = Global.getSector().getPlayerStats().getAdminNumber().getModifiedInt();
                    if (Global.getSector().getCharacterData().getAdmins().size() >= max) return false;
                    PersonAPI a = OfficerManagerEvent.createAdmin(Global.getSector().getPlayerFaction(),
                            q + learned >= 3 ? 2 : 1, random);
                    copyIdentity(c, a);
                    List<String> skills = new ArrayList<>(Kids.goodSkills(c, false));
                    skills.addAll(Kids.badSkills(c, false));
                    if (c.academy) skills.add(Kids.ACADEMY_ADMIN);
                    if (c.classes) skills.add(Kids.BUSINESS);
                    giveSkills(c, a, skills);
                    Global.getSector().getCharacterData().addAdmin(a);
                    with = skillNames(skills);
                    news(c.name + " is one of your administrators now." + with);
                    break;
                }
                case Child.WORKER:
                    c.pay = Math.round(WORKER_PAY * scale);
                    Portfolio.get().adultKids().add(c);
                    c.homeId = p.id;
                    news(c.name + " went to work in the mines and will send " + Misc.getDGSCredits(c.pay)
                            + " home every month.");
                    break;
                case Child.AGENT: {
                    if (!nexerelin() || c.person == null) return false;
                    removePerson(c.person);
                    NexAgents.add(c.person, 1 + q / 2 + learned - bad / 2, p.market(), c.classes);
                    news(c.name + " is your agent now.");
                    break;
                }
                case Child.GROWTH: {
                    MarketAPI colony = colonyFor(p);
                    if (colony == null) return false;
                    c.growth = GROWTH * scale;
                    if (c.classes) c.stability = 1f;
                    c.marketId = colony.getId();
                    Portfolio.get().adultKids().add(c);
                    news(c.name + " moved to " + colony.getName() + ". " + (c.female ? "Her" : "His") + " beauty inspires many people to start a family of their own.");
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

    /** 1.8.0: a grown kid's officer level is 1 + childhood quality + what they learned, at most this. */
    public static final int OFFICER_MAX_START = 5;

    private static void giveSkills(Child c, PersonAPI to, List<String> skills) {
        for (String id : skills) {
            try {
                to.getStats().setSkillLevel(id, 1f);
                c.skills().add(id);
            } catch (Throwable t) {
                log.warn("Landlordsector: no skill " + id, t);
            }
        }
    }

    /** " Starts with: ADHD, Academy graduate." or "". */
    private static String skillNames(List<String> skills) {
        if (skills.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (String id : skills) {
            String n = id;
            try {
                n = Global.getSettings().getSkillSpec(id).getName();
            } catch (Throwable ignored) {
            }
            names.add(n);
        }
        return " Starts with: " + String.join(", ", names) + ".";
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
        String face = c.adultPortrait != null ? c.adultPortrait : c.portrait;
        if (face != null) to.setPortraitSprite(face);
        if (to.getRelToPlayer() != null) to.getRelToPlayer().setRel(1f);
    }

    private static void news(String text) {
        Notify.news(text, null, true);
    }

    // ---- Once a day and once a month ----

    /** Pregnancies, births, kids' happiness and growing up. Public for the headless check. */
    public static void daily(Random random) {
        countGovernance();
        for (Property p : new ArrayList<>(Portfolio.get().properties())) {
            if (!p.playerHome) continue;
            Tenant t = p.partner();
            if (t != null && t.conceivedAt != 0L) {
                float d = daysSince(t.conceivedAt);
                if (!t.toldPregnant && d >= TELL_DAYS) {
                    t.toldPregnant = true;
                    Notify.event(p, female(t) ? subject(t) + " is pregnant." : "You're pregnant. " + subject(t)
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
                    Kids.daily(p, c, random);
                    if (!p.children().contains(c)) continue;
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
            // 1.3.0 (Andrzej: "Make the kid send the money HOME - For repairs, new furniture, upgrades, etc."):
            // the pay goes to the home they grew up in, or your home now. Only with no home at all is it yours.
            Property home = kidHome(c);
            if (home != null) {
                sendHome(home, c, c.pay, RANDOM_HOME);
                continue;
            }
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
        // 1.8.0 (Andrzej: a child "costs more money to maintain"): every kid at home costs by stage, adults nothing.
        float raising = 0f;
        for (Property p : portfolio.properties()) {
            for (Child c : p.children()) {
                float cost = Kids.upkeep(c);
                if (cost <= 0f) continue;
                raising += cost;
                if (report != null) {
                    FDNode n = report.getNode(root, "raising_" + c.id);
                    n.name = "Raising " + c.name;
                    n.upkeep += cost;
                }
            }
        }
        // 1.8.0: miners who took extra classes sometimes find a piece of furniture and send it home.
        for (Child c : portfolio.adultKids()) {
            if (c.pay <= 0f || !c.classes || RANDOM_HOME.nextFloat() >= MINER_FIND_CHANCE) continue;
            List<Upgrades.Def> pieces = new ArrayList<>(Upgrades.of(Upgrades.Kind.FURNITURE));
            if (pieces.isEmpty()) continue;
            Upgrades.Def d = pieces.get(RANDOM_HOME.nextInt(pieces.size()));
            portfolio.furniture().merge(d.id, 1, Integer::sum);
            Notify.news(c.name + " found " + article(d.name) + " in the mine and sent it home. It is in your furniture"
                    + " storage.", d.name, true);
        }
        portfolio.lastKidPay = pay;
        portfolio.lastAlimony = alimony;
        portfolio.lastKidCost = raising;
    }

    private static final Random RANDOM_HOME = new Random();
    /** 1.8.0: a miner with extra classes finds a piece of furniture this often a month. */
    public static final float MINER_FIND_CHANCE = 0.25f;

    private static String article(String name) {
        String n = name.toLowerCase();
        return ("aeiou".indexOf(n.charAt(0)) >= 0 ? "an " : "a ") + n;
    }

    /** The home a grown miner sends money to: the one they grew up in, else any home you live in, else null. */
    public static Property kidHome(Child c) {
        Property any = null;
        for (Property p : Portfolio.get().properties()) {
            if (p.market() == null) continue;
            if (c.homeId != null && c.homeId.equals(p.id)) return p;
            if (any == null && p.playerHome) any = p;
        }
        return any;
    }

    /**
     * 1.3.0: money a grown kid sends home. It goes into the home's fund, which pays for repairs first, then
     * furniture for empty slots, then upgrades the home can use. What is left waits for next month.
     */
    public static void sendHome(Property p, Child c, float amount, Random random) {
        p.homeFund += amount;
        float repair = p.repairCost();
        if (repair > 0f && repair <= p.homeFund) {
            p.homeFund -= repair;
            p.condition = 1f;
            p.condemned = false;
            p.traits().removeIf(id -> {
                landlordsector.data.HouseTraits.Def d = landlordsector.data.HouseTraits.get(id);
                return d != null && d.mark;
            });
            p.addLog("Repaired with " + c.name + "'s money.", true, "Repaired");
        }
        List<Upgrades.Def> furniture = new ArrayList<>(Upgrades.of(Upgrades.Kind.FURNITURE));
        Collections.shuffle(furniture, random);
        for (Upgrades.Def d : furniture) {
            if (p.furnitureCount() >= p.tier().slots) break;
            if (p.hasFurniture(d.id) || !Upgrades.fits(d, p)) continue;
            float price = furniturePrice(d);
            if (price > p.homeFund) continue;
            p.homeFund -= price;
            p.upgrades().add(new Installed(d.id, true, 0f));
            p.addLog("Bought with " + c.name + "'s money: " + d.name + ".", true, d.name);
        }
        List<Upgrades.Def> pool = new ArrayList<>(Upgrades.of(Upgrades.Kind.RENOVATION));
        Collections.shuffle(pool, random);
        for (Upgrades.Def d : pool) {
            if (d.oneTime || !wouldBuy(p, d)) continue;
            float cost = Actions.renovationCost(p, d);
            if (cost > p.homeFund) continue;
            p.homeFund -= cost;
            if (!d.fixes.isEmpty()) p.traits().removeAll(d.fixes);
            p.upgrades().add(Installed.done(d.id));
            p.addLog("Bought with " + c.name + "'s money: " + d.name + ".", true, d.name);
        }
    }

    /** What a piece of furniture costs in the shops (its item's base price), 3,000 when the game can't say. */
    static float furniturePrice(Upgrades.Def d) {
        try {
            float v = Global.getSettings().getSpecialItemSpec(d.itemId()).getBasePrice();
            return v > 0f ? v : 3000f;
        } catch (Throwable t) {
            return 3000f;
        }
    }

    // ---- The Kite drive (1.3.0) ----

    /** Crash chance of a Kite drive: 2%, plus 5% for each D-mod on the ship. */
    public static final float KITE_CRASH = 0.02f, KITE_CRASH_PER_DMOD = 0.05f;

    /** The Kite in your fleet with the fewest D-mods, or null when you have none. */
    public static FleetMemberAPI kite() {
        FleetMemberAPI best = null;
        int bestD = Integer.MAX_VALUE;
        try {
            for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (m.isFighterWing() || m.getHullSpec() == null) continue;
                String base = m.getHullSpec().getBaseHullId();
                String hull = m.getHullSpec().getHullId();
                if (!(base != null && base.startsWith("kite")) && !(hull != null && hull.startsWith("kite"))) continue;
                int d = dmods(m);
                if (d < bestD) {
                    best = m;
                    bestD = d;
                }
            }
        } catch (Throwable ignored) {
            // No player fleet (a headless check).
        }
        return best;
    }

    static int dmods(FleetMemberAPI m) {
        try {
            return com.fs.starfarer.api.impl.campaign.DModManager.getNumDMods(m.getVariant());
        } catch (Throwable t) {
            return 0;
        }
    }

    public static float crashChance(int dmods) {
        return KITE_CRASH + KITE_CRASH_PER_DMOD * Math.max(0, dmods);
    }

    /**
     * The drive in your Kite. Null when it went fine; otherwise the Kite exploded and the child died, and this is the
     * line to show. The ship is lost too.
     */
    public static String kiteDrive(Property p, Child c, Random random) {
        FleetMemberAPI k = kite();
        if (k == null) return null;
        if (random.nextFloat() >= crashChance(dmods(k))) return null;
        try {
            Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(k);
        } catch (Throwable ignored) {
            // A headless check without a fleet.
        }
        p.children().remove(c);
        if (c.person != null) removePerson(c.person);
        c.person = null;
        Tenant t = p.partner();
        if (t != null) t.mood = Pricing.clamp(t.mood - 30f, -30f, 30f);
        Feats.mark("kite");
        Feats.mark("childLost");
        String line = "The Kite's engine failed on takeoff. " + c.name + " died in the explosion.";
        Notify.event(p, c.name + " died when the Kite exploded.", false, true);
        return line;
    }

    /** 1.8.0: a kid died in an event. Your spouse takes it hard. */
    static void kidDies(Property p, Child c) {
        Feats.mark("childLost");
        p.children().remove(c);
        if (c.person != null) removePerson(c.person);
        c.person = null;
        Tenant t = p.partner();
        if (t != null) t.mood = Pricing.clamp(t.mood - 30f, -30f, 30f);
    }

    /** Markets where your wife or husband lives with you: no tariff there. */
    public static Set<String> spouseMarkets() {
        Set<String> out = new HashSet<>();
        for (Property p : Portfolio.get().properties()) {
            if (p.playerHome && p.partner() != null && p.partner().isSpouse()) out.add(p.marketId);
        }
        return out;
    }

    /** 1.8.0: stability each colony gets from your celebrity kids who took extra classes. */
    public static float stabilityAt(String marketId) {
        float s = 0f;
        for (Child c : Portfolio.get().adultKids()) if (marketId.equals(c.marketId)) s += c.stability;
        return s;
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

    static void removePerson(PersonAPI who) {
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
