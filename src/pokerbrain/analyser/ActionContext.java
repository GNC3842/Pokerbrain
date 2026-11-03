package pokerbrain.analyser;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.GameState;
import pokerbrain.model.history.Hand;
import pokerbrain.model.history.PlayerState;

import java.util.ArrayList;
import java.util.List;

/**
 * État de la table reconstitué JUSTE AVANT une action donnée.
 *
 * Pourquoi cette classe : l'analyseur lisait auparavant l'objet `Player` vivant, qui est
 * muté tout au long de la main — au moment de l'analyse il porte donc les valeurs de FIN
 * de main, pas celles de l'instant analysé. Et quand le snapshot manquait, le board
 * retombait sur `hand.getBoard()`, c'est-à-dire les 5 cartes finales : l'équité d'une
 * action au flop était calculée en connaissant la river.
 *
 * Deux règles corrigent cela :
 *
 *  1. Le board ne vient JAMAIS du snapshot : il est déduit du round de l'action
 *     (PREFLOP→0, FLOP→3, TURN→4, RIVER→5) et tronqué sur `hand.getBoard()`.
 *     Le board ne bouge pas à l'intérieur d'une street, donc c'est exact, et il est
 *     impossible de voir une carte future.
 *
 *  2. Les valeurs de street (mise à égaler, mise déjà engagée) ne sont reprises du
 *     snapshot que s'il appartient AU MÊME round. Le snapshot précédent une première
 *     action de flop est le dernier snapshot preflop : sa mise à égaler vaut encore
 *     le montant de l'open, ce qui faisait passer un joueur qui checke pour un joueur
 *     face à une mise.
 */
final class ActionContext {

    final BettingRound round;
    final List<Card> board;
    final int totalPot;        // pot avant l'action, mises de la street incluses
    final int betLevel;        // montant total à égaler sur cette street
    final int heroBet;         // ce que le héro a déjà engagé sur cette street
    final int heroStack;       // tapis du héro avant l'action
    final int effectiveStack;  // min(tapis héro, plus gros tapis adverse encore en jeu)
    final Position heroPosition;
    final int opponents;       // adversaires encore en jeu (non couchés, non éliminés)
    final int playersBehind;   // adversaires qui parlent après le héro sur cette street (-1 si inconnu)
    final int bigBlind;

    private ActionContext(BettingRound round, List<Card> board, int totalPot, int betLevel,
                          int heroBet, int heroStack, int effectiveStack,
                          Position heroPosition, int opponents, int playersBehind, int bigBlind) {
        this.round = round;
        this.board = board;
        this.totalPot = totalPot;
        this.betLevel = betLevel;
        this.heroBet = heroBet;
        this.heroStack = heroStack;
        this.effectiveStack = effectiveStack;
        this.heroPosition = heroPosition;
        this.opponents = opponents;
        this.playersBehind = playersBehind;
        this.bigBlind = bigBlind;
    }

    //Montant à payer pour suivre (0 si le héro peut checker)
    int callAmount() {
        return Math.max(0, betLevel - heroBet);
    }

    boolean facingBet() {
        return callAmount() > 0;
    }

    //Stack-to-pot ratio : combien de pots il reste derrière. Sous 3, on est engagé.
    double spr() {
        if (totalPot <= 0) return -1;
        return (double) effectiveStack / totalPot;
    }

    double stackInBigBlinds() {
        if (bigBlind <= 0) return -1;
        return (double) heroStack / bigBlind;
    }

