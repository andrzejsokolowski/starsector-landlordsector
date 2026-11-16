package landlordsector;

import com.fs.starfarer.api.characters.PersonAPI;

import data.kaysaar.aotd.vok.scripts.research.AoTDFactionResearchManager;
import data.kaysaar.aotd.vok.scripts.research.AoTDMainResearchManager;
import data.kaysaar.aotd.vok.scripts.research.scientist.models.ScientistPerson;

/**
 * 1.2.2: AoTD Vaults of Knowledge is optional. This class is the only one that names its classes, and it is only used
 * while the mod is on ({@link Family#sophia()} checks first).
 */
final class AotdScience {

    private AotdScience() {
    }

    /** True when this person sits on the player's research council or heads it. */
    static boolean onCouncil(PersonAPI person) {
        AoTDFactionResearchManager m = AoTDMainResearchManager.getInstance().getManagerForPlayerFaction();
        if (m == null || person == null) return false;
        if (same(m.currentHeadOfCouncil, person)) return true;
        if (m.researchCouncil == null) return false;
        for (ScientistPerson s : m.researchCouncil) if (same(s, person)) return true;
        return false;
    }

    private static boolean same(ScientistPerson s, PersonAPI person) {
        PersonAPI p = s != null ? s.getScientistPerson() : null;
        return p != null && (p == person || (p.getId() != null && p.getId().equals(person.getId())));
    }
}
