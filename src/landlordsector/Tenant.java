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

    public List<String> traits() {
        if (traits == null) traits = new ArrayList<>();
        return traits;
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

    public float traitHappiness() {
        float h = 0f;
        for (String id : traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null) h += d.happiness;
        }
        return h;
    }
}
