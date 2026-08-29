package pokerbrain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pokerbrain.analyser.ActionAdvice;
import pokerbrain.analyser.HandAnalyser;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Couvre les corrections de logique du HandAnalyser :
 * fuite de board, frontière de street, CHECK de la BB, ALL_IN, niveau de relance
 * preflop, tirages et robustesse.
 */
public class HandAnalyserTest {

    private static HandAnalyser analyser;

    @BeforeAll static void setup() { analyser = new HandAnalyser(); }

    /**
     * Sans GameState (cas d'une main rechargée depuis le disque), l'analyseur retombait
     * sur hand.getBoard(), c'est-à-dire les 5 cartes finales : l'équité d'une action au
     * flop était calculée en connaissant la river.
     *
     * Ici le héro a 7-2 dépareillé sur un flop A-K-Q : une main sans valeur. Ce n'est
     * qu'à la river qu'il touche un brelan de 2. Une équité élevée au flop prouverait
     * que l'analyseur a vu les cartes futures.
     */
    @Test void flopEquityIgnoresFutureCards() {
        Player hero = player("hero", "Hero", 200, Position.BTN, "2c", "7d");
        Player villain = player("bot", "Bot", 200, Position.BB, "Ts", "9d");

        Hand hand = new Hand.Builder()
            .handId("leak").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .board(cards("Ah", "Kh", "Qh", "2s", "2d"))
            .addAction(new Action(hero, ActionType.CHECK, BettingRound.FLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertTrue(advice.isAnalysable(), "l'action aurait dû être analysée");
        assertTrue(advice.getEquity() >= 0, "l'équité doit être calculée");
        assertTrue(advice.getEquity() < 0.35,
            "7-2 sur A-K-Q ne vaut pas " + advice.getEquity() + " : la river a fuité dans l'analyse");
    }

    /**
     * Le snapshot qui précède la première action d'une street est le dernier snapshot
     * de la street précédente : sa mise à égaler vaut encore le montant de l'open.
     * Le héro qui checke au flop était donc analysé comme s'il faisait face à une mise.
     */
    @Test void newStreetResetsBetLevel() {
        Player hero = player("hero", "Hero", 194, Position.BB, "Ah", "Kd");
        Player villain = player("bot", "Bot", 194, Position.BTN, "Ts", "9d");

        GameState lastPreflop = new GameState.Builder()
            .round(BettingRound.PREFLOP)
            .board(new ArrayList<>())
            .totalPot(12)
            .currentBetLevel(6)   // l'open preflop, déjà égalisé
            .players(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .build();

        Hand hand = new Hand.Builder()
            .handId("street").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .board(cards("Ac", "7d", "2s"))
            .addAction(new Action(villain, ActionType.CALL, 4, BettingRound.PREFLOP))
            .addGameState(lastPreflop)
            .addAction(new Action(hero, ActionType.CHECK, BettingRound.FLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertTrue(advice.getPotOdds() < 0,
            "un CHECK au flop ne fait face à aucune mise, la cote du pot n'a pas à être calculée");
    }

    /**
     * CHECK était normalisé en FOLD : la BB qui prend son option gratuitement était
     * notée comme si elle avait jeté sa main.
     */
    @Test void bigBlindCheckIsNotAFold() {
        Player hero = player("hero", "Hero", 200, Position.BB, "7c", "2d");
        Player villain = player("bot", "Bot", 200, Position.BTN, "Ts", "9d");

        Hand hand = new Hand.Builder()
            .handId("option").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .addAction(new Action(villain, ActionType.CALL, 2, BettingRound.PREFLOP))
            .addAction(new Action(hero, ActionType.CHECK, BettingRound.PREFLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertEquals(ActionType.CHECK, advice.getPlayed());
        assertTrue(advice.getScore() >= 90,
            "prendre son option gratuitement avec 72o est correct, score obtenu : " + advice.getScore());
    }

    /**
     * ALL_IN était écrasé en RAISE : un tapis de 100 BB avec AA était noté 100/100
     * comme une relance standard, alors que c'est un sizing qui perd de la valeur.
     */
    @Test void allInKeepsItsIdentityAndSizingIsJudged() {
        Player hero = player("hero", "Hero", 200, Position.BTN, "Ah", "Ad");
        Player villain = player("bot", "Bot", 200, Position.BB, "Ts", "9d");

        Hand hand = new Hand.Builder()
            .handId("shove").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .addAction(new Action(hero, ActionType.ALL_IN, 200, BettingRound.PREFLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertEquals(ActionType.ALL_IN, advice.getPlayed(), "l'ALL_IN ne doit plus être requalifié en RAISE");
        assertTrue(advice.getScore() < 70,
            "un tapis de 100 BB avec AA n'est pas un coup optimal, score obtenu : " + advice.getScore());
    }

    /**
     * currentBetLevel n'était jamais lu en preflop : face à un 3-bet, l'analyseur
     * comparait encore la main à une range d'open et pouvait recommander une relance.
     */
    @Test void facingThreeBetTightensTheReference() {
        Player hero = player("hero", "Hero", 200, Position.BTN, "8h", "7h");
        Player opener = player("a", "A", 200, Position.UTG, "Kc", "Qc");
        Player threeBettor = player("b", "B", 200, Position.CO, "Kd", "Kh");

        Hand hand = new Hand.Builder()
            .handId("3bet").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, opener, threeBettor))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(opener), PlayerState.of(threeBettor)))
            .addAction(new Action(opener, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .addAction(new Action(threeBettor, ActionType.RAISE, 18, BettingRound.PREFLOP))
            .addAction(new Action(hero, ActionType.CALL, 18, BettingRound.PREFLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertEquals(ActionType.FOLD, advice.getRecommended(),
            "87s face à un 3-bet se couche, la range d'open du bouton n'est plus la référence");
        assertTrue(advice.getContext().contains("3-bet"),
            "le contexte doit mentionner le 3-bet, obtenu : " + advice.getContext());
    }

    //Un open standard reste bien noté : la référence d'ouverture n'a pas été cassée
    @Test void standardOpenStillScoresWell() {
        Player hero = player("hero", "Hero", 200, Position.BTN, "Ah", "Kh");
        Player villain = player("bot", "Bot", 200, Position.BB, "Ts", "9d");

        Hand hand = new Hand.Builder()
            .handId("open").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .addAction(new Action(hero, ActionType.RAISE, 5, BettingRound.PREFLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertEquals(ActionType.RAISE, advice.getRecommended());
        assertEquals(100, advice.getScore());
    }

    //Un open de 8 BB est une bonne décision mal dimensionnée : le montant est jugé
    @Test void oversizedOpenIsPenalised() {
        Player hero = player("hero", "Hero", 200, Position.BTN, "Ah", "Kh");
        Player villain = player("bot", "Bot", 200, Position.BB, "Ts", "9d");

        Hand hand = new Hand.Builder()
            .handId("sizing").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .addAction(new Action(hero, ActionType.RAISE, 16, BettingRound.PREFLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertEquals(ActionType.RAISE, advice.getRecommended(), "la décision reste la bonne");
        assertTrue(advice.getScore() < 100, "un open de 8 BB doit être signalé comme surdimensionné");
    }

    //Les tirages sont comptés : As2s sur un flop à deux coeurs... ici tirage couleur à pique
    @Test void flushDrawIsCounted() {
        Player hero = player("hero", "Hero", 200, Position.BTN, "As", "2s");
        Player villain = player("bot", "Bot", 200, Position.BB, "Tc", "9d");

        Hand hand = new Hand.Builder()
            .handId("draw").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .board(cards("Ks", "7s", "3d"))
            .addAction(new Action(hero, ActionType.CHECK, BettingRound.FLOP))
            .build();

        ActionAdvice advice = analyser.analyse(hand).get(0);
        assertTrue(advice.getOuts() >= 9,
            "un tirage couleur vaut 9 outs, compté : " + advice.getOuts());
    }

    /**
     * Une main sans cartes fermées levait une IllegalStateException, attrapée plus haut,
     * ce qui faisait perdre silencieusement TOUS les conseils de la main.
     */
    @Test void missingHoleCardsProducesAdviceNotException() {
        Player hero = new Player("hero", "Hero", 200);
        hero.setPosition(Position.BTN);
        Player villain = player("bot", "Bot", 200, Position.BB, "Ts", "9d");

        Hand hand = new Hand.Builder()
            .handId("broken").heroId("hero").smallBlind(1).bigBlind(2)
            .players(List.of(hero, villain))
            .initialStates(List.of(PlayerState.of(hero), PlayerState.of(villain)))
            .addAction(new Action(hero, ActionType.RAISE, 6, BettingRound.PREFLOP))
            .build();

        List<ActionAdvice> advices = assertDoesNotThrow(() -> analyser.analyse(hand));
        assertEquals(1, advices.size(), "le conseil doit exister, même dégradé");
        assertFalse(advices.get(0).isAnalysable());
        assertFalse(advices.get(0).isCriticalMistake(), "une action non analysable n'est pas une erreur du joueur");
    }

    //HELPERS

    private static Player player(String id, String name, int stack, Position pos, String c1, String c2) {
        Player p = new Player(id, name, stack);
        p.setPosition(pos);
        p.receiveHoleCards(Card.fromString(c1), Card.fromString(c2));
        return p;
    }

    private static List<Card> cards(String... notations) {
        List<Card> list = new ArrayList<>();
        for (String n : notations) list.add(Card.fromString(n));
        return list;
    }
}
