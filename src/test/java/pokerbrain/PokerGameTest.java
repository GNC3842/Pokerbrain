package pokerbrain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pokerbrain.engine.PokerGame;
import pokerbrain.model.game.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class PokerGameTest {
    private PokerGame game;
    private List<Player> players;

    @BeforeEach void setup() {
        players = List.of(new Player("p1","A",200), new Player("p2","B",200), new Player("p3","C",200));
        game = new PokerGame(new ArrayList<>(players), 1, 2);
    }
    @Test void dealsCards() { game.startNewHand(); for (Player p : players) assertEquals(2, p.getHoleCards().size()); }
    @Test void blindsInPot() { game.startNewHand(); assertTrue(game.getTotalPot() >= 3); }
    @Test void initialRound() { game.startNewHand(); assertEquals(BettingRound.PREFLOP, game.getCurrentRound()); }
    @Test void foldWorks() {
        game.startNewHand();
        long before = players.stream().filter(p -> !p.isFolded()).count();
        game.applyAction(ActionType.FOLD);
        assertEquals(before - 1, players.stream().filter(p -> !p.isFolded()).count());
    }
    @Test void flopThreeCards() { game.startNewHand(); completeRound(); assertEquals(3, game.dealFlop().size()); }

    private void completeRound() {
        while (!game.isRoundComplete() && !game.isHandOver()) {
            List<ActionType> v = game.getValidActions();
            if (v.contains(ActionType.CHECK)) game.applyAction(ActionType.CHECK);
            else if (v.contains(ActionType.CALL)) game.applyAction(ActionType.CALL);
            else game.applyAction(ActionType.FOLD);
        }
    }
}
