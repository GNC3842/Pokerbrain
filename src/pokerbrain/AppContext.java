package pokerbrain;

import pokerbrain.simulator.LookupTableGenerator;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.concurrent.CompletableFuture;

/**
 * Contexte applicatif singleton — gère la génération unique de la lookup table
 * Monte-Carlo de manière thread-safe.
 *
 * USAGE :
 *   // Au démarrage (MainApp.start) — lance la génération en background
 *   AppContext.initAsync();
 *
 *   // Quand un bot ou l'UI a besoin d'un engine MC — bloque si pas prêt
 *   MonteCarloEngine engine = AppContext.getEngine();
 *
 *   // Pour vérifier sans bloquer
 *   if (AppContext.isReady()) { ... }
 *
 * GARANTIES :
 *   - La lookup table n'est générée qu'UNE SEULE FOIS (CompletableFuture)
 *   - Chaque appel à getEngine() retourne une NOUVELLE instance
 *     (chaque thread/bot a son propre engine, zéro contention)
 *   - initAsync() est idempotent
 */
public final class AppContext {

    private AppContext() {}

    // Future de la lookup table — thread-safe, lazy, idempotent
    private static volatile CompletableFuture<int[]> tableFuture;

    /**
     * Lance la génération de la lookup table en thread daemon.
     * Idempotent : appeler plusieurs fois n'a aucun effet.
     */
    public static synchronized void initAsync() {
        if (tableFuture != null) return;

        tableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("[AppContext] Génération de la lookup table...");
            long start = System.currentTimeMillis();
            int[] table = LookupTableGenerator.generate();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[AppContext] Lookup table prête en " + elapsed + " ms");
            return table;
        });
    }

    /**
     * Retourne un MonteCarloEngine prêt à l'emploi.
     * Bloque si la lookup table n'est pas encore générée.
     * Chaque appel crée une NOUVELLE instance (thread-safe).
     */
    public static MonteCarloEngine getEngine() {
        if (tableFuture == null) {
            initAsync();
        }
        try {
            int[] table = tableFuture.join();
            return new MonteCarloEngine(table);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de créer le MonteCarloEngine", e);
        }
    }

    /**
     * Vérifie si la lookup table est prête sans bloquer.
     */
    public static boolean isReady() {
        return tableFuture != null && tableFuture.isDone() && !tableFuture.isCompletedExceptionally();
    }

    /**
     * Retourne la lookup table brute. Bloque si pas encore générée.
     * Utilisé par HandAnalyser qui crée son propre engine.
     */
    public static int[] getTable() {
        if (tableFuture == null) initAsync();
        try {
            return tableFuture.join();
        } catch (Exception e) {
            throw new RuntimeException("Impossible de récupérer la lookup table", e);
        }
    }
}
