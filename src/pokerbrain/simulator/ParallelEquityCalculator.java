package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calcul d'équité parallèle via Fork/Join.
 *
 * Principe :
 *   - Découpe maxIter en N blocs (un par thread disponible)
 *   - Chaque thread possède sa propre instance MonteCarloEngine (zéro contention)
 *   - Agrège les EquityResult partiels en un résultat global
 *
 * Usage :
 *   ParallelEquityCalculator calc = new ParallelEquityCalculator(lookupTable);
 *   EquityResult r = calc.calculate(heroHole, board, 50_000);
 *   calc.shutdown(); // à appeler en fin de vie
 */
public final class ParallelEquityCalculator {

    private final int[] lookupTable;
    private final ExecutorService pool;
    private final int threadCount;

    // Pool de MonteCarloEngine — un par thread, ThreadLocal évite la contention
    private final ThreadLocal<MonteCarloEngine> enginePool;

    public ParallelEquityCalculator(int[] lookupTable) {
        this(lookupTable, Runtime.getRuntime().availableProcessors());
    }

    public ParallelEquityCalculator(int[] lookupTable, int threads) {
        if (lookupTable == null)
            throw new IllegalArgumentException("Lookup table ne peut pas être null");
        if (threads <= 0)
            throw new IllegalArgumentException("threads doit être positif");

        this.lookupTable  = lookupTable;
        this.threadCount  = threads;
        this.pool         = Executors.newFixedThreadPool(threads);
        this.enginePool   = ThreadLocal.withInitial(() -> new MonteCarloEngine(lookupTable));
    }


    
    //Calcule l'équité en parallèle.
    public MonteCarloEngine.EquityResult calculate(
            List<Card> heroHole, List<Card> board, int totalIter)
            throws InterruptedException, ExecutionException {

        int itersPerThread = totalIter / threadCount;
        int remainder      = totalIter % threadCount;

        @SuppressWarnings("unchecked")
        Future<MonteCarloEngine.EquityResult>[] futures = new Future[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int iters = itersPerThread + (t == 0 ? remainder : 0);
            // Capture finale pour la lambda
            final List<Card> fHole  = heroHole;
            final List<Card> fBoard = board;

            futures[t] = pool.submit(() -> {
                MonteCarloEngine engine = enginePool.get();
                return engine.calculateEquity(fHole, fBoard, iters);
            });
        }

        // Agréger les résultats
        int wins = 0, ties = 0, losses = 0, iters = 0;
        for (Future<MonteCarloEngine.EquityResult> f : futures) {
            MonteCarloEngine.EquityResult r = f.get();
            wins   += r.wins;
            ties   += r.ties;
            losses += r.losses;
            iters  += r.iterations;
        }

        return new MonteCarloEngine.EquityResult(wins, ties, losses, iters);
    }

    //Version sans checked exception — wrappe en RuntimeException
    public MonteCarloEngine.EquityResult calculateUnchecked(
            List<Card> heroHole, List<Card> board, int totalIter) {
        try {
            return calculate(heroHole, board, totalIter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation interrompue", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Erreur dans la simulation", e.getCause());
        }
    }

    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getThreadCount() { 
    	return threadCount; 
    }
}
