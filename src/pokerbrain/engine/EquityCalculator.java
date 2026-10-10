package pokerbrain.engine;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Deck;
import pokerbrain.simulator.CardPrimitiveBridge;
import pokerbrain.simulator.FastHandEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

/**
 * Calcule l'équité <b>exacte</b> hero vs villain par énumération de toutes les
 * completions possibles du board parmi les cartes restantes.
 * La boucle est parallélisée sur tous les cœurs disponibles.
 *
 * <p>L'évaluation passe par {@link FastHandEvaluator} (classe canonique CactusKev 1-7462,
 * la plus petite étant la plus forte). L'ancienne version appelait l'évaluateur objet et
 * allouait, par combinaison, deux {@code Card[]} puis deux {@code HandRank} — eux-mêmes
 * construits sur une {@code ArrayList} et une {@code LinkedHashMap}. Sur un preflop
 * (1 712 304 combinaisons) cela faisait plusieurs millions d'objets.
 *
 * <p>Résultat : double[3] = { heroWin%, villainWin%, tie% }, de somme 100.
 */
public final class EquityCalculator {

    private EquityCalculator() {}

    public static double[] calculate(List<Card> heroHand, List<Card> villainHand, List<Card> board) {
        if (heroHand == null || heroHand.size() != 2 || villainHand == null || villainHand.size() != 2) {
            throw new IllegalArgumentException("Chaque joueur doit avoir exactement 2 cartes");
        }
        if (board == null || board.size() > 5) {
            throw new IllegalArgumentException("Board invalide : " + (board == null ? "null" : board.size()));
        }

        // 1. Retirer les cartes connues du deck
        List<Card> known = new ArrayList<>(heroHand);
        known.addAll(villainHand);
        known.addAll(board);

        Deck deck = new Deck();
        deck.removeCards(known);
        List<Card> remainingCards = deck.getRemainingCards();

        final int[] remaining = new int[remainingCards.size()];
        for (int i = 0; i < remaining.length; i++) {
            remaining[i] = CardPrimitiveBridge.encode(remainingCards.get(i));
        }

        final int boardSize = board.size();
        final int needed = 5 - boardSize;
        final int n = remaining.length;

        // Cartes fixes pré-encodées, une fois pour toutes : [0-1] = main, [2…] = board connu
        final int[] fixedHero = new int[7];
        final int[] fixedVillain = new int[7];
        fixedHero[0] = CardPrimitiveBridge.encode(heroHand.get(0));
        fixedHero[1] = CardPrimitiveBridge.encode(heroHand.get(1));
        fixedVillain[0] = CardPrimitiveBridge.encode(villainHand.get(0));
        fixedVillain[1] = CardPrimitiveBridge.encode(villainHand.get(1));
        for (int i = 0; i < boardSize; i++) {
            int enc = CardPrimitiveBridge.encode(board.get(i));
            fixedHero[2 + i] = enc;
            fixedVillain[2 + i] = enc;
        }

        // 2. Board complet : une seule comparaison
        if (needed == 0) {
            FastHandEvaluator evaluator = new FastHandEvaluator();
            int hero = evaluator.evaluate7(fixedHero);
            int villain = evaluator.evaluate7(fixedVillain);
            if (hero < villain) return new double[]{100.0, 0.0, 0.0};
            if (hero > villain) return new double[]{0.0, 100.0, 0.0};
            return new double[]{0.0, 0.0, 100.0};
        }

        // 3. Énumération parallélisée sur le premier indice
        LongAdder heroWins = new LongAdder(), villainWins = new LongAdder(), ties = new LongAdder();

        IntStream.range(0, n - needed + 1).parallel().forEach(first -> {
            // État local à chaque tâche : FastHandEvaluator porte un tampon d'instance
            FastHandEvaluator evaluator = new FastHandEvaluator();
            int[] hero = fixedHero.clone();
            int[] villain = fixedVillain.clone();

            int[] idx = new int[needed];
            idx[0] = first;
            for (int k = 1; k < needed; k++) idx[k] = first + k;

            long won = 0, lost = 0, tied = 0;

            while (true) {
                for (int k = 0; k < needed; k++) {
                    int enc = remaining[idx[k]];
                    hero[2 + boardSize + k] = enc;
                    villain[2 + boardSize + k] = enc;
                }

                int h = evaluator.evaluate7(hero);
                int v = evaluator.evaluate7(villain);
                if      (h < v) won++;
                else if (h > v) lost++;
                else            tied++;

                // Combinaison suivante avec idx[0] fixé
                int i = needed - 1;
                while (i > 0 && idx[i] == n - needed + i) i--;
                if (i == 0) break;
                idx[i]++;
                for (int j = i + 1; j < needed; j++) idx[j] = idx[j - 1] + 1;
            }

            // Un seul report par tâche plutôt qu'un par combinaison
            heroWins.add(won);
            villainWins.add(lost);
            ties.add(tied);
        });

        double total = heroWins.sum() + villainWins.sum() + ties.sum();
        if (total == 0) {
            throw new IllegalStateException("Aucune combinaison énumérable : deck épuisé");
        }
        return new double[]{
            heroWins.sum()    / total * 100.0,
            villainWins.sum() / total * 100.0,
            ties.sum()        / total * 100.0
        };
    }
}
