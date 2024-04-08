package megamek.server;

import megamek.common.*;
import megamek.common.options.OptionsConstants;
import org.apache.logging.log4j.LogManager;

import java.util.*;

public class UnitManager {
    private final GameManager gameManager;

    public UnitManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * Have the loader load the indicated unit. The unit being loaded loses its
     * turn.
     *
     * @param loader - the <code>Entity</code> that is loading the unit.
     * @param unit   - the <code>Entity</code> being loaded.
     */
    public void loadUnit(Entity loader, Entity unit, int bayNumber) {
        // ProtoMechs share a single turn for a Point. When loading one we don't remove its turn
        // unless it's the last unit in the Point to act.
        int remainingProtos = 0;
        if (unit.hasETypeFlag(Entity.ETYPE_PROTOMECH)) {
            remainingProtos = gameManager.getGame().getSelectedEntityCount(en -> en.hasETypeFlag(Entity.ETYPE_PROTOMECH)
                    && en.getId() != unit.getId()
                    && en.isSelectableThisTurn()
                    && en.getOwnerId() == unit.getOwnerId()
                    && en.getUnitNumber() == unit.getUnitNumber());
        }

        if (!gameManager.getGame().getPhase().isLounge() && !unit.isDone() && (remainingProtos == 0)) {
            // Remove the *last* friendly turn (removing the *first* penalizes
            // the opponent too much, and re-calculating moves is too hard).
            gameManager.getGame().removeTurnFor(unit);
            gameManager.send(gameManager.createTurnVectorPacket());
        }

        // Fighter Squadrons may become too big for the bay they're parked in
        if ((loader instanceof FighterSquadron) && (loader.getTransportId() != Entity.NONE)) {
            Entity carrier = gameManager.getGame().getEntity(loader.getTransportId());
            Transporter bay = carrier.getBay(loader);

            if (bay.getUnused() < 1) {
                if (gameManager.getGame().getPhase().isLounge()) {
                    // In the lobby, unload the squadron if too big
                    loader.setTransportId(Entity.NONE);
                    carrier.unload(loader);
                    gameManager.entityUpdate(carrier.getId());
                } else {
                    // Outside the lobby, reject the load
                    gameManager.entityUpdate(unit.getId());
                    gameManager.entityUpdate(loader.getId());
                    return;
                }
            }
        }

        // When loading an Aero into a squadron in the lounge, make sure the
        // loaded aero has the same bomb loadout as the squadron
        // We want to do this before the fighter is loaded: when the fighter
        // is loaded into the squadron, the squadrons bombing attacks are
        // adjusted based on the bomb loadout on the fighter.
        if (gameManager.getGame().getPhase().isLounge() && (loader instanceof FighterSquadron)) {
            ((IBomber) unit).setBombChoices(((FighterSquadron) loader).getExtBombChoices());
            ((FighterSquadron) loader).updateSkills();
            ((FighterSquadron) loader).updateWeaponGroups();
        }

        // Load the unit. Do not check for elevation during deployment
        boolean checkElevation = !gameManager.getGame().getPhase().isLounge()
                && !gameManager.getGame().getPhase().isDeployment();
        try {
            loader.load(unit, checkElevation, bayNumber);
        } catch (IllegalArgumentException e) {
            LogManager.getLogger().info(e.getMessage());
            gameManager.sendServerChat(e.getMessage());
            return;
        }
        // The loaded unit is being carried by the loader.
        unit.setTransportId(loader.getId());

        // Remove the loaded unit from the screen.
        unit.setPosition(null);

        // set deployment round of the loadee to equal that of the loader
        unit.setDeployRound(loader.getDeployRound());

        // Update the loading unit's passenger count, if it's a large craft
        if ((loader instanceof SmallCraft) || (loader instanceof Jumpship)) {
            // Don't add DropShip crew to a JumpShip or station's passenger list
            if (!unit.isLargeCraft()) {
                loader.setNPassenger(loader.getNPassenger() + unit.getCrew().getSize());
            }
        }

        // Update the loaded unit.
        gameManager.entityUpdate(unit.getId());
        gameManager.entityUpdate(loader.getId());
    }

