package landlordsector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.data.Environment;
import landlordsector.data.TenantTraits;

/**
 * 1.8.0 (LLS-28, Andrzej, 2026-11-17): kids grow up in five stages. A Baby only lies there and giggles, a Toddler
 * talks a little, a Child talks about what they want to become (the career lines), a Teen has that wish locked and
 * can be trained (lessons, days together), an Adult works entry-level jobs and can go to the Academy (Industrial
 * Evolution) or take extra classes. Traits come at the Child stage, from the home and from how happy the family is,
 * and more come as a Teen. What they learned and what went wrong decide the skills they start their life with.
 */
public final class Kids {

    public static final int BABY = 1, TODDLER = 2, CHILD = 3, TEEN = 4, ADULT = 5;
    private static final String[] STAGE = {"", "Baby", "Toddler", "Child", "Teen", "Adult"};
    private static final String[] STAGE_KEY = {"", "baby", "toddler", "child", "teen", "adult"};

    /** Your spouse's happiness per kid at home, by stage (a teen rolls help or trouble). */
    public static final float BURDEN_BABY = -5f, BURDEN_TODDLER = -8f, BURDEN_CHILD = -3f, TEEN_SWING = 4f;
    /** What each kid at home costs a month, by stage. Adults work and cost nothing. */
    public static final float UPKEEP_BABY = 500f, UPKEEP_TODDLER = 500f, UPKEEP_CHILD = 1500f, UPKEEP_TEEN = 2500f;

    /** Teen lessons: price, how many, and how far apart. */
    public static final float LESSON_COST = 25000f;
    public static final int MAX_LESSONS = 3;
    public static final float LESSON_DAYS = 30f;
    /** Days together (Teen and Adult): once a week, every 4 count as one lesson, at most 2. */
    public static final float BOND_DAYS = 7f;
    public static final int BONDS_PER_POINT = 4, MAX_BOND_POINTS = 2;
    /** Adult: the Academy (officers and administrators) and extra classes (anyone). */
    public static final float ACADEMY_COST = 100000f, CLASSES_COST = 250000f;
    public static final String ACADEMY_ID = "IndEvo_Academy";
    public static final String INDEVO_MOD = "IndEvo";

    /** Childhood traits from the home: chance when a score is bad (E or F) or perfect (S+). */
    public static final float HOME_TRAIT_CHANCE = 0.6f, SIZE_TRAIT_CHANCE = 0.5f;
    /** A family happier than this on average gives the kid a bad mark, and again below the second. */
    public static final float BAD_CHILDHOOD = 35f, AWFUL_CHILDHOOD = 20f;

    /** Bad skills for a hard childhood. */
    public static final String[] LOW_OFFICER = {"lls_timid", "lls_hothead"};
    public static final String[] LOW_ADMIN = {"lls_unpopular", "lls_wasteful"};
    public static final String ACADEMY_OFFICER = "lls_academy_officer", ACADEMY_ADMIN = "lls_academy_admin";
    public static final String TUTORED = "lls_tutored", BUSINESS = "lls_business_school";

    /** Portrait variants per stage and sex (graphics/landlordsector/portraits/kids/<stage>_<f|m>_<n>.png). */
    public static final int LOOKS = 3;

    private Kids() {
    }

    // ---- Stages ----

    public static float stageDays() {
        return Family.growUpDays() / 5f;
    }

    /** BABY to ADULT from the kid's age; ADULT once grown. */
    public static int stageOf(Child c) {
        if (c.grown) return ADULT;
        int s = 1 + (int) (Family.ageDays(c) / Math.max(1f, stageDays()));
        return Math.max(BABY, Math.min(ADULT, s));
    }

    public static String stageName(Child c) {
        return STAGE[stageOf(c)];
    }

    /** "your baby", "your teen"... */
    public static String stageNoun(int stage) {
        return STAGE[stage].toLowerCase();
    }

