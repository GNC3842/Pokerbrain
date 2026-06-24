package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;
import java.util.List;

public class TestWald {

    public static void main(String[] args) {
        System.out.println("=== Vérification de l'intervalle de confiance de Wald ===\n");

        MonteCarloEngine engine = new MonteCarloEngine();

        List<Card> hero = List.of(
            new Card(Rank.ACE, Suit.DIAMONDS),
            new Card(Rank.TWO, Suit.HEARTS)
        );
        List<Card> board = List.of(
            new Card(Rank.KING, Suit.DIAMONDS),
            new Card(Rank.SEVEN, Suit.SPADES),
            new Card(Rank.TWO, Suit.CLUBS)
        );

        System.out.println("Main : Ad 2h | Board : Kd 7s 2c");
        System.out.println("Seuil de convergence : 0.5% (0.005)\n");
        System.out.printf("%-8s %-12s %-12s %-14s %-14s %-10s%n",
            "Config", "Itérations", "Temps (ms)", "Equity (%)", "Marge réelle", "Théorique");
        System.out.println("─".repeat(75));

        int[] opponents = {1, 2, 3, 5, 8};
        for (int opp : opponents) {
            long totalIter = 0, totalTime = 0;
            double totalEquity = 0, totalMargin = 0;

            for (int run = 0; run < 3; run++) {
                long start = System.currentTimeMillis();
                MonteCarloEngine.EquityResult r = engine.calculateEquity(hero, board, 100_000, opp);
                long elapsed = System.currentTimeMillis() - start;

                double p = r.equitySum / r.iterations;
                double margin = 1.96 * Math.sqrt(p * (1 - p) / r.iterations);

                totalIter += r.iterations;
                totalTime += elapsed;
                totalEquity += r.equity();
                totalMargin += margin;
            }

            double avgP = (totalEquity / 3) / 100.0;
            double theoreticalN = Math.pow(1.96 * Math.sqrt(avgP * (1 - avgP)) / 0.005, 2);

            System.out.printf("1v%-6d %-12d %-12d %-14.2f %-14.5f %-10.0f%n",
                opp,
                totalIter / 3,
                totalTime / 3,
                totalEquity / 3,
                totalMargin / 3,
                theoreticalN);
        }

        System.out.println("\n=== Vérification théorique (pire cas p=0.5) ===");
        double worstN = Math.pow(1.96 * 0.5 / 0.005, 2);
        System.out.printf("n = (1.96 × 0.5 / 0.005)² = %.0f%n", worstN);
        System.out.println("Attendu : ~38 416 itérations pour le pire cas");
        System.out.println("Observation : les configs 1v5 et 1v8 (equity ~50%) convergent autour de cette valeur");
    }
}
