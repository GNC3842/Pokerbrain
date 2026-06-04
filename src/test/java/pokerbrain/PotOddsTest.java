package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.bot.PotOddsCalculator;
import static org.junit.jupiter.api.Assertions.*;

public class PotOddsTest {
    @Test void breakEven() { assertEquals(0.333, PotOddsCalculator.breakEvenEquity(100, 50), 0.01); }
    @Test void freeCall() { assertEquals(0.0, PotOddsCalculator.breakEvenEquity(100, 0)); }
    @Test void positiveEV() { assertTrue(PotOddsCalculator.callEV(0.5, 100, 50) > 0); }
    @Test void negativeEV() { assertTrue(PotOddsCalculator.callEV(0.1, 100, 200) < 0); }
    @Test void ruleOfFour() { assertEquals(0.36, PotOddsCalculator.outsToEquity(9, 2), 0.01); }
}