    /**
     * Reconstitue le contexte précédant `action`.
     * `stateBefore` est le snapshot pris après l'action précédente, ou null s'il n'y en a pas.
     */
    static ActionContext build(Hand hand, Action action, GameState stateBefore) {
        BettingRound round = action.getRound();
        String heroId = action.getPlayer().getId();
        int bigBlind = hand.getBigBlind();

        List<Card> board = boardForRound(hand, round);

        // Le snapshot n'est utilisable pour les valeurs de street que s'il est du même round
        boolean sameRound = stateBefore != null && stateBefore.getRound() == round;

        int totalPot;
        if (stateBefore != null) {
            // Le pot est cumulatif : même venu du round précédent, il est correct
            totalPot = stateBefore.getTotalPot();
        } else {
            totalPot = hand.getSmallBlind() + hand.getBigBlind();
        }

        int betLevel;
        int heroBet;
        if (sameRound) {
            betLevel = stateBefore.getCurrentBetLevel();
            heroBet = betOf(stateBefore, heroId);
        } else if (round == BettingRound.PREFLOP) {
            // Début du preflop : les blindes sont déjà postées
            betLevel = bigBlind;
            heroBet = blindOf(hand, heroId);
        } else {
            // Début d'une street postflop : plus personne n'a de mise engagée
            betLevel = 0;
            heroBet = 0;
        }

        int heroStack = 0;
        int maxOpponentStack = 0;
        int opponents = 0;
        Position heroPosition = null;

        List<PlayerState> reference = (stateBefore != null)
            ? stateBefore.getPlayers() : hand.getInitialStates();

        for (PlayerState ps : reference) {
            if (ps.getId().equals(heroId)) {
                heroStack = ps.getStack();
                heroPosition = ps.getPosition();
            } else if (ps.getStatus() != PlayerStatus.FOLDED && ps.getStatus() != PlayerStatus.OUT) {
                opponents++;
                maxOpponentStack = Math.max(maxOpponentStack, ps.getStack());
            }
        }
        if (heroPosition == null) {
            heroPosition = positionFromInitialStates(hand, heroId);
        }

        int effectiveStack = Math.min(heroStack, maxOpponentStack);
        if (effectiveStack <= 0) {
            effectiveStack = heroStack;
        }

        return new ActionContext(round, board, totalPot, betLevel, heroBet, heroStack,
                                 effectiveStack, heroPosition, Math.max(1, opponents),
                                 countPlayersBehind(reference, heroId, round), bigBlind);
    }

    /**
     * Adversaires encore en jeu qui parlent APRÈS le héro sur cette street.
     *
     * L'ordre de parole n'est pas conservé dans les snapshots, et n'a pas besoin de l'être :
     * il est entièrement déterminé par la position et le round
     * (`Position.speakingRank`). Seuls les joueurs ACTIVE sont comptés — un joueur couché
     * ou déjà à tapis ne prendra plus de décision derrière.
     */
    private static int countPlayersBehind(List<PlayerState> states, String heroId,
                                          BettingRound round) {
        boolean preflop = round == BettingRound.PREFLOP;
        int heroRank = -1;
        for (PlayerState ps : states) {
            if (ps.getId().equals(heroId) && ps.getPosition() != null) {
                heroRank = ps.getPosition().speakingRank(preflop);
            }
        }
        if (heroRank < 0) return -1;

        int behind = 0;
        for (PlayerState ps : states) {
            if (ps.getId().equals(heroId) || ps.getPosition() == null) continue;
            if (ps.getStatus() != PlayerStatus.ACTIVE) continue;
            if (ps.getPosition().speakingRank(preflop) > heroRank) behind++;
        }
        return behind;
    }

    //Board visible pendant ce round — jamais plus, jamais les cartes futures
    private static List<Card> boardForRound(Hand hand, BettingRound round) {
        int expected;
        switch (round) {
            case FLOP:  expected = 3; break;
            case TURN:  expected = 4; break;
            case RIVER: expected = 5; break;
            default:    expected = 0; break;
        }
        List<Card> full = hand.getBoard();
        if (full == null || expected == 0) return new ArrayList<>();
        return new ArrayList<>(full.subList(0, Math.min(expected, full.size())));
    }

    private static int betOf(GameState state, String playerId) {
        for (PlayerState ps : state.getPlayers()) {
            if (ps.getId().equals(playerId)) return ps.getCurrentbet();
        }
        return 0;
    }

    //Blinde postée par le joueur, les blindes n'étant pas enregistrées comme des Action
    private static int blindOf(Hand hand, String playerId) {
        Position pos = positionFromInitialStates(hand, playerId);
        if (pos == null) return 0;
        boolean hasSmallBlindSeat = false;
        for (PlayerState ps : hand.getInitialStates()) {
            if (ps.getPosition() == Position.SB) hasSmallBlindSeat = true;
        }
        boolean headsUp = hand.getInitialStates().size() == 2 && !hasSmallBlindSeat;
        if (pos == Position.BB) return hand.getBigBlind();
        if (pos == Position.SB || (headsUp && pos == Position.BTN)) return hand.getSmallBlind();
        return 0;
    }

    private static Position positionFromInitialStates(Hand hand, String playerId) {
        for (PlayerState ps : hand.getInitialStates()) {
            if (ps.getId().equals(playerId)) return ps.getPosition();
        }
        return null;
    }
}
