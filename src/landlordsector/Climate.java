package landlordsector;

import java.util.Locale;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

/** The climate a picture is drawn for. Read from the planet type. */
public enum Climate {
    STATION("station", "Station"),
    TEMPERATE("temperate", "Temperate"),
    ARID("arid", "Arid"),
    COLD("cold", "Cold"),
    OCEAN("ocean", "Ocean"),
    JUNGLE("jungle", "Jungle"),
    HOSTILE("hostile", "Hostile");

    public final String id;
    public final String label;

    Climate(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static Climate of(MarketAPI market) {
        if (market == null) return HOSTILE;
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null || planet.isStar()
                || (market.getPrimaryEntity() != null && market.getPrimaryEntity().hasTag(Tags.STATION))) {
            return STATION;
        }
        if (planet.isGasGiant()) return HOSTILE;
        return ofType(planet.getTypeId());
    }

    /** Vanilla planet type ids, and the words modded types usually contain. */
    public static Climate ofType(String typeId) {
        String t = typeId == null ? "" : typeId.toLowerCase(Locale.ROOT);
        if (t.contains("terran") || t.contains("continental") || t.contains("temperate") || t.contains("gaia")
                || t.contains("savanna") || t.contains("forest")) {
            return TEMPERATE;
        }
        if (t.contains("jungle") || t.contains("swamp") || t.contains("rainforest")) return JUNGLE;
        if (t.contains("water") || t.contains("ocean") || t.contains("archipelago")) return OCEAN;
        if (t.contains("arid") || t.contains("desert") || t.contains("dune")) return ARID;
        if (t.contains("tundra") || t.contains("frozen") || t.contains("ice") || t.contains("cryovolcanic")
                || t.contains("glacial")) {
            return COLD;
        }
        return HOSTILE;
    }
}
