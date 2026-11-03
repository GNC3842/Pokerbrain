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

    /**
     * L'état de table relu doit être celui qui a été joué.
     *
     * C'est la condition d'existence de l'analyse en production : toutes les mains
     * analysées viennent du disque, et sans `players` ni `currentBetLevel` dans les
     * snapshots, `ActionContext` voyait un `betLevel` à 0 — donc jamais de mise à
     * affronter, un SPR nul et aucun jugement de sizing.
     */
    @Test void etatDeTableIdentiqueApresRelecture() throws Exception {
        List<Player> players = new ArrayList<>(List.of(
            new Player("p0", "Alice", 200),
            new Player("p1", "Bob",   200),
            new Player("p2", "Chloe", 200)
        ));
        PokerGame game = new PokerGame(players, 1, 2);
        game.startNewHand();
        HandRecord rec = new HandRecord(game, "p0");

        // Preflop : une relance, puis tout le monde suit
        rec.recordAction(game.applyAction(ActionType.RAISE, game.getMinRaiseTo()));
        while (!game.isRoundComplete()) rec.recordAction(game.applyAction(ActionType.CALL));
        // Flop : une mise, puis tout le monde suit
        game.dealFlop();
        rec.recordAction(game.applyAction(ActionType.BET, 6));
        while (!game.isRoundComplete()) rec.recordAction(game.applyAction(ActionType.CALL));
        // Turn et river : tout le monde checke
        game.dealTurn();
        while (!game.isRoundComplete()) rec.recordAction(game.applyAction(ActionType.CHECK));
        game.dealRiver();
        while (!game.isRoundComplete()) rec.recordAction(game.applyAction(ActionType.CHECK));

        Hand original = rec.buildHand(game.showdown());
        Hand parsed = HandHistoryParser.parseString(HandHistoryWriter.serialize(original));

        assertEquals(original.getGameStates().size(), parsed.getGameStates().size());
        for (int i = 0; i < original.getGameStates().size(); i++) {
            GameState attendu = original.getGameStates().get(i);
            GameState relu    = parsed.getGameStates().get(i);
            String ou = "snapshot " + i + " (" + attendu.getRound() + ")";

            assertEquals(attendu.getTotalPot(), relu.getTotalPot(), ou + " : pot");
            assertEquals(attendu.getCurrentBetLevel(), relu.getCurrentBetLevel(), ou + " : mise à égaler");
            assertEquals(attendu.getBoard().size(), relu.getBoard().size(), ou + " : board");
            assertEquals(attendu.getPlayers().size(), relu.getPlayers().size(), ou + " : joueurs");

            for (PlayerState avant : attendu.getPlayers()) {
                PlayerState apres = relu.getPlayers().stream()
                    .filter(ps -> ps.getId().equals(avant.getId())).findFirst().orElseThrow();
                assertEquals(avant.getStack(), apres.getStack(), ou + " : tapis de " + avant.getId());
                assertEquals(avant.getStatus(), apres.getStatus(), ou + " : statut de " + avant.getId());
                // La mise de street n'est comparée que sur les joueurs encore en jeu :
                // collectBets ne remet à zéro que les non-couchés, donc un joueur couché
                // traîne en partie réelle une mise périmée que personne ne lit.
                if (avant.getStatus() != PlayerStatus.FOLDED) {
                    assertEquals(avant.getCurrentbet(), apres.getCurrentbet(),
                        ou + " : mise engagée de " + avant.getId());
                }
            }
        }
    }

    /**
     * Un nom qui contient un tiret faisait éclater la ligne PLAYERS en cinq morceaux :
     * la main entière était perdue sur une `ParseException`.
     */
    @Test void unNomAvecTiretNeCassePasLaMain() throws ParseException {
        Player p1 = new Player("hero", "Jean-Marc", 200);
        Player p2 = new Player("bot",  "Bob", 200);
        p1.setPosition(Position.BTN); p1.setSeat(1);
        p2.setPosition(Position.BB);  p2.setSeat(2);
        p1.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        p2.receiveHoleCards(Card.fromString("Qs"), Card.fromString("Jd"));
        Hand h = new Hand.Builder()
            .handId("tiret").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(p1, p2))
            .initialStates(List.of(PlayerState.of(p1), PlayerState.of(p2)))
            .addAction(new Action(p1, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(p2, ActionType.FOLD, BettingRound.PREFLOP))
            .build();

        Hand parsed = HandHistoryParser.parseString(HandHistoryWriter.serialize(h));
        assertEquals("Jean-Marc", parsed.getInitialStates().get(0).getName());
        assertEquals(2, parsed.getActions().size());
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
