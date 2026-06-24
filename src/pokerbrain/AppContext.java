package pokerbrain;

import pokerbrain.simulator.MonteCarloEngine;

/**
 * Contexte applicatif singleton.
 * Les tables CactusKev sont chargées statiquement à la première utilisation.
 */
public final class AppContext {

    private AppContext() {}

    /** Appel idempotent conservé pour compatibilité avec MainApp.start(). */
    public static void initAsync() {}

    /** Retourne un nouveau MonteCarloEngine prêt à l'emploi. */
    public static MonteCarloEngine getEngine() {
        return new MonteCarloEngine();
    }

    /** Toujours vrai : les tables sont disponibles dès le chargement de la classe. */
    public static boolean isReady() {
        return true;
    }
}
