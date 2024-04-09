package megamek.server.manager;

import megamek.common.*;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import megamek.common.options.OptionsConstants;

import java.util.*;

public class MinefieldManager {
    private final GameManager gameManager;

    public MinefieldManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * Check for any detonations when an entity enters a minefield, except a
     * vibrabomb.
     *
     * @param entity      - the <code>entity</code> who entered the minefield
     * @param c           - the <code>Coords</code> of the minefield
     * @param curElev     - an <code>int</code> for the elevation of the entity entering
     *                    the minefield (used for underwater sea mines)
     * @param isOnGround  - <code>true</code> if the entity is not in the middle of a
     *                    jump
     * @param vMineReport - the {@link Report} <code>Vector</code> that reports will be added to
     * @return - <code>true</code> if the entity set off any mines
     */
    public boolean enterMinefield(Entity entity, Coords c, int curElev, boolean isOnGround,
                                  Vector<Report> vMineReport) {
        return enterMinefield(entity, c, curElev, isOnGround, vMineReport, -1);
    }

    /**
     * Check for any detonations when an entity enters a minefield, except a
     * vibrabomb.
     *
     * @param entity      - the <code>entity</code> who entered the minefield
     * @param c           - the <code>Coords</code> of the minefield
     * @param curElev     - an <code>int</code> for the elevation of the entity entering
     *                    the minefield (used for underwater sea mines)
     * @param isOnGround  - <code>true</code> if the entity is not in the middle of a
     *                    jump
     * @param vMineReport - the {@link Report} <code>Vector</code> that reports will be added to
     * @param target      - the <code>int</code> target number for detonation. If this
     *                    will be determined by density, it should be -1
     * @return - <code>true</code> if the entity set off any mines
     */
    public boolean enterMinefield(Entity entity, Coords c, int curElev, boolean isOnGround,
                                  Vector<Report> vMineReport, int target) {
        Report r;
        boolean trippedMine = false;
        // flying units cannot trip a mine
        if (curElev > 0) {
            return false;
        }

        // Check for Mine sweepers
        Mounted minesweeper = null;
        for (Mounted m : entity.getMisc()) {
            if (m.getType().hasFlag(MiscType.F_MINESWEEPER) && m.isReady() && (m.getArmorValue() > 0)) {
                minesweeper = m;
                break; // Can only have one minesweeper
            }
        }

        Vector<Minefield> fieldsToRemove = new Vector<Minefield>();
        // loop through mines in this hex
        for (Minefield mf : gameManager.getGame().getMinefields(c)) {
            // vibrabombs are handled differently
            if (mf.getType() == Minefield.TYPE_VIBRABOMB) {
                continue;
            }

            // if we are in the water, then the sea mine will only blow up if at
            // the right depth
            if (gameManager.getGame().getBoard().getHex(mf.getCoords()).containsTerrain(Terrains.WATER)) {
                if ((Math.abs(curElev) != mf.getDepth())
                        && (Math.abs(curElev + entity.getHeight()) != mf.getDepth())) {
                    continue;
                }
            }

            // Check for mine-sweeping. Vibramines handled elsewhere
            if ((minesweeper != null)
                    && ((mf.getType() == Minefield.TYPE_CONVENTIONAL)
                    || (mf.getType() == Minefield.TYPE_ACTIVE)
                    || (mf.getType() == Minefield.TYPE_INFERNO))) {
                // Check to see if the minesweeper clears
                Roll diceRoll = Compute.rollD6(2);

                // Report minefield roll
                if (gameManager.doBlind()) { // only report if DB, otherwise all players see
                    r = new Report(2152, Report.PLAYER);
                    r.player = mf.getPlayerId();
                    r.add(Minefield.getDisplayableName(mf.getType()));
                    r.add(mf.getCoords().getBoardNum());
                    r.add(diceRoll);
                    r.newlines = 0;
                    vMineReport.add(r);
                }

                if (diceRoll.getIntValue() >= 6) {
                    // Report hit
                    if (gameManager.doBlind()) {
                        r = new Report(5543, Report.PLAYER);
                        r.player = mf.getPlayerId();
                        vMineReport.add(r);
                    }

                    // Clear the minefield
                    r = new Report(2158);
                    r.subject = entity.getId();
                    r.add(entity.getShortName(), true);
                    r.add(Minefield.getDisplayableName(mf.getType()), true);
                    r.add(mf.getCoords().getBoardNum(), true);
                    r.indent();
                    vMineReport.add(r);
                    fieldsToRemove.add(mf);

                    // Handle armor value damage
                    int remainingAV = minesweeper.getArmorValue() - 6;
                    minesweeper.setArmorValue(Math.max(remainingAV, 0));

                    r = new Report(2161);
                    r.indent(2);
                    r.subject = entity.getId();
                    r.add(entity.getShortName(), true);
                    r.add(6);
                    r.add(Math.max(remainingAV, 0));
                    vMineReport.add(r);

                    if (remainingAV <= 0) {
                        minesweeper.setDestroyed(true);
                    }
                    // Check for damage transfer
                    if (remainingAV < 0) {
                        int damage = Math.abs(remainingAV);
                        r = new Report(2162);
                        r.indent(2);
                        r.subject = entity.getId();
                        r.add(damage, true);
                        vMineReport.add(r);

                        // Damage is dealt to the location of minesweeper
                        HitData hit = new HitData(minesweeper.getLocation());
                        Vector<Report> damageReports = gameManager.damageEntity(entity, hit, damage);
                        for (Report r1 : damageReports) {
                            r1.indent(1);
                        }
                        vMineReport.addAll(damageReports);
                    }
                    Report.addNewline(vMineReport);
                    // If the minefield is cleared, we're done processing it
                    continue;
                } else {
                    // Report miss
                    if (gameManager.doBlind()) {
                        r = new Report(5542, Report.PLAYER);
                        r.player = mf.getPlayerId();
                        vMineReport.add(r);
                    }
                }
            }

            // check whether we have an active mine
            if ((mf.getType() == Minefield.TYPE_ACTIVE) && isOnGround) {
                continue;
            } else if ((mf.getType() != Minefield.TYPE_ACTIVE) && !isOnGround) {
                continue;
            }

            // set the target number
            if (target == -1) {
                target = mf.getTrigger();
                if (mf.getType() == Minefield.TYPE_ACTIVE) {
                    target = 9;
                }
                if (entity instanceof Infantry) {
                    target += 1;
                }
                if (entity.hasAbility(OptionsConstants.MISC_EAGLE_EYES)) {
                    target += 2;
                }
                if ((entity.getMovementMode() == EntityMovementMode.HOVER)
                        || (entity.getMovementMode() == EntityMovementMode.WIGE)) {
                    target = Minefield.HOVER_WIGE_DETONATION_TARGET;
                }
            }

            Roll diceRoll = Compute.rollD6(2);

            // Report minefield roll
            if (gameManager.doBlind()) { // Only do if DB, otherwise all players will see
                r = new Report(2151, Report.PLAYER);
                r.player = mf.getPlayerId();
                r.add(Minefield.getDisplayableName(mf.getType()));
                r.add(mf.getCoords().getBoardNum());
                r.add(target);
                r.add(diceRoll);
                r.newlines = 0;
                vMineReport.add(r);
            }

            if (diceRoll.getIntValue() < target) {
                // Report miss
                if (gameManager.doBlind()) {
                    r = new Report(2217, Report.PLAYER);
                    r.player = mf.getPlayerId();
                    vMineReport.add(r);
                }
                continue;
            }

            // Report hit
            if (gameManager.doBlind()) {
                r = new Report(2270, Report.PLAYER);
                r.player = mf.getPlayerId();
                vMineReport.add(r);
            }

            // apply damage
            trippedMine = true;
            // explodedMines.add(mf);
            mf.setDetonated(true);
            if (mf.getType() == Minefield.TYPE_INFERNO) {
                // report hitting an inferno mine
                r = new Report(2155);
                r.subject = entity.getId();
                r.add(entity.getShortName(), true);
                r.add(mf.getCoords().getBoardNum(), true);
                r.indent();
                vMineReport.add(r);
                vMineReport.addAll(gameManager.deliverInfernoMissiles(entity, entity, mf.getDensity() / 2));
            } else {
                r = new Report(2150);
                r.subject = entity.getId();
                r.add(entity.getShortName(), true);
                r.add(mf.getCoords().getBoardNum(), true);
                r.indent();
                vMineReport.add(r);
                int damage = mf.getDensity();
                while (damage > 0) {
                    int cur_damage = Math.min(5, damage);
                    damage = damage - cur_damage;
                    HitData hit;
                    if (minesweeper == null) {
                        hit = entity.rollHitLocation(Minefield.TO_HIT_TABLE,
                                Minefield.TO_HIT_SIDE);
                    } else { // Minesweepers cause mines to hit minesweeper loc
                        hit = new HitData(minesweeper.getLocation());
                    }
                    vMineReport.addAll(gameManager.damageEntity(entity, hit, cur_damage));
                }

                if (entity instanceof Tank) {
                    // Tanks check for motive system damage from minefields as
                    // from a side hit even though the damage proper hits the
                    // front above; exact side doesn't matter, though.
                    vMineReport.addAll(gameManager.vehicleMotiveDamage((Tank) entity,
                            entity.getMotiveSideMod(ToHitData.SIDE_LEFT)));
                }
                Report.addNewline(vMineReport);
            }

            // check the direct reduction
            mf.checkReduction(0, true);
            revealMinefield(mf);
        }

        for (Minefield mf : fieldsToRemove) {
            removeMinefield(mf);
        }

        return trippedMine;
    }