    /**
     * Have the loader tow the indicated unit. The unit being towed loses its
     * turn.
     *
     * @param loader - the <code>Entity</code> that is towing the unit.
     * @param unit   - the <code>Entity</code> being towed.
     */
    public void towUnit(Entity loader, Entity unit) {
        if (!gameManager.getGame().getPhase().isLounge() && !unit.isDone()) {
            // Remove the *last* friendly turn (removing the *first* penalizes
            // the opponent too much, and re-calculating moves is too hard).
            gameManager.getGame().removeTurnFor(unit);
            gameManager.send(gameManager.createTurnVectorPacket());
        }

        loader.towUnit(unit.getId());

        // set deployment round of the loadee to equal that of the loader
        unit.setDeployRound(loader.getDeployRound());

        // Update the loader and towed units.
        gameManager.entityUpdate(unit.getId());
        gameManager.entityUpdate(loader.getId());
    }

    /**
     * Have the tractor drop the indicated trailer. This will also disconnect all
     * trailers that follow the one dropped.
     *
     * @param tractor  - the <code>Entity</code> that is disconnecting the trailer.
     * @param unloaded - the <code>Targetable</code> unit being unloaded.
     * @param pos      - the <code>Coords</code> for the unloaded unit.
     * @return <code>true</code> if the unit was successfully unloaded,
     * <code>false</code> if the trailer isn't carried by tractor.
     */
    public boolean disconnectUnit(Entity tractor, Targetable unloaded, Coords pos) {
        // We can only unload Entities.
        Entity trailer;
        if (unloaded instanceof Entity) {
            trailer = (Entity) unloaded;
        } else {
            return false;
        }
        // disconnectUnit() updates anything behind 'trailer' too, so copy
        // the list of trailers before we alter it so entityUpdate() can be
        // run on all of them. Also, add the entity towing Trailer to the list
        List<Integer> trailerList = new ArrayList<Integer>(trailer.getConnectedUnits());
        trailerList.add(trailer.getTowedBy());

        // Unload the unit.
        tractor.disconnectUnit(trailer.getId());

        // Update the tractor and all affected trailers.
        for (int id : trailerList) {
            gameManager.entityUpdate(id);
        }
        gameManager.entityUpdate(trailer.getId());
        gameManager.entityUpdate(tractor.getId());

        // Unloaded successfully.
        return true;
    }

    public boolean unloadUnit(Entity unloader, Targetable unloaded,
                              Coords pos, int facing, int elevation) {
        return unloadUnit(unloader, unloaded, pos, facing, elevation, false,
                false);
    }

