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

    private PlayerStatistics stats(int hands, int vpip, int pfr) {
        return new PlayerStatistics("h","H", hands, vpip, pfr, 20, 10, 10, 5, 0, 2);
    }
}