    /**
     * cycle through all mines on the board, check to see whether they should do
     * collateral damage to other mines due to detonation, resets detonation to
     * false, and removes any mines whose density has been reduced to zero.
     */
    public void resetMines() {
        Enumeration<Coords> mineLoc = gameManager.getGame().getMinedCoords();
        while (mineLoc.hasMoreElements()) {
            Coords c = mineLoc.nextElement();
            Enumeration<Minefield> minefields = gameManager.getGame().getMinefields(c).elements();
            while (minefields.hasMoreElements()) {
                Minefield minefield = minefields.nextElement();
                if (minefield.hasDetonated()) {
                    minefield.setDetonated(false);
                    Enumeration<Minefield> otherMines = gameManager.getGame().getMinefields(c).elements();
                    while (otherMines.hasMoreElements()) {
                        Minefield otherMine = otherMines.nextElement();
                        if (otherMine.equals(minefield)) {
                            continue;
                        }
                        int bonus = 0;
                        if (otherMine.getDensity() > minefield.getDensity()) {
                            bonus = 1;
                        }
                        if (otherMine.getDensity() < minefield.getDensity()) {
                            bonus = -1;
                        }
                        otherMine.checkReduction(bonus, false);
                    }
                }
            }
            // cycle through a second time to see if any mines at these coords
            // need to be removed
            List<Minefield> mfRemoved = new ArrayList<Minefield>();
            Enumeration<Minefield> mines = gameManager.getGame().getMinefields(c).elements();
            while (mines.hasMoreElements()) {
                Minefield mine = mines.nextElement();
                if (mine.getDensity() < 5) {
                    mfRemoved.add(mine);
                }
            }
            // we have to do it this way to avoid a concurrent error problem
            for (Minefield mf : mfRemoved) {
                removeMinefield(mf);
            }
            // update the mines at these coords
            gameManager.sendChangedMines(c);
        }
    }