    /**
     * Have the unloader unload the indicated unit. The unit being unloaded may
     * or may not gain a turn
     *
     * @param unloader   - the <code>Entity</code> that is unloading the unit.
     * @param unloaded   - the <code>Targetable</code> unit being unloaded.
     * @param pos        - the <code>Coords</code> for the unloaded unit.
     * @param facing     - the <code>int</code> facing for the unloaded unit.
     * @param elevation  - the <code>int</code> elevation at which to unload, if both
     *                   loader and loaded units use VTOL movement.
     * @param evacuation - a <code>boolean</code> indicating whether this unit is being
     *                   unloaded as a result of its carrying units destruction
     * @return <code>true</code> if the unit was successfully unloaded,
     * <code>false</code> if the unit isn't carried in unloader.
     */
    public boolean unloadUnit(Entity unloader, Targetable unloaded,
                              Coords pos, int facing, int elevation, boolean evacuation,
                              boolean duringDeployment) {

        // We can only unload Entities.
        Entity unit;
        if (unloaded instanceof Entity) {
            unit = (Entity) unloaded;
        } else {
            return false;
        }

        // Unload the unit.
        if (!unloader.unload(unit)) {
            return false;
        }

        // The unloaded unit is no longer being carried.
        unit.setTransportId(Entity.NONE);

        // Place the unloaded unit onto the screen.
        unit.setPosition(pos);

        // Units unloaded onto the screen are deployed.
        if (pos != null) {
            unit.setDeployed(true);
        }

        // Point the unloaded unit in the given direction.
        unit.setFacing(facing);
        unit.setSecondaryFacing(facing);

        Hex hex = gameManager.getGame().getBoard().getHex(pos);
        boolean isBridge = (hex != null)
                && hex.containsTerrain(Terrains.PAVEMENT);

        if (hex == null) {
            unit.setElevation(elevation);
        } else if (unloader.getMovementMode() == EntityMovementMode.VTOL) {
            if (unit.getMovementMode() == EntityMovementMode.VTOL) {
                // Flying units unload to the same elevation as the flying
                // transport
                unit.setElevation(elevation);
            } else if (gameManager.getGame().getBoard().getBuildingAt(pos) != null) {
                // non-flying unit unloaded from a flying onto a building
                // -> sit on the roof
                unit.setElevation(hex.terrainLevel(Terrains.BLDG_ELEV));
            } else {
                while (elevation >= -hex.depth()) {
                    if (unit.isElevationValid(elevation, hex)) {
                        unit.setElevation(elevation);
                        break;
                    }
                    elevation--;
                    // If unit is landed, the while loop breaks before here
                    // And unit.moved will be MOVE_NONE
                    // If we can jump, use jump
                    if (unit.getJumpMP() > 0) {
                        unit.moved = EntityMovementType.MOVE_JUMP;
                    } else { // Otherwise, use walk trigger check for ziplines
                        unit.moved = EntityMovementType.MOVE_WALK;
                    }
                }
                if (!unit.isElevationValid(elevation, hex)) {
                    return false;
                }
            }
        } else if (gameManager.getGame().getBoard().getBuildingAt(pos) != null) {
            // non flying unit unloading units into a building
            // -> sit in the building at the same elevation
            unit.setElevation(elevation);
        } else if (hex.terrainLevel(Terrains.WATER) > 0) {
            if ((unit.getMovementMode() == EntityMovementMode.HOVER)
                    || (unit.getMovementMode() == EntityMovementMode.WIGE)
                    || (unit.getMovementMode() == EntityMovementMode.HYDROFOIL)
                    || (unit.getMovementMode() == EntityMovementMode.NAVAL)
                    || (unit.getMovementMode() == EntityMovementMode.SUBMARINE)
                    || (unit.getMovementMode() == EntityMovementMode.INF_UMU)
                    || hex.containsTerrain(Terrains.ICE) || isBridge) {
                // units that can float stay on the surface, or we go on the
                // bridge
                // this means elevation 0, because elevation is relative to the
                // surface
                unit.setElevation(0);
            }
        } else {
            // default to the floor of the hex.
            // unit elevation is relative to the surface
            unit.setElevation(hex.floor() - hex.getLevel());
        }

        // Check for zip lines PSR -- MOVE_WALK implies ziplines
        if (unit.moved == EntityMovementType.MOVE_WALK) {
            if (gameManager.getGame().getOptions().booleanOption(OptionsConstants.ADVGRNDMOV_TACOPS_ZIPLINES)
                    && (unit instanceof Infantry)
                    && !((Infantry) unit).isMechanized()) {

                // Handle zip lines
                PilotingRollData psr = GameManager.getEjectModifiers(gameManager.getGame(), unit, 0, false,
                        unit.getPosition(), "Anti-mek skill");
                // Factor in Elevation
                if (unloader.getElevation() > 0) {
                    psr.addModifier(unloader.getElevation(), "elevation");
                }
                Roll diceRoll = Compute.rollD6(2);

                // Report ziplining
                Report r = new Report(9920);
                r.subject = unit.getId();
                r.addDesc(unit);
                r.newlines = 0;
                gameManager.addReport(r);

                // Report TN
                r = new Report(9921);
                r.subject = unit.getId();
                r.add(psr.getValue());
                r.add(psr.getDesc());
                r.add(diceRoll);
                r.newlines = 0;
                gameManager.addReport(r);

                if (diceRoll.getIntValue() < psr.getValue()) { // Failure!
                    r = new Report(9923);
                    r.subject = unit.getId();
                    r.add(psr.getValue());
                    r.add(diceRoll);
                    gameManager.addReport(r);

                    HitData hit = unit.rollHitLocation(ToHitData.HIT_NORMAL, ToHitData.SIDE_FRONT);
                    hit.setIgnoreInfantryDoubleDamage(true);
                    gameManager.addReport(gameManager.damageEntity(unit, hit, 5));
                } else { //  Report success
                    r = new Report(9922);
                    r.subject = unit.getId();
                    r.add(psr.getValue());
                    r.add(diceRoll);
                    gameManager.addReport(r);
                }
                gameManager.addNewLines();
            } else {
                return false;
            }
        }

        gameManager.addReport(gameManager.doSetLocationsExposure(unit, hex, false, unit.getElevation()));

        // unlike other unloaders, entities unloaded from droppers can still
        // move (unless infantry)
        if (!evacuation && (unloader instanceof SmallCraft)
                && !(unit instanceof Infantry)) {
            unit.setUnloaded(false);
            unit.setDone(false);

            // unit uses half of walk mp and is treated as moving one hex
            unit.mpUsed = unit.getOriginalWalkMP() / 2;
            unit.delta_distance = 1;
        }

        // If we unloaded during deployment, allow a turn
        if (duringDeployment) {
            unit.setUnloaded(false);
            unit.setDone(false);
        }

        //Update the transport unit's passenger count, if it's a large craft
        if (unloader instanceof SmallCraft || unloader instanceof Jumpship) {
            //Don't add dropship crew to a jumpship or station's passenger list
            if (!unit.isLargeCraft()) {
                unloader.setNPassenger(Math.max(0, unloader.getNPassenger() - unit.getCrew().getSize()));
            }
        }

        // Update the unloaded unit.
        gameManager.entityUpdate(unit.getId());

        // Unloaded successfully.
        return true;
    }

