package megamek.server.victory;

import megamek.common.Player;
import megamek.common.Team;
import megamek.server.manager.EloManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EloManagerTest {

    /**
     * Teste l'ajout d'un joueur avec le Elo par défaut.
     */
    @Test
    public void testAjoutJoueurAvecEloParDefaut() {
        EloManager eloManager = new EloManager();

        // Création des joueurs Ramz et Rama
        Player ramz = new Player(0, "Ramz");
        Player rama = new Player(1, "Rama");

        // Ajout des joueurs au gestionnaire Elo
        eloManager.addPlayer(ramz);
        eloManager.addPlayer(rama);

        // Vérification que les joueurs ont bien le Elo par défaut (800)
        assertEquals(1500, ramz.getElo());
        assertEquals(1500, rama.getElo());
    }

    /**
     * Teste la mise à jour des classements Elo après une série de matchs où chaque équipe gagne une fois.
     * Vérifie que les Elo restent approximativement les mêmes.
     */
    @Test
    public void testMiseAJourEloApresVictoiresDesEquipes_EloResteApproximativementLeMeme() {
        EloManager eloManager = new EloManager();

        // Création des joueurs Ramz et Rama
        Player ramz = new Player(0, "Ramz");
        Player rama = new Player(1, "Rama");

        // Attribution d'adresses IP pour chaque joueur
        ramz.setIp("192.168.2.1");
        rama.setIp("192.168.2.2");

        // Ajout des joueurs au gestionnaire Elo
        eloManager.addPlayer(ramz);
        eloManager.addPlayer(rama);

        // Création de deux équipes avec les joueurs
        Team ramzTeam = new Team(0);
        Team ramaTeam = new Team(1);
        ramzTeam.addPlayer(ramz);
        ramaTeam.addPlayer(rama);

        // Simulation de deux matchs où chaque équipe gagne une fois
        Team[] teams = new Team[] { ramzTeam, ramaTeam };
        eloManager.updateRankings(teams, 1);
        eloManager.updateRankings(teams, 2);

        // Vérification que les Elo des joueurs restent approximativement les mêmes
        assertEquals(1501,ramz.getElo());
        assertEquals(1499, rama.getElo());
    }

    /**
     * Teste le scénario où un joueur gagne un match, quitte le jeu puis se reconnecte,
     * et vérifie que son Elo reste inchangé après la reconnexion.
     */
    @Test
    public void testVictoireJoueur1_QuitteLeJeu_GardeEloApresReconnexion() {
        EloManager eloManager = new EloManager();

        // Création des joueurs Ramz et Rama
        Player ramz = new Player(0, "Ramz");
        Player rama = new Player(1, "Rama");

        // Attribution d'adresses IP pour chaque joueur
        ramz.setIp("192.168.2.1");
        rama.setIp("192.168.2.2");

        // Ajout des joueurs au gestionnaire Elo
        eloManager.addPlayer(ramz);
        eloManager.addPlayer(rama);

        // Création de deux équipes avec les joueurs
        Team ramzTeam = new Team(0);
        Team ramaTeam = new Team(1);
        ramzTeam.addPlayer(ramz);
        ramaTeam.addPlayer(rama);

        // Simulation d'un match où l'équipe 1 (à laquelle appartient Ramz) gagne
        Team[] teams = new Team[] { ramzTeam, ramaTeam };
        eloManager.updateRankings(teams, 1);

        // Simulation de Ramz quittant le jeu puis se reconnectant
        Player ramzReconnect = new Player(0, "Ramz");
        ramzReconnect.setIp("192.168.2.1");
        eloManager.addPlayer(ramzReconnect);

        // Vérification que Ramz garde le même Elo après s'être reconnecté
        assertEquals(1516,ramzReconnect.getElo());
    }

    /**
     * Teste la mise à jour des classements Elo après une victoire de l'équipe 1.
     * Vérifie que l'équipe 1 gagne des points Elo et que l'équipe 2 en perd.
     */
    @Test
    public void testSimulationMatch_VictoireEquipe1_Equipe1GagneDesPointsEloEtEquipe2EnPerd() {
        EloManager eloManager = new EloManager();

        // Création des joueurs Ramz et Rama
        Player ramz = new Player(0, "Ramz");
        Player rama = new Player(1, "Rama");

        // Attribution d'adresses IP pour chaque joueur
        ramz.setIp("192.168.2.1");
        rama.setIp("192.168.2.2");

        // Ajout des joueurs au gestionnaire Elo
        eloManager.addPlayer(ramz);
        eloManager.addPlayer(rama);

        // Création de deux équipes avec les joueurs
        Team ramzTeam = new Team(0);
        Team ramaTeam = new Team(1);
        ramzTeam.addPlayer(ramz);
        ramaTeam.addPlayer(rama);

        // Simulation d'un match où l'équipe 1 (à laquelle appartient Ramz) gagne
        Team[] teams = new Team[] { ramzTeam, ramaTeam };
        eloManager.updateRankings(teams, 1);

        // Vérification que les Elo des joueurs sont mis à jour correctement après la victoire de l'équipe 1
        assertEquals(1516, ramz.getElo().intValue());
        assertEquals(1484, rama.getElo().intValue());

    }    }
