package pokerbrain.analyser.statistics;

/**
 * Statistiques calculées d'un joueur, immuable
 *
 * VPIP = Voluntarily Put money In Pot (hors blindes forcées)
 * PFR = Pre-Flop Raise percentage
 * 3-Bet = fréquence de relance face à une relance adverse preflop
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

	private final int threeBetCount;
	private final int threeBetOpportunities;

	private final int wentToShowDown;
	private final int wonAtShowDown;

	private final int totalWinnings;
	private final int bigBlind;

	//Constructeur complet
	public PlayerStatistics(String playerId, String playerName, int handsPlayed,int vpipCount,
			int pfrCount, int callCount, int agressiveCount, int threeBetCount, int threeBetOpportunities,
			int wentToShowDown, int wonAtShowDown, int totalWinnings, int bigBlind) {
		this.playerId = playerId;
		this.playerName = playerName;
		this.handsPlayed = handsPlayed;
		this.vpipCount = vpipCount;
		this.pfrCount = pfrCount;
		this.callCount = callCount;
		this.agressiveCount = agressiveCount;
		this.threeBetCount = threeBetCount;
		this.threeBetOpportunities = threeBetOpportunities;
		this.wentToShowDown = wentToShowDown;
		this.wonAtShowDown = wonAtShowDown;
		this.totalWinnings = totalWinnings;
		this.bigBlind = bigBlind;
	}

	
	//Constructeur sans données de 3-bet (le 3-bet est alors non mesuré)
	public PlayerStatistics(String playerId, String playerName, int handsPlayed,int vpipCount,
			int pfrCount, int callCount, int agressiveCount, int wentToShowDown, int wonAtShowDown,
			int totalWinnings, int bigBlind) {
		this(playerId, playerName, handsPlayed, vpipCount, pfrCount, callCount, agressiveCount,
			 0, 0, wentToShowDown, wonAtShowDown, totalWinnings, bigBlind);
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
	
	//Fréquence de 3-bet : relances face à une relance adverse / occasions de le faire
	public double getThreeBet() {
		if (this.threeBetOpportunities == 0) {
			return 0.0;
		}
		return this.threeBetCount * 100.0 / this.threeBetOpportunities;
	}

	//Vrai si le 3-bet a été mesuré sur au moins une occasion (sinon getThreeBet() n'a pas de sens)
	public boolean hasThreeBetSample() {
		return this.threeBetOpportunities > 0;
	}

	/**
	 * Facteur d'agressivité = (bets + raises) / calls.
	 *
	 * Un joueur qui ne suit jamais mais mise a une agressivité infinie : renvoyer 0.0
	 * dans ce cas ferait passer le joueur le plus agressif de la table pour un passif.
	 * On distingue donc « aucune action postflop » (0.0) de « jamais de call » (infini).
	 */
	public double getAF() {
		if (this.callCount == 0) {
			if (this.agressiveCount == 0) {
				return 0.0; // aucune donnée
			}
			return Double.POSITIVE_INFINITY;
		}
		return (double) this.agressiveCount / this.callCount;
	}

	//Nombre d'actions volontaires prises en compte dans l'AF (taille d'échantillon)
	public int getActionCount() {
		return this.agressiveCount + this.callCount;
	}

	//Went to show down : parmi les mains réellement jouées (VPIP), en pourcentage
	public double getWTSD() {
		int postflopHands = this.vpipCount; //main jouée ie en postflop, le joueur ne s'est pas couché de suite
		if(postflopHands == 0) {
			return 0.0;
		}
		return this.wentToShowDown * 100.0 / postflopHands;
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
    public String getProfile() {
        double vpip = getVPIP();
        double pfr  = getPFR();

        if (vpip < 15 && pfr < 12) {
            return "NIT";
        }
        if (vpip < 25 && pfr > 15){ 
            return "TAG";
        }
        if (vpip > 30 && pfr > 20) {
            return "LAG";
        }
        if (vpip > 40 && pfr < 10) {
            return "FISH";
        }
        if (vpip < 20 && getAF() > 2){ 
            return "ROCK";
        }
        return "UNKNOWN";
    }
    
    //TOusl es getters
    public String getPlayerId() {
    	return this.playerId;
    }
    
    public String getPlayerName() {
    	return this.playerName;
    }
    
    public int getHandsPlayed() {
    	return this.handsPlayed;
    }
    
    public int getVpipCount() {
    	return this.vpipCount;
    }
    
    public int getPfrCount() {
    	return this.pfrCount;
    }
    
    public int getAgressiveCount() {
    	return this.agressiveCount;
    }
    
    public int getCallCount() {
    	return this.callCount;
    }
    
    public int getThreeBetCount() {
    	return this.threeBetCount;
    }

    public int getThreeBetOpportunities() {
    	return this.threeBetOpportunities;
    }

    public int getWentToShowDown() {
    	return this.wentToShowDown;
    }
    
    public int getWonAtShowDown() {
    	return this.wonAtShowDown;
    }
    
    public int getTotalWinnings() {
    	return this.totalWinnings;
    }
    
    public int getBigBlind() {
    	return this.bigBlind;
    }
    
    @Override
    public String toString() {
        String af;
        if (Double.isInfinite(getAF())) {
            af = "∞";
        } else {
            af = String.format("%.1f", getAF());
        }
        return String.format("%s [%s] VPIP=%.0f%% PFR=%.0f%% 3Bet=%.0f%% AF=%s WTSD=%.0f%% WSD=%.0f%% BB/100=%.1f (%d mains)",
            this.playerName, getProfile(), getVPIP(), getPFR(), getThreeBet(), af,
            getWTSD(), getWSD(), getBBPer100(), this.handsPlayed);
    }
}
