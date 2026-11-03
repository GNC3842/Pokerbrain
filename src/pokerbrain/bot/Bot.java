package pokerbrain.bot;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.game.*;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.*;

/**
 * Bot de poker configurable par profil (heatmap preflop + parametres postflop).
 *
 * DECISION PREFLOP :
 *   1. Consulter la heatmap du profil pour la position courante
 *   2. La heatmap retourne FOLD / CALL / RAISE
 *   3. Adapter l'action aux actions légales (validActions)
 *
 * DECISION POSTFLOP :
 *   1. Estimer l'équité via Monte Carlo (si engine disponible) ou fallback 0.5
 *   2. Face à une mise : comparer équité vs cote du pot -> call/fold/raise
 *   3. Sans mise : c-bet / value-bet / bluff selon paramètres du profil
 *
 * CONSTRUCTION DES ACTIONS :
 *   buildAction() est le point de sécurité : si l'action voulue n'est pas
 *   dans validActions, elle tombe en cascade vers un fallback (CHECK > FOLD).
 */
public class Bot {
    private final String playerId;
    private final BotProfile profile;
    private final Random rng;
    private final MonteCarloEngine engine;

    // Itérations Monte Carlo pour l'estimation d'équité
    private static final int MC_ITERATIONS = 10000;

    // Seuil d'équité pour value-bet
    private static final double VALUE_BET_THRESHOLD = 0.62;

    // Seuil sous lequel on considère la main faible (eligible bluff)
    private static final double BLUFF_EQUITY_MAX = 0.30;

    // Réduction du barrel frequency sur les streets suivantes
    private static final double TURN_BARREL_FACTOR  = 0.65;
    private static final double RIVER_BARREL_FACTOR = 0.45;

    //Constructeurs

    //Constructeur sans Monte Carlo engine - equity estimée à 0.5 au cas où
    public Bot(String playerId, BotProfile profile) {
        this(playerId, profile, null);
    }

    //Constructeur normal 
    public Bot(String playerId, BotProfile profile, MonteCarloEngine engine) {
        if (playerId == null || playerId.isEmpty()) {
            throw new IllegalArgumentException("playerId ne peut pas être null ou vide");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile ne peut pas être null");
        }
        this.playerId = playerId;
        this.profile = profile;
        this.engine = engine;
        this.rng = new Random();
    }

    //Point d'entrée : retourne l'action que le bot décide de jouer.
    public Action decide(BettingRound round, Player me, List<Card> board,
                         int totalPot, int currentBetLevel, int bigBlind,
                         List<ActionType> validActions, int nbAdversaires) {
        if (validActions == null || validActions.isEmpty()) {
            throw new IllegalArgumentException("Aucune action valide");
        }

        if (round == BettingRound.PREFLOP) {
            return decidePreflopFromHeatmap(me, currentBetLevel, bigBlind, round, validActions);
        } else {
            return decidePostflop(me, board, totalPot, currentBetLevel, bigBlind, round, validActions, nbAdversaires);
        }
    }

    //Décision en Preflop par lecture de la heatmap
    private Action decidePreflopFromHeatmap(Player me, int currentBetLevel,
                                             int bigBlind, BettingRound round,
                                             List<ActionType> valid) {
        List<Card> hole = me.getHoleCards();
        Card c1 = hole.get(0);
        Card c2 = hole.get(1);
        boolean isSuited = c1.getSuit() == c2.getSuit();
        Position pos = me.getPosition();

        // Lire l'action dans la heatmap
        ActionType heatmapAction = profile.getRangeFor(pos)
                .getAction(c1.getRank(), c2.getRank(), isSuited);

        switch (heatmapAction) {
            case FOLD:
                // La main est nulle -> CHECK si possible, sinon FOLD
                if (valid.contains(ActionType.CHECK)) {
                    return buildAction(me, ActionType.CHECK, valid, 0, round);
                }
                return buildAction(me, ActionType.FOLD, valid, 0, round);

            case CALL:
                // La main est correcte 6> CALL si quelqu'un a misé, CHECK sinon
                if (valid.contains(ActionType.CALL)) {
                    return buildAction(me, ActionType.CALL, valid, 0, round);
                }
                if (valid.contains(ActionType.CHECK)) {
                    return buildAction(me, ActionType.CHECK, valid, 0, round);
                }
                return buildAction(me, ActionType.FOLD, valid, 0, round);

            case RAISE:
                boolean facingRaise = currentBetLevel > bigBlind;

                if (!facingRaise) {
                    // Personne n'a relancé -> open raise
                    int openSize = (int)(bigBlind * profile.getOpenRaiseMultiplier());
                    if (valid.contains(ActionType.RAISE)) {
                        return buildAction(me, ActionType.RAISE, valid, openSize, round);
                    }
                    if (valid.contains(ActionType.BET)) {
                        return buildAction(me, ActionType.BET, valid, openSize, round);
                    }
                } else {
                    // Facing un raise -> 3-bet si main premium, sinon call
                    if (isPremium(c1.getRank(), c2.getRank(), isSuited)) {
                        int threeBetSize = currentBetLevel * 3;
                        if (valid.contains(ActionType.RAISE)) {
                            return buildAction(me, ActionType.RAISE, valid, threeBetSize, round);
                        }
                    }
                    // Main RAISE non premium face à un raise -> call
                    if (valid.contains(ActionType.CALL)) {
                        return buildAction(me, ActionType.CALL, valid, 0, round);
                    }
                }
                // Fallback : CHECK ou FOLD
                if (valid.contains(ActionType.CHECK)) {
                    return buildAction(me, ActionType.CHECK, valid, 0, round);
                }
                return buildAction(me, ActionType.FOLD, valid, 0, round);

            default:
                return buildAction(me, ActionType.FOLD, valid, 0, round);
        }
    }

