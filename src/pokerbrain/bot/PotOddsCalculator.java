package pokerbrain.bot;

import pokerbrain.model.core.Rank;
import pokerbrain.model.game.ActionType;

/**
 * Calcule les pot odds, equity requise et expected value.
 * Utilitaire stateless — toutes les methodes sont statiques.
 */
public final class PotOddsCalculator {
	//COnstructeur vide
	private PotOddsCalculator() {}
	
	/**
     * Pot odds en ratio : combien on risque pour combien on peut gagner.
     * Ex: pot=100, call=25 -> ratio=4.0 (on risque 1 pour en gagner 4)
     */
    public static double potOddsRatio(int pot, int callAmount) {
        if (callAmount <= 0) {
        	return Double.MAX_VALUE;
        }
        return (double) pot / callAmount;
    }
    
    /**
     * Equite minimale pour que le call soit profitable.
     * breakEvenEquity = call / (pot + call)
     */
    public static double breakEvenEquity(int pot, int callAmount) {
        if (callAmount <= 0) {
        	return 0.0;
        }
        return (double) callAmount / (pot + callAmount);
    }
    
    /*Value attendue d'un call.
    * EV = equity * (pot + call) - (1 - equity) * call
    *    = equity * pot - (1 - equity) * call + equity * call - equity * call
    *    = equity * pot - call + equity * call
    */
    public static double callEV(double equity, int pot, int callAmount) {
        return equity * pot - (1.0 - equity) * callAmount;
    }
    
    //Valeur attendue d'un bluff/bet
    public static double betEV(double foldEquity, double equity, int pot, int betAmount) {
        double evIfFold = foldEquity * pot;
        double evIfCall = (1.0 - foldEquity) * (equity * (pot + betAmount) - (1.0 - equity) * betAmount);
        return evIfFold + evIfCall;
    }
    
    //Taille de mise en fraction du pot pour un bluff avec break-even.
    // betFraction = foldEquityNeeded / (1 - foldEquityNeeded)
    public static double bluffBreakEvenFoldEquity(double betFractionOfPot) {
        return betFractionOfPot / (1.0 + betFractionOfPot);
    }
    
    //Outs vers equity approximative (regle des 2 et 4).
    // Au flop (2 cartes a venir) : outs * 4 = equity%
    //A la turn (1 carte a venir): outs * 2 = equity%
    public static double outsToEquity(int outs, int cardsTocome) {
    	int multiplier;
    	if(cardsTocome ==2) {
    		multiplier= 4;
    	} else {
    		multiplier =2;
    	}
        return Math.min(1.0, outs * multiplier / 100.0);
    }
    
    //Definit l'action pour une main suited specifique (ex: AKs)
    public RangeMap setSuited(Rank high, Rank low, ActionType action) {
    	
    }
    
    //Definit l'action pour toutes les mains suited high-X avec X >= minLow
    public RangeMap setSuitedFrom(Rank high, Rank minLow, ActionType action) {
    	
    }
    
    //Defini l'action pour une main offsuit specifique
    public RangeMap setOffsuit(Rank high, Rank low, ActionType action) {
    	
    }
    
    //efinit l'action pour toutes les mains offsuit high-X avec X >= minLow
    public RangeMap setOffsuitFrom(Rank high, Rank minLow, ActionType action) {
    	
    }
    
    //Consulter les mains à jouer
    public ActionType getAction(Rank rank1, Rank rank2, boolean isSuited) {
    	
    }
    
    //PPourcentage de mains jouees (CALL ou RAISE) — correspond au VPIP theorique
    public double getVPIP() {
    	
    }
    
    //Pourcentage de mains ou on relance (RAISE) — correspond au PFR theorique
    public double getPFR() {
    	
    }
    
    public static double outsToEquity(int outs, int cardsTocome) {
    	
    }

    @Override
    public String toString() {
        return String.format("RangeMap[VPIP=%.1f%% PFR=%.1f%%]", getVPIP(), getPFR());
    }
}