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

    /**
     * Conservation des jetons sur des mains jouées au hasard jusqu'au bout, 2 à 6 joueurs.
     * C'est l'invariant du chemin argent : PotManager.findWinners attribue les pots, et
     * depuis le passage à FastHandEvaluator il exige un board complet et deux cartes par
     * joueur éligible. Un side pot mal découpé, un showdown qui lève, ou un gagnant mal
     * désigné se voient tous ici — la somme des tapis doit être inchangée.
     */
    @Test void lesJetonsSontConserves() {
        Random rng = new Random(99); // graine fixe : échec reproductible
        int aboutis = 0;

        for (int seats = 2; seats <= 6; seats++) {
            for (int hand = 0; hand < 60; hand++) {
                List<Player> seated = new ArrayList<>();
                for (int i = 0; i < seats; i++) {
                    seated.add(new Player("p" + i, "J" + i, 100 + rng.nextInt(400)));
                }
                int before = seated.stream().mapToInt(Player::getStack).sum();
                Map<String, Integer> startStacks = new HashMap<>();
                for (Player p : seated) startStacks.put(p.getId(), p.getStack());

                PokerGame g = new PokerGame(seated, 5, 10);
                g.startNewHand();
                while (!g.isHandOver()) {
                    if (g.isRoundComplete()) {
                        switch (g.getCurrentRound()) {
                            case PREFLOP -> g.dealFlop();
                            case FLOP    -> g.dealTurn();
                            case TURN    -> g.dealRiver();
                            case RIVER   -> g.showdown();
                        }
                        continue;
                    }
                    List<ActionType> valid = g.getValidActions();
                    ActionType pick = valid.get(rng.nextInt(valid.size()));
                    switch (pick) {
                        case RAISE -> g.applyAction(pick, g.getMinRaiseTo());
                        case BET   -> g.applyAction(pick, 10);
                        default    -> g.applyAction(pick);
                    }
                }

                int after = seated.stream().mapToInt(Player::getStack).sum();
                assertEquals(before, after,
                    seats + " joueurs, main " + hand + " : jetons créés ou perdus");
                assertTrue(g.isHandOver(), seats + " joueurs, main " + hand + " : main non terminée");

                // Un joueur couché ne peut que perdre ce qu'il a engagé — jamais finir
                // plus riche qu'au départ. Attrape les pots attribués à un joueur couché,
                // que la conservation des jetons laisse passer.
                for (Player p : seated) {
                    if (p.isFolded()) {
                        assertTrue(p.getStack() <= startStacks.get(p.getId()),
                            p.getName() + " s'est couché et a pourtant gagné des jetons ("
                            + startStacks.get(p.getId()) + " → " + p.getStack() + ")");
                    }
                }
                // Toute main résolue a reversé des jetons à quelqu'un : sans cela
                // Hand.winnings part vide sur disque et le BB/100 devient négatif.
                assertFalse(g.getWinnings().isEmpty(),
                    seats + " joueurs, main " + hand + " : aucun gain enregistré");
                aboutis++;
            }
        }
        assertEquals(300, aboutis, "toutes les mains doivent se résoudre");
    }

    /**
     * Tout montant de mise est un TOTAL à atteindre sur la street.
     *
     * La BB ne doit rien à payer (callAmount == 0) alors qu'une mise de 2 est en jeu :
     * elle recevait donc BET, et son « relance à 4 » ajoutait 4 à ses 2 → total 6, avec
     * 4 jetons sortis du tapis au lieu de 2. Elle reçoit maintenant RAISE, dont le
     * minimum tient compte de ce qu'elle a déjà engagé.
     */
    @Test void laMiseEstUnTotalAAtteindre() {
        List<Player> hu = List.of(new Player("btn", "A", 200), new Player("bb", "B", 200));
        PokerGame g = new PokerGame(new ArrayList<>(hu), 1, 2);
        g.startNewHand();

        g.applyAction(ActionType.CALL); // le BTN complète à 2, la parole revient à la BB
        Player bb = g.getCurrentPlayer();
        assertEquals(2, bb.getCurrentBet(), "la BB a posté la grosse blinde");
        assertFalse(g.getValidActions().contains(ActionType.BET),
            "une mise est déjà en jeu : c'est RAISE, pas BET");
        assertEquals(4, g.getMinRaiseTo());

        g.applyAction(ActionType.RAISE, 4);
        assertEquals(4, bb.getCurrentBet(), "relance À 4, pas +4");
        assertEquals(196, bb.getStack());
    }

    /**
     * Le bouton tourne sur les sièges, pas modulo la liste des joueurs encore en jeu :
     * à quatre joueurs dont le premier saute, il passait du siège 1 au siège 3 en oubliant
     * le 2 (et pouvait reculer, ou rester deux mains de suite sur le même joueur).
     */
    @Test void leBoutonAvanceDansLOrdreDesSieges() {
        List<Player> table = List.of(
            new Player("a", "A", 200), new Player("b", "B", 200),
            new Player("c", "C", 200), new Player("d", "D", 200));
        PokerGame g = new PokerGame(new ArrayList<>(table), 1, 2);

        g.startNewHand();
        assertEquals("B", porteurDuBouton(table), "première main : bouton au siège 1");
        while (!g.isHandOver()) g.applyAction(ActionType.FOLD);

        table.get(0).setStack(0); // A est éliminé
        g.startNewHand();
        assertEquals("C", porteurDuBouton(table),
            "le bouton passe au siège suivant, pas deux crans plus loin");
    }

    private String porteurDuBouton(List<Player> table) {
        return table.stream().filter(p -> p.getPosition() == Position.BTN)
            .findFirst().orElseThrow().getName();
    }

    private void completeRound() {
        while (!game.isRoundComplete() && !game.isHandOver()) {
            List<ActionType> v = game.getValidActions();
            if (v.contains(ActionType.CHECK)) game.applyAction(ActionType.CHECK);
            else if (v.contains(ActionType.CALL)) game.applyAction(ActionType.CALL);
            else game.applyAction(ActionType.FOLD);
        }
    }
}
