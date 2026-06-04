package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.analyser.statistics.*;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerStatisticsTest {
    @Test void empty() { assertEquals(0, PlayerStatisticsCalculator.calculate(List.of(), "hero", 2).getHandsPlayed()); }
    @Test void raiseIsVpipAndPfr() {
        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(hand(ActionType.RAISE, 6)), "hero", 2);
        assertEquals(100.0, s.getVPIP(), 0.01);
        assertEquals(100.0, s.getPFR(), 0.01);
    }
    @Test void foldIsNothing() {
        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(hand(ActionType.FOLD, 0)), "hero", 2);
        assertEquals(0.0, s.getVPIP(), 0.01);
    }

    private Hand hand(ActionType type, int amt) {
        Player h = new Player("hero","H",200); Player b = new Player("bot","B",200);
        h.setPosition(Position.BTN); h.setSeat(1); b.setPosition(Position.SB); b.setSeat(2);
        h.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        Hand.Builder bld = new Hand.Builder().handId("t").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(h,b)).initialStates(List.of(PlayerState.of(h), PlayerState.of(b)));
        if (type.involvesMoney() && amt > 0) bld.addAction(new Action(h, type, amt, BettingRound.PREFLOP));
        else bld.addAction(new Action(h, type, BettingRound.PREFLOP));
        bld.addAction(new Action(b, ActionType.FOLD, BettingRound.PREFLOP));
        return bld.build();
    }
}
