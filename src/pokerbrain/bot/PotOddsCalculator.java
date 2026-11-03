package pokerbrain.bot;

/**
 * Calcule les pot odds, equity requise et expected value.
 * Utilitaire stateless — toutes les methodes sont statiques.
 */
public final class PotOddsCalculator {
	//COnstructeur vide
	private PotOddsCalculator() {}
	
    
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
 
}