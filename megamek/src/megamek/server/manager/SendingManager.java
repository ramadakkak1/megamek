package megamek.server.manager;

import megamek.common.Player;
import megamek.common.net.packets.Packet;
import megamek.server.Server;

public class SendingManager {
    public SendingManager() {
    }

    public void send(Packet p) {
        Server.getServerInstance().send(p);
    }

    public void send(int connId, Packet p) {
        Server.getServerInstance().send(connId, p);
    }

    public void transmitPlayerUpdate(Player p) {
        Server.getServerInstance().transmitPlayerUpdate(p);
    }

    public void sendServerChat(String message) {
        Server.getServerInstance().sendServerChat(message);
    }

    public void sendServerChat(int connId, String message) {
        Server.getServerInstance().sendServerChat(connId, message);
    }

    public void sendChat(String origin, String message) {
        Server.getServerInstance().sendChat(origin, message);
    }

    public void sendChat(int connId, String origin, String message) {
        Server.getServerInstance().sendChat(connId, origin, message);
    }
}