    /**
     * Do a piloting skill check to attempt landing
     *
     * @param entity The <code>Entity</code> that is landing
     * @param roll   The <code>PilotingRollData</code> to be used for this landing.
     */
    public void attemptLanding(Entity entity, PilotingRollData roll) {
        if (roll.getValue() == TargetRoll.AUTOMATIC_SUCCESS) {
            return;
        }

        // okay, print the info
        Report r = new Report(9605);
        r.subject = entity.getId();
        r.addDesc(entity);
        r.add(roll.getLastPlainDesc(), true);
        gameManager.addReport(r);

        // roll
        final Roll diceRoll = Compute.rollD6(2);
        r = new Report(9606);
        r.subject = entity.getId();
        r.add(roll.getValueAsString());
        r.add(roll.getDesc());
        r.add(diceRoll);

        // boolean suc;
        if (diceRoll.getIntValue() < roll.getValue()) {
            r.choose(false);
            gameManager.addReport(r);
            int mof = roll.getValue() - diceRoll.getIntValue();
            int damage = 10 * (mof);
            // Report damage taken
            r = new Report(9609);
            r.indent();
            r.addDesc(entity);
            r.add(damage);
            r.add(mof);
            gameManager.addReport(r);

            int side = ToHitData.SIDE_FRONT;
            if ((entity instanceof Aero) && ((Aero) entity).isSpheroid()) {
                side = ToHitData.SIDE_REAR;
            }
            while (damage > 0) {
                HitData hit = entity.rollHitLocation(ToHitData.HIT_NORMAL, side);
                gameManager.addReport(gameManager.damageEntity(entity, hit, 10));
                damage -= 10;
            }
            // suc = false;
        } else {
            r.choose(true);
            gameManager.addReport(r);
            // suc = true;
        }
    }

