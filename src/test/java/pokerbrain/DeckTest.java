package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.model.core.*;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

public class DeckTest {
    @Test void fiftyTwoUnique() {
        Deck d = new Deck();
        HashSet<Card> s = new HashSet<>();
        for (int i = 0; i < 52; i++) s.add(d.deal());
        assertEquals(52, s.size());
    }
}
