package landlordsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.HashSet;
import java.util.Set;

/**
 * 1.15.0 landlord achievements (LLS-42, Andrzej: "Landlord Achievements in achievements - Renewed. I like it"). The
 * mod sets sector memory keys {@code $lls_ach_<key>}; Achievements - Renewed reads them with its built-in
 * {@code memory:} script (data/config/achievements_renewed/achievements.csv), so there is no code link to that mod and
 * Landlordsector runs the same without it. Flags are true; counts only ever go up.
 */
public final class Feats {

    public static final String PREFIX = "$lls_ach_";
    /** Every key the CSV may name (checked by RulesCheck). */
    public static final String[] KEYS = {"homes", "estate", "worlds", "flip", "rent", "tenantMonths", "happy",
            "furniture", "headhunters", "married", "child", "grown", "cheated", "childLost", "kite", "divorce",
            "condemned", "crate", "raid", "sophia"};

    private Feats() {
    }

    private static MemoryAPI memory() {
        try {
            return Global.getSector().getMemoryWithoutUpdate();
        } catch (Throwable e) {
            return null;
        }
    }

    /** Sets a flag. */
    public static void mark(String key) {
        MemoryAPI m = memory();
        if (m == null) return;
        try {
            m.set(PREFIX + key, true);
        } catch (Throwable ignored) {
        }
    }

    /** Raises a count to at least {@code n}. */
    public static void atLeast(String key, long n) {
        MemoryAPI m = memory();
        if (m == null) return;
        try {
            Object o = m.get(PREFIX + key);
            long old = o instanceof Number ? ((Number) o).longValue() : 0L;
            if (n > old) m.set(PREFIX + key, (int) Math.min(Integer.MAX_VALUE, n));
        } catch (Throwable ignored) {
        }
    }

    /** Adds to a count. */
    public static void add(String key, long n) {
        MemoryAPI m = memory();
        if (m == null || n <= 0) return;
        try {
            Object o = m.get(PREFIX + key);
            long old = o instanceof Number ? ((Number) o).longValue() : 0L;
            m.set(PREFIX + key, (int) Math.min(Integer.MAX_VALUE, old + n));
        } catch (Throwable ignored) {
        }
    }

    /** Looks over the homes: how many, on how many worlds, an estate, long tenants, full happiness, furniture, ruin. */
    public static void scan() {
        Portfolio pf = Portfolio.get();
        Set<String> worlds = new HashSet<>();
        int homes = 0;
        for (Property p : pf.properties()) {
            homes++;
            worlds.add(p.marketId);
            if ("estate".equals(p.tierId)) mark("estate");
            if (p.condemned) mark("condemned");
            atLeast("furniture", p.furnitureCount());
            Tenant t = p.tenant;
            if (t != null && !t.isPartner()) {
                long months = (long) (Global.getSector().getClock().getElapsedDaysSince(t.since) / 30f);
                atLeast("tenantMonths", months);
                if (p.stats().happiness >= 99.5f) mark("happy");
            }
        }
        atLeast("homes", homes);
        atLeast("worlds", worlds.size());
    }
}
