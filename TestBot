package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.bot.*;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class BotTest {
    @Test void tagFoldTrash() { assertEquals(ActionType.FOLD, decide("2h","7c", Position.UTG, BotProfileFactory.createTightAggressive())); }
    @Test void tagRaiseAces() { assertEquals(ActionType.RAISE, decide("Ah","As", Position.BTN, BotProfileFactory.createTightAggressive())); }
    @Test void fishDoesntRaiseWeak() {
        ActionType a = decide("9h","6c", Position.BTN, BotProfileFactory.createCallingStation());
        assertNotEquals(ActionType.RAISE, a);
    }
    @Test void nullIdThrows() { assertThrows(IllegalArgumentException.class, () -> new Bot(null, BotProfileFactory.createTightAggressive())); }
    @Test void neverInvalidAction() {
        Bot bot = new Bot("t", BotProfileFactory.createTightAggressive());
        Player me = makePlayer("Ah","Kh", Position.BTN);
        List<ActionType> valid = List.of(ActionType.FOLD, ActionType.CALL);
        Action a = bot.decide(BettingRound.FLOP, me, List.of(Card.fromString("2c"),Card.fromString("3d"),Card.fromString("7s")), 50, 20, 2, valid, 2);
        assertTrue(valid.contains(a.getType()));
    }

    private ActionType decide(String c1, String c2, Position pos, BotProfile prof) {
        Bot bot = new Bot("t", prof);
        return bot.decide(BettingRound.PREFLOP, makePlayer(c1,c2,pos), List.of(), 10, 2, 2,
            List.of(ActionType.FOLD, ActionType.CALL, ActionType.RAISE), 2).getType();
    }
    private Player makePlayer(String c1, String c2, Position pos) {
        Player p = new Player("t","T",200);
        p.receiveHoleCards(Card.fromString(c1), Card.fromString(c2));
        p.setPosition(pos);
        return p;
    }
}