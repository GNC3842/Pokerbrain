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
	private final int winAtShowDown;
	
	private final int totalWinnings;
	private final int bigBlind;
	
	//Constrcuteur
	public PlayerStatistics(String playerId, String playerName, int handsPlayed,int vpipCount,
			int pfrCount, int callCount, int agressiveCount, int wentToShowDown, int winAtShowDown,
			int totalWinnings, int bigBlind) {
		this.playerId = playerId;
		this.playerName = playerName;
		this.handsPlayed = handsPlayed;
		this.vpipCount = vpipCount;
		this.pfrCount = pfrCount;
		this.callCount = callCount;
		this.agressiveCount = agressiveCount;
		this.wentToShowDown = wentToShowDown;
		this.winAtShowDown = winAtShowDown;
		this.totalWinnings = totalWinnings;
		this.bigBlind = bigBlind;
	}
}
