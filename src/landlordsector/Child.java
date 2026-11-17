package landlordsector;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.characters.PersonAPI;

/** One of the player's children (1.2.0). Saved in the portfolio: keep field names, add fields with defaults. */
public class Child {

    /** What a child wants to become, picked by the player while they grow up. */
    public static final String OFFICER = "officer";
    public static final String ADMIN = "admin";
    public static final String WORKER = "worker";
    public static final String AGENT = "agent";
    public static final String GROWTH = "growth";

    public String id;
    /** Their person in the comm directory while they live at home. */
    public PersonAPI person;
    /** Kept here too, so the name survives when the person is gone. */
    public String name;
    public boolean female;
    public String portrait;
    /** The partner's tenant id: kids leave with their own mother or father. */
    public String parentId;
    public String parentName;
    public long bornAt;
    public List<String> traits;
    /** OFFICER, ADMIN, WORKER, AGENT, GROWTH or null while not picked. */
    public String path;
    /** Happiness added up once a day while they grow up, and the days counted. */
    public float happySum;
    public int happyDays;
    /** Today's happiness, for the Manage page. */
    public float happiness = 50f;
    public boolean grown;
    /** Grown workers and inspirers: what they give, set when they grow up. */
    public float pay;
    public float growth;
    /** 1.3.0: grown miners send their pay to this home (a property id), not to you. */
    public String homeId;
    /** Grown inspirers: the colony they live on. */
    public String marketId;

    // ---- 1.8.0 stages (LLS-28). New fields load as 0, false or null from older saves. ----

    /** The stage last seen by the daily pass (Kids.BABY to Kids.ADULT), 0 before 1.8.0. */
    public int stage;
    /** True once the wish (path) can't change any more: from the Teen stage. */
    public boolean pathLocked;
    /** Teen: lessons paid for. */
    public int lessons;
    public long lastLesson;
    /** Days spent together (Teen and Adult), and the last one. */
    public int bonds;
    public long lastBond;
    /** Adult: sent to the IndEvo Academy, and whether it held an Omega core then. */
    public boolean academy;
    public boolean omegaAcademy;
    /** Adult: extracurricular classes paid for. */
    public boolean classes;
    /** Teen: true helps your spouse (+), false wears them out (-). Rolled when they become a teen. */
    public boolean teenHelps;
    /** Kid events: the last one, and what they added to the childhood (days of happiness, signed). */
    public long lastEvent;
    /** The random portrait from your faction picked at birth: shown from the Adult stage on (1.8.1). */
    public String adultPortrait;
    /** Picks the portrait variant, so it stays the same across stages. */
    public int look = -1;
    /** Grown: the good and bad skills they started their life with (skill ids). */
    public List<String> skills;
    /** Grown celebrities with classes: stability on their colony. */
    public float stability;

    public List<String> skills() {
        if (skills == null) skills = new ArrayList<>();
        return skills;
    }

    public List<String> traits() {
        if (traits == null) traits = new ArrayList<>();
        return traits;
    }

    /** The average happiness of their childhood, 50 before the first day. */
    public float childhood() {
        return happyDays <= 0 ? happiness : happySum / happyDays;
    }

    /** 0 to 4 from a 0 to 100 childhood: how good they turn out. */
    public int quality() {
        return Math.max(0, Math.min(4, Math.round(childhood() / 25f)));
    }

    public String noun() {
        return female ? "daughter" : "son";
    }
}
