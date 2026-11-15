package landlordsector;

import java.util.ArrayList;
import java.util.List;

import landlordsector.data.TenantTraits;

/** A tenant, an applicant or a tenant waiting in the player's pool. Saved in the portfolio. */
public class Tenant {

    public String id;
    public String name;
    public String portrait;
    /** The faction the tenant comes from. */
    public String factionId;
    public List<String> traits = new ArrayList<>();
    /** Traits shown before screening (the first ones). Screened tenants show all. */
    public int shown = 1;
    public boolean screened;
    /** 1 to 5: sets the highest rent they pay without grumbling. */
    public int wealth = 3;
    /** Goes up and down with events, back toward 0 by a point a day. */
    public float mood;
    /** When they moved in, applied or joined the pool. */
    public long since;
    /** "advert" or the finder's id. */
    public String source;

    // ---- 1.2.0 family. New fields load as 0, false or null from older saves. ----

    /** 0: a tenant. 1: the player's partner, living with them. 2: the player's wife or husband. */
    public int partner;
    /** The partner's own person (a former officer or administrator), in the market's comm directory. */
    public com.fs.starfarer.api.characters.PersonAPI person;
    /** When the player last docked at the partner's market. */
    public long lastVisit;
    /** When the player last tried for a child with them. */
    public long lastTry;
    /** When a child was conceived, 0 when none is on the way. */
    public long conceivedAt;
    /** True once the player was told a child is on the way. */
    public boolean toldPregnant;
    public long marriedAt;
    /** Days the player was away before their last visit, for the greeting. */
    public float lastGap;
    /** Children living in the home, counted each day (kids' upgrades count for them). */
    public int kidsAtHome;

    public List<String> traits() {
        if (traits == null) traits = new ArrayList<>();
        return traits;
    }

    public boolean isPartner() {
        return partner > 0;
    }

    public boolean isSpouse() {
        return partner >= 2;
    }

    public boolean isShown(int index) {
        return screened || index < shown;
    }

    public boolean hasTrait(String id) {
        return traits().contains(id);
    }

    public boolean loyal() {
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null && d.loyal) return true;
        }
        return false;
    }

    /** The trait multipliers for one event, multiplied together. */
    public float eventMult(String eventId) {
        float m = 1f;
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d == null) continue;
            Float f = d.events.get(eventId);
            if (f != null) m *= f;
        }
        return m;
    }

    public float wearMult() {
        float m = 1f;
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null) m *= d.wear;
        }
        return m;
    }

    /** How many tiers bigger (+) or smaller (-) than their wealth fits this tenant wants their home. */
    public int homeShift() {
        int n = 0;
        for (String id : traits()) {
            landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(id);
            if (d != null) n += d.homeShift;
        }
        return n;
    }

    /** True when a trait marks them a criminal: security makes them unhappy instead of happy. */
    public boolean criminal() {
        for (String id : traits()) {
            landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(id);
            if (d != null && d.criminal) return true;
        }
        return false;
    }

    /** True when the tenant has this trait. */
    public boolean has(String traitId) {
        return traits().contains(traitId);
    }

    /** "Matteo" from "Matteo Zaniah". */
    public String firstName() {
        if (name == null || name.trim().isEmpty()) return "They";
        return name.trim().split("\s+")[0];
    }

    /**
     * 0.4.0: what one home level costs this tenant compared with others (Loves heat: 0 for heat). Traits add up:
     * each trait's multiplier minus 1 is added to 1, never below 0, so every trait's share can be shown on its own.
     */
    public float levelMult(String levelId) {
        float m = 1f;
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d == null) continue;
            Float f = d.levels.get(levelId);
            if (f != null) m += f - 1f;
        }
        return Math.max(0f, m);
    }

    /** 0.4.0: multiplies every health cost (Hardy halves them). Adds up like {@link #levelMult}. */
    public float healthCost() {
        float m = 1f;
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null) m += d.healthCost - 1f;
        }
        return Math.max(0f, m);
    }

    /** 0.4.0: true when the tenant has kids (Family with kids, or 1.2.0: the player's own kids at home). */
    public boolean hasKids() {
        if (kidsAtHome > 0) return true;
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null && d.kids) return true;
        }
        return false;
    }

    public float traitHappiness() {
        float h = 0f;
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null) h += d.happiness;
        }
        return h;
    }
}
