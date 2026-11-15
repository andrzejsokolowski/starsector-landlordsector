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
    /** Grown inspirers: the colony they live on. */
    public String marketId;

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
