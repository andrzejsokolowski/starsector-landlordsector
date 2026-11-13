package landlordsector;

import com.fs.starfarer.api.Global;

import landlordsector.data.Upgrades;

/** One upgrade in a home (0.4.0): placed furniture or a renovation, built or still being built. Saved. */
public class Installed {

    public String id;
    /** When it was placed or the work started. */
    public long startedAt;
    /** Days the work takes; 0 for furniture. */
    public float days;
    public boolean furniture;

    public Installed() {
    }

    public Installed(String id, boolean furniture, float days) {
        this.id = id;
        this.furniture = furniture;
        this.days = days;
        this.startedAt = Global.getSector().getClock().getTimestamp();
    }

    /** An upgrade that was already there (a home for sale, a pre-0.4.0 trait). */
    public static Installed done(String id) {
        Installed i = new Installed();
        i.id = id;
        i.furniture = false;
        i.days = 0f;
        i.startedAt = 0L;
        return i;
    }

    public Upgrades.Def def() {
        return Upgrades.get(id);
    }

    public boolean ready() {
        return days <= 0f || daysLeft() <= 0f;
    }

    public float daysLeft() {
        if (days <= 0f) return 0f;
        return days - Global.getSector().getClock().getElapsedDaysSince(startedAt);
    }

    public String name() {
        Upgrades.Def d = def();
        return d != null ? d.name : id;
    }
}
