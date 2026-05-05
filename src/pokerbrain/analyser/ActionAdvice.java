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
    
    //les getters as usal
    public int getActionIndex() {
    	return this.actionIndex;
    }
    
    public String getPlayerName() {
    	return this.playerName;
    }
    
    public BettingRound getRound() {
    	return this.round;
    }
    
    public ActionType getPlayed() {
    	return this.played;
    }
    
    public int getPlayedAmount() {
    	return this.playedAmount;
    }
    
    public ActionType getRecommended() {
    	return this.recommended;
    }
    
    public int getRecommendedAmount() {
    	return this.recommendedAmount;
    }
    
    public int getScore() {
    	return this.score;
    }
    
    public double getEquity() {
    	return this.equity;
    }
    
    public double getPotOdds() {
    	return this.potOdds;
    }
    
    public String getReasonning() {
    	return this.reasoning;
    }
    
    //Methode utile
    public boolean isMistake() {
    	return this.played != this.recommended;
    }
    
    public boolean isCriticalMistake() {
    	return this.score < 40;
    }
    
    //ToString
    @Override
    public String toString() {
        return String.format("[%s] %s — Joué: %s | Optimal: %s | Score: %d/100 — %s",
            round, playerName, played, recommended, score, reasoning);
    }
    
    //Et le builder
    public static final class Builder{
    	private int actionIndex;
        private String playerName = "";
        private BettingRound round;
        private ActionType played;
        private int playedAmount;
        private ActionType recommended;
        private int recommendedAmount;
        private int score = 100;
        private double equity = -1;
        private double potOdds = -1;
        private String reasoning = "";
        
        public Builder actionIndex(int v) {
        	this.actionIndex = v;
        	return this;
        }
        
        public Builder playerName(String v) {
        	this.playerName = v;
        	return this;
        }
        
        public Builder round(BettingRound v) {
        	this.round = v;
        	return this;
        }
        
        public Builder played(ActionType v) {
        	this.played = v;
        	return this;
        }
        
        public Builder playedAmount(int v) {
        	this.playedAmount = v;
        	return this;
        }
        
        public Builder recommended(ActionType v) {
        	this.recommended = v;
        	return this;
        }
        
        public Builder recommendedAmount(int v) { 
        	this.recommendedAmount = v; 
        	return this; 
        }
        
        public Builder score(int v){ 
        	this.score = Math.max(0, Math.min(100, v));
        	return this; 
        }
        
        public Builder equity(double v){
        	this.equity = v; 
        	return this; 
        }
        
        public Builder potOdds(double v){ 
        	this.potOdds = v; 
        	return this; 
        }
        
        public Builder reasoning(String v){ 
        	this.reasoning = v;
        	return this; 
        }
        
        public ActionAdvice build() { 
        	return new ActionAdvice(this); 
        }

    }

}
