package pokerbrain.analyser;

import pokerbrain.model.game.ActionType;
import pokerbrain.model.game.BettingRound;

import java.util.List;

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

    // Détail structuré du calcul, pour que l'UI puisse afficher autre chose qu'un texte
    /**
     * EV perdue par rapport au coup recommandé, en BB :
     *   0   = coup optimal, rien de perdu ;
     *   > 0 = erreur, et de combien ;
     *   -1  = non chiffrable.
     *
     * Le -1 n'est pas un oubli : l'écart « CALL au lieu de RAISE » ou « open trop petit »
     * demanderait de modéliser le déroulement de la relance (fold equity adverse, streets
     * suivantes), ce que l'analyseur ne fait pas. Les deux erreurs qui se ramènent à l'EV
     * d'un call — coucher un call profitable, payer un call perdant — sont chiffrées.
     */
    private final double evDelta;
    private final int outs;        // outs du héro sur un tirage (-1 = non applicable)
    private final double spr;      // stack-to-pot ratio au moment du coup (-1 = non applicable)
    private final String context;  // ex: "pot 3-bet, multiway, SPR 2.4"
    private final boolean analysable; // faux si l'action n'a pas pu être analysée

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
        this.evDelta = b.evDelta;
        this.outs = b.outs;
        this.spr = b.spr;
        this.context = b.context;
        this.analysable = b.analysable;
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
    
    public String getReasoning() {
    	return this.reasoning;
    }
    
    public double getEvDelta() {
    	return this.evDelta;
    }

    /**
     * Actions classées de la meilleure à la moins bonne dans ce spot, l'action recommandée
     * en tête. Déduit de `recommended` plutôt que stocké : une liste figée dans chaque
     * branche de l'analyseur se serait désynchronisée de la recommandation au premier
     * ajustement de seuil.
     *
     * Volontairement sans EV par alternative : seule celle du call est calculable sans
     * modéliser le déroulement d'une relance (voir `evDelta`). L'ordre, lui, est sûr.
     */
    public List<ActionType> getAlternatives() {
        if (this.recommended == null) return List.of();
        return switch (this.recommended) {
            case RAISE  -> List.of(ActionType.RAISE, ActionType.CALL, ActionType.FOLD);
            case ALL_IN -> List.of(ActionType.ALL_IN, ActionType.CALL, ActionType.FOLD);
            case CALL   -> List.of(ActionType.CALL, ActionType.FOLD);
            case FOLD   -> List.of(ActionType.FOLD, ActionType.CALL);
            case BET    -> List.of(ActionType.BET, ActionType.CHECK);
            case CHECK  -> List.of(ActionType.CHECK, ActionType.BET);
        };
    }

    public int getOuts() {
    	return this.outs;
    }

    public double getSpr() {
    	return this.spr;
    }

    public String getContext() {
    	return this.context;
    }

    //Faux quand l'action n'a pas pu être analysée (main incomplète, données manquantes).
    //Le conseil porte alors le motif dans reasoning et ne doit pas compter dans les scores.
    public boolean isAnalysable() {
    	return this.analysable;
    }

    //Methode utile
    public boolean isMistake() {
    	return this.analysable && this.played != this.recommended;
    }

    public boolean isCriticalMistake() {
    	return this.analysable && this.score < 40;
    }

    //ToString
    @Override
    public String toString() {
        if (!analysable) {
            return String.format("[%s] %s — non analysable : %s", round, playerName, reasoning);
        }
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
        private double evDelta = -1;
        private int outs = -1;
        private double spr = -1;
        private String context = "";
        private boolean analysable = true;


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

        public Builder evDelta(double v){
        	this.evDelta = v;
        	return this;
        }

        public Builder outs(int v){
        	this.outs = v;
        	return this;
        }

        public Builder spr(double v){
        	this.spr = v;
        	return this;
        }

        public Builder context(String v){
        	this.context = v;
        	return this;
        }

        public Builder analysable(boolean v){
        	this.analysable = v;
        	return this;
        }


        public ActionAdvice build() { 
        	return new ActionAdvice(this); 
        }

    }

}
