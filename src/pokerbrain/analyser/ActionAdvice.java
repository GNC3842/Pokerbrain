package pokerbrain.analyser;

import pokerbrain.model.game.ActionType;
import pokerbrain.model.game.BettingRound;

/**
 * Conseil produit par le HandAnalyser pour une action précise
 *
 * Compare l'action réellement jouée à l'action recommandée,avec un raisonnement détaillé 
 * et un score de qualité
 *
 * SCORE DE QUALITÉ (0 à 100) :
 *   100 = action optimale jouée
 *   70+ = action correcte, pas optimale (ex: CALL au lieu de RAISE mais profitable)
 *   40-69 = action discutable (ex: CALL alors que fold était meilleur en EV)
 *   0-39 = erreur claire (ex: FOLD avec 75% d'équité et pot odds favorables)
 *
 * USAGE (dans ReplayController) :
 *   List<ActionAdvice> advices = HandAnalyser.analyse(hand, lookupTable);
 *   for (ActionAdvice advice : advices) {
 *       if (advice.getScore() < 70) {
 *           // Afficher le conseil en surbrillance
 *           display(advice.getReasoning());
 *       }
 *   }
 */

public class ActionAdvice {
	private final int actionIndex;	// index dans Hand.getActions()
    private final String playerName;
    private final BettingRound round;

    private final ActionType played; // ce que le joueur a fait
    private final int playedAmount;

    private final ActionType recommended; // ce qu'il aurait dû faire
    private final int recommendedAmount;

    private final int score; // 0-100 (qualité du coup joué)
    private final double equity; // équité MC au moment du coup (0.0-1.0)
    private final double potOdds;// cote du pot requise (0.0-1.0), -1 si pas applicable
    private final String reasoning;// explication lisible
    
    private ActionAdvice(Builder b) {
        this.actionIndex = b.actionIndex;
        this.playerName = b.playerName;
        this.round = b.round;
        this.played = b.played;
        this.playedAmount = b.playedAmount;
        this.recommended = b.recommended;
        this.recommendedAmount = b.recommendedAmount;
        this.score = b.score;
        this.equity = b.equity;
        this.potOdds = b.potOdds;
        this.reasoning = b.reasoning;
    }

}
