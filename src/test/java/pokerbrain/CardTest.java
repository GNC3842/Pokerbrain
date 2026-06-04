package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.model.core.*;
import static org.junit.jupiter.api.Assertions.*;

public class CardTest {
    @Test void fromString() {
        Card c = Card.fromString("Ah");
        assertEquals(Rank.ACE, c.getRank());
        assertEquals(Suit.HEARTS, c.getSuit());
    }
    @Test void toStringFormat() { assertEquals("Ks", Card.fromString("Ks").toString()); }
    @Test void invalidThrows() { assertThrows(Exception.class, () -> Card.fromString("Xx")); }
    @Test void equality() { assertEquals(Card.fromString("Ah"), Card.fromString("Ah")); }
}
