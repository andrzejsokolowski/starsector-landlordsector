package landlordsector;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.characters.PersonAPI;

import landlordsector.data.TenantTraits;

/** Makes tenants: name and portrait from their faction, traits from the faction and the market. */
public final class Tenants {

    private static final String[] FIRST = {"Ada", "Bren", "Cato", "Dara", "Emil", "Fenna", "Gus", "Hela", "Ivo",
        "Juno", "Kass", "Lio", "Mira", "Nils", "Oona", "Pell", "Quin", "Rhea", "Soren", "Tova"};
    private static final String[] LAST = {"Arden", "Brask", "Corvin", "Drake", "Esker", "Fallow", "Grail",
        "Hale", "Ingram", "Joss", "Kade", "Lorne", "Marsh", "Nore", "Orrin", "Pike", "Quell", "Rook", "Sable", "Teague"};

    private Tenants() {
    }

    public static Set<String> conditions(MarketAPI m) {
        if (m == null || m.getConditions() == null) return Collections.emptySet();
        Set<String> s = new HashSet<>();
        for (MarketConditionAPI c : m.getConditions()) {
            // A suppressed condition (Filtered Air hiding pollution, for example) has no effect, so it does not count.
            if (c != null && c.getId() != null && !m.isConditionSuppressed(c.getId())) s.add(c.getId());
        }
        return s;
    }

    /**
     * A new tenant.
     *
     * @param factionId where the tenant comes from: their name, portrait, wealth and traits
     * @param market where they are found: its conditions weigh the traits (may be null)
     * @param badMult multiplies the chance of bad traits (screening service 0.5, good finders lower)
     * @param screened all traits visible
     */
    public static Tenant create(String factionId, MarketAPI market, float badMult, boolean screened, String source,
            Random random) {
        Tenant t = new Tenant();
        t.id = Portfolio.get().newId();
        t.factionId = factionId;
        t.source = source;
        t.screened = screened;
        t.shown = 1;
        t.since = Global.getSector().getClock().getTimestamp();
        List<String> traits = TenantTraits.roll(factionId, conditions(market), badMult, random);
        // Show a random one first, so the visible trait does not always come from the same end of the list.
        Collections.shuffle(traits, random);
        t.traits = traits;
        int wealth = TenantTraits.baseWealth(factionId) + random.nextInt(3) - 1;
        for (String id : traits) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null) wealth += d.wealth;
        }
        t.wealth = Math.max(1, Math.min(5, wealth));
        nameAndFace(t, factionId, random);
        return t;
    }

    private static void nameAndFace(Tenant t, String factionId, Random random) {
        try {
            FactionAPI f = Global.getSector().getFaction(factionId);
            PersonAPI p = f != null ? f.createRandomPerson(random) : null;
            if (p != null) {
                t.name = p.getNameString();
                t.portrait = p.getPortraitSprite();
            }
        } catch (Throwable ignored) {
            // A faction that cannot make people still gets tenants, with a plain name.
        }
        if (t.name == null || t.name.trim().isEmpty()) {
            t.name = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
        }
    }

    /** The faction's display name, or the id when the faction is not in this game. */
    public static String factionName(String factionId) {
        try {
            FactionAPI f = Global.getSector().getFaction(factionId);
            if (f != null) return f.getDisplayName();
        } catch (Throwable ignored) {
        }
        return factionId;
    }
}