    /**
     * Prise de décision postflop basée sur l'équité MC et les paramètres du profil
     *
     * Logique :
     *   - Face à une mise ->comparer equity vs cote du pot
     *   - Pas de mise -> c-bet / value-bet / bluff selon profil
     *   - Sur turn/river, réduire la fréquence de barrel
     */
    private Action decidePostflop(Player me, List<Card> board, int totalPot,
                                   int currentBetLevel, int bigBlind,
                                   BettingRound round, List<ActionType> valid, int nbAdversaires) {
        double equity = estimateEquity(me, board, nbAdversaires);

        // Facteur de barrel (réduction sur turn/river)
        double barrelFactor = 1.0;
        if (round == BettingRound.TURN) {
            barrelFactor = TURN_BARREL_FACTOR;
        }
        if (round == BettingRound.RIVER) {
            barrelFactor = RIVER_BARREL_FACTOR;
        }

        /*
         * Faire face à une mise se lit sur les montants, pas sur la présence de CALL :
         * `getValidActions` retire CALL quand la mise couvre le tapis (il ne reste que
         * FOLD et ALL_IN). Le bot passait alors dans la branche « personne n'a misé »,
         * n'y trouvait pas CHECK, et se couchait — y compris avec les nuts.
         */
        boolean facingBet = currentBetLevel > me.getCurrentBet();

        if (facingBet) {
            //analyse des pots odds
            int callAmount = Math.max(1, currentBetLevel - me.getCurrentBet());
            double breakEven = PotOddsCalculator.breakEvenEquity(totalPot, callAmount);

            if (equity >= VALUE_BET_THRESHOLD && valid.contains(ActionType.RAISE)) {
                // Équité excellente -> raise pour value
                int raiseSize = (int)(totalPot * profile.getCBetSizingFraction()) + currentBetLevel;
                return buildAction(me, ActionType.RAISE, valid, raiseSize, round);
            }

            if (equity >= breakEven) {
                // Équité suffisante pour call profitablement
                return buildAction(me, ActionType.CALL, valid, 0, round);
            }

            // Equity insuffisante -> fold to raise selon profil
            if (rng.nextDouble() < profile.getFoldToRaisePercent()) {
                return buildAction(me, ActionType.FOLD, valid, 0, round);
            }
            // Parfois call quand même (calling station)
            if (valid.contains(ActionType.CALL)) {
                return buildAction(me, ActionType.CALL, valid, 0, round);
            }
            return buildAction(me, ActionType.FOLD, valid, 0, round);

        } else {
            //Pas de mise : c-bet / value-bet / bluff
            if (equity >= VALUE_BET_THRESHOLD) {
                // Value bet
                if (valid.contains(ActionType.BET)) {
                    int betSize = Math.max(bigBlind,
                        (int)(totalPot * profile.getCBetSizingFraction()));
                    return buildAction(me, ActionType.BET, valid, betSize, round);
                }
            }

            // C-bet (modifié par barrel factor)
            double effectiveCBet = profile.getCBetFrequency() * barrelFactor;
            if (rng.nextDouble() < effectiveCBet && valid.contains(ActionType.BET)) {
                int betSize = Math.max(bigBlind,
                    (int)(totalPot * profile.getCBetSizingFraction()));
                return buildAction(me, ActionType.BET, valid, betSize, round);
            }

            // Bluff occasionnel (main faible)
            if (equity <= BLUFF_EQUITY_MAX
                    && rng.nextDouble() < profile.getBluffFrequency() * barrelFactor
                    && valid.contains(ActionType.BET)) {
                int bluffSize = Math.max(bigBlind, (int)(totalPot * 0.5));
                return buildAction(me, ActionType.BET, valid, bluffSize, round);
            }

            // Sinon check
            if (valid.contains(ActionType.CHECK)) {
                return buildAction(me, ActionType.CHECK, valid, 0, round);
            }
            return buildAction(me, ActionType.FOLD, valid, 0, round);
        }
    }

