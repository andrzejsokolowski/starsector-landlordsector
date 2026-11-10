package landlordsector;

import lunalib.lunaSettings.LunaSettings;

/** LunaLib values, read live. A missing value falls back to the default in LunaSettings.csv. */
public final class Settings {

    public static final String MOD_ID = "landlordsector";

    private Settings() {
    }

    public static float rentMult() {
        try {
            Double d = LunaSettings.getDouble(MOD_ID, "lls_rentMult");
            return d == null ? 1f : Math.max(0f, d.floatValue());
        } catch (Throwable t) {
            return 1f;
        }
    }

    /** The most properties the player may own, 0 for no limit. */
    public static int maxProperties() {
        try {
            Integer i = LunaSettings.getInt(MOD_ID, "lls_maxProperties");
            return i == null ? 0 : Math.max(0, i);
        } catch (Throwable t) {
            return 0;
        }
    }
}
