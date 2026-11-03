package pokerbrain.analyser;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Suit;

import java.util.List;

/**
 * Compte les outs du héro sur un tirage (couleur et quinte).
 *
 * Un « out » est une carte qui améliore la main vers une couleur ou une quinte faite.
 * On ne compte que les tirages auxquels les cartes fermées du héro participent : un
 * tirage entièrement sur le board est partagé par toute la table et ne vaut rien.
 *
 * Les tirages de paire/brelan ne sont pas comptés : leur valeur est déjà prise en
 * compte par l'équité Monte-Carlo. Les outs servent ici à distinguer un bluff sec
 * d'un semi-bluff, et à calculer la fold equity nécessaire.
 */
final class DrawAnalyser {

    static final int FLUSH_DRAW_OUTS = 9;

    private DrawAnalyser() {}

    //Résultat de l'analyse de tirage
    static final class Draws {
        final int flushOuts;
        final int straightOuts;

        Draws(int flushOuts, int straightOuts) {
            this.flushOuts = flushOuts;
            this.straightOuts = straightOuts;
        }

        //Outs totaux, plafonnés : couleur et quinte partagent souvent des cartes
        int total() {
            return Math.min(flushOuts + straightOuts, 15);
        }

        boolean hasFlushDraw()    { return flushOuts > 0; }
        //Un tirage quinte bilatéral vaut 8 outs, un ventre 4
        boolean hasOpenEnded()    { return straightOuts >= 8; }

        String describe() {
            if (flushOuts > 0 && straightOuts > 0) return "tirage couleur + quinte";
            if (flushOuts > 0) return "tirage couleur";
            if (straightOuts >= 8) return "tirage quinte bilatéral";
            if (straightOuts > 0) return "tirage quinte par le ventre";
            return "pas de tirage";
        }
    }

    static Draws analyse(List<Card> hole, List<Card> board) {
        if (hole == null || hole.size() != 2 || board == null || board.isEmpty()) {
            return new Draws(0, 0);
        }
        if (board.size() >= 5) {
            // Plus aucune carte à venir : un tirage n'a plus de valeur
            return new Draws(0, 0);
        }
        return new Draws(flushOuts(hole, board), straightOuts(hole, board));
    }

    //9 outs si exactement 4 cartes d'une couleur, dont au moins une carte fermée
    private static int flushOuts(List<Card> hole, List<Card> board) {
        for (Suit suit : Suit.values()) {
            int total = 0;
            int fromHole = 0;
            for (Card c : hole) {
                if (c.getSuit() == suit) { total++; fromHole++; }
            }
            for (Card c : board) {
                if (c.getSuit() == suit) total++;
            }
            if (total == 4 && fromHole >= 1) {
                return FLUSH_DRAW_OUTS;
            }
        }
        return 0;
    }

    /**
     * Outs de quinte : on teste les 10 fenêtres de 5 rangs consécutifs (A-5 à T-A).
     * Une fenêtre où 4 rangs sur 5 sont présents désigne un rang manquant, soit 4 outs.
     * Deux fenêtres incomplètes différentes = tirage bilatéral = 8 outs, une seule = ventre.
     * On exige que la fenêtre utilise au moins une carte fermée du héro.
     */
    private static int straightOuts(List<Card> hole, List<Card> board) {
        boolean[] present = new boolean[15]; // index = valeur du rang, 1 = As bas
        boolean[] fromHole = new boolean[15];

        for (Card c : board) {
            int v = c.getRank().getValue();
            present[v] = true;
            if (v == 14) present[1] = true;
        }
        for (Card c : hole) {
            int v = c.getRank().getValue();
            present[v] = true;
            fromHole[v] = true;
            if (v == 14) { present[1] = true; fromHole[1] = true; }
        }

        boolean[] isOut = new boolean[15];
        for (int start = 1; start <= 10; start++) {
            int count = 0;
            int missing = -1;
            boolean usesHole = false;
            for (int r = start; r < start + 5; r++) {
                if (present[r]) {
                    count++;
                    if (fromHole[r]) usesHole = true;
                } else {
                    missing = r;
                }
            }
            if (count == 5) {
                return 0; // quinte déjà faite, ce n'est plus un tirage
            }
            if (count == 4 && usesHole && missing > 0) {
                // Un As manquant se joue haut : c'est la même carte que le rang 1
                isOut[missing == 1 ? 14 : missing] = true;
            }
        }

        int distinctOuts = 0;
        for (int r = 2; r <= 14; r++) {
            if (isOut[r]) distinctOuts++;
        }
        return distinctOuts * 4;
    }
}