    /**
     * Any aerospace unit that lands in a rough or rubble hex takes landing hear damage.
     *
     * @param aero         The landing unit
     * @param vertical     Whether the landing is vertical
     * @param touchdownPos The coordinates of the hex of touchdown
     * @param finalPos     The coordinates of the hex in which the unit comes to a stop
     * @param facing       The facing of the landing unit
     * @
     */
    public void checkLandingTerrainEffects(IAero aero, boolean vertical, Coords touchdownPos, Coords finalPos, int facing) {
        // Landing in a rough for rubble hex damages landing gear.
        Set<Coords> landingPositions = aero.getLandingCoords(vertical, touchdownPos, facing);
        if (landingPositions.stream().map(c -> gameManager.getGame().getBoard().getHex(c)).filter(Objects::nonNull)
                .anyMatch(h -> h.containsTerrain(Terrains.ROUGH) || h.containsTerrain(Terrains.RUBBLE))) {
            aero.setGearHit(true);
            Report r = new Report(9125);
            r.subject = ((Entity) aero).getId();
            gameManager.addReport(r);
        }
        // Landing in water can destroy or immobilize the unit.
        Hex hex = gameManager.getGame().getBoard().getHex(finalPos);
        if ((aero instanceof Aero) && hex.containsTerrain(Terrains.WATER) && !hex.containsTerrain(Terrains.ICE)
                && (hex.terrainLevel(Terrains.WATER) > 0)
                && !((Entity) aero).hasWorkingMisc(MiscType.F_FLOTATION_HULL)) {
            if ((hex.terrainLevel(Terrains.WATER) > 1) || !(aero instanceof Dropship)) {
                Report r = new Report(9702);
                r.subject(((Entity) aero).getId());
                r.addDesc((Entity) aero);
                gameManager.addReport(r);
                gameManager.addReport(gameManager.destroyEntity((Entity) aero, "landing in deep water"));
            }
        }
    }

