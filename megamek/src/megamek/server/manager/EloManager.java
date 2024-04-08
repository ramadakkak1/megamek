package megamek.server.manager;

import megamek.common.Player;
import megamek.common.Team;
import java.util.*;

/**
 * Gère le système Elo pour les joueurs.
 */
public class EloManager {
    // Constante pour l'Elo initial(cette valeur est arbitraire, selon le
    //jeu d'échec, c'est 1500)
    private final int INITIAL_ELO = 1500;

    // Map pour stocker les classements Elo des joueurs
    // La clé est une clé composite de l'adresse IP du joueur et s'il s'agit d'un bot.
    // La valeur est l'Elo.
    private final Map<String, Integer> eloRanking = new HashMap<>();

    /**
     * Ajoute un joueur au classement Elo.
     * @param player Le joueur à ajouter.
     */
    public void addPlayer(Player player) {
        String key = createCompositeKey(player.getIp(), player.isBot());
        Integer elo = eloRanking.getOrDefault(key, INITIAL_ELO);
        player.setElo(elo);
    }

    /**
     * Met à jour les classements Elo après un match.
     * @param teams Les équipes participant au match.
     * @param winningTeam L'équipe gagnante du match.
     */
    public void updateRankings(Team[] teams, int winningTeam) {
        if (teams.length > 2 || winningTeam > 2)
            return; // NON PRIS EN CHARGE, SEULEMENT SUPPORTÉ POUR 2 ÉQUIPES.

        int eloGain = calculateEloGain(teams);

        if (winningTeam == 1) {
            updateTeamRanking(teams[0], eloGain);
            updateTeamRanking(teams[1], -eloGain);
        }

        if (winningTeam == 2) {
            updateTeamRanking(teams[0], -eloGain);
            updateTeamRanking(teams[1], eloGain);
        }
    }

    /**
     * Met à jour le classement Elo d'une équipe.
     * @param team L'équipe à mettre à jour.
     * @param elo Le changement de Elo.
     */
    /**
     * Met à jour le classement Elo de tous les joueurs d'une équipe en fonction du gain ou de la perte d'Elo spécifié.
     * @param team L'équipe dont les classements Elo doivent être mis à jour.
     * @param elo Le gain ou la perte d'Elo à appliquer.
     */
    private void updateTeamRanking(Team team, int elo) {
        List<Player> teamPlayers = team.players(); // Obtention de la liste des joueurs de l'équipe

        for (Player player : teamPlayers) {
            // Création de la clé composite
            String key = createCompositeKey(player.getIp(), player.isBot());

            // Calcul du nouveau Elo du joueur
            int newElo = player.getElo() + elo;

            // Mise à jour du Elo du joueur
            player.setElo(newElo);

            // Mise à jour du classement Elo
            eloRanking.put(key, newElo);
        }
    }

    /**
     * Calcule le gain de Elo pour une équipe.
     * @param teams Les équipes participant au match.
     * @return Le gain de Elo.
     */
    private Integer calculateEloGain(Team[] teams) {
        if (teams.length > 2)
            return 0; // NON PRIS EN CHARGE, SEULEMENT SUPPORTÉ POUR 2 ÉQUIPES.

        int eq1AvrgElo = teams[0].calculateAverageElo();
        int eq2AvrgElo = teams[1].calculateAverageElo();

        // Calcul de l'issue attendue
        double exponent = (double) (eq2AvrgElo - eq1AvrgElo) / 400;
        double expectedOutcome = (1 / (1 + (Math.pow(10, exponent))));

        // Facteur K
        int K = 32;

        int eloGain = (int) Math.round(K * (1 - expectedOutcome));
        return eloGain;
    }

    /**
     * Crée une clé composite à partir de l'adresse IP et du statut de bot.
     * @param ip L'adresse IP du joueur.
     * @param isBot Le statut de bot.
     * @return La clé composite.
     */
    private String createCompositeKey(String ip, boolean isBot) {
        return isBot ? ip + "_true" : ip + "_false";
    }
}
