package pokerbrain.engine;

import pokerbrain.model.core.*;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

/**
 * Calcule l'équité exacte hero vs villain par énumération de toutes les
 * completions possibles du board parmi les cartes restantes.
 * La boucle est parallélisée sur tous les cœurs disponibles.
 *
 * Résultat : double[3] = { heroWin%, villainWin%, tie% }
 */
public final class EquityCalculator {

    private EquityCalculator() {}

    public static double[] calculate(List<Card> heroHand, List<Card> villainHand, List<Card> board) {

        // 1. Retirer les cartes connues du deck
        List<Card> known = new ArrayList<>(heroHand);
        known.addAll(villainHand);
        known.addAll(board);

        Deck deck = new Deck();
        deck.removeCards(known);
        Card[] remaining = deck.getRemainingCards().toArray(new Card[0]);

        int needed = 5 - board.size();
        int n = remaining.length;

        // Cartes fixes (hole cards + board déjà posé)
        Card h0 = heroHand.get(0),    h1 = heroHand.get(1);
        Card v0 = villainHand.get(0), v1 = villainHand.get(1);
        Card[] boardArr = board.toArray(new Card[0]);

        // 2. Board complet : résultat immédiat
        if (needed == 0) {
            Card[] hFull = buildHand(h0, h1, boardArr, new Card[0]);
            Card[] vFull = buildHand(v0, v1, boardArr, new Card[0]);
            int cmp = eval(hFull).compareTo(eval(vFull));
            if (cmp > 0) return new double[]{100.0, 0.0, 0.0};
            if (cmp < 0) return new double[]{0.0, 100.0, 0.0};
            return new double[]{50.0, 50.0, 100.0};
        }

        // 3. Enumération parallélisée sur le premier indice
        LongAdder heroWins = new LongAdder(), villainWins = new LongAdder(), ties = new LongAdder();

        IntStream.range(0, n - needed + 1).parallel().forEach(first -> {
            // Tableaux locaux à chaque thread — pas de partage d'état
            int[] idx = new int[needed];
            idx[0] = first;
            for (int k = 1; k < needed; k++) idx[k] = first + k;

            while (true) {
                Card[] extra = new Card[needed];
                for (int k = 0; k < needed; k++) extra[k] = remaining[idx[k]];

                Card[] hCards = buildHand(h0, h1, boardArr, extra);
                Card[] vCards = buildHand(v0, v1, boardArr, extra);
                int cmp = eval(hCards).compareTo(eval(vCards));
                if      (cmp > 0) heroWins.increment();
                else if (cmp < 0) villainWins.increment();
                else              ties.increment();

                // Combinaison suivante avec idx[0] fixé
                int i = needed - 1;
                while (i > 0 && idx[i] == n - needed + i) i--;
                if (i == 0) break;
                idx[i]++;
                for (int j = i + 1; j < needed; j++) idx[j] = idx[j - 1] + 1;
            }
        });

        double total = heroWins.sum() + villainWins.sum() + ties.sum();
        return new double[]{
            heroWins.sum()    / total * 100.0,
            villainWins.sum() / total * 100.0,
            ties.sum()        / total * 100.0
        };
    }

    private static Card[] buildHand(Card h0, Card h1, Card[] board, Card[] extra) {
        Card[] hand = new Card[2 + board.length + extra.length];
        hand[0] = h0; hand[1] = h1;
        System.arraycopy(board, 0, hand, 2, board.length);
        System.arraycopy(extra, 0, hand, 2 + board.length, extra.length);
        return hand;
    }

    private static HandRank eval(Card[] cards) {
        return HandEvaluator.evaluateBestHand(Arrays.asList(cards));
    }
}