    public boolean launchUnit(Entity unloader, Targetable unloaded,
                              Coords pos, int facing, int velocity, int altitude, int[] moveVec,
                              int bonus) {

        Entity unit;
        if (unloaded instanceof Entity && unloader instanceof Aero) {
            unit = (Entity) unloaded;
        } else {
            return false;
        }

        // must be an ASF, Small Craft, or DropShip
        if (!unit.isAero() || unit instanceof Jumpship) {
            return false;
        }
        IAero a = (IAero) unit;

        Report r;

        // Unload the unit.
        if (!unloader.unload(unit)) {
            return false;
        }

        // The unloaded unit is no longer being carried.
        unit.setTransportId(Entity.NONE);

        // pg. 86 of TW: launched fighters can move in fire in the turn they are
        // unloaded
        unit.setUnloaded(false);

        // Place the unloaded unit onto the screen.
        unit.setPosition(pos);

        // Units unloaded onto the screen are deployed.
        if (pos != null) {
            unit.setDeployed(true);
        }

        // Point the unloaded unit in the given direction.
        unit.setFacing(facing);
        unit.setSecondaryFacing(facing);

        // the velocity of the unloaded unit is the same as the loader
        a.setCurrentVelocity(velocity);
        a.setNextVelocity(velocity);

        // if using advanced movement then set vectors
        unit.setVectors(moveVec);

        unit.setAltitude(altitude);

        // it seems that the done button is still being set and I can't figure
        // out where
        unit.setDone(false);

        // if the bonus was greater than zero then too many fighters were
        // launched and they
        // must all make control rolls
        if (bonus > 0) {
            PilotingRollData psr = unit.getBasePilotingRoll();
            psr.addModifier(bonus, "safe launch rate exceeded");
            Roll diceRoll = Compute.rollD6(2);
            r = new Report(9375);
            r.subject = unit.getId();
            r.add(unit.getDisplayName());
            r.add(psr);
            r.add(diceRoll);
            r.indent(1);

            if (diceRoll.getIntValue() < psr.getValue()) {
                r.choose(false);
                gameManager.addReport(r);
                // damage the unit
                int damage = 10 * (psr.getValue() - diceRoll.getIntValue());
                HitData hit = unit.rollHitLocation(ToHitData.HIT_NORMAL, ToHitData.SIDE_FRONT);
                Vector<Report> rep = gameManager.damageEntity(unit, hit, damage);
                Report.indentAll(rep, 1);
                rep.lastElement().newlines++;
                gameManager.addReport(rep);
                // did we destroy the unit?
                if (unit.isDoomed()) {
                    // Clean out the entity.
                    unit.setDestroyed(true);
                    gameManager.getGame().moveToGraveyard(unit.getId());
                    gameManager.send(gameManager.createRemoveEntityPacket(unit.getId()));
                }
            } else {
                // avoided damage
                r.choose(true);
                r.newlines++;
                gameManager.addReport(r);
            }
        } else {
            r = new Report(9374);
            r.subject = unit.getId();
            r.add(unit.getDisplayName());
            r.indent(1);
            r.newlines++;
            gameManager.addReport(r);
        }

        // launching from an OOC vessel causes damage
        // same thing if faster than 2 velocity in atmosphere
        if ((((Aero) unloader).isOutControlTotal() && !unit.isDoomed())
                || ((((Aero) unloader).getCurrentVelocity() > 2) && !gameManager.getGame()
                .getBoard().inSpace())) {
            Roll diceRoll = Compute.rollD6(2);
            int damage = diceRoll.getIntValue() * 10;
            String rollCalc = damage + "[" + diceRoll.getIntValue() + " * 10]";
            r = new Report(9385);
            r.subject = unit.getId();
            r.add(unit.getDisplayName());
            r.addDataWithTooltip(rollCalc, diceRoll.getReport());
            gameManager.addReport(r);
            HitData hit = unit.rollHitLocation(ToHitData.HIT_NORMAL, ToHitData.SIDE_FRONT);
            gameManager.addReport(gameManager.damageEntity(unit, hit, damage));
            // did we destroy the unit?
            if (unit.isDoomed()) {
                // Clean out the entity.
                unit.setDestroyed(true);
                gameManager.getGame().moveToGraveyard(unit.getId());
                gameManager.send(gameManager.createRemoveEntityPacket(unit.getId()));
            }
        }

        // Update the unloaded unit.
        gameManager.entityUpdate(unit.getId());

        // Set the turn mask. We need to be specific otherwise we run the risk
        // of having a unit of another class consume the turn and leave the
        // unloaded unit without a turn
        int turnMask;
        List<GameTurn> turnVector = gameManager.getGame().getTurnVector();
        if (unit instanceof Dropship) {
            turnMask = GameTurn.CLASS_DROPSHIP;
        } else if (unit instanceof SmallCraft) {
            turnMask = GameTurn.CLASS_SMALL_CRAFT;
        } else {
            turnMask = GameTurn.CLASS_AERO;
        }
        // Add one, otherwise we consider the turn we're currently processing
        int turnInsertIdx = gameManager.getGame().getTurnIndex() + 1;
        // We have to figure out where to insert this turn, to maintain proper
        // space turn order (JumpShips, Small Craft, DropShips, Aeros)
        for (; turnInsertIdx < turnVector.size(); turnInsertIdx++) {
            GameTurn turn = turnVector.get(turnInsertIdx);
            if (turn.isValidEntity(unit, gameManager.getGame())) {
                break;
            }
        }

        // ok add another turn for the unloaded entity so that it can move
        GameTurn newTurn = new GameTurn.EntityClassTurn(unit.getOwner().getId(), turnMask);
        gameManager.getGame().insertTurnAfter(newTurn, turnInsertIdx);
        // brief everybody on the turn update
        gameManager.send(gameManager.createTurnVectorPacket());

        return true;
    }

