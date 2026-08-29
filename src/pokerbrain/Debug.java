package pokerbrain;

import java.util.function.Supplier;

/**
 * Traces de mise au point, désactivées par défaut.
 *
 * Les enregistreurs de main et le parser écrivaient directement sur System.out :
 * une partie produisait des centaines de lignes, et la sortie de `mvn test` était
 * noyée sous les snapshots. Les traces sont conservées — elles sont utiles quand
 * l'historique disque ne correspond pas à la partie jouée — mais derrière un
 * interrupteur.
 *
 * Activation :
 *   mvn -o javafx:run -Dpokerbrain.debug=true
 *   java -Dpokerbrain.debug=true ...
 *
 * Le message est passé en Supplier pour n'être construit que si les traces sont
 * actives : ces appels sont dans des boucles chaudes (un snapshot par joueur et
 * par action).
 */
public final class Debug {

    private static final boolean ENABLED = Boolean.getBoolean("pokerbrain.debug");

    private Debug() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static void log(Supplier<String> message) {
        if (ENABLED) {
            System.out.println(message.get());
        }
    }
}
