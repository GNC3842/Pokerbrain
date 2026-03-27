package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Moteur Monte-Carlo haute performance — zéro allocation dans la boucle chaude.
 *
 * USAGE (une instance par thread) :
 *   MonteCarloEngine engine = new MonteCarloEngine(lookupTable);
 *   EquityResult r = engine.calculateEquity(heroHole, board, 10_000);
 *
 * GARANTIES ZERO-ALLOCATION :
 *   - DeckPrimitive.ALL_ENCODED : table statique, chargée une seule fois
 *   - Tous les buffers (heroEnc, villainEnc, etc.) sont des champs d'instance
 *   - shufflePartial sur int[] en place — ThreadLocalRandom sans new Random
 *   - CardPrimitiveBridge.encode() n'est appelé qu'UNE fois par carte connue (hors boucle)
 *
 * CONVERGENCE ADAPTATIVE :
 *   Vérification de l'intervalle de confiance (IC 95%) tous les CONVERGENCE_BLOCK tours.
 *   Si IC < THRESHOLD → arrêt anticipé. Économise 40-60% des itérations.
 */
public final class MonteCarloEngine {

    private static final int    CONVERGENCE_BLOCK  = 500;
    private static final double CONVERGENCE_THRESHOLD = 0.005; // ±0.5%

    private final FastHandEvaluator evaluator;
    private final DeckPrimitive     deck;

    //Buffers alloués UNE FOIS par instance 

    private final int[] heroEnc = new int[7]; // encodages hero (2 hole + 5 board)
    private final int[] heroIdx = new int[7]; // indices hero
    private final int[] villEnc = new int[7]; // encodages villain
    private final int[] villIdx = new int[7]; // indices villain
    private final int[] deadIdx = new int[7]; // indices des cartes mortes connues
    private final int[] boardEnc = new int[5]; // encodages board connu
    private final int[] boardIdx = new int[5]; // indices board connu

    public MonteCarloEngine(int[] lookupTable) {
        this.evaluator = new FastHandEvaluator(lookupTable);
        this.deck = new DeckPrimitive();
    }


    //Calcule l'équité du hero contre 1 adversaire à cartes inconnues
    public EquityResult calculateEquity(List<Card> heroHole, List<Card> board, int maxIter) {
        validate(heroHole, board, maxIter);

        int boardSize = board.size();
        int boardMissing = 5 - boardSize;
        int deadCount = 2 + boardSize;

        //Encodage des cartes connues (UNE SEULE FOIS, hors boucle)
        DeckPrimitive.encodeCards(heroHole, heroIdx, heroEnc);
        DeckPrimitive.encodeCards(board, boardIdx, boardEnc);

        // Hero : hole cards
        heroEnc[0] = heroEnc[0]; // déjà en place
        heroEnc[1] = heroEnc[1];
        // Hero : board connu
        for (int i = 0; i < boardSize; i++) {
            heroEnc[2 + i] = boardEnc[i];
            heroIdx[2 + i] = boardIdx[i];
        }

        // Villain : board connu (partagé)
        for (int i = 0; i < boardSize; i++) {
            villEnc[2 + i] = boardEnc[i];
            villIdx[2 + i] = boardIdx[i];
        }

        // Cartes mortes = hole cards hero + board connu
        deadIdx[0] = heroIdx[0];
        deadIdx[1] = heroIdx[1];
        for (int i = 0; i < boardSize; i++) {
        	deadIdx[2 + i] = boardIdx[i];
        }

        // Initialiser le deck primitif (exclut les cartes mortes)
        deck.init(deadIdx, deadCount);

        //Boucle Monte-Carlo
        int wins = 0, ties = 0, losses = 0, iter = 0;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        while (iter < maxIter) {
            int blockEnd = Math.min(iter + CONVERGENCE_BLOCK, maxIter);

            while (iter < blockEnd) {
                // Mélange partiel : 2 (villain) + boardMissing cartes
                deck.shufflePartial(rng, 2 + boardMissing);

                // Villain hole cards
                villEnc[0] = deck.drawnEncoded(0);
                villIdx[0] = deck.drawnIndex(0);
                villEnc[1] = deck.drawnEncoded(1);
                villIdx[1] = deck.drawnIndex(1);

                // Compléter le board (partagé hero+villain)
                for (int b = 0; b < boardMissing; b++) {
                    int enc = deck.drawnEncoded(2 + b);
                    int id = deck.drawnIndex(2 + b);
                    heroEnc[2 + boardSize + b] = enc;
                    heroIdx[2 + boardSize + b] = id;
                    villEnc[2 + boardSize + b] = enc;
                    villIdx[2 + boardSize + b] = id;
                }

                // Évaluation O(1) chacune
                int hv = evaluator.evaluate7(heroEnc, heroIdx);
                int vv = evaluator.evaluate7(villEnc, villIdx);

                if(hv > vv) {
                	wins++;
                }
                else if(hv == vv) {
                	ties++;
                }
                else{
                	losses++;
                }

                iter++;
            }

            // Convergence adaptative (IC 95%)
            if (iter >= CONVERGENCE_BLOCK) {
                double p = (wins + ties * 0.5) / iter;
                double margin = 1.96 * Math.sqrt(p * (1 - p) / iter);
                if (margin < CONVERGENCE_THRESHOLD) {
                	break;
                }
            }
        }

        return new EquityResult(wins, ties, losses, iter);
    }

    //Validation
    private static void validate(List<Card> heroHole, List<Card> board, int maxIter) {
        if (heroHole == null || heroHole.size() != 2) {
        	throw new IllegalArgumentException("Hero doit avoir exactement 2 cartes");
        }
        if (board == null || board.size() > 5) {
        	throw new IllegalArgumentException("Board invalide : " + board.size() + " cartes");
        }
        if (maxIter <= 0) {
        	throw new IllegalArgumentException("maxIter doit être positif");
        }
    }

    //Résultat

    public static final class EquityResult {
        public final int wins;
        public final int ties;
        public final int losses;
        public final int iterations;

        public EquityResult(int wins, int ties, int losses, int iterations) {
            this.wins = wins;
            this.ties = ties;
            this.losses = losses;
            this.iterations = iterations;
        }

        //Équité totale : victoires + moitié des égalités
        public double equity() {
        	if(iterations == 0) {
        		return 0.0;
        	}else {
        		return (wins + ties * 0.5) / iterations * 100.0;
        	}
        }

        public double winPct(){ 
        	if (iterations == 0) {
        		return 0.0;
        	} else {
        		return wins*100.0 / iterations;
        	}
        }
        public double tiePct(){
        	if (iterations == 0) {
        		return 0.0;
        	} else {
        		return ties*100.0 / iterations;
        	}
        }
        public double lossPct() { 
        	if (iterations == 0) {
        		return 0.0;
        	} else {
        		return losses*100.0 / iterations;
        	}
        }

        @Override
        public String toString() {
            return String.format("Equity=%.1f%% (W=%.1f%% T=%.1f%% L=%.1f%%) [%d iter]",
                equity(), winPct(), tiePct(), lossPct(), iterations);
        }
    }
}
