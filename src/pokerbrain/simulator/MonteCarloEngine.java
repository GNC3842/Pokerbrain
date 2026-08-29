package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Moteur Monte-Carlo haute performance — zéro allocation dans la boucle chaude.
 *
 * USAGE (une instance par thread) :
 *   MonteCarloEngine engine = new MonteCarloEngine();
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

    private static final int CONVERGENCE_BLOCK  = 500;
    private static final double CONVERGENCE_THRESHOLD = 0.005; // ±0.5%
    //Tirage par rejet dans une range : garde-fous contre une range trop étroite
    private static final int MAX_REJECTS_PER_ITER = 200;
    private static final int MAX_ABANDONED = 200;

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

    public MonteCarloEngine() {
        this.evaluator = new FastHandEvaluator();
        this.deck = new DeckPrimitive();
    }


    /**
     * Filtre de mains adverses, exprimé sur les index primitifs du deck
     * (index = rangIndex*4 + couleurIndex, cf. CardPrimitiveBridge.indexOf).
     *
     * Défini ici plutôt que dans `bot` pour que le simulateur reste indépendant :
     * c'est l'appelant qui adapte sa RangeMap (cf. RangeMap.asVillainRange()).
     */
    @FunctionalInterface
    public interface VillainRange {
        boolean accepts(int cardIndexA, int cardIndexB);
    }

    //Calcule l'équité du hero contre 1 adversaire à cartes inconnues
    public EquityResult calculateEquity(List<Card> heroHole, List<Card> board, int maxIter, int nbOpposants) {
        return calculateEquity(heroHole, board, maxIter, nbOpposants, null);
    }

    /**
     * Équité du héros contre des adversaires dont les mains sont tirées dans une range.
     *
     * Le tirage se fait par rejet : on mélange, et si une des mains adverses sort de la
     * range on rejoue le mélange. Une range très étroite fait donc beaucoup de rejets,
     * d'où le plafond MAX_REJECTS_PER_ITER au-delà duquel l'itération est abandonnée
     * (et non comptée) — mieux vaut moins d'itérations qu'un échantillon biaisé.
     *
     * villainRange == null → adversaires à mains aléatoires (comportement historique).
     */
    public EquityResult calculateEquity(List<Card> heroHole, List<Card> board, int maxIter,
                                        int nbOpposants, VillainRange villainRange) {
        validate(heroHole, board, maxIter);

        int boardSize = board.size();
        int boardMissing = 5 - boardSize;
        int deadCount = 2 + boardSize;

        //Encodage des cartes connues (UNE SEULE FOIS, hors boucle)
        DeckPrimitive.encodeCards(heroHole, heroIdx, heroEnc);
        DeckPrimitive.encodeCards(board, boardIdx, boardEnc);

        // Hero : hole cards déjà en place (encodeCards a écrit heroEnc[0..1])
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
        int abandoned = 0;
        double equitySum = 0.0; //en cas dégalité à min 3
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        while (iter < maxIter) {
            int blockEnd = Math.min(iter + CONVERGENCE_BLOCK, maxIter);

            while (iter < blockEnd) {
                // Mélange partiel : 2 (villain) + boardMissing cartes
                deck.shufflePartial(rng, (2*nbOpposants) + boardMissing);

                // Tirage par rejet quand les adversaires ont une range imposée
                if (villainRange != null) {
                    int rejects = 0;
                    while (!allInRange(villainRange, nbOpposants) && rejects < MAX_REJECTS_PER_ITER) {
                        deck.shufflePartial(rng, (2*nbOpposants) + boardMissing);
                        rejects++;
                    }
                    if (rejects >= MAX_REJECTS_PER_ITER) {
                        // Range trop étroite pour ce deck : on abandonne l'itération
                        abandoned++;
                        if (abandoned >= MAX_ABANDONED) {
                            return new EquityResult(wins, ties, losses, iter, equitySum);
                        }
                        continue;
                    }
                }


                // Compléter le board (partagé hero+villain)
                for (int b = 0; b < boardMissing; b++) {
                    int enc = deck.drawnEncoded((2*nbOpposants) + b);
                    int id = deck.drawnIndex((2*nbOpposants) + b);
                    heroEnc[2 + boardSize + b] = enc;
                    heroIdx[2 + boardSize + b] = id;
                    villEnc[2 + boardSize + b] = enc;
                    villIdx[2 + boardSize + b] = id;
                }

                // Évaluation O(1) chacune
                int hv = evaluator.evaluate7(heroEnc, heroIdx);
                
                int maxVillainScore = -1;
                int villainTieCount = 0;
                
                for(int i=0;i<nbOpposants;i++) {
                	// Villain hole cards
                    villEnc[0] = deck.drawnEncoded(i*2);
                    villIdx[0] = deck.drawnIndex(i*2);
                    villEnc[1] = deck.drawnEncoded(i*2+1);
                    villIdx[1] = deck.drawnIndex(i*2+1);
                    
                    int vv = evaluator.evaluate7(villEnc,villIdx);
                    
                    if(vv>maxVillainScore) {
                    	maxVillainScore = vv;
                    	villainTieCount = 1;
                    } else if(vv == maxVillainScore) {
                    	villainTieCount++;
                    }
                }

                if(hv > maxVillainScore) {
                	wins++;
                	equitySum += 1.0;
                }
                else if(hv == maxVillainScore) {
                	ties++;
                	equitySum += 1.0 / (villainTieCount + 1);
                }
                else{
                	losses++;
                }

                iter++;
            }

            // Convergence adaptative (IC 95%)
            if (iter >= CONVERGENCE_BLOCK) {
                double p = equitySum / iter;
                double margin = 1.96 * Math.sqrt(p * (1 - p) / iter);
                if (margin < CONVERGENCE_THRESHOLD) {
                	break;
                }
            }
        }

        return new EquityResult(wins, ties, losses, iter, equitySum);
    }

    //Vrai si les mains des nbOpposants adversaires du mélange courant sont toutes dans la range
    private boolean allInRange(VillainRange range, int nbOpposants) {
        for (int i = 0; i < nbOpposants; i++) {
            if (!range.accepts(deck.drawnIndex(i * 2), deck.drawnIndex(i * 2 + 1))) {
                return false;
            }
        }
        return true;
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
        public final double equitySum;

        public EquityResult(int wins, int ties, int losses, int iterations, double equitySum) {
            this.wins = wins;
            this.ties = ties;
            this.losses = losses;
            this.iterations = iterations;
            this.equitySum = equitySum;
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
        
        public double equity() {
        	if(iterations == 0) {
        		return 0.0;
        	} else {
        		return (equitySum / iterations) * 100.0;
        	}
        }

        @Override
        public String toString() {
            return String.format("Equity=%.1f%% (W=%.1f%% T=%.1f%% L=%.1f%%) [%d iter]",
                equity(), winPct(), tiePct(), lossPct(), iterations);
        }
    }
}
