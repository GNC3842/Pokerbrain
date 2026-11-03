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

    /**
     * Le calculateur passait aggressiveCount et callCount dans l'ordre inverse des
     * paramètres du constructeur : l'AF affiché était l'inverse de l'AF réel, et un
     * joueur hyper-agressif était diagnostiqué « trop passif ».
     */
    @Test void aggressionFactorIsNotInverted() {
        // 3 relances pour 1 call : AF = 3, pas 0,33
        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(aggressiveHand()), "hero", 2);
        assertEquals(3, s.getAgressiveCount());
        assertEquals(1, s.getCallCount());
        assertEquals(3.0, s.getAF(), 0.01);
    }

    //getWTSD() faisait une division entière sans ×100 : le résultat valait toujours 0
    @Test void wtsdIsAPercentage() {
        PlayerStatistics s = new PlayerStatistics("h", "H", 100, 40, 20, 30, 60, 5, 40, 10, 6, 0, 2);
        assertEquals(25.0, s.getWTSD(), 0.01, "10 showdowns sur 40 mains jouées = 25 %");
    }

    //Un joueur qui ne suit jamais a une agressivité infinie, pas nulle
    @Test void neverCallingIsInfiniteAggression() {
        PlayerStatistics s = new PlayerStatistics("h", "H", 100, 40, 30, 0, 50, 12, 5, 0, 2);
        assertTrue(Double.isInfinite(s.getAF()));
    }

    //Le 3-bet était câblé à zéro : il est maintenant mesuré face à un open
    @Test void threeBetIsMeasured() {
        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(threeBetHand()), "hero", 2);
        assertEquals(1, s.getThreeBetOpportunities());
        assertEquals(1, s.getThreeBetCount());
        assertEquals(100.0, s.getThreeBet(), 0.01);
        assertTrue(s.hasThreeBetSample());
    }

    //Se coucher face à un open compte au dénominateur du 3-bet
    @Test void foldingToAnOpenIsAThreeBetOpportunity() {
        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(foldToOpenHand()), "hero", 2);
        assertEquals(1, s.getThreeBetOpportunities());
        assertEquals(0, s.getThreeBetCount());
    }

    /**
     * Les blindes ne passent pas par une Action : sans elles, l'investissement était
     * sous-évalué et le gain net surévalué d'une blinde par main.
     */
    @Test void blindsCountAsInvestment() {
        // Le héro est en BB (2 jetons postés), se couche, et ne gagne rien : il perd 2.
        Player hero = new Player("hero", "H", 200);
        Player bot  = new Player("bot", "B", 200);
        hero.setPosition(Position.BB); bot.setPosition(Position.SB);
        hero.receiveHoleCards(Card.fromString("7c"), Card.fromString("2d"));
        bot.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));

        Hand h = new Hand.Builder().handId("blind").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, bot))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(bot)))
            .addAction(new Action(bot, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(hero, ActionType.FOLD, BettingRound.PREFLOP))
            .build();

        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(h), "hero", 2);
        assertEquals(-2, s.getTotalWinnings(), "la big blind postée doit compter comme investissement");
    }

    /**
     * Le showdown était approximé par « une action à la river qui n'est pas un fold ».
     * Un adversaire qui se couche face à une mise river ne produit pourtant aucun showdown.
     */
    @Test void riverBetThatEveryoneFoldsIsNotAShowdown() {
        Player hero = new Player("hero", "H", 200);
        Player bot  = new Player("bot", "B", 200);
        hero.setPosition(Position.BTN); bot.setPosition(Position.BB);
        hero.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        bot.receiveHoleCards(Card.fromString("7c"), Card.fromString("2d"));

        Hand h = new Hand.Builder().handId("noshow").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, bot))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(bot)))
            .board(List.of(Card.fromString("Ac"), Card.fromString("7d"), Card.fromString("2s"),
                           Card.fromString("9h"), Card.fromString("3c")))
            .addAction(new Action(hero, ActionType.CALL, 2, BettingRound.PREFLOP))
            .addAction(new Action(hero, ActionType.BET, 10, BettingRound.RIVER))
            .addAction(new Action(bot, ActionType.FOLD, BettingRound.RIVER))
            .build();

        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(h), "hero", 2);
        assertEquals(0, s.getWentToShowDown(),
            "l'adversaire s'est couché : les cartes n'ont jamais été comparées");
    }

    //Deux joueurs non couchés sur un board complet : c'est un vrai showdown
    @Test void bothPlayersReachingTheEndIsAShowdown() {
        Player hero = new Player("hero", "H", 200);
        Player bot  = new Player("bot", "B", 200);
        hero.setPosition(Position.BTN); bot.setPosition(Position.BB);
        hero.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        bot.receiveHoleCards(Card.fromString("7c"), Card.fromString("2d"));

        Hand h = new Hand.Builder().handId("show").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, bot))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(bot)))
            .board(List.of(Card.fromString("Ac"), Card.fromString("7d"), Card.fromString("2s"),
                           Card.fromString("9h"), Card.fromString("3c")))
            .addAction(new Action(hero, ActionType.CALL, 2, BettingRound.PREFLOP))
            .addAction(new Action(hero, ActionType.CHECK, BettingRound.RIVER))
            .addAction(new Action(bot, ActionType.CHECK, BettingRound.RIVER))
            .addWinning("hero", 8)
            .build();

        PlayerStatistics s = PlayerStatisticsCalculator.calculate(List.of(h), "hero", 2);
        assertEquals(1, s.getWentToShowDown());
        assertEquals(1, s.getWonAtShowDown());
    }

    //3 actions agressives pour 1 call, sur plusieurs streets
    private Hand aggressiveHand() {
        Player h = new Player("hero","H",200); Player b = new Player("bot","B",200);
        h.setPosition(Position.BTN); b.setPosition(Position.BB);
        h.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        b.receiveHoleCards(Card.fromString("7c"), Card.fromString("2d"));
        return new Hand.Builder().handId("agg").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(h,b)).initialStates(List.of(PlayerState.of(h), PlayerState.of(b)))
            .addAction(new Action(h, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(h, ActionType.BET, 8, BettingRound.FLOP))
            .addAction(new Action(h, ActionType.BET, 20, BettingRound.TURN))
            .addAction(new Action(h, ActionType.CALL, 30, BettingRound.RIVER))
            .build();
    }

    //Le bot ouvre, le héro 3-bet
    private Hand threeBetHand() {
        Player h = new Player("hero","H",200); Player b = new Player("bot","B",200);
        h.setPosition(Position.BTN); b.setPosition(Position.UTG);
        h.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Ad"));
        b.receiveHoleCards(Card.fromString("7c"), Card.fromString("2d"));
        return new Hand.Builder().handId("3bet").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(h,b)).initialStates(List.of(PlayerState.of(h), PlayerState.of(b)))
            .addAction(new Action(b, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(h, ActionType.RAISE, 18, BettingRound.PREFLOP))
            .build();
    }

    //Le bot ouvre, le héro se couche : occasion manquée, pas absence d'occasion
    private Hand foldToOpenHand() {
        Player h = new Player("hero","H",200); Player b = new Player("bot","B",200);
        h.setPosition(Position.BTN); b.setPosition(Position.UTG);
        h.receiveHoleCards(Card.fromString("7c"), Card.fromString("2d"));
        b.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Ad"));
        return new Hand.Builder().handId("fold3bet").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(h,b)).initialStates(List.of(PlayerState.of(h), PlayerState.of(b)))
            .addAction(new Action(b, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(h, ActionType.FOLD, BettingRound.PREFLOP))
            .build();
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
