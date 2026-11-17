package landlordsector;

/**
 * 1.12.0 (LLS-32): a question waiting for the player's answer, or an outcome of an earlier answer still to come.
 * Saved in the home: keep the field names, add fields with defaults.
 */
public class Decision {

    /** The question's event id (tenant_events.json). */
    public String eventId;
    /** The tenant it is about: when they are gone, the question is dropped. */
    public String tenantId;
    public long askedAt;
    /** The question as shown, with the names filled in. */
    public String text;
    /** What the tenant said about it, or null. */
    public String said;
    public String who;
    /** The intel item with the answer buttons, or null (the tenant manager answered at once). */
    public QuestionIntel intel;

    // ---- An outcome still to come ----

    /** The answer it belongs to. */
    public int answer;
    /** When it happens. */
    public long dueAt;
}
