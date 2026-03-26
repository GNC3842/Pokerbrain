package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import java.util.*;
import java.util.concurrent.*;

/**
 * Calcul parallele d'equite en batch — pour l'analyse de ranges completes.
 *
 * NE PAS utiliser pour une decision bot temps reel (1 vs 1) :
 *   MonteCarloEngine.calculateEquity() seul converge en <1 ms.
 *
 * CAS D'USAGE LEGITIMES :
 *   - Analyse de range complete (169 x 169 combos)
 *   - Simulation multi-joueurs en batch (3+ adversaires)
 *   - Calcul en arriere-plan pendant que l'UI tourne
 *
 * GESTION DES RESSOURCES (Java 21+) :
 *   L'ExecutorService est cree et ferme dans calculate() via try-with-resources.
 *   Aucun pool persistant → zero fuite possible, meme si le calcul est annule.
 *
 *   Alternative Java 11-17 : le pattern try-with-resources marche aussi avec
 *   un pool cree localement dans la methode (pas de champ d'instance).
 */
public final class RangeBatchCalculator {

    private final int[] lookupTable;

    public RangeBatchCalculator(int[] lookupTable) {
        this.lookupTable = Objects.requireNonNull(lookupTable);
    }

    /**
     * Calcule l'equite de plusieurs mains en parallele.
     */
    public List<MonteCarloEngine.EquityResult> calculateBatch(
            List<List<Card>> hands,
            List<Card> board,
            int itersEach) throws InterruptedException, ExecutionException {

        int threads = Math.min(hands.size(), Runtime.getRuntime().availableProcessors());

        // Java 21 : newVirtualThreadPerTaskExecutor() — pas de pool fixe a fermer
        // Java 11 : Executors.newFixedThreadPool(threads) dans le try-with-resources
        try (ExecutorService exec = Executors.newFixedThreadPool(threads)) {

            // ThreadLocal : chaque thread a son propre MonteCarloEngine (zero contention)
            ThreadLocal<MonteCarloEngine> engines =
                ThreadLocal.withInitial(() -> new MonteCarloEngine(lookupTable));

            List<Future<MonteCarloEngine.EquityResult>> futures = new ArrayList<>(hands.size());

            for (List<Card> hole : hands) {
                futures.add(exec.submit(() ->
                    engines.get().calculateEquity(hole, board, itersEach)));
            }

            List<MonteCarloEngine.EquityResult> results = new ArrayList<>(hands.size());
            for (Future<MonteCarloEngine.EquityResult> f : futures) {
                results.add(f.get());
            }
            return results;

        } // exec.close() appele automatiquement — zero fuite
    }
}