    //Estimation d'équité
    private double estimateEquity(Player me, List<Card> board, int nbAdversaires) {
        if (engine == null) {
            return 0.5;
        }

        List<Card> hole = me.getHoleCards();
        if (hole.size() != 2) {
            throw new IllegalStateException("Le bot " + playerId + " n'a pas exactement 2 cartes : " + hole.size()
        + ". Bug dans DealerEngine ou Player.clearHoleCards() appelé trop tôt.");
        }

        try {
            MonteCarloEngine.EquityResult result =
                engine.calculateEquity(hole, board, MC_ITERATIONS, Math.max(1,nbAdversaires));
            return result.equity() / 100.0; // equity() retourne un pourcentage
        } catch (Exception e) {
            return 0.5;
        }
    }

    //Construction d'action sécurisée cad en vérifiant que wantedAction est dans validActions
    private Action buildAction(Player me, ActionType wanted,
                                List<ActionType> valid, int amount,
                                BettingRound round) {
        // Si l'action voulue est directement disponible
        if (valid.contains(wanted)) {
            if (wanted.involvesMoney()) {
                // Borner le montant au stack
                int effectiveAmount = Math.min(amount, me.getStack());
                if (effectiveAmount <= 0) {
                    effectiveAmount = 1;
                }

                // Si le montant dépasse le stack → ALL_IN
                if (amount >= me.getStack() && valid.contains(ActionType.ALL_IN)) {
                    return new Action(me, ActionType.ALL_IN, me.getStack(), round);
                }
                return new Action(me, wanted, effectiveAmount, round);
            }
            return new Action(me, wanted, round);
        }

        // Cascade de fallback
        if (wanted == ActionType.RAISE && valid.contains(ActionType.CALL)) {
            return buildAction(me, ActionType.CALL, valid, 0, round);
        }
        // Suivre une mise qui couvre le tapis, c'est y aller à tapis — pas se coucher.
        if (wanted == ActionType.CALL && valid.contains(ActionType.ALL_IN)) {
            return new Action(me, ActionType.ALL_IN, me.getStack(), round);
        }
        if (wanted == ActionType.BET && valid.contains(ActionType.CHECK)) {
            return new Action(me, ActionType.CHECK, round);
        }
        if (valid.contains(ActionType.CHECK)) {
            return new Action(me, ActionType.CHECK, round);
        }
        if (valid.contains(ActionType.FOLD)) {
            return new Action(me, ActionType.FOLD, round);
        }
        // Dernier recours
        if (valid.contains(ActionType.ALL_IN)) {
            return new Action(me, ActionType.ALL_IN, me.getStack(), round);
        }

        //Cas extrememe au cas oùùùù
        throw new IllegalStateException(
            "Aucun fallback trouvé pour " + wanted + " parmi " + valid);
    }

    //Helpers

    //Définit les mains premium : JJ+, AKs, AKo
    private boolean isPremium(Rank r1, Rank r2, boolean isSuited) {
        int h = Math.max(r1.getValue(), r2.getValue());
        int l = Math.min(r1.getValue(), r2.getValue());
        // Paires JJ+
        if (h == l && h >= Rank.JACK.getValue()) {
            return true;
        }
        // AK
        if (h == Rank.ACE.getValue() && l == Rank.KING.getValue()) {
            return true;
        }
        return false;
    }

    //Getters

    public String getPlayerId() { 
        return this.playerId; 
    }
    public BotProfile getProfile() { 
        return this.profile;
    }
}