    /** Old saves (before 1.8.0): the stage they are at now, no traits before the Child stage. Public for the check. */
    public static void migrate(Property p, Child c, Random random) {
        if (c.stage != 0 || c.grown) {
            if (c.grown && c.stage == 0) c.stage = ADULT;
            return;
        }
        int s = stageOf(c);
        c.stage = s;
        if (s < CHILD) {
            // Andrzej: "my infant was born with tidy, athletic ... only roll those traits on childhood stage".
            c.traits().clear();
        } else if (c.traits().isEmpty()) {
            rollChildhood(p, c, random);
        }
        if (s >= TEEN) {
            c.pathLocked = c.path != null;
            c.teenHelps = random.nextBoolean();
        }
        c.look = look(c, random);
        updatePortrait(c);
    }

    /** Once a day for a kid at home who is not grown yet. */
    public static void daily(Property p, Child c, Random random) {
        if (c.stage == 0) migrate(p, c, random);
        int s = stageOf(c);
        while (c.stage < s) {
            c.stage++;
            enter(p, c, c.stage, random);
        }
        maybeEvent(p, c, random);
    }

    private static void enter(Property p, Child c, int stage, Random random) {
        updatePortrait(c);
        String first = firstName(c);
        switch (stage) {
            case TODDLER:
                Notify.event(p, first + " took " + his(c) + " first steps.", true, false);
                break;
            case CHILD:
                rollChildhood(p, c, random);
                Notify.event(p, first + " started school and talks about what " + he(c) + " wants to become.", true,
                        true);
                break;
            case TEEN: {
                boolean picked = c.path != null;
                if (!picked) c.path = randomPath(random);
                c.pathLocked = true;
                c.teenHelps = random.nextBoolean();
                rollTeen(p, c, random);
                String what = Family.pathName(c.path);
                Notify.event(p, first + " is a teenager now and has made up " + his(c) + " mind: " + he(c)
                        + " wants to become " + what + ".", true, true);
                break;
            }
            case ADULT:
                Notify.event(p, first + " is an adult now and works entry-level jobs. " + capital(he(c))
                        + " no longer costs you anything.", true, true);
                break;
            default:
                break;
        }
    }

    private static String randomPath(Random random) {
        List<String> paths = new ArrayList<>();
        Collections.addAll(paths, Child.OFFICER, Child.ADMIN, Child.WORKER, Child.GROWTH);
        if (Family.nexerelin()) paths.add(Child.AGENT);
        return paths.get(random.nextInt(paths.size()));
    }

    // ---- Traits ----

    /**
     * Andrzej: "Bad air? Pneumonia, Asthma, Allergic. Bad noise? Deaf. irritable Bad commute? lazy, ADHD. Bad
     * Location? Loner, NEET. At a lower-size house? Bullied in school, but this works vice versa: Perfect air? Athletic
     * more likely. Perfect noise (s+)? Book reader ... Perfect location? Affluent". Then one or two random traits,
     * good ones likelier the happier the family has been.
     */
    public static void rollChildhood(Property p, Child c, Random random) {
        PropertyStats st = p.stats();
        homeTrait(c, bad(st, "air"), random, "asthma", "allergies", "weak_lungs");
        homeTrait(c, bad(st, "noise"), random, "hard_of_hearing", "irritable");
        homeTrait(c, bad(st, "commute"), random, "lazy", "adhd");
        homeTrait(c, badGrade(st.grade), random, "loner");
        homeTrait(c, perfect(st, "air"), random, "athletic");
        homeTrait(c, perfect(st, "noise"), random, "book_reader");
        homeTrait(c, perfect(st, "commute"), random, "many_friends");
        homeTrait(c, "S+".equals(st.grade), random, "affluent");
        int tier = p.tier().index;
        if (tier <= 1 && random.nextFloat() < SIZE_TRAIT_CHANCE) add(c, "bullied");
        if (tier >= 4 && random.nextFloat() < SIZE_TRAIT_CHANCE) add(c, "popular");
        randomTrait(c, false, random);
        if (random.nextFloat() < 0.4f) randomTrait(c, false, random);
    }

    /** Andrzej: a teen "Gets random traits, like tenants do". A bad location may make them a NEET. */
    public static void rollTeen(Property p, Child c, Random random) {
        if (badGrade(p.stats().grade) && random.nextFloat() < HOME_TRAIT_CHANCE) add(c, "neet");
        randomTrait(c, true, random);
        if (random.nextFloat() < 0.4f) randomTrait(c, true, random);
    }

