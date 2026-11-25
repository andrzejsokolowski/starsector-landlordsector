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

    /**
     * 1.0.1 (Andrzej: "I want players to be incentivized to sell off shacks and studios in favour of better houses"):
     * 5 by default.
     */
    public static final int DEFAULT_MAX_PROPERTIES = 5;

    /** The most properties the player may own, 0 for no limit. */
    public static int maxProperties() {
        try {
            Integer i = LunaSettings.getInt(MOD_ID, "lls_maxProperties");
            return i == null ? DEFAULT_MAX_PROPERTIES : Math.max(0, i);
        } catch (Throwable t) {
            return DEFAULT_MAX_PROPERTIES;
        }
    }

    /**
     * 1.5.1 (Andrzej: "40x40 is pretty small. The icons are legible from 120x120"): size of the home condition icons,
     * 120 by default. The icon files are 128 x 128. 1.5.2 (Andrzej: "Let's lower the size to maybe 60x60"): 60.
     */
    public static final int DEFAULT_ICON_SIZE = 60;

    public static float iconSize() {
        try {
            Integer i = LunaSettings.getInt(MOD_ID, "lls_iconSize");
            return i == null ? DEFAULT_ICON_SIZE : Math.max(40, Math.min(128, i));
        } catch (Throwable t) {
            return DEFAULT_ICON_SIZE;
        }
    }

    /** 1.2.0: cycles a child takes to grow up. */
    public static final int DEFAULT_GROW_UP_CYCLES = 5;

    public static int growUpCycles() {
        try {
            Integer i = LunaSettings.getInt(MOD_ID, "lls_growUpCycles");
            return i == null ? DEFAULT_GROW_UP_CYCLES : Math.max(1, i);
        } catch (Throwable t) {
            return DEFAULT_GROW_UP_CYCLES;
        }
    }


    /**
     * "Kaysaar's Demise" (Andrzej, 0.3.8), off by default. 1.2.2 (Andrzej): with AoTD, Sophia Anderson can marry you
     * once she is on your research council, and she always says yes.
     */
    public static boolean kaysaarsDemise() {
        try {
            Boolean b = LunaSettings.getBoolean(MOD_ID, "lls_kaysaarsDemise");
            return b != null && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 1.7.0: "From your character", "Male" or "Female". */
    public static String playerSex() {
        try {
            String v = LunaSettings.getString(MOD_ID, "lls_playerSex");
            return v == null ? "From your character" : v;
        } catch (Throwable t) {
            return "From your character";
        }
    }
}