    /**
     * attempt to clear a minefield
     *
     * @param mf     - a <code>Minefield</code> to clear
     * @param en     - <code>entity</code> doing the clearing
     * @param target - <code>int</code> needed to roll for a successful clearance
     * @return <code>true</code> if clearance successful
     */
    public boolean clearMinefield(Minefield mf, Entity en, int target,
                                  Vector<Report> vClearReport) {
        return clearMinefield(mf, en, target, -1, vClearReport, 2);
    }

    public boolean clearMinefield(Minefield mf, Entity en, int target,
                                  int botch, Vector<Report> vClearReport) {
        return clearMinefield(mf, en, target, botch, vClearReport, 1);
    }

    /**
     * attempt to clear a minefield We don't actually remove the minefield here,
     * because if this is called up from within a loop, that will cause problems
     *
     * @param mf           - a <code>Minefield</code> to clear
     * @param en           - <code>entity</code> doing the clearing
     * @param target       - <code>int</code> needed to roll for a successful clearance
     * @param botch        - <code>int</code> that indicates an accidental detonation
     * @param vClearReport - The report collection to report to
     * @param indent       - The number of indents for the report
     * @return <code>true</code> if clearance successful
     */
    public boolean clearMinefield(Minefield mf, Entity en, int target,
                                  int botch, Vector<Report> vClearReport, int indent) {
        Report r;
        Roll diceRoll = Compute.rollD6(2);

        if (diceRoll.getIntValue() >= target) {
            r = new Report(2250);
            r.subject = en.getId();
            r.add(Minefield.getDisplayableName(mf.getType()));
            r.add(target);
            r.add(diceRoll);
            r.indent(indent);
            vClearReport.add(r);
            return true;
        } else if (diceRoll.getIntValue() <= botch) {
            // TODO : detonate the minefield
            r = new Report(2255);
            r.subject = en.getId();
            r.indent(indent);
            r.add(Minefield.getDisplayableName(mf.getType()));
            r.add(target);
            r.add(diceRoll);
            vClearReport.add(r);
            // The detonation damages any units that were also attempting to
            // clear mines in the same hex
            for (Entity victim : gameManager.getGame().getEntitiesVector(mf.getCoords())) {
                Report rVictim;
                if (victim.isClearingMinefield()) {
                    rVictim = new Report(2265);
                    rVictim.subject = victim.getId();
                    rVictim.add(victim.getShortName(), true);
                    rVictim.indent(indent + 1);
                    vClearReport.add(rVictim);
                    int damage = mf.getDensity();
                    while (damage > 0) {
                        int cur_damage = Math.min(5, damage);
                        damage = damage - cur_damage;
                        HitData hit = victim.rollHitLocation(
                                Minefield.TO_HIT_TABLE, Minefield.TO_HIT_SIDE);
                        vClearReport.addAll(gameManager.damageEntity(victim, hit, cur_damage));
                    }
                }
            }
            // reduction works differently here
            if (mf.getType() == Minefield.TYPE_CONVENTIONAL) {
                mf.setDensity(Math.max(5, mf.getDensity() - 5));
            } else {
                // congratulations, you cleared the mine by blowing yourself up
                return true;
            }
        } else {
            // failure
            r = new Report(2260);
            r.subject = en.getId();
            r.indent(indent);
            r.add(Minefield.getDisplayableName(mf.getType()));
            r.add(target);
            r.add(diceRoll);
            vClearReport.add(r);
        }
        return false;
    }

