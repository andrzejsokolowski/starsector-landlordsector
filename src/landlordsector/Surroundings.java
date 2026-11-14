package landlordsector;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.data.Environment;

/**
 * Where a home sits on its colony (0.5.0, Andrzej: "the house randomly picks one building on the colony to be
 * 'nearby' it"): the building next door and what the colony's buildings mean to tenants.
 */
public final class Surroundings {

    private Surroundings() {
    }

    /** The colony's buildings: industry id to its tags. Empty for a stand-in market without industries. */
    public static Map<String, Collection<String>> buildings(MarketAPI m) {
        Map<String, Collection<String>> out = new LinkedHashMap<>();
        if (m == null) return out;
        try {
            for (Industry ind : m.getIndustries()) {
                if (ind == null || ind.getId() == null) continue;
                Collection<String> tags = Collections.emptySet();
                try {
                    if (ind.getSpec() != null && ind.getSpec().getTags() != null) tags = ind.getSpec().getTags();
                } catch (Throwable ignored) {
                    // A building without a spec: no tags.
                }
                out.put(ind.getId(), tags);
            }
        } catch (Throwable ignored) {
            // A stand-in market without industry objects.
        }
        return out;
    }

    /** Rolls the building a new home sits next to. */
    public static String roll(MarketAPI m, Random random) {
        return Environment.roll(buildings(m), random);
    }

    /** The building next to the home, or {@link Environment#NOTHING} when it is none or the building is gone. */
    public static String current(MarketAPI m, String neighborId) {
        if (m == null || neighborId == null || Environment.NOTHING.equals(neighborId)) return Environment.NOTHING;
        return buildings(m).containsKey(neighborId) ? neighborId : Environment.NOTHING;
    }

    /** What the building next to the home does (no effects when there is none). */
    public static Environment.Neighbor neighbor(MarketAPI m, String neighborId) {
        String id = current(m, neighborId);
        if (Environment.NOTHING.equals(id)) return Environment.of(null, null);
        return Environment.of(id, buildings(m).get(id));
    }

    /** "the city center", "Mining", or "nothing". */
    public static String name(MarketAPI m, String neighborId) {
        String id = current(m, neighborId);
        if (Environment.NOTHING.equals(id)) return "nothing";
        Environment.Neighbor n = Environment.of(id, buildings(m).get(id));
        if (n.name != null) return n.name;
        try {
            Industry ind = m.getIndustry(id);
            if (ind != null && ind.getCurrentName() != null && !ind.getCurrentName().isEmpty()) {
                return ind.getCurrentName();
            }
        } catch (Throwable ignored) {
            // A stand-in market.
        }
        return id;
    }

    /** "Next to Mining" or "Next to nothing". */
    public static String nextTo(MarketAPI m, String neighborId) {
        return "Next to " + name(m, neighborId);
    }

    /** The location multiplier of one home: the market's, times its neighbour's price, within the limits. */
    public static float homeMult(MarketAPI m, String neighborId) {
        float base = MarketRules.mult(m);
        return Pricing.clamp(base * neighbor(m, neighborId).price, Pricing.MULT_MIN, Pricing.MULT_MAX);
    }

    /** Tags tenants like or hate on the whole colony (police, spying). */
    public static Set<String> colonyTags(MarketAPI m) {
        return Environment.colonyTags(buildings(m));
    }

    /** The name of the colony building that carries a colony tag ("Patrol HQ"), or null. */
    public static String colonySource(MarketAPI m, String tag) {
        for (Map.Entry<String, Collection<String>> e : buildings(m).entrySet()) {
            if (!Environment.colonyTags(e.getKey(), e.getValue()).contains(tag)) continue;
            try {
                Industry ind = m.getIndustry(e.getKey());
                if (ind != null && ind.getCurrentName() != null) return ind.getCurrentName();
            } catch (Throwable ignored) {
                // A stand-in market.
            }
            return e.getKey();
        }
        return null;
    }
}