    private static void homeTrait(Child c, boolean when, Random random, String... ids) {
        if (!when || random.nextFloat() >= HOME_TRAIT_CHANCE) return;
        add(c, ids[random.nextInt(ids.length)]);
    }

    private static boolean bad(PropertyStats st, String scoreId) {
        Float v = st.env.get(scoreId);
        return v != null && v < 35f;
    }

    private static boolean perfect(PropertyStats st, String scoreId) {
        Float v = st.env.get(scoreId);
        return v != null && "S+".equals(Environment.letter(v));
    }

    private static boolean badGrade(String grade) {
        return "E".equals(grade) || "F".equals(grade);
    }

    /** Adds a trait unless the kid has it or one it conflicts with. */
    public static boolean add(Child c, String id) {
        TenantTraits.Def d = TenantTraits.get(id);
        if (d == null || c.traits().contains(id)) return false;
        for (String o : c.traits()) {
            TenantTraits.Def od = TenantTraits.get(o);
            if (d.conflicts.contains(o) || (od != null && od.conflicts.contains(id))) return false;
        }
        c.traits().add(id);
        return true;
    }

    /**
     * One random trait. Andrzej: "Roll them skewed to the good side if happiness of the family is high, and vice versa
     * if it's low": a fifth are neutral, the rest good with the chance of the childhood's average happiness.
     */
    public static void randomTrait(Child c, boolean teen, Random random) {
        Boolean want;
        if (random.nextFloat() < 0.2f) want = null;
        else want = random.nextFloat() < Pricing.clamp(c.childhood() / 100f, 0.05f, 0.95f);
        List<TenantTraits.Def> pool = new ArrayList<>();
        float total = 0f;
        for (TenantTraits.Def d : TenantTraits.all()) {
            if (d.eventOnly || Family.CHILD_SKIP_TRAITS.contains(d.id) || c.traits().contains(d.id)) continue;
            if (d.teenOnly && !teen) continue;
            if (want == null ? d.good != null : !want.equals(d.good)) continue;
            if (d.weight <= 0f || clashes(c, d)) continue;
            pool.add(d);
            total += d.weight;
        }
        if (pool.isEmpty()) return;
        float r = random.nextFloat() * total;
        for (TenantTraits.Def d : pool) {
            r -= d.weight;
            if (r <= 0f) {
                c.traits().add(d.id);
                return;
            }
        }
        c.traits().add(pool.get(pool.size() - 1).id);
    }

    private static boolean clashes(Child c, TenantTraits.Def d) {
        for (String o : c.traits()) {
            TenantTraits.Def od = TenantTraits.get(o);
            if (d.conflicts.contains(o) || (od != null && od.conflicts.contains(d.id))) return true;
        }
        return false;
    }

    // ---- Your spouse and your money ----

    /** What each kid at home does to your spouse's happiness: {line, amount}. */
    public static List<Object[]> burden(Tenant partner) {
        List<Object[]> out = new ArrayList<>();
        if (partner == null || !partner.isPartner()) return out;
        for (Property p : Portfolio.get().properties()) {
            if (p.tenant != partner) continue;
            for (Child c : p.children()) {
                if (c.grown) continue;
                int s = stageOf(c);
                String first = firstName(c);
                switch (s) {
                    case BABY:
                        out.add(new Object[] {"Your baby, " + first, BURDEN_BABY});
                        break;
                    case TODDLER:
                        out.add(new Object[] {"Your toddler, " + first, BURDEN_TODDLER});
                        break;
                    case CHILD:
                        out.add(new Object[] {"Your child, " + first, BURDEN_CHILD});
                        break;
                    case TEEN:
                        out.add(c.teenHelps ? new Object[] {"Your teen, " + first + ", helps around the house", TEEN_SWING}
                                : new Object[] {"Your teen, " + first + ", is a handful", -TEEN_SWING});
                        break;
                    default:
                        break;
                }
            }
        }
        return out;
    }

    /** What a kid at home costs a month (Andrzej: a child "costs more money to maintain", adults cost nothing). */
    public static float upkeep(Child c) {
        if (c.grown) return 0f;
        switch (stageOf(c)) {
            case BABY:
                return UPKEEP_BABY;
            case TODDLER:
                return UPKEEP_TODDLER;
            case CHILD:
                return UPKEEP_CHILD;
            case TEEN:
                return UPKEEP_TEEN;
            default:
                return 0f;
        }
    }