    /**
     * Clear any detonated mines at these coords
     */
    public void clearDetonatedMines(Coords c, int target) {
        Enumeration<Minefield> minefields = gameManager.getGame().getMinefields(c).elements();
        List<Minefield> mfRemoved = new ArrayList<Minefield>();
        while (minefields.hasMoreElements()) {
            Minefield minefield = minefields.nextElement();
            if (minefield.hasDetonated() && (Compute.d6(2) >= target)) {
                mfRemoved.add(minefield);
            }
        }
        // we have to do it this way to avoid a concurrent error problem
        for (Minefield mf : mfRemoved) {
            removeMinefield(mf);
        }
    }

    /**
     * Checks to see if an entity sets off any vibrabombs.
     */
    public boolean checkVibrabombs(Entity entity, Coords coords, boolean displaced,
                                   Vector<Report> vMineReport) {
        return checkVibrabombs(entity, coords, displaced, null, null, vMineReport);
    }

    /**
     * Checks to see if an entity sets off any vibrabombs.
     */
    public boolean checkVibrabombs(Entity entity, Coords coords, boolean displaced, Coords lastPos,
                                   Coords curPos, Vector<Report> vMineReport) {
        int mass = (int) entity.getWeight();

        // Check for Mine sweepers
        Mounted minesweeper = null;
        for (Mounted m : entity.getMisc()) {
            if (m.getType().hasFlag(MiscType.F_MINESWEEPER) && m.isReady() && (m.getArmorValue() > 0)) {
                minesweeper = m;
                break; // Can only have one minesweeper
            }
        }

        // Check for minesweepers sweeping VB minefields
        if (minesweeper != null) {
            Vector<Minefield> fieldsToRemove = new Vector<Minefield>();
            for (Minefield mf : gameManager.getGame().getVibrabombs()) {
                // Ignore mines if they aren't in this position
                if (!mf.getCoords().equals(coords)) {
                    continue;
                }

                // Minesweepers on units within 9 tons of the vibrafield setting
                // automatically clear the minefield
                if (Math.abs(mass - mf.getSetting()) < 10) {
                    // Clear the minefield
                    Report r = new Report(2158);
                    r.subject = entity.getId();
                    r.add(entity.getShortName(), true);
                    r.add(Minefield.getDisplayableName(mf.getType()), true);
                    r.add(mf.getCoords().getBoardNum(), true);
                    r.indent();
                    vMineReport.add(r);
                    fieldsToRemove.add(mf);

                    // Handle armor value damage
                    int remainingAV = minesweeper.getArmorValue() - 10;
                    minesweeper.setArmorValue(Math.max(remainingAV, 0));

                    r = new Report(2161);
                    r.indent(2);
                    r.subject = entity.getId();
                    r.add(entity.getShortName(), true);
                    r.add(10);
                    r.add(Math.max(remainingAV, 0));
                    vMineReport.add(r);

                    if (remainingAV <= 0) {
                        minesweeper.setDestroyed(true);
                    }
                    // Check for damage transfer
                    if (remainingAV < 0) {
                        int damage = Math.abs(remainingAV);
                        r = new Report(2162);
                        r.indent(2);
                        r.subject = entity.getId();
                        r.add(damage, true);
                        vMineReport.add(r);

                        // Damage is dealt to the location of minesweeper
                        HitData hit = new HitData(minesweeper.getLocation());
                        Vector<Report> damageReports = gameManager.damageEntity(entity, hit, damage);
                        for (Report r1 : damageReports) {
                            r1.indent(1);
                        }
                        vMineReport.addAll(damageReports);
                        entity.applyDamage();
                    }
                    Report.addNewline(vMineReport);
                }
            }
            for (Minefield mf : fieldsToRemove) {
                removeMinefield(mf);
            }
        }

        boolean boom = false;
        // Only mechs can set off vibrabombs. QuadVees should only be able to set off a
        // vibrabomb in Mech mode. Those that are converting to or from Mech mode should
        // are using leg movement and should be able to set them off.
        if (!(entity instanceof Mech) || (entity instanceof QuadVee
                && (entity.getConversionMode() == QuadVee.CONV_MODE_VEHICLE)
                && !entity.isConvertingNow())) {
            return false;
        }

        Enumeration<Minefield> e = gameManager.getGame().getVibrabombs().elements();

        while (e.hasMoreElements()) {
            Minefield mf = e.nextElement();

            // Bug 954272: Mines shouldn't work underwater, and BMRr says
            // Vibrabombs are mines
            if (gameManager.getGame().getBoard().getHex(mf.getCoords()).containsTerrain(Terrains.WATER)
                    && !gameManager.getGame().getBoard().getHex(mf.getCoords()).containsTerrain(Terrains.PAVEMENT)
                    && !gameManager.getGame().getBoard().getHex(mf.getCoords()).containsTerrain(Terrains.ICE)) {
                continue;
            }

            // Mech weighing 10 tons or less can't set off the bomb
            if (mass <= (mf.getSetting() - 10)) {
                continue;
            }

            int effectiveDistance = (mass - mf.getSetting()) / 10;
            int actualDistance = coords.distance(mf.getCoords());

            if (actualDistance <= effectiveDistance) {
                Report r = new Report(2156);
                r.subject = entity.getId();
                r.add(entity.getShortName(), true);
                r.add(mf.getCoords().getBoardNum(), true);
                vMineReport.add(r);

                // if the moving entity is not actually moving into the vibrabomb
                // hex, it won't get damaged
                Integer excludeEntityID = null;
                if (!coords.equals(mf.getCoords())) {
                    excludeEntityID = entity.getId();
                }

                gameManager.explodeVibrabomb(mf, vMineReport, excludeEntityID);
            }

            // Hack; when moving, the Mech isn't in the hex during
            // the movement.
            if (!displaced && (actualDistance == 0)) {
                // report getting hit by vibrabomb
                Report r = new Report(2160);
                r.subject = entity.getId();
                r.add(entity.getShortName(), true);
                vMineReport.add(r);
                int damage = mf.getDensity();
                while (damage > 0) {
                    int cur_damage = Math.min(5, damage);
                    damage = damage - cur_damage;
                    HitData hit = entity.rollHitLocation(Minefield.TO_HIT_TABLE, Minefield.TO_HIT_SIDE);
                    vMineReport.addAll(gameManager.damageEntity(entity, hit, cur_damage));
                }
                vMineReport.addAll(gameManager.resolvePilotingRolls(entity, true, lastPos, curPos));
                // we need to apply Damage now, in case the entity lost a leg,
                // otherwise it won't get a leg missing mod if it hasn't yet
                // moved and lost a leg, see bug 1071434 for an example
                entity.applyDamage();
            }

            // don't check for reduction until the end or units in the same hex
            // through
            // movement will get the reduced damage
            if (mf.hasDetonated()) {
                boom = true;
                mf.checkReduction(0, true);
                revealMinefield(mf);
            }

        }
        return boom;
    }

