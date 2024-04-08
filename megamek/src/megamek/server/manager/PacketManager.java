package megamek.server.manager;

import megamek.common.*;
import megamek.common.actions.EntityAction;
import megamek.common.force.Force;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;

import java.util.List;
import java.util.Set;
import java.util.Vector;

public class PacketManager {
    private final GameManager gameManager;

    public PacketManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * Mark the unit as destroyed! Units transported in the destroyed unit will
     * get a chance to escape.
     *
     * @param entity - the <code>Entity</code> that has been destroyed.
     * @param reason - a <code>String</code> detailing why the entity was
     *               destroyed.
     * @return a <code>Vector</code> of <code>Report</code> objects that can be
     * sent to the output log.
     */
    public Vector<Report> destroyEntity(Entity entity, String reason) {
        return gameManager.destroyEntity(entity, reason, true);
    }

    /**
     * Creates a packet detailing the removal of a list of entities.
     *
     * @param entityIds      - the <code>int</code> ID of each entity being removed.
     * @param condition      - the <code>int</code> condition the units were in. This value
     *                       must be one of constants in
     *                       <code>IEntityRemovalConditions</code>, or an
     *                       <code>IllegalArgumentException</code> will be thrown.
     * @param affectedForces - a list of forces that are affected by the removal and
     *                       must be updated
     * @return A <code>Packet</code> to be sent to clients.
     */
    public Packet createRemoveEntityPacket(List<Integer> entityIds, List<Force> affectedForces, int condition) {
        if ((condition != IEntityRemovalConditions.REMOVE_UNKNOWN)
                && (condition != IEntityRemovalConditions.REMOVE_IN_RETREAT)
                && (condition != IEntityRemovalConditions.REMOVE_PUSHED)
                && (condition != IEntityRemovalConditions.REMOVE_SALVAGEABLE)
                && (condition != IEntityRemovalConditions.REMOVE_EJECTED)
                && (condition != IEntityRemovalConditions.REMOVE_CAPTURED)
                && (condition != IEntityRemovalConditions.REMOVE_DEVASTATED)
                && (condition != IEntityRemovalConditions.REMOVE_NEVER_JOINED)) {
            throw new IllegalArgumentException("Unknown unit condition: " + condition);
        }

        return new Packet(PacketCommand.ENTITY_REMOVE, entityIds, condition, affectedForces);
    }

    /**
     * Creates a packet containing a hex, and the coordinates it goes at.
     */
    public Packet createHexChangePacket(Coords coords, Hex hex) {
        return new Packet(PacketCommand.CHANGE_HEX, coords, hex);
    }

    /**
     * Creates a packet containing a hex, and the coordinates it goes at.
     */
    public Packet createHexesChangePacket(Set<Coords> coords, Set<Hex> hex) {
        return new Packet(PacketCommand.CHANGE_HEXES, coords, hex);
    }

    /**
     * Creates a packet for an attack
     */
    public Packet createAttackPacket(List<?> vector, int charges) {
        return new Packet(PacketCommand.ENTITY_ATTACK, vector, charges);
    }

    /**
     * Creates a packet for an attack
     */
    public Packet createAttackPacket(EntityAction ea, int charge) {
        Vector<EntityAction> vector = new Vector<EntityAction>(1);
        vector.addElement(ea);
        return new Packet(PacketCommand.ENTITY_ATTACK, vector, charge);
    }
}