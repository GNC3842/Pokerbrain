package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.bot.RangeMap;
import pokerbrain.model.core.Rank;
import pokerbrain.model.game.ActionType;
import static org.junit.jupiter.api.Assertions.*;

public class RangeMapTest {
    @Test void emptyIsFold() { assertEquals(ActionType.FOLD, RangeMap.empty().getAction(Rank.ACE, Rank.KING, true)); }
    @Test void setPair() {
        RangeMap m = new RangeMap();
        m.setPair(Rank.ACE, ActionType.RAISE);
        assertEquals(ActionType.RAISE, m.getAction(Rank.ACE, Rank.ACE, false));
    }
    @Test void setSuited() {
        RangeMap m = new RangeMap();
        m.setSuited(Rank.ACE, Rank.KING, ActionType.RAISE);
        assertEquals(ActionType.RAISE, m.getAction(Rank.ACE, Rank.KING, true));
        assertEquals(ActionType.FOLD, m.getAction(Rank.ACE, Rank.KING, false));
    }
    @Test void reversedOrder() {
        RangeMap m = new RangeMap();
        m.setSuited(Rank.ACE, Rank.KING, ActionType.RAISE);
        assertEquals(ActionType.RAISE, m.getAction(Rank.KING, Rank.ACE, true));
    }
}