    /**
     * Removes the minefield from the game.
     *
     * @param mf The <code>Minefield</code> to remove
     */
    public void removeMinefield(Minefield mf) {
        if (gameManager.getGame().containsVibrabomb(mf)) {
            gameManager.getGame().removeVibrabomb(mf);
        }
        gameManager.getGame().removeMinefield(mf);

        Enumeration<Player> players = gameManager.getGame().getPlayers();
        while (players.hasMoreElements()) {
            Player player = players.nextElement();
            removeMinefield(player, mf);
        }
    }

    /**
     * Removes the minefield from a player.
     *
     * @param player The <code>Player</code> whose minefield should be removed
     * @param mf     The <code>Minefield</code> to be removed
     */
    public void removeMinefield(Player player, Minefield mf) {
        if (player.containsMinefield(mf)) {
            player.removeMinefield(mf);
            gameManager.send(player.getId(), new Packet(PacketCommand.REMOVE_MINEFIELD, mf));
        }
    }

    /**
     * Reveals a minefield for all players.
     *
     * @param mf The <code>Minefield</code> to be revealed
     */
    public void revealMinefield(Minefield mf) {
        gameManager.getGame().getTeams().forEach(team -> revealMinefield(team, mf));
    }

    /**
     * Reveals a minefield for all players on a team.
     *
     * @param team The <code>team</code> whose minefield should be revealed
     * @param mf   The <code>Minefield</code> to be revealed
     */
    public void revealMinefield(Team team, Minefield mf) {
        for (Player player : team.players()) {
            if (!player.containsMinefield(mf)) {
                player.addMinefield(mf);
                gameManager.send(player.getId(), new Packet(PacketCommand.REVEAL_MINEFIELD, mf));
            }
        }
    }

