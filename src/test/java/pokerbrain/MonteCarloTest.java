package pokerbrain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pokerbrain.model.core.Card;
import pokerbrain.simulator.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloTest {
    private static MonteCarloEngine engine;
    @BeforeAll static void setup() { engine = new MonteCarloEngine(); }

    @Test void acesAbove80() { assertTrue(eq("Ah","As",1) > 80); }
    @Test void worstBelow40() { assertTrue(eq("7h","2c",1) < 40); }
    @Test void moreOpponentsLessEquity() { assertTrue(eq("Kh","Qs",1) > eq("Kh","Qs",3)); }

    /**
     * Équité contre une range, et non plus seulement contre une main au hasard.
     * KQ dépareillé bat souvent une main quelconque, beaucoup moins souvent une range
     * de relance UTG bourrée d'as et de paires servies.
     */
    @Test void equityAgainstTightRangeIsLowerThanAgainstRandom() {
        List<Card> hero = List.of(Card.fromString("Kh"), Card.fromString("Qs"));
        pokerbrain.bot.RangeMap utg = pokerbrain.bot.BotProfileFactory.createTightAggressive()
            .getRangeFor(pokerbrain.model.game.Position.UTG);

        double vsRandom = engine.calculateEquity(hero, List.of(), 5000, 1).equity();
        double vsRange = engine.calculateEquity(hero, List.of(), 5000, 1, utg.asVillainRange()).equity();

        assertTrue(vsRange < vsRandom,
            "KQo vaut " + vsRange + "% contre une range UTG et " + vsRandom + "% contre une main aléatoire");
    }

    //Le filtre de range est bien appliqué : contre AA seul, KQo n'a presque aucune équité
    @Test void equityAgainstAcesOnly() {
        List<Card> hero = List.of(Card.fromString("Kh"), Card.fromString("Qs"));
        double eq = engine.calculateEquity(hero, List.of(), 3000, 1,
            (a, b) -> a / 4 == 12 && b / 4 == 12).equity(); // deux as
        assertTrue(eq < 20, "KQo contre AA vaut environ 13 %, obtenu : " + eq);
    }

    private double eq(String c1, String c2, int opp) {
        return engine.calculateEquity(List.of(Card.fromString(c1), Card.fromString(c2)), List.of(), 3000, opp).equity();
    }
}
