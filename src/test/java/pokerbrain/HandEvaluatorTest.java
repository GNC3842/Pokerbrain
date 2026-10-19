package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.model.core.*;
import pokerbrain.engine.HandEvaluator;
import pokerbrain.simulator.FastHandEvaluator;
import pokerbrain.simulator.CardPrimitiveBridge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class HandEvaluatorTest {
    @Test void royalFlush() {
        assertEquals(HandType.ROYAL_FLUSH, HandEvaluator.evaluateBestHand(List.of(
            Card.fromString("As"), Card.fromString("Ks"), Card.fromString("Qs"),
            Card.fromString("Js"), Card.fromString("Ts"))).getType());
    }
    @Test void fullHouse() {
        assertEquals(HandType.FULL_HOUSE, HandEvaluator.evaluateBestHand(List.of(
            Card.fromString("Kh"), Card.fromString("Kd"), Card.fromString("Kc"),
            Card.fromString("5s"), Card.fromString("5h"))).getType());
    }
    @Test void higherPairWins() {
        HandRank kk = HandEvaluator.evaluateBestHand(List.of(Card.fromString("Kh"), Card.fromString("Kd"), Card.fromString("3c"), Card.fromString("5s"), Card.fromString("7h")));
        HandRank qq = HandEvaluator.evaluateBestHand(List.of(Card.fromString("Qh"), Card.fromString("Qd"), Card.fromString("3s"), Card.fromString("5c"), Card.fromString("7d")));
        assertTrue(kk.compareTo(qq) > 0);
    }
    @Test void decodeTypeFromFastEval() {
        assertEquals(HandType.ROYAL_FLUSH, fast(
            "As", "Ks", "Qs", "Js", "Ts", "2h", "3d").getType());
    }
    @Test void decodeKickersMatchHandEvaluator() {
        assertSameAsOracle("Kh", "Kd", "Kc", "5s", "5h", "2c", "3d");
    }

    /**
     * La classe canonique est ordonnée à l'envers de la force : 1 = quinte flush royale.
     * Un `>` oublié au lieu d'un `<` inverserait tous les showdowns, d'où ce garde-fou.
     */
    @Test void classeCanoniqueCroitQuandLaMainFaiblit() {
        int royale  = encodeAndEval("As", "Ks", "Qs", "Js", "Ts", "2h", "3d");
        int carre   = encodeAndEval("Ah", "Ad", "Ac", "As", "Ts", "2h", "3d");
        int paire   = encodeAndEval("Ah", "Ad", "9c", "7s", "5s", "4h", "3d");
        int hauteur = encodeAndEval("Ah", "Kd", "9c", "7s", "5s", "4h", "3d");
        assertEquals(1, royale);
        assertTrue(royale < carre, "la royale doit être la classe la plus petite");
        assertTrue(carre < paire);
        assertTrue(paire < hauteur);
        assertTrue(hauteur <= 7462);
    }

    /**
     * Régression : evaluateFlush7 gardait les 5 cartes les plus hautes de la couleur,
     * ce qui ratait la quinte flush dès qu'il y avait 6 ou 7 cartes assorties.
     * Lue « couleur à l'As » au lieu de « quinte flush au 9 ».
     */
    @Test void quinteFlushAvecSixCartesAssorties() {
        assertSameAsOracle("Ah", "9h", "8h", "7h", "6h", "5h", "2d");
        assertSameAsOracle("Ad", "Kd", "5d", "4d", "3d", "2d", "8h"); // roue en flush
        assertSameAsOracle("Ac", "Kc", "Qc", "Jc", "Tc", "9c", "8c"); // 7 assorties
    }

    /** Fuzz fast vs lent : l'évaluateur objet sert d'oracle. */
    @Test void fastConcordeAvecLOracleSurMainsAleatoires() {
        List<Card> deck = new ArrayList<>();
        for (Rank r : Rank.values()) for (Suit s : Suit.values()) deck.add(new Card(r, s));

        FastHandEvaluator evaluator = new FastHandEvaluator();
        Random rng = new Random(20261102L); // graine fixe : échec reproductible
        int[] enc = new int[7];

        for (int t = 0; t < 30_000; t++) {
            Collections.shuffle(deck, rng);
            List<Card> seven = new ArrayList<>(deck.subList(0, 7));
            for (int i = 0; i < 7; i++) enc[i] = CardPrimitiveBridge.encode(seven.get(i));
            HandRank attendu = HandEvaluator.evaluateBestHand(seven);
            HandRank obtenu  = FastHandEvaluator.toHandRank(evaluator.evaluate7(enc));
            assertEquals(attendu.getType(),    obtenu.getType(),    "type pour " + seven);
            assertEquals(attendu.getKickers(), obtenu.getKickers(), "kickers pour " + seven);
        }
    }

    // Helpers

    /**
     * La main finale se décrit en français. Le décodeur `toHandRank` existait sans aucun
     * appelant : au showdown comme au replay, rien n'était affiché.
     */
    @Test void laMainFinaleSeDecritEnFrancais() {
        assertEquals("Full aux Rois par les Dames",
            fast("Kh", "Kd", "Ks", "Qc", "Qd", "2s", "7h").describe());
        assertEquals("Couleur à l'As",
            fast("Ah", "3h", "Kh", "9h", "7h", "2c", "4d").describe());
        assertEquals("Quinte flush au 9",
            fast("9h", "8h", "7h", "6h", "5h", "Ah", "2c").describe());
        assertEquals("Paire d'As",
            fast("Ah", "Ad", "2c", "7d", "9s", "Jh", "4c").describe());
        assertEquals("Double paire, Rois et 7",
            fast("Kh", "Kd", "7c", "7d", "9s", "3h", "4c").describe());
    }

    private static int encodeAndEval(String... cards) {
        int[] enc = new int[7];
        for (int i = 0; i < 7; i++) enc[i] = CardPrimitiveBridge.encode(Card.fromString(cards[i]));
        return new FastHandEvaluator().evaluate7(enc);
    }

    private static HandRank fast(String... cards) {
        return FastHandEvaluator.toHandRank(encodeAndEval(cards));
    }

    private static void assertSameAsOracle(String... cards) {
        List<Card> seven = new ArrayList<>();
        for (String c : cards) seven.add(Card.fromString(c));
        HandRank attendu = HandEvaluator.evaluateBestHand(seven);
        HandRank obtenu  = fast(cards);
        assertEquals(attendu.getType(),    obtenu.getType(),    "type pour " + seven);
        assertEquals(attendu.getKickers(), obtenu.getKickers(), "kickers pour " + seven);
    }
}
