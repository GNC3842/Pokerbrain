package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.*;
import pokerbrain.parser.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ParserWriterTest {
    @Test void roundTrip() throws ParseException {
        Hand h = sample();
        Hand parsed = HandHistoryParser.parseString(HandHistoryWriter.serialize(h));
        assertEquals(h.getHandId(), parsed.getHandId());
        assertEquals(h.getSmallBlind(), parsed.getSmallBlind());
        assertEquals(h.getActions().size(), parsed.getActions().size());
    }
    @Test void holeCardsPreserved() throws ParseException {
        Hand parsed = HandHistoryParser.parseString(HandHistoryWriter.serialize(sample()));
        PlayerState hero = parsed.getInitialStates().stream().filter(ps -> ps.getId().equals("hero")).findFirst().orElse(null);
        assertNotNull(hero);
        assertEquals(2, hero.getHoleCards().size());
    }
    @Test void multipleSeparator() throws ParseException {
        String text = HandHistoryWriter.serialize(sample()) + "\n---\n\n" + HandHistoryWriter.serialize(sample());
        assertEquals(2, HandHistoryParser.parseMultiple(text).size());
    }

    private Hand sample() {
        Player p1 = new Player("hero","Alice",200); Player p2 = new Player("bot","Bob",200);
        p1.setPosition(Position.BTN); p1.setSeat(1); p2.setPosition(Position.SB); p2.setSeat(2);
        p1.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        p2.receiveHoleCards(Card.fromString("Qs"), Card.fromString("Jd"));
        return new Hand.Builder().handId("t1").heroId("hero").tableName("T").smallBlind(1).bigBlind(2)
            .players(List.of(p1,p2)).initialStates(List.of(PlayerState.of(p1), PlayerState.of(p2)))
            .addAction(new Action(p1, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(p2, ActionType.FOLD, BettingRound.PREFLOP))
            .addWinning("hero", 9).build();
    }
}
