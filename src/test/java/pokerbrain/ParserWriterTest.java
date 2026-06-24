package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.engine.PokerGame;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.*;
import pokerbrain.parser.*;
import java.util.ArrayList;
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
        PlayerState hero = parsed.getInitialStates().stream()
            .filter(ps -> ps.getId().equals("hero")).findFirst().orElse(null);
        assertNotNull(hero);
        assertEquals(2, hero.getHoleCards().size());
    }

    @Test void multipleSeparator() throws ParseException {
        String text = HandHistoryWriter.serialize(sample()) + "\n---\n\n" + HandHistoryWriter.serialize(sample());
        assertEquals(2, HandHistoryParser.parseMultiple(text).size());
    }

    @Test void actionAmountPreserved() throws ParseException {
        Hand parsed = HandHistoryParser.parseString(HandHistoryWriter.serialize(sample()));
        Action first = parsed.getActions().get(0);
        assertEquals(ActionType.RAISE, first.getType());
        assertEquals(6, first.getAmount());
    }

    @Test void boardPreserved() throws ParseException {
        Hand h = sampleWithBoard();
        Hand parsed = HandHistoryParser.parseString(HandHistoryWriter.serialize(h));
        assertEquals(h.getBoard().size(), parsed.getBoard().size());
        for (int i = 0; i < h.getBoard().size(); i++) {
            assertEquals(h.getBoard().get(i).toString(), parsed.getBoard().get(i).toString());
        }
    }

    @Test void winningsPreserved() throws ParseException {
        Hand parsed = HandHistoryParser.parseString(HandHistoryWriter.serialize(sample()));
        assertEquals(9, parsed.getWinnings().get("hero"));
    }

    @Test void gameStatesReconstructedAfterParse() throws Exception {
        List<Player> players = new ArrayList<>(List.of(
            new Player("hero", "Alice", 200),
            new Player("bot",  "Bob",   200)
        ));
        PokerGame game = new PokerGame(players, 1, 2);
        game.startNewHand();
        HandRecord rec = new HandRecord(game, "hero");

        Action a1 = game.applyAction(ActionType.CALL);
        rec.recordAction(a1);
        Action a2 = game.applyAction(ActionType.CHECK);
        rec.recordAction(a2);

        Hand original = rec.buildHand();
        String serialized = HandHistoryWriter.serialize(original);
        Hand parsed = HandHistoryParser.parseString(serialized);

        assertEquals(original.getActions().size(), parsed.getGameStates().size(),
            "One GameState per action must be reconstructed");
        // Pot must be at least SB+BB
        parsed.getGameStates().forEach(gs ->
            assertTrue(gs.getTotalPot() >= original.getSmallBlind() + original.getBigBlind()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Hand sample() {
        Player p1 = new Player("hero", "Alice", 200);
        Player p2 = new Player("bot",  "Bob",   200);
        p1.setPosition(Position.BTN); p1.setSeat(1);
        p2.setPosition(Position.SB);  p2.setSeat(2);
        p1.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        p2.receiveHoleCards(Card.fromString("Qs"), Card.fromString("Jd"));
        return new Hand.Builder()
            .handId("t1").heroId("hero").tableName("T").smallBlind(1).bigBlind(2)
            .players(List.of(p1, p2))
            .initialStates(List.of(PlayerState.of(p1), PlayerState.of(p2)))
            .addAction(new Action(p1, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(p2, ActionType.FOLD, BettingRound.PREFLOP))
            .addWinning("hero", 9)
            .build();
    }

    private Hand sampleWithBoard() {
        Player p1 = new Player("hero", "Alice", 200);
        Player p2 = new Player("bot",  "Bob",   200);
        p1.setPosition(Position.BTN); p1.setSeat(1);
        p2.setPosition(Position.SB);  p2.setSeat(2);
        p1.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        p2.receiveHoleCards(Card.fromString("Qs"), Card.fromString("Jd"));
        return new Hand.Builder()
            .handId("t2").heroId("hero").tableName("T").smallBlind(1).bigBlind(2)
            .players(List.of(p1, p2))
            .initialStates(List.of(PlayerState.of(p1), PlayerState.of(p2)))
            .addAction(new Action(p1, ActionType.CALL, 2, BettingRound.PREFLOP))
            .addAction(new Action(p2, ActionType.CHECK, BettingRound.PREFLOP))
            .addAction(new Action(p1, ActionType.BET, 4, BettingRound.FLOP))
            .addAction(new Action(p2, ActionType.FOLD, BettingRound.FLOP))
            .board(List.of(
                Card.fromString("Th"), Card.fromString("7h"), Card.fromString("2c")))
            .addWinning("hero", 8)
            .build();
    }
}