    public void dropUnit(Entity drop, Entity entity, Coords curPos, int altitude) {
        // Unload the unit.
        entity.unload(drop);
        // The unloaded unit is no longer being carried.
        drop.setTransportId(Entity.NONE);

        // OK according to Welshman's pending ruling, when on the ground map
        // units should be deployed in the ring two hexes away from the DropShip
        // optimally, we should let people choose here, but that would be
        // complicated
        // so for now I am just going to distribute them. I will give each unit
        // the first
        // emptiest hex that has no water or magma in it.
        // I will start the circle based on the facing of the dropper
        // Spheroid - facing
        // Aerodyne - opposite of facing
        // http://www.classicbattletech.com/forums/index.php?topic=65600.msg1568089#new
        if (gameManager.getGame().getBoard().onGround() && (null != curPos)) {
            boolean selected = false;
            int count;
            int max = 0;
            int facing = entity.getFacing();
            if (entity.getMovementMode() == EntityMovementMode.AERODYNE) {
                // no real rule for this but it seems to make sense that units
                // would drop behind an
                // aerodyne rather than in front of it
                facing = (facing + 3) % 6;
            }
            boolean checkDanger = true;
            while (!selected) {
                // we can get caught in an infinite loop if all available hexes
                // are dangerous, so check for this
                boolean allDanger = true;
                for (int i = 0; i < 6; i++) {
                    int dir = (facing + i) % 6;
                    Coords newPos = curPos.translated(dir, 2);
                    count = 0;
                    if (gameManager.getGame().getBoard().contains(newPos)) {
                        Hex newHex = gameManager.getGame().getBoard().getHex(newPos);
                        Building bldg = gameManager.getGame().getBoard().getBuildingAt(newPos);
                        boolean danger = newHex.containsTerrain(Terrains.WATER)
                                || newHex.containsTerrain(Terrains.MAGMA)
                                || (null != bldg);
                        for (Entity unit : gameManager.getGame().getEntitiesVector(newPos)) {
                            if ((unit.getAltitude() == altitude)
                                    && !unit.isAero()) {
                                count++;
                            }
                        }
                        if ((count <= max) && (!danger || !checkDanger)) {
                            selected = true;
                            curPos = newPos;
                            break;
                        }
                        if (!danger) {
                            allDanger = false;
                        }
                    }
                    newPos = newPos.translated((dir + 2) % 6);
                    count = 0;
                    if (gameManager.getGame().getBoard().contains(newPos)) {
                        Hex newHex = gameManager.getGame().getBoard().getHex(newPos);
                        Building bldg = gameManager.getGame().getBoard().getBuildingAt(newPos);
                        boolean danger = newHex.containsTerrain(Terrains.WATER)
                                || newHex.containsTerrain(Terrains.MAGMA)
                                || (null != bldg);
                        for (Entity unit : gameManager.getGame().getEntitiesVector(newPos)) {
                            if ((unit.getAltitude() == altitude) && !unit.isAero()) {
                                count++;
                            }
                        }
                        if ((count <= max) && (!danger || !checkDanger)) {
                            selected = true;
                            curPos = newPos;
                            break;
                        }
                        if (!danger) {
                            allDanger = false;
                        }
                    }
                }
                if (allDanger && checkDanger) {
                    checkDanger = false;
                } else {
                    max++;
                }
            }
        }

        // Place the unloaded unit onto the screen.
        drop.setPosition(curPos);

        // Units unloaded onto the screen are deployed.
        if (curPos != null) {
            drop.setDeployed(true);
        }

        // Point the unloaded unit in the given direction.
        drop.setFacing(entity.getFacing());
        drop.setSecondaryFacing(entity.getFacing());

        drop.setAltitude(altitude);
        gameManager.entityUpdate(drop.getId());
    }
}