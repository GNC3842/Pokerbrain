package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.bot.*;
import pokerbrain.engine.GameSession;
import pokerbrain.engine.PokerGame;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.Hand;
import pokerbrain.simulator.MonteCarloEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class BotTest {
    @Test void tagFoldTrash() { assertEquals(ActionType.FOLD, decide("2h","7c", Position.UTG, BotProfileFactory.createTightAggressive())); }
    @Test void tagRaiseAces() { assertEquals(ActionType.RAISE, decide("Ah","As", Position.BTN, BotProfileFactory.createTightAggressive())); }
    @Test void fishDoesntRaiseWeak() {
        ActionType a = decide("9h","6c", Position.BTN, BotProfileFactory.createCallingStation());
        assertNotEquals(ActionType.RAISE, a);
    }
    /**
     * Les blindes ont leurs propres heatmaps. Le repli SB→BTN / BB→CO leur faisait jouer
     * la range d'une position de fin : les deux mains ci-dessous étaient couchées.
     */
    @Test void lesBlindesJouentLeursPropresRanges() {
        BotProfile tag = BotProfileFactory.createTightAggressive();
        // 76s : ouverture SB (tag_sb.csv), absente de la range BTN
        assertEquals(ActionType.RAISE, decide("7h", "6h", Position.SB, tag));
        // J8s : défense BB face à un open (tag_bb_vs_open.csv), absente de la range CO
        assertEquals(ActionType.CALL, decide("Jh", "8h", Position.BB, tag));
    }

    @Test void nullIdThrows() { assertThrows(IllegalArgumentException.class, () -> new Bot(null, BotProfileFactory.createTightAggressive())); }
    @Test void neverInvalidAction() {
        Bot bot = new Bot("t", BotProfileFactory.createTightAggressive());
        Player me = makePlayer("Ah","Kh", Position.BTN);
        List<ActionType> valid = List.of(ActionType.FOLD, ActionType.CALL);
        Action a = bot.decide(BettingRound.FLOP, me, List.of(Card.fromString("2c"),Card.fromString("3d"),Card.fromString("7s")), 50, 20, 2, valid, 2);
        assertTrue(valid.contains(a.getType()));
    }

    /**
     * Face à une mise qui couvre son tapis, `getValidActions` ne propose que FOLD et
     * ALL_IN : le bot lisait « personne n'a misé » (il cherchait CALL), n'y trouvait pas
     * CHECK, et se couchait — ici avec une quinte flush royale.
     */
    @Test void neSeCouchePasFaceAUneMiseQuiLeCouvre() {
        Bot bot = new Bot("t", BotProfileFactory.createTightAggressive(), new MonteCarloEngine());
        Player me = new Player("t", "T", 50);
        me.receiveHoleCards(Card.fromString("Ah"), Card.fromString("Kh"));
        me.setPosition(Position.BTN);
        List<Card> board = List.of(Card.fromString("Qh"), Card.fromString("Jh"), Card.fromString("Th"));

        Action a = bot.decide(BettingRound.FLOP, me, board, 400, 200, 10,
            List.of(ActionType.FOLD, ActionType.ALL_IN), 1);

        assertEquals(ActionType.ALL_IN, a.getType(), "les nuts ne se couchent pas");
    }

    /**
     * Garde-fou du livelock : le bot dimensionne au pot et demandait parfois une relance
     * sous le minimum légal. `BettingRoundManager` levait, `GameController` avalait
     * l'exception et rappelait le même joueur, qui reprenait la même décision
     * déterministe. `GameSession` borne maintenant le montant : toute main se termine.
     * Vérifie au passage que le héro enregistré est bien celui du siège choisi.
     */
    @Test void lesBotsJouentDesMainsEntieres() {
        Random rng = new Random(7);
        for (int main = 0; main < 12; main++) {
            List<Player> seated = new ArrayList<>();
            List<Bot> bots = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                seated.add(new Player("joueur" + i, "J" + i, 100 + rng.nextInt(300)));
                // Une instance de moteur par bot : MonteCarloEngine porte des buffers.
                bots.add(new Bot("joueur" + i, BotProfileFactory.createTightAggressive(),
                                 new MonteCarloEngine()));
            }

            GameSession session = new GameSession(seated, 0, bots, 5, 10);
            session.startNewHand();
            PokerGame g = session.getGame();

            int gardeFou = 0;
            while (!g.isHandOver()) {
                assertTrue(gardeFou++ < 500, "main " + main + " : la boucle de jeu ne converge pas");
                if (g.isRoundComplete()) {
                    switch (g.getCurrentRound()) {
                        case PREFLOP -> g.dealFlop();
                        case FLOP    -> g.dealTurn();
                        case TURN    -> g.dealRiver();
                        case RIVER   -> g.showdown();
                    }
                    continue;
                }
                Player current = g.getCurrentPlayer();
                Action decision = session.decideBotAction(
                    session.getBot(current.getId()).orElseThrow(), current);
                session.applyAction(decision.getType(), decision.getAmount());
            }

            Hand hand = session.buildAndCloseHand();
            assertNotNull(hand, "main " + main + " : main non enregistrée");
            assertEquals("joueur0", hand.getHeroId(), "l'id du héro vient du siège choisi");
        }
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