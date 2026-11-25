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

    // ---- Mixed skills from ADHD, Loner and Homebody (1.16.0, LLS-46, numbers from Landlordsector Renewed) ----

    /** lls_adhd, shown as Hyperfocus: ADHD is no flaw any more. */
    public static class Hyperfocus extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getBallisticRoFMult().modifyMult(id, 1.1f);
            stats.getEnergyRoFMult().modifyMult(id, 1.1f);
            stats.getPeakCRDuration().modifyMult(id, 0.85f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getBallisticRoFMult().unmodify(id);
            stats.getEnergyRoFMult().unmodify(id);
            stats.getPeakCRDuration().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% ballistic and energy weapon rate of fire, -15% peak performance time";
        }
    }

    public static class SelfReliant extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getCombatWeaponRepairTimeMult().modifyMult(id, 0.75f);
            stats.getCombatEngineRepairTimeMult().modifyMult(id, 0.75f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getCombatWeaponRepairTimeMult().unmodify(id);
            stats.getCombatEngineRepairTimeMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "Weapons and engines are repaired 25% faster in combat";
        }
    }

    public static class Careful extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getWeaponDamageTakenMult().modifyMult(id, 0.85f);
            stats.getEngineDamageTakenMult().modifyMult(id, 0.85f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getWeaponDamageTakenMult().unmodify(id);
            stats.getEngineDamageTakenMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-15% damage to weapons and engines";
        }
    }

    public static class BigIdeas extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getIncomeMult().modifyMult(id, 1.1f, "Big ideas");
            market.getUpkeepMult().modifyMult(id, 1.1f, "Big ideas");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getIncomeMult().unmodify(id);
            market.getUpkeepMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% colony income, +10% colony upkeep";
        }
    }

    public static class Independent extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getStability().modifyFlat(id, 1f, "Independent governor");
            market.getAccessibilityMod().modifyFlat(id, -0.05f, "Independent governor");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getStability().unmodify(id);
            market.getAccessibilityMod().unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+1 stability, -5% accessibility";
        }
    }

    public static class KeepsWatch extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getHazard().modifyFlat(id, -0.05f, "Governor keeps an eye on things");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getHazard().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-5% hazard rating";
        }
    }

    // ---- Bad officer skills ----

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

    // ---- 1.9.0: good skills from good traits (Andrzej: "Yes, do both") ----

    public static class Fit extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getPeakCRDuration().modifyMult(id, 1.2f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getPeakCRDuration().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+20% peak performance time";
        }
    }

    public static class Steady extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getAutofireAimAccuracy().modifyMult(id, 1.15f);
            stats.getWeaponTurnRateBonus().modifyPercent(id, 10f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getAutofireAimAccuracy().unmodify(id);
            stats.getWeaponTurnRateBonus().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+15% autofire aim accuracy, +10% weapon turn rate";
        }
    }

    public static class Brave extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getArmorDamageTakenMult().modifyMult(id, 0.9f);
            stats.getHullDamageTakenMult().modifyMult(id, 0.9f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getArmorDamageTakenMult().unmodify(id);
            stats.getHullDamageTakenMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-10% armor and hull damage taken";
        }
    }

    public static class TeamPlayer extends Ship {
        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getMaxSpeed().modifyPercent(id, 10f);
            stats.getAcceleration().modifyPercent(id, 10f);
            stats.getDeceleration().modifyPercent(id, 10f);
            stats.getMaxTurnRate().modifyPercent(id, 10f);
            stats.getTurnAcceleration().modifyPercent(id, 10f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);
            stats.getTurnAcceleration().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+10% top speed and maneuverability";
        }
    }

    public static class HardWorker extends Production {
        @Override
        int units() {
            return 1;
        }
    }

    public static class Thrifty extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getUpkeepMult().modifyMult(id, 0.9f, "Thrifty governor");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getUpkeepMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "-10% colony upkeep";
        }
    }

    public static class Beloved extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getStability().modifyFlat(id, 1f, "Beloved governor");
        }

        @Override
        public void unapply(MarketAPI market, String id) {
            market.getStability().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+1 stability";
        }
    }

    public static class Connected extends Colony {
        @Override
        public void apply(MarketAPI market, String id, float level) {
            market.getIncomeMult().modifyMult(id, 1.1f, "Well-connected governor");
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
