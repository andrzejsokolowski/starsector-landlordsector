package landlordsector.skills;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MarketSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/**
 * 1.8.0 (LLS-28, Andrzej: "debuff skills as penalties for bad management ... like an officer skill "ADHD" which
 * reduces autoaim accuracy ... For administrators, negative skills could be more upkeep, less produced goods ...
 * If trained in the academy as an officer or administrator, should get additional beneficial skills. Also should get
 * beneficial skills for extracurricular activities"). The skills your grown kids start with. Never picked on a level
 * up (npc_only); the numbers are here and in each description.
 */
public final class KidSkills {

    private KidSkills() {
    }

    /** Officer skills: one ship effect each. */
    private abstract static class Ship implements ShipSkillEffect {
        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }
    }

    /** Administrator skills on the colony they govern. */
    private abstract static class Colony implements MarketSkillEffect {
        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.GOVERNED_OUTPOST;
        }
    }

    /** Production on the colony they govern, through the governor's own stats as the game's Industrial Planning does. */
    private abstract static class Production implements CharacterStatsSkillEffect {
        abstract int units();

        @Override
        public void apply(MutableCharacterStatsAPI stats, String id, float level) {
            stats.getDynamic().getMod(Stats.SUPPLY_BONUS_MOD).modifyFlat(id, units());
        }

        @Override
        public void unapply(MutableCharacterStatsAPI stats, String id) {
            stats.getDynamic().getMod(Stats.SUPPLY_BONUS_MOD).unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            int u = units();
            return "All industries supply " + Math.abs(u) + (u > 0 ? " more" : " less") + " unit of what they produce";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.GOVERNED_OUTPOST;
        }
    }

    // ---- Bad officer skills ----

    public static class Adhd extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getAutofireAimAccuracy().modifyMult(id, 0.75f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getAutofireAimAccuracy().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-25% autofire aim accuracy";
        }
    }

    public static class Lazy extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getPeakCRDuration().modifyMult(id, 0.8f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getPeakCRDuration().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-20% peak performance time";
        }
    }

    public static class WeakLungs extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getMaxCombatReadiness().modifyFlat(id, -0.1f, "Weak lungs");
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getMaxCombatReadiness().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-10% maximum combat readiness";
        }
    }

    public static class Deaf extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getSightRadiusMod().modifyMult(id, 0.75f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getSightRadiusMod().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-25% sight radius in combat";
        }
    }

    public static class Hothead extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getArmorDamageTakenMult().modifyMult(id, 1.1f);
            stats.getHullDamageTakenMult().modifyMult(id, 1.1f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getArmorDamageTakenMult().unmodify(id);
            stats.getHullDamageTakenMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% armor and hull damage taken";
        }
    }

    public static class Timid extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getBallisticWeaponDamageMult().modifyMult(id, 0.9f);
            stats.getEnergyWeaponDamageMult().modifyMult(id, 0.9f);
            stats.getMissileWeaponDamageMult().modifyMult(id, 0.9f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
            stats.getMissileWeaponDamageMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-10% weapon damage";
        }
    }

    // ---- Good officer skills ----

    public static class AcademyOfficer extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getMaxCombatReadiness().modifyFlat(id, 0.1f, "Academy graduate");
            stats.getAutofireAimAccuracy().modifyMult(id, 1.25f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getMaxCombatReadiness().unmodify(id);
            stats.getAutofireAimAccuracy().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% maximum combat readiness, +25% autofire aim accuracy";
        }
    }

    public static class Tutored extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getMaxSpeed().modifyPercent(id, 10f);
            stats.getAcceleration().modifyPercent(id, 20f);
            stats.getDeceleration().modifyPercent(id, 20f);
            stats.getMaxTurnRate().modifyPercent(id, 20f);
            stats.getTurnAcceleration().modifyPercent(id, 20f);
            stats.getBallisticWeaponDamageMult().modifyPercent(id, 5f);
            stats.getEnergyWeaponDamageMult().modifyPercent(id, 5f);
            stats.getMissileWeaponDamageMult().modifyPercent(id, 5f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);
            stats.getTurnAcceleration().unmodify(id);
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
            stats.getMissileWeaponDamageMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% top speed, +20% maneuverability, +5% weapon damage";
        }
    }

    // ---- Bad administrator skills ----

    public static class Wasteful extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getUpkeepMult().modifyMult(id, 1.15f, "Wasteful governor");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getUpkeepMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+15% colony upkeep";
        }
    }

    public static class Slacker extends Production {
        @Override
        int units() {
            return -1;
        }
    }

    public static class Unpopular extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getStability().modifyFlat(id, -1f, "Unpopular governor");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getStability().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-1 stability";
        }
    }

    // ---- Good administrator skills ----

    public static class AcademyAdmin extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getStability().modifyFlat(id, 1f, "Academy graduate");
            market.getUpkeepMult().modifyMult(id, 0.9f, "Academy graduate");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getStability().unmodify(id);
            market.getUpkeepMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+1 stability, -10% colony upkeep";
        }
    }

    public static class BusinessSchool extends Production {
        @Override
        int units() {
            return 1;
        }
    }

    public static class BusinessIncome extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getIncomeMult().modifyMult(id, 1.1f, "Business school");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getIncomeMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% colony income";
        }
    }
}
