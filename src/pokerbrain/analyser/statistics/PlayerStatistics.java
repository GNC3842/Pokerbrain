package pokerbrain.analyser.statistics;

/**
 * Statistiques calculées d'un joueur, immuable
 *
 * VPIP = Voluntarily Put money In Pot (hors blindes forcées)
 * PFR = Pre-Flop Raise percentage
 * AF = Aggression Factor = (bets + raises) / calls
 * WTSD = Went To ShowDown percentage (parmi les mains jouées postflop)
 * WSD= Won at ShowDown percentage
 * BB/100= Big Blinds gagnées par 100 mains (winrate)
 *
 * PROFILS DÉTECTÉS :
 *   TAG  : VPIP < 25  et PFR > 15  -> Tight-Aggressive (optimal)
 *   LAG  : VPIP > 30  et PFR > 20  -> Loose-Aggressive
 *   FISH : VPIP > 40  et PFR < 10  -> Calling station
 *   NIT  : VPIP < 15  et PFR < 12  -> Selectif mais passif
 *   ROCK : VPIP < 20  et AF > 2    -> Selectif et agressif
 */


public class PlayerStatistics {
	//Info joueur
	private final String playerId;
	private final String playerName;
	
	//Compteurs bruts
	private final int handsPlayed;
	private final int vpipCount;
	private final int pfrCount;
	private final int callCount;
	private final int agressiveCount;
	
	private final int wentToShowDown;
	private final int wonAtShowDown;
	
	private final int totalWinnings;
	private final int bigBlind;
	
	//Constrcuteur
	public PlayerStatistics(String playerId, String playerName, int handsPlayed,int vpipCount,
			int pfrCount, int callCount, int agressiveCount, int wentToShowDown, int wonAtShowDown,
			int totalWinnings, int bigBlind) {
		this.playerId = playerId;
		this.playerName = playerName;
		this.handsPlayed = handsPlayed;
		this.vpipCount = vpipCount;
		this.pfrCount = pfrCount;
		this.callCount = callCount;
		this.agressiveCount = agressiveCount;
		this.wentToShowDown = wentToShowDown;
		this.wonAtShowDown = wonAtShowDown;
		this.totalWinnings = totalWinnings;
		this.bigBlind = bigBlind;
	}
	
	//Calcul des stats
	public double getVPIP() {
		if( this.handsPlayed == 0) {
			return 0.0;
		}
		return this.vpipCount*100.0 / this.handsPlayed;
	}
	
	public double getPFR() {
		if(this.handsPlayed == 0) {
			return 0.0;
		}
		return this.pfrCount*100.0 / this.handsPlayed;
	}
	
	//Facteur d'agressivité
	public double getAF() {
		if(this.callCount == 0) {
			return 0.0;
		}
		return (double) this.agressiveCount / this.callCount;
	}
	
	//Went to show down
	public double getWTSD() {
		int postflopHands = this.vpipCount; //main jouée ie en postflop, le joueur ne s'est aps couhcé de suite
		if(postflopHands == 0) {
			return 0.0;
		}
		return this.wentToShowDown / postflopHands;
	}
	
	//Won at show down
	public double getWSD() {
		if(this.wentToShowDown == 0) {
			return 0.0;
		}
		return this.wonAtShowDown*100.0 / this.wentToShowDown;
	}
	
	// BB/100 = gains nets / BB / mains * 100
    public double getBBPer100() {
        if (this.handsPlayed == 0 || this.bigBlind == 0) {
            return 0.0;
        }
        return (this.totalWinnings * 100.0) / (this.bigBlind * this.handsPlayed);
    }
    
    //Detcttion de profil en fonction de vpip et pfr
}
