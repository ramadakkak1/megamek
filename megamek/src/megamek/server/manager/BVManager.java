package megamek.server.manager;

public class BVManager {
    protected static class BVCountHelper {
        int bv;
        int bvInitial;
        int bvFled;
        int unitsCount;
        int unitsInitialCount;
        int unitsLightDamageCount;
        int unitsModerateDamageCount;
        int unitsHeavyDamageCount;
        int unitsCrippledCount;
        int unitsDestroyedCount;
        int unitsCrewEjectedCount;
        int unitsCrewTrappedCount;
        int unitsCrewKilledCount;
        int unitsFledCount;
        int ejectedCrewActiveCount;
        int ejectedCrewPickedUpByTeamCount;
        int ejectedCrewPickedUpByEnemyTeamCount;
        int ejectedCrewKilledCount;
        int ejectedCrewFledCount;

        public BVCountHelper() {
            this.bv = 0;
            this.bvInitial = 0;
            this.bvFled = 0;
            this.unitsCount = 0;
            this.unitsInitialCount = 0;
            this.unitsLightDamageCount = 0;
            this.unitsModerateDamageCount = 0;
            this.unitsHeavyDamageCount = 0;
            this.unitsCrippledCount = 0;
            this.unitsDestroyedCount = 0;
            this.unitsCrewEjectedCount = 0;
            this.unitsCrewTrappedCount = 0;
            this.unitsCrewKilledCount = 0;
            this.unitsFledCount = 0;
            this.ejectedCrewActiveCount = 0;
            this.ejectedCrewPickedUpByTeamCount = 0;
            this.ejectedCrewPickedUpByEnemyTeamCount = 0;
            this.ejectedCrewKilledCount = 0;
            this.ejectedCrewFledCount = 0;
        }
    }
}