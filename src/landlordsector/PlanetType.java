package landlordsector;

import java.util.Locale;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

/**
 * The planet type a house picture is drawn for (0.4.2, Andrzej's art: one picture per home size and planet type).
 * Only the pictures use it; house traits, upgrades and comfort keep the broader {@link Climate}.
 */
public enum PlanetType {
    STATION("station"),
    TERRAN("terran"),
    JUNGLE("jungle"),
    WATER("water"),
    ARID("arid"),
    DESERT("desert"),
    TUNDRA("tundra"),
    FROZEN("frozen"),
    CRYOVOLCANIC("cryovolcanic"),
    BARREN("barren"),
    TOXIC("toxic"),
    VOLCANIC("volcanic"),
    IRRADIATED("irradiated"),
    GAS_GIANT("gas_giant");

    public final String id;

    PlanetType(String id) {
        this.id = id;
    }

    /** Same station rule as {@link Climate#of}: no planet, a star, or a station entity. */
    public static PlanetType of(MarketAPI market) {
        if (market == null) return BARREN;
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null || planet.isStar()
                || (market.getPrimaryEntity() != null && market.getPrimaryEntity().hasTag(Tags.STATION))) {
            return STATION;
        }
        if (planet.isGasGiant()) return GAS_GIANT;
        return ofType(planet.getTypeId());
    }

    /**
     * Vanilla planet type ids, and the words modded types usually contain. Order matters: "barren-desert" is a
     * desert, "cryovolcanic" is not volcanic, "ice_giant" is a gas giant, not ice.
     */
    public static PlanetType ofType(String typeId) {
        String t = typeId == null ? "" : typeId.toLowerCase(Locale.ROOT);
        if (t.contains("giant") && !t.contains("star")) return GAS_GIANT;
        if (t.contains("cryo")) return CRYOVOLCANIC;
        if (t.contains("volcan") || t.contains("lava") || t.contains("magma") || t.contains("molten")) return VOLCANIC;
        if (t.contains("irradiated") || t.contains("radiat")) return IRRADIATED;
        if (t.contains("toxic") || t.contains("venus") || t.contains("acid") || t.contains("sulfur")) return TOXIC;
        if (t.contains("desert") || t.contains("dune") || t.contains("sand")) return DESERT;
        if (t.contains("arid") || t.contains("savanna") || t.contains("steppe")) return ARID;
        if (t.contains("jungle") || t.contains("swamp") || t.contains("rainforest")) return JUNGLE;
        if (t.contains("water") || t.contains("ocean") || t.contains("archipelago")) return WATER;
        if (t.contains("tundra")) return TUNDRA;
        if (t.contains("frozen") || t.contains("ice") || t.contains("glacial") || t.contains("snow")) return FROZEN;
        if (t.contains("terran") || t.contains("continental") || t.contains("temperate") || t.contains("gaia")
                || t.contains("forest")) {
            return TERRAN;
        }
        return BARREN;
    }

    /** For homes whose market is gone: the closest type from the saved climate. */
    public static PlanetType fromClimate(String climateId) {
        if (climateId == null) return BARREN;
        switch (climateId) {
            case "station": return STATION;
            case "temperate": return TERRAN;
            case "jungle": return JUNGLE;
            case "ocean": return WATER;
            case "arid": return ARID;
            case "cold": return TUNDRA;
            default: return BARREN;
        }
    }
}
