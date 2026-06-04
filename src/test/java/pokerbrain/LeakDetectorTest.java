package test.java.pokerbrain;

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
        assertEquals(0, LeakDetector.detect(stats(200, 46, 38)).stream()
            .filter(l -> l.getSeverity() == LeakSeverity.CRITICAL).count());
    }
    @Test void smallSampleWarning() {
        assertTrue(LeakDetector.detect(stats(20, 5, 4)).stream()
            .anyMatch(l -> l.getCategory().equals("Échantillon")));
    }

    private PlayerStatistics stats(int hands, int vpip, int pfr) {
        return new PlayerStatistics("h","H", hands, vpip, pfr, 20, 10, 10, 5, 0, 2);
    }
}
