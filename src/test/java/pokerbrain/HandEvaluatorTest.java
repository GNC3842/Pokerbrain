package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.model.core.*;
import pokerbrain.engine.HandEvaluator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class HandEvaluatorTest {
    @Test void royalFlush() {
        assertEquals(HandType.ROYAL_FLUSH, HandEvaluator.evaluateBestHand(List.of(
            Card.fromString("As"), Card.fromString("Ks"), Card.fromString("Qs"),
            Card.fromString("Js"), Card.fromString("Ts"))).getType());
    }
    @Test void fullHouse() {
        assertEquals(HandType.FULL_HOUSE, HandEvaluator.evaluateBestHand(List.of(
            Card.fromString("Kh"), Card.fromString("Kd"), Card.fromString("Kc"),
            Card.fromString("5s"), Card.fromString("5h"))).getType());
    }
    @Test void higherPairWins() {
        HandRank kk = HandEvaluator.evaluateBestHand(List.of(Card.fromString("Kh"), Card.fromString("Kd"), Card.fromString("3c"), Card.fromString("5s"), Card.fromString("7h")));
        HandRank qq = HandEvaluator.evaluateBestHand(List.of(Card.fromString("Qh"), Card.fromString("Qd"), Card.fromString("3s"), Card.fromString("5c"), Card.fromString("7d")));
        assertTrue(kk.compareTo(qq) > 0);
    }
}
