package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;
import java.util.List;

public class TestSimulator {

    public static void main(String[] args) {
        System.out.println("=== Initialisation du PokerBrain Simulator ===");

        List<Card> heroHole = List.of(
            new Card(Rank.ACE, Suit.DIAMONDS),
            new Card(Rank.TWO, Suit.HEARTS)
        );
        List<Card> board = List.of(
            new Card(Rank.KING, Suit.DIAMONDS),
            new Card(Rank.SEVEN, Suit.SPADES),
            new Card(Rank.TWO, Suit.CLUBS)
        );

        System.out.println("Main du Hero : " + heroHole);
        System.out.println("Board actuel : " + board);

        MonteCarloEngine engine = new MonteCarloEngine();

        int numOpponents = 8;
        int maxIterations = 100000;
        System.out.println("Nombre d'adversaires: " + numOpponents);

        System.out.println("Calcul en cours (Monte-Carlo adaptatif monocœur)...");
        long startTime = System.currentTimeMillis();

        MonteCarloEngine.EquityResult result = engine.calculateEquity(heroHole, board, maxIterations, numOpponents);
        long executionTime = System.currentTimeMillis() - startTime;

        System.out.println("\n--- RÉSULTATS ---");
        System.out.println(result.toString());
        System.out.println("Temps d'exécution : " + executionTime + " ms");

        if (executionTime < 50) {
            System.out.println("Vérification : PERFORMANCE OK (Temps réel validé)");
        } else {
            System.out.println("Vérification : PERFORMANCE À OPTIMISER (Au-dessus de 50ms)");
        }
    }
}
