package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;
import java.util.ArrayList;
import java.util.List;

public class TestSimulator {

    public static void main(String[] args) {
        System.out.println("=== Initialisation du PokerBrain Simulator ===");
        
        // 1. Génération de la Lookup Table (Optimisée par nombres premiers)
        long startTime = System.currentTimeMillis();
        int[] lookupTable = LookupTableGenerator.generate();
        long endTime = System.currentTimeMillis();
        System.out.println("Lookup Table générée en : " + (endTime - startTime) + " ms");

        // 2. Définition d'une situation de jeu (Hero vs 1 Adversaire)
        // Hero: Ad Ah
        List<Card> heroHole = new ArrayList<>();
        heroHole.add(new Card(Rank.ACE, Suit.DIAMONDS));
        heroHole.add(new Card(Rank.TWO, Suit.HEARTS));

        // Board (Flop): Kd 7s 2c
        List<Card> board = new ArrayList<>();
        board.add(new Card(Rank.KING, Suit.DIAMONDS));
        board.add(new Card(Rank.SEVEN, Suit.SPADES));
        board.add(new Card(Rank.TWO, Suit.CLUBS));

        System.out.println("Main du Hero : " + heroHole);
        System.out.println("Board actuel : " + board);

        // 3. Lancement du calcul d'équité (Monocœur direct et sans allocation)
        MonteCarloEngine engine = new MonteCarloEngine(lookupTable);
        
        int numOpponents = 2; // Test en Heads-Up (1 contre numOpp)
        int maxIterations = 100000; // La marge de 0.5% sera atteinte bien avant
        
        System.out.println("Calcul en cours (Monte-Carlo adaptatif monocœur)...");
        startTime = System.currentTimeMillis();
        
        MonteCarloEngine.EquityResult result = engine.calculateEquity(heroHole, board, maxIterations, numOpponents);        
        endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        // 4. Affichage des résultats
        System.out.println("\n--- RÉSULTATS ---");
        System.out.println(result.toString());
        System.out.println("Temps d'exécution : " + executionTime + " ms");
        
        // Vérification de la contrainte de performance (On vise l'ultra-rapide maintenant)
        if (executionTime < 50) {
            System.out.println("Vérification : PERFORMANCE OK (Temps réel validé)");
        } else {
            System.out.println("Vérification : PERFORMANCE À OPTIMISER (Au-dessus de 50ms)");
        }
    }
}