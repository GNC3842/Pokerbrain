package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.engine.PokerGame;
import pokerbrain.model.game.*;
import pokerbrain.model.history.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class HandRecordTest {
    @Test void capturesAction() {
        List<Player> p = new ArrayList<>(List.of(new Player("p1","A",200), new Player("p2","B",200)));
        PokerGame game = new PokerGame(p, 1, 2);
        game.startNewHand();
        HandRecord rec = new HandRecord(game, "p1");
        rec.recordAction(game.applyAction(ActionType.CALL));
        Hand hand = rec.buildHand();
        assertEquals("p1", hand.getHeroId());
        assertFalse(hand.getActions().isEmpty());
    }
}
