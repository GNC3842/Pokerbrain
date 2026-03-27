package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;
import java.util.ArrayList;
import java.util.List;

public class TestSimulator {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		        System.out.println("=== Initialisation du PokerBrain Simulator ===");
		        
		        // 1. Génération de la Lookup Table (Optimisée par nombres premiers)
		        long startTime = System.currentTimeMillis();
		        int[] lookupTable = LookupTableGenerator.generate();
		        long endTime = System.currentTimeMillis();
		        System.out.println("Lookup Table générée en : " + (endTime - startTime) + " ms");

		        // 2. Définition d'une situation de jeu (Ex: Paire d'As contre main aléatoire)
		        // Hero: Ad Ah
		        List<Card> heroHole = new ArrayList<>();
		        heroHole.add(new Card(Rank.ACE, Suit.DIAMONDS));
		        heroHole.add(new Card(Rank.ACE, Suit.HEARTS));

		        // Board (Flop): Kd 7s 2c
		        List<Card> board = new ArrayList<>();
		        board.add(new Card(Rank.KING, Suit.DIAMONDS));
		        board.add(new Card(Rank.SEVEN, Suit.SPADES));
		        board.add(new Card(Rank.TWO, Suit.CLUBS));

		        System.out.println("Main du Hero : " + heroHole);
		        System.out.println("Board actuel : " + board);

		        // 3. Lancement du calcul d'équité parallèle
		        ParallelEquityCalculator calculator = new ParallelEquityCalculator(lookupTable);
		        
		        try {
		            System.out.println("Calcul en cours (Monte-Carlo adaptatif)...");
		            startTime = System.currentTimeMillis();
		            
		            // On demande 100 000 itérations max, le moteur s'arrêtera avant si 95% de confiance est atteint
		            MonteCarloEngine.EquityResult result = calculator.calculate(heroHole, board, 100_000);
		            
		            endTime = System.currentTimeMillis();
		            
		            // 4. Affichage des résultats
		            System.out.println("\n--- RÉSULTATS ---");
		            System.out.println(result.toString());
		            System.out.println("Temps d'exécution : " + (endTime - startTime) + " ms");
		            
		            // Vérification de la contrainte de performance (< 2 secondes)
		            if ((endTime - startTime) < 2000) {
		                System.out.println("Vérification : PERFORMANCE OK [cite: 40]");
		            } else {
		                System.out.println("Vérification : PERFORMANCE À OPTIMISER [cite: 40]");
		            }

		        } catch (Exception e) {
		            e.printStackTrace();
		        } finally {
		            calculator.shutdown();
		        }
		    }
		
}


