package pokerbrain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pokerbrain.model.core.Card;
import pokerbrain.simulator.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloTest {
    private static MonteCarloEngine engine;
    @BeforeAll static void setup() { engine = new MonteCarloEngine(); }

    @Test void acesAbove80() { assertTrue(eq("Ah","As",1) > 80); }
    @Test void worstBelow40() { assertTrue(eq("7h","2c",1) < 40); }
    @Test void moreOpponentsLessEquity() { assertTrue(eq("Kh","Qs",1) > eq("Kh","Qs",3)); }

    private double eq(String c1, String c2, int opp) {
        return engine.calculateEquity(List.of(Card.fromString(c1), Card.fromString(c2)), List.of(), 3000, opp).equity();
    }
}
