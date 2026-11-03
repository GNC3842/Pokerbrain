package pokerbrain;

import java.util.List;
import org.junit.jupiter.api.Test;
import pokerbrain.analyser.leaks.*;
import pokerbrain.analyser.statistics.PlayerStatistics;
import static org.junit.jupiter.api.Assertions.*;

public class LeakDetectorTest {
    @Test void highVpipCritical() {
        assertTrue(LeakDetector.detect(stats(100, 55, 8)).stream()
            .anyMatch(l -> l.getCategory().equals("VPIP") && l.getSeverity() == LeakSeverity.CRITICAL));
    }

    
    @Test void tagNoCritical() {
        PlayerStatistics s = new PlayerStatistics("h", "Hero",
            200, 44, 36, 18, 50, 12, 7, 500, 2);
        List<Leak> leaks = LeakDetector.detect(s);
        leaks.stream()
            .filter(l -> l.getSeverity() == LeakSeverity.CRITICAL)
            .forEach(l -> System.out.println("CRITICAL trouvé: " + l));
        assertEquals(0, leaks.stream()
            .filter(l -> l.getSeverity() == LeakSeverity.CRITICAL).count());
    }

    @Test void smallSampleWarning() {
        assertTrue(LeakDetector.detect(stats(20, 5, 4)).stream()
            .anyMatch(l -> l.getCategory().equals("Échantillon")));
    }

    /**
     * Sur zéro main, toutes les stats valent zéro : le détecteur y lisait
     * « PFR beaucoup trop bas » et « VPIP trop bas » et rendait deux fuites
     * critiques à un joueur qui n'avait jamais joué.
     */
    @Test void emptySampleOnlyWarnsAboutTheSample() {
        List<Leak> leaks = LeakDetector.detect(stats(0, 0, 0));
        assertEquals(1, leaks.size(), "aucune fuite ne peut être diagnostiquée sans données : " + leaks);
        assertEquals("Échantillon", leaks.get(0).getCategory());
    }

    //Sous 50 mains, une tendance reste une tendance : la sévérité est abaissée d'un cran
    @Test void smallSampleDowngradesSeverity() {
        List<Leak> leaks = LeakDetector.detect(stats(20, 18, 1)); // VPIP 90 %, PFR 5 %
        assertEquals(0, leaks.stream().filter(l -> l.getSeverity() == LeakSeverity.CRITICAL).count(),
            "20 mains ne suffisent pas à affirmer une fuite critique");
        assertTrue(leaks.stream().anyMatch(l -> l.getCategory().equals("VPIP")),
            "la tendance doit tout de même être signalée");
    }

    /**
     * getAF() renvoyait 0 quand le joueur ne suivait jamais : le joueur le plus
     * agressif de la table était accusé de jouer « trop passif ».
     */
    @Test void neverCallingIsNotDiagnosedAsPassive() {
        // 60 actions agressives, aucun call
        PlayerStatistics s = new PlayerStatistics("h", "H", 200, 44, 36, 0, 60, 12, 5, 500, 2);
        List<Leak> leaks = LeakDetector.detect(s);
        assertTrue(leaks.stream().noneMatch(l -> l.getCategory().equals("AF")
                && l.getMessage().contains("passif")),
            "ne jamais suivre est un excès d'agression, pas de la passivité : " + leaks);
        assertTrue(leaks.stream().anyMatch(l -> l.getCategory().equals("AF")),
            "l'excès doit tout de même être relevé");
    }

    //L'AF ne se juge pas sur une poignée d'actions
    @Test void aggressionNotJudgedOnTinyActionSample() {
        PlayerStatistics s = new PlayerStatistics("h", "H", 200, 44, 36, 3, 1, 12, 7, 500, 2);
        assertTrue(LeakDetector.detect(s).stream().noneMatch(l -> l.getCategory().equals("AF")),
            "4 actions ne permettent pas de juger l'agressivité");
    }

    //Cas jamais traité : gagner presque tous ses showdowns veut dire qu'on se couche trop
    @Test void veryHighWsdIsALeak() {
        // 20 showdowns, 19 gagnés
        PlayerStatistics s = new PlayerStatistics("h", "H", 200, 60, 40, 18, 50, 12, 8, 20, 19, 500, 2);
        assertTrue(LeakDetector.detect(s).stream().anyMatch(l -> l.getCategory().equals("WSD")),
            "un W$SD de 95 % signale un jeu trop craintif avant le showdown");
    }

    //Cas jamais traité : le 3-bet, une fois qu'il est réellement mesuré
    @Test void missingThreeBetIsDetected() {
        // 30 occasions de 3-bet, aucune saisie
        PlayerStatistics s = new PlayerStatistics("h", "H", 200, 44, 36, 18, 50, 0, 30, 12, 7, 500, 2);
        assertTrue(LeakDetector.detect(s).stream().anyMatch(l -> l.getCategory().equals("3-Bet")),
            "ne jamais 3-better sur 30 occasions est une fuite");
    }

    //Sans occasion mesurée, aucune fuite de 3-bet ne doit être inventée
    @Test void threeBetIgnoredWithoutSample() {
        PlayerStatistics s = new PlayerStatistics("h", "H", 200, 44, 36, 18, 50, 0, 0, 12, 7, 500, 2);
        assertTrue(LeakDetector.detect(s).stream().noneMatch(l -> l.getCategory().equals("3-Bet")));
    }

    //Cas jamais traité : la statistique qui compte, le winrate
    @Test void losingWinrateIsReported() {
        // -1000 jetons sur 200 mains à 2 de BB = -250 BB/100
        PlayerStatistics s = new PlayerStatistics("h", "H", 200, 44, 36, 18, 50, 12, 7, -1000, 2);
        assertTrue(LeakDetector.detect(s).stream()
            .anyMatch(l -> l.getCategory().equals("Winrate") && l.getSeverity() == LeakSeverity.CRITICAL));
    }

    //Cas jamais traité : le profil calling station, invisible statistique par statistique
    @Test void callingStationProfileIsDetected() {
        // VPIP 45 %, PFR 5 %, AF 0,3
        PlayerStatistics s = new PlayerStatistics("h", "H", 200, 90, 10, 60, 18, 30, 12, 0, 2);
        assertTrue(LeakDetector.detect(s).stream().anyMatch(l -> l.getCategory().equals("Profil")),
            "beaucoup de mains jouées sans agression = calling station");
    }

    private PlayerStatistics stats(int hands, int vpip, int pfr) {
        return new PlayerStatistics("h","H", hands, vpip, pfr, 20, 10, 10, 5, 0, 2);
    }
}