    /**
     * Reveals a minefield for a specific player
     * If on a team, does it for the whole team. Otherwise, just the player.
     */
    public void revealMinefield(Player player, Minefield mf) {
        Team team = gameManager.getGame().getTeamForPlayer(player);

        if (team != null) {
            revealMinefield(team, mf);
        } else {
            if (!player.containsMinefield(mf)) {
                player.addMinefield(mf);
                gameManager.send(player.getId(), new Packet(PacketCommand.REVEAL_MINEFIELD, mf));
            }
        }
    }

    /**
     * checks whether a newly set mine should be revealed to players based on
     * LOS. If so, then it reveals the mine
     */
    public void checkForRevealMinefield(Minefield mf, Entity layer) {
        // loop through each team and determine if they can see the mine, then
        // loop through players on team
        // and reveal the mine
        for (Team team : gameManager.getGame().getTeams()) {
            boolean canSee = false;

            // the players own team can always see the mine
            if (team.equals(gameManager.getGame().getTeamForPlayer(gameManager.getGame().getPlayer(mf.getPlayerId())))) {
                canSee = true;
            } else {
                // need to loop through all entities on this team and find the
                // one with the best shot of seeing
                // the mine placement
                int target = Integer.MAX_VALUE;
                Iterator<Entity> entities = gameManager.getGame().getEntities();
                while (entities.hasNext()) {
                    Entity en = entities.next();
                    // are we on the right team?
                    if (!team.equals(gameManager.getGame().getTeamForPlayer(en.getOwner()))) {
                        continue;
                    }
                    if (LosEffects.calculateLOS(gameManager.getGame(), en,
                            new HexTarget(mf.getCoords(), Targetable.TYPE_HEX_CLEAR)).canSee()) {
                        target = 0;
                        break;
                    }
                    LosEffects los = LosEffects.calculateLOS(gameManager.getGame(), en, layer);
                    if (los.canSee()) {
                        // TODO : need to add mods
                        ToHitData current = new ToHitData(4, "base");
                        current.append(Compute.getAttackerMovementModifier(gameManager.getGame(), en.getId()));
                        current.append(Compute.getTargetMovementModifier(gameManager.getGame(), layer.getId()));
                        current.append(los.losModifiers(gameManager.getGame()));
                        if (current.getValue() < target) {
                            target = current.getValue();
                        }
                    }
                }

                if (Compute.d6(2) >= target) {
                    canSee = true;
                }
            }
            if (canSee) {
                revealMinefield(team, mf);
            }
        }
    }
}