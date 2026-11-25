package landlordsector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 1.16.0 (LLS-46): the partner features picked from Landlordsector Renewed. Proposals from afar, new
 * relationship energy, events that read as your wife's or husband's, and a partner who looks after the home. Numbers
 * and lines follow Renewed; every line says "your wife" or "your husband", never "partner" (the owner's note on LLS-46).
 */
public final class Partners {

    private Partners() {
    }

    // ---- Proposing from afar ----

    /** Person memory: turned down a proposal from afar, so only asks in person until you meet. */
    public static final String ASK_IN_PERSON = "$lls_askInPerson";
    /** A proposal to someone who isn't with you works this often. */
    public static final float AFAR_CHANCE = 0.15f;
    /** "With you": your fleet this close to their colony (plus its radius). */
    public static final float NEAR = 400f;
    /** {@link Family#cannotPropose} gives this while they want to be asked in person. */
    public static final String ASK_REASON = "Ask in person.";

    /** Officers in your fleet and Sophia are always with you; administrators when your fleet is at their colony. */
    public static boolean withYou(PersonAPI who) {
        if (who == null) return false;
        if (Family.isSophia(who) || Family.inFleet(who)) return true;
        MarketAPI m = marketOf(who);
        if (m == null || m.getPrimaryEntity() == null) return false;
        try {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            SectorEntityToken e = m.getPrimaryEntity();
            if (fleet == null || fleet.getContainingLocation() != e.getContainingLocation()) return false;
            return Misc.getDistance(fleet.getLocation(), e.getLocation()) <= e.getRadius() + NEAR;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The colony they govern, else the market they belong to. */
    static MarketAPI marketOf(PersonAPI who) {
        try {
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (m.isPlayerOwned() && m.getAdmin() == who) return m;
            }
        } catch (Throwable ignored) {
            // No economy (a headless check).
        }
        return who.getMarket();
    }

    /** "Gilead", or null when nobody knows. */
    static String whereIs(PersonAPI who) {
        MarketAPI m = marketOf(who);
        return m != null ? m.getName() : null;
    }

    /** True while they want to be asked in person. Meeting them clears it. */
    public static boolean askInPerson(PersonAPI who) {
        if (who == null || who.getMemoryWithoutUpdate() == null) return false;
        if (withYou(who)) {
            who.getMemoryWithoutUpdate().unset(ASK_IN_PERSON);
            return false;
        }
        return who.getMemoryWithoutUpdate().getBoolean(ASK_IN_PERSON);
    }

    /**
     * The roll for a proposal from afar. Null when it goes ahead (they are with you, or the 15% came up), else their
     * answer; a no makes them want to be asked in person.
     */
    static String fromAfar(PersonAPI who, Random random) {
        if (withYou(who)) {
            if (who.getMemoryWithoutUpdate() != null) who.getMemoryWithoutUpdate().unset(ASK_IN_PERSON);
            return null;
        }
        if (random.nextFloat() < AFAR_CHANCE) return null;
        if (who.getMemoryWithoutUpdate() != null) who.getMemoryWithoutUpdate().set(ASK_IN_PERSON, true);
        return who.getNameString() + ": \"Not like this. Ask me in person.\"";
    }

    /** The line after their role on the Family page: "On Gilead. Unlikely to accept from afar", or null with you. */
    public static String awayLine(PersonAPI who) {
        if (withYou(who)) return null;
        String world = whereIs(who);
        return (world != null ? "On " + world + ". " : "Away. ")
                + (askInPerson(who) ? "Wants to be asked in person" : "Unlikely to accept from afar");
    }

    /** The Propose button's tooltip when nothing else stands in the way. */
    public static String proposeTip(PersonAPI who) {
        String world = whereIs(who);
        if (!withYou(who) && askInPerson(who)) {
            return who.getNameString() + " turned down your proposal from afar. Go to " + (world != null ? world : "them")
                    + " and ask in person.";
        }
        if (!withYou(who)) {
            return (world != null ? who.getNameString() + " is on " + world + "." : who.getNameString()
                    + " isn't with you.") + " Proposals made from afar rarely succeed. Go there and ask in person.";
        }
        return Family.isSophia(who) ? "Moves in with you and keeps her research post." : "Retires and moves in with you.";
    }

    // ---- New relationship energy ----

    public static final float NEW_LOVE = 30f, NEW_LOVE_FULL_DAYS = 180f, NEW_LOVE_END_DAYS = 365f;

    /** Happiness from a new marriage: +30 for 6 months, then fading to nothing at a cycle. */
    public static float newLove(Tenant t) {
        if (t == null || !t.isPartner() || t.since == 0L) return 0f;
        float d = Family.daysSince(t.since);
        if (d <= NEW_LOVE_FULL_DAYS) return NEW_LOVE;
        if (d >= NEW_LOVE_END_DAYS) return 0f;
        return NEW_LOVE * (NEW_LOVE_END_DAYS - d) / (NEW_LOVE_END_DAYS - NEW_LOVE_FULL_DAYS);
    }

    /** The first 6 months: they won't leave you for being unhappy. */
    public static boolean honeymoon(Tenant t) {
        return t != null && t.isPartner() && t.since != 0L && Family.daysSince(t.since) < NEW_LOVE_FULL_DAYS;
    }

    // ---- Events that read as your wife's or husband's ----

    /** Tenant events your wife or husband never has, on top of the rent ones in {@link Family#PARTNER_SKIP_EVENTS}. */
    public static final Set<String> SKIP_EVENTS = new HashSet<>(Arrays.asList("graffiti", "trashed", "promotion",
            "lost_job", "loyal_stay"));
    /** Events a partner who works away (Sophia keeps her research post) never has. */
    public static final Set<String> AWAY_SKIP_EVENTS = new HashSet<>(Arrays.asList("heater_blast", "night_noise"));
    /** Their own wording of some events. */
    public static final Map<String, String> TEXT = new HashMap<>();
    /** The wording for a partner who works away. */
    public static final Map<String, String> AWAY_TEXT = new HashMap<>();
    /** What they say on big events; big events not listed here get no quote from them. */
    public static final Map<String, String[]> SAY = new HashMap<>();

    static {
        TEXT.put("mess", "$tenant made a filthy mess of $home.");
        TEXT.put("smashed_window", "$tenant broke a window at $home moving furniture.");
        TEXT.put("quietest", "The neighbors say $tenant is the quietest person on the street.");
        TEXT.put("recommended", "$tenant has been telling the neighbors good things about you.");
        TEXT.put("fell_sick", "$tenant fell sick and spent a few days in bed.");
        AWAY_TEXT.put("mugged", "$tenant was mugged on a visit home.");
        AWAY_TEXT.put("locked_out", "$tenant came home for a few days, found the lock jammed and broke a window to get in.");
        AWAY_TEXT.put("broken_lock", "The front door lock at $home broke.");
        SAY.put("small_fire", new String[] {"The kitchen caught fire. Everyone's out and fine. The kitchen isn't.",
            "Fire's out. Mostly. Someone should look at the wiring."});
        SAY.put("hospital", new String[] {"Doctors want to keep me a few more days.",
            "I'm in the ward for a bit. Let you know when I get out."});
        SAY.put("police_raid", new String[] {"I swear that crate wasn't mine.",
            "Police tore the place apart. The door needs fixing."});
        SAY.put("sabotage", new String[] {"It had to be done. The Lord sees all.", "The machines are silent now. Ludd be praised."});
        SAY.put("new_furniture", new String[] {"I got us some new furniture!", "Hope you like the new sofa. I didn't ask."});
    }

    /** Sophia keeps her research post: she works away from home. */
    public static boolean worksAway(Tenant t) {
        return t != null && Family.isSophia(t.person);
    }

    /** False when your wife or husband can't have this event. Only a Zealot wrecks machinery for Ludd. */
    public static boolean allowed(Tenant t, String eventId) {
        if (t == null || !t.isPartner() || eventId == null) return true;
        if (SKIP_EVENTS.contains(eventId)) return false;
        if (worksAway(t) && AWAY_SKIP_EVENTS.contains(eventId)) return false;
        if ("sabotage".equals(eventId) && !t.has("zealot")) return false;
        return true;
    }

    /** The event's text for your wife or husband (their own wording where there is one). */
    public static String text(Tenant t, String eventId, String text) {
        if (t == null || !t.isPartner() || eventId == null) return text;
        if (worksAway(t) && AWAY_TEXT.containsKey(eventId)) return AWAY_TEXT.get(eventId);
        return TEXT.getOrDefault(eventId, text);
    }

    /** What they say on a big event, or null. */
    public static String say(String eventId, Random random) {
        String[] lines = SAY.get(eventId);
        return lines == null ? null : lines[random.nextInt(lines.length)];
    }

    /** Traits your wife or husband never keeps (1.16.0: Long hours and Works from home too). */
    public static void stripTraits(Tenant t) {
        if (t != null && t.isPartner()) t.traits().removeAll(Family.PARTNER_SKIP_TRAITS);
    }

    // ---- A partner who looks after the home ----

    /** Warnings below these conditions; a condemned home gives this many days to repair before your family leaves. */
    public static final float WARN_LOW = 0.25f, WARN_CRITICAL = 0.10f, CONDEMNED_GRACE_DAYS = 30f;

    /** Your home with your wife or husband living there (Sophia is at her research post, so not her). */
    public static boolean caredFor(Property p) {
        Tenant t = p.partner();
        return p.playerHome && t != null && !worksAway(t);
    }

    /**
     * Once a month after the wear: a home with your wife or husband in it always gets the full monthly repair a happy
     * tenant gives, however happy they are. Accidents still damage it.
     */
    public static float monthlyCare(Property p, float happiness) {
        if (!caredFor(p) || p.condemned) return 0f;
        Economy eco = TierTable.economy();
        return Math.max(0f, eco.happyRepair - Pricing.happyRepair(happiness, eco.happyRepair, eco.happyRepairFrom));
    }

    /** Once a day for your home: warnings as it falls apart, and the 30 days a condemned home gives. */
    public static void daily(Property p) {
        boolean family = p.playerHome && p.partner() != null;
        if (!family || p.condition >= WARN_LOW) p.careWarned = 0;
        if (!family || !p.condemned) p.condemnedAt = 0L;
        if (!family) return;
        if (p.condemned) {
            if (p.condemnedAt == 0L) {
                p.condemnedAt = Family.now();
                String line = p.displayName() + " has been condemned. Unless it is repaired within "
                        + Math.round(CONDEMNED_GRACE_DAYS) + " days, " + whoLeaves(p) + ". Repairs would cost "
                        + Misc.getDGSCredits(p.repairCost()) + ".";
                warn(p, line, "has been condemned");
            }
            return;
        }
        int level = p.condition < WARN_CRITICAL ? 2 : (p.condition < WARN_LOW ? 1 : 0);
        if (level == 0 || level <= p.careWarned) return;
        p.careWarned = level;
        String part = level == 2 ? "close to being condemned" : "falling into disrepair";
        warn(p, p.displayName() + " is " + part + ". Repairs would cost " + Misc.getDGSCredits(p.repairCost())
                + ". If it is condemned, " + whoLeaves(p) + ".", part);
    }

    private static void warn(Property p, String line, String part) {
        p.addLog(line, false, part);
        Notify.news(line, part, false);
    }

    /** "your wife Mira Vance will leave you and take Ada and Ben". */
    static String whoLeaves(Property p) {
        Tenant t = p.partner();
        if (t == null) return "nobody will leave";
        List<String> kids = new ArrayList<>();
        for (Child c : p.children()) {
            if (!t.isSpouse() && !t.id.equals(c.parentId)) continue;
            kids.add(Kids.firstName(c));
        }
        return Family.who(t) + " will leave you" + (kids.isEmpty() ? "" : " and take " + Family.names(kids));
    }

    /** True while your family still waits for a condemned home to be repaired. */
    public static boolean waits(Property p) {
        if (!p.playerHome || p.partner() == null || !p.condemned) return false;
        if (p.condemnedAt == 0L) return true;
        return Family.daysSince(p.condemnedAt) < CONDEMNED_GRACE_DAYS;
    }

    /** The Family page's red line for a home in trouble, or null. */
    public static String repairWarning(Property p) {
        if (!p.playerHome || p.partner() == null) return null;
        if (p.condemned) {
            int days = (int) Math.max(1, Math.ceil(CONDEMNED_GRACE_DAYS - Family.daysSince(p.condemnedAt)));
            return "Condemned. Repair within " + days + (days == 1 ? " day" : " days");
        }
        if (p.careWarned > 0) return "Needs repairs: " + Math.round(p.condition * 100f) + "%";
        return null;
    }
}