    // ---- Training (Andrzej: "You can spend 1 entire cycle buffing up your kid the most you can") ----

    /** Lessons, days together (every 4, up to 2) and traits like Book reader. */
    public static int training(Child c) {
        int t = c.lessons + Math.min(MAX_BOND_POINTS, c.bonds / BONDS_PER_POINT);
        for (String id : c.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null) t += d.training;
        }
        return Math.max(0, t);
    }

    /** Bad traits plus a hard childhood: each costs a grown miner, agent or celebrity a fifth. */
    public static int badMarks(Child c) {
        int n = 0;
        for (String id : c.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null && Boolean.FALSE.equals(d.good)) n++;
        }
        float h = c.childhood();
        if (h < BAD_CHILDHOOD) n++;
        if (h < AWFUL_CHILDHOOD) n++;
        return n;
    }

    /** The bad skills a grown officer (true) or administrator (false) starts with, each once. */
    public static List<String> badSkills(Child c, boolean officer) {
        List<String> out = new ArrayList<>();
        for (String id : c.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d == null) continue;
            String s = officer ? d.officerSkill : d.adminSkill;
            if (s != null && !out.contains(s)) out.add(s);
        }
        String[] low = officer ? LOW_OFFICER : LOW_ADMIN;
        float h = c.childhood();
        if (h < BAD_CHILDHOOD && !out.contains(low[0])) out.add(low[0]);
        if (h < AWFUL_CHILDHOOD && !out.contains(low[1])) out.add(low[1]);
        return out;
    }

    /** Null when you can spend a day with this kid now, else why not. */
    public static String cannotBond(Child c) {
        int s = stageOf(c);
        if (c.grown || s < TEEN) return "Not old enough.";
        if (c.lastBond != 0L) {
            float d = Family.daysSince(c.lastBond);
            if (d < BOND_DAYS) {
                int left = Math.max(1, Math.round(BOND_DAYS - d));
                return "You spent a day together recently. Again in " + left + (left == 1 ? " day." : " days.");
            }
        }
        return null;
    }

    public static void bond(Property p, Child c) {
        c.bonds++;
        c.lastBond = Family.now();
        c.happySum += 5f * 10f;
        p.addLog("You spent the day with " + firstName(c) + ".");
    }

    /** Null when the teen can start new lessons now, else why not. */
    public static String cannotLesson(Child c) {
        if (c.grown || stageOf(c) != TEEN) return "Only teens take lessons.";
        if (c.lessons >= MAX_LESSONS) return "Took every lesson already.";
        if (c.lastLesson != 0L) {
            float d = Family.daysSince(c.lastLesson);
            if (d < LESSON_DAYS) {
                int left = Math.max(1, Math.round(LESSON_DAYS - d));
                return "Still busy with the last lessons. New ones in " + left + (left == 1 ? " day." : " days.");
            }
        }
        if (Actions.credits() < LESSON_COST) return "You can't afford it.";
        return null;
    }

    public static void lesson(Property p, Child c) {
        Actions.spend(LESSON_COST);
        c.lessons++;
        c.lastLesson = Family.now();
        p.addLog(firstName(c) + " started new lessons.");
    }

    /** Your colony with a working Academy (Industrial Evolution), or null. */
    public static Industry academy() {
        try {
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (!MarketRules.isOwn(m)) continue;
                Industry ind = m.getIndustry(ACADEMY_ID);
                if (ind != null && ind.isFunctional()) return ind;
            }
        } catch (Throwable ignored) {
            // No economy (a headless check).
        }
        return null;
    }

    public static boolean indEvo() {
        try {
            return Global.getSettings().getModManager().isModEnabled(INDEVO_MOD);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The Academy is for future officers and administrators (Andrzej). */
    public static boolean academyPath(Child c) {
        return Child.OFFICER.equals(c.path) || Child.ADMIN.equals(c.path);
    }

    public static String cannotAcademy(Child c) {
        if (c.grown || stageOf(c) != ADULT) return "Only adults go to the Academy.";
        if (!academyPath(c)) return "The Academy trains officers and administrators.";
        if (c.academy) return "Already graduated.";
        if (academy() == null) return "You need an Academy on one of your colonies.";
        if (Actions.credits() < ACADEMY_COST) return "You can't afford it.";
        return null;
    }

    public static void sendToAcademy(Property p, Child c) {
        Industry ind = academy();
        Actions.spend(ACADEMY_COST);
        c.academy = true;
        c.omegaAcademy = ind != null && com.fs.starfarer.api.impl.campaign.ids.Commodities.OMEGA_CORE.equals(
                ind.getAICoreId());
        p.addLog(firstName(c) + " went to the Academy" + (ind != null ? " on " + ind.getMarket().getName() : "")
                + ".");
    }

    public static String cannotClasses(Child c) {
        if (c.grown || stageOf(c) != ADULT) return "Only adults take extra classes.";
        if (c.path == null) return "Pick what they want to become first.";
        if (c.classes) return "Already took them.";
        if (Actions.credits() < CLASSES_COST) return "You can't afford it.";
        return null;
    }

    public static void takeClasses(Property p, Child c) {
        Actions.spend(CLASSES_COST);
        c.classes = true;
        p.addLog(firstName(c) + " started extra classes.");
    }

    /** What extra classes give, by the wish. */
    public static String classesGive(String path) {
        if (path == null) return "";
        switch (path) {
            case Child.OFFICER:
                return "Starts with Private tutors: +10% top speed, +20% maneuverability, +5% weapon damage.";
            case Child.ADMIN:
                return "Starts with Business school: industries supply 1 more unit, +10% colony income.";
            case Child.WORKER:
                return "Learns to spot valuables: sometimes sends home a piece of furniture found in the mine.";
            case Child.AGENT:
                return "Learns a second specialty.";
            case Child.GROWTH:
                return "+1 stability on the colony " + "they move to.";
            default:
                return "";
        }
    }

    // ---- Kid events (Andrzej: "Your son/daughter can be killed in negative tenant events") ----

    public static final class Event {
        public String id, text;
        public boolean good, dies, crime, health;
        public int minStage = BABY, maxStage = ADULT;
        public float weight = 1f, happy, spouse, condition;
        public String trait;
        public List<String> needs = Collections.emptyList();
    }

    public static final String EVENTS_PATH = "data/config/landlordsector/kid_events.json";
    private static List<Event> events = Collections.emptyList();
    private static float eventChance = 0.04f, eventGap = 20f;

    public static void load(JSONObject json) {
        List<Event> out = new ArrayList<>();
        eventChance = (float) json.optDouble("dailyChance", 0.04);
        eventGap = (float) json.optDouble("minDaysBetween", 20);
        JSONArray arr = json.optJSONArray("events");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Event e = new Event();
                e.id = o.optString("id");
                e.text = o.optString("text");
                e.good = o.optBoolean("good", false);
                e.dies = o.optBoolean("dies", false);
                e.crime = o.optBoolean("crime", false);
                e.health = o.optBoolean("health", false);
                JSONArray st = o.optJSONArray("stages");
                if (st != null && st.length() == 2) {
                    e.minStage = st.optInt(0, BABY);
                    e.maxStage = st.optInt(1, ADULT);
                }
                e.weight = (float) o.optDouble("weight", 1);
                e.happy = (float) o.optDouble("happy", 0);
                e.spouse = (float) o.optDouble("spouse", 0);
                e.condition = (float) o.optDouble("condition", 0);
                e.trait = o.has("trait") && !o.isNull("trait") ? o.optString("trait") : null;
                JSONArray needs = o.optJSONArray("needs");
                if (needs != null) {
                    e.needs = new ArrayList<>();
                    for (int k = 0; k < needs.length(); k++) e.needs.add(needs.optString(k));
                }
                out.add(e);
            }
        }
        events = out;
    }

    public static List<Event> events() {
        return events;
    }

    /** The odds of each event for this kid now: happy kids have more good ones, crime follows security. */
    public static float weight(Property p, Child c, Event e, PropertyStats st) {
        int s = stageOf(c);
        if (s < e.minStage || s > e.maxStage) return 0f;
        if (e.trait != null && c.traits().contains(e.trait)) return 0f;
        if (!e.needs.isEmpty()) {
            boolean any = false;
            for (String n : e.needs) {
                if (c.traits().contains(n) || ("bad_air".equals(n) && bad(st, "air"))) any = true;
            }
            if (!any) return 0f;
        }
        float w = e.weight;
        float h = c.happiness;
        w *= e.good ? 0.5f + h / 50f : 2.5f - h / 50f;
        if (e.crime) w *= 1.5f - st.security / 100f;
        if (e.health) w *= 1.5f - st.health / 100f;
        return Math.max(0f, w);
    }

    private static void maybeEvent(Property p, Child c, Random random) {
        if (events.isEmpty()) return;
        if (c.lastEvent != 0L && Family.daysSince(c.lastEvent) < eventGap) return;
        if (random.nextFloat() >= eventChance) return;
        Event e = pick(p, c, random);
        if (e != null) apply(p, c, e);
    }

    /** One event for this kid, or null. Public for the headless check. */
    public static Event pick(Property p, Child c, Random random) {
        PropertyStats st = p.stats();
        float total = 0f;
        List<Event> pool = new ArrayList<>();
        List<Float> ws = new ArrayList<>();
        for (Event e : events) {
            float w = weight(p, c, e, st);
            if (w <= 0f) continue;
            pool.add(e);
            ws.add(w);
            total += w;
        }
        if (pool.isEmpty()) return null;
        float r = random.nextFloat() * total;
        for (int i = 0; i < pool.size(); i++) {
            r -= ws.get(i);
            if (r <= 0f) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    /** Applies one kid event. Public for the headless check. */
    public static void apply(Property p, Child c, Event e) {
        c.lastEvent = Family.now();
        String text = e.text.replace("$kid", firstName(c)).replace("$Parent", Family.parentWord())
                .replace("$his", his(c)).replace("$he", he(c)).replace("$home", p.displayName());
        if (e.happy != 0f) c.happySum += e.happy * 10f;
        Tenant t = p.partner();
        if (t != null && e.spouse != 0f) t.mood = Pricing.clamp(t.mood + e.spouse, -30f, 30f);
        if (e.condition != 0f && !p.condemned) p.condition = Pricing.clamp(p.condition + e.condition, 0f, 1f);
        if (e.trait != null) add(c, e.trait);
        if (e.dies) {
            Family.kidDies(p, c);
            Notify.event(p, text, false, true);
            return;
        }
        Notify.event(p, text, e.good, false);
    }

    // ---- Portraits (LLS-30) ----

    private static int look(Child c, Random random) {
        return c.look >= 0 ? c.look : random.nextInt(LOOKS);
    }

    /** settings.json "graphics" category that lists the kid portraits, keyed <stage>_<f|m>_<n>. */
    public static final String PORTRAITS = "lls_kid_portraits";

    /** The picture for this stage, or null when the mod lists none (then the old one stays). */
    public static String portrait(Child c) {
        int s = stageOf(c);
        int n = 1 + Math.floorMod(c.look < 0 ? 0 : c.look, LOOKS);
        String key = STAGE_KEY[s] + "_" + (c.female ? "f" : "m") + "_" + n;
        try {
            String path = Global.getSettings().getSpriteName(PORTRAITS, key);
            return path == null || path.isEmpty() || !path.endsWith(".png") ? null : path;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Shows the picture for the kid's stage on the Family tab and in the comm directory. */
    public static void updatePortrait(Child c) {
        if (c.look < 0) c.look = new Random().nextInt(LOOKS);
        String path = portrait(c);
        if (path == null) return;
        c.portrait = path;
        if (c.person != null) c.person.setPortraitSprite(path);
    }

    // ---- Words ----

    public static String firstName(Child c) {
        if (c.name == null || c.name.trim().isEmpty()) return "Your kid";
        return c.name.trim().split("\\s+")[0];
    }

    public static String he(Child c) {
        return c.female ? "she" : "he";
    }

    public static String his(Child c) {
        return c.female ? "her" : "his";
    }

    static String capital(String s) {
        return Family.capital(s);
    }
}
