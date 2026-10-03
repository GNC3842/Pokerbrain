package pokerbrain;

import org.junit.jupiter.api.Test;
import pokerbrain.engine.EquityCalculator;
import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EquityCalculatorTest {

    private static List<Card> cards(String... codes) {
        return java.util.Arrays.stream(codes).map(Card::fromString).toList();
    }

    //Vérification d'une somme tjrs à 100% 
    @Test void sommeCentPourCent() {
        double[] r = EquityCalculator.calculate(
            cards("Ah", "Kh"), cards("Qs", "Qd"), cards("2c", "7d", "9s"));
        assertEquals(100.0, r[0] + r[1] + r[2], 1e-9);
    }

    //Si le board est dit "complet" et que les joueurs jouent le board, l'égalité est parfaite : 100% pour le board, 0% pour les joueurs.
    @Test void egaliteSurBoardComplet() {
        double[] r = EquityCalculator.calculate(
            cards("2c", "3d"), cards("2h", "3s"),
            cards("Ah", "Kh", "Qh", "Jh", "Th"));   // couleur du board pour tout le monde
        assertArrayEquals(new double[]{0.0, 0.0, 100.0}, r, 1e-9);
        assertEquals(50.0, r[0] + r[2] / 2.0, 1e-9, "équité héro telle que l'UI la calcule");
    }

    //Board complet et main gagnante : 100 % sans égalité
    @Test void gagnantNetSurBoardComplet() {
        double[] r = EquityCalculator.calculate(
            cards("Ad", "As"), cards("Kd", "Ks"),
            cards("Ah", "7h", "2c", "5d", "9s"));
        assertArrayEquals(new double[]{100.0, 0.0, 0.0}, r, 1e-9);
    }

    //AA contre KK preflop : équité de référence connue, environ 82,4 % pour les As
    // (82,36 % hero win, 0,39 % d'égalité sur les 1 712 304 boards).
    // Verrouille à la fois l'énumération et l'ordre des classes canoniques.
    @Test void aaContreKkPreflop() {
        double[] r = EquityCalculator.calculate(
            cards("Ah", "Ad"), cards("Kh", "Kd"), List.of());
        double heroEquity = r[0] + r[2] / 2.0;
        assertEquals(82.6, heroEquity, 0.5, "AA vs KK preflop ≈ 82,6 %");
        assertEquals(100.0, r[0] + r[1] + r[2], 1e-9);
    }

    //Le Monte-Carlo doit converger vers l'équité exacte contre un adversaire aléatoire.
    //
    // Sur un board complet, cette équité se calcule exactement : on énumère les
    // C(45,2) = 990 mains adverses possibles et on moyenne. Les deux chemins passent par
    // des codes différents (énumération vs tirage, buffers vs cartes fixes), donc l'accord
    // valide le tirage du MC, l'ordre des classes canoniques et la gestion des égalités.

    @Test void monteCarloConvergeVersLExact() {
        List<Card> hero  = cards("Ah", "Kh");
        List<Card> board = cards("2c", "7d", "9s", "Td", "4c");

        List<Card> deck = new ArrayList<>();
        for (Rank r : Rank.values()) for (Suit s : Suit.values()) deck.add(new Card(r, s));
        deck.removeAll(hero);
        deck.removeAll(board);

        double somme = 0;
        int combos = 0;
        for (int i = 0; i < deck.size(); i++) {
            for (int j = i + 1; j < deck.size(); j++) {
                double[] r = EquityCalculator.calculate(hero, List.of(deck.get(i), deck.get(j)), board);
                somme += r[0] + r[2] / 2.0;
                combos++;
            }
        }
        assertEquals(990, combos, "C(45,2) mains adverses");
        double exact = somme / combos;

        double mc = new MonteCarloEngine().calculateEquity(hero, board, 20_000, 1).equity();

        assertEquals(exact, mc, 1.5,
            "MC " + mc + " % vs exact " + exact + " % — au-delà de 1,5 point, "
            + "soupçonner un ordre de classes inversé, pas du bruit");
    }

    @Test void refuseUneMainIncomplete() {
        assertThrows(IllegalArgumentException.class, () ->
            EquityCalculator.calculate(cards("Ah"), cards("Qs", "Qd"), List.of()));
    }
}
