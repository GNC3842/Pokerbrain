package pokerbrain.bot;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.game.*;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.*;

/*
 * Configration des bots, leur comportement dépend du profil fourni
 */

public class Bot {
	private final String playerId;
    private final BotProfile profile;
    private final Random rng;
    private final MonteCarloEngine engine;
    
    //Itération monte carlo
    private static final int MC_ITERATION = 10000;
    
    // Seuil d'équité à partir duquel on value-bet 
    private static final double VALUE_BET_THRESHOLD = 0.62;
    
    // Seuil en dessous duquel on considère une main "faible" (eligible bluff)
    private static final double BLUFF_EQUITY_MAX = 0.30;
    
	// Réduction du barrel frequency sur les streets suivantes
    private static final double TURN_BARREL_FACTOR  = 0.65;
    private static final double RIVER_BARREL_FACTOR = 0.45;
    
    
    /*
     * Constructeurs
     */
    
    //Constructeur sans montecralo engine: equity = 0.5
    public Bot(String playerId, BotProfile profile) {
        this(playerId, profile, null);
    }
    
    //Constructeur sans montecarlo engine
    public Bot(String playerId, BotProfile profile, MonteCarloEngine engine) {
    	if(playerId.isEmpty() || playerId == null) {
    		throw new IllegalArgumentException("playerId ne peut pas etre null");
    	}
        this.playerId = playerId;
        if(profile == null) {
        	throw new IllegalArgumentException("profile ne peut pas etre null");
        }
        this.profile = profile;
        this.engine = engine;
        this.rng = new Random();
    }
    
    //Décision principale: retourne l'action a faire
    public Action decide(BettingRound round,Player me,List<Card> board,int totalPot,int currentBetLevel,int bigBlind,List<ActionType> validActions) {
    	if (validActions == null || validActions.isEmpty()) {
    		throw new IllegalArgumentException("Aucune action valide");
    	}
    	if (round == BettingRound.PREFLOP) {
    		return decidePreflopFromHeatmap(me, currentBetLevel,bigBlind, round, validActions);
    		} else {
    		return decidePostflop(me, board, totalPot, currentBetLevel, round, validActions);
    	}
    }
    
    //Lecture diercte de la heatmap en preflop
    private Action decidePreflopFromHeatmap(Player me,int currentBetLevel, int bigBlind,BettingRound round, List<ActionType> valid) {
    	List<Card> hole = me.getHoleCards();
    	
    	Card c1 = hole.get(0);
    	Card c2 = hole.get(1);
    	boolean isSuited = c1.getSuit() == c2.getSuit();
    	Position pos = me.getPosition();
    	
    	//Lire action dans heatmap
    	ActionType heatmapAction = profile.getRangeFor(pos).getAction(c1.getRank(), c2.getRank(), isSuited);
    	
    	switch(heatmapAction) {
    	//la main est nulle donc on FOLD ou CHECK si possible 
    	case FOLD: 
    		if(valid.contains(ActionType.CHECK)) {
    			return buildAction(me,ActionType.CHECK,valid,0,round);
    		}
    		return buildAction(me,ActionType.FOLD,valid,0,round);
    	//la main est ok
    	case CALL:
    		return buildAction(me, ActionType.CALL, valid, 0, round);
    	//La main est bonne: si personen n'a relancé, on utilise openRaiseMultiplier sinon 3-bet (main premium) ou call
    	case RAISE:
    		boolean facingRaise = currentBetLevel > bigBlind;
    		if(!facingRaise) {
    			int openSize = (int)(bigBlind * profile.getOpenRaiseMultiplier());
    		}
    	}
    }
    
    //Lecture directe de la heatmap en postflop
    private Action decidePostFlop(Player me,List<Card> board, int totalPot,int currentBetLevel, BettingRound round, List<ActionType> valid) {
    	//Si mise il y a:
    		//Calculer la cote du Pot pour avoir si rentable
    		//Comparé équité et cote
    		//RAISE ou CALL en fonction du profile/niveau d'agressivité
    	//Sinon
    		//Au flop il mise aléatoirement en fonction de CBetFrequency
    		//Mise en fonction de ses probas
    }
    
    //Equité du bot
    private double getEquity() {
    	return this.equity;
    }
    
    //Construction de l'action avec avec repli si dispo
    private Action buildAction(Player me,ActionType wantedAction,List<ActionType> valid,int amount,BettingRound round) {
    	//Fonction dé sécurité
    	//Permet de lié l'action voulu par le bot et la liste des actions valdies
    }
    
    //Défini les mains premiums (les meilleurs mains au poker)
    //Valet-Valet, Dame-Dame, Roi-Roi, As-As, et As-Roi
    private boolean isPremium(Rank r1, Rank r2, boolean isSuited) {
    	int h = Math.max(r1.getValue(), r2.getValue());
    	int l = Math.min(r1.getValue(), r2.getValue());
    	if (h == l && h >= Rank.JACK.getValue()){
    		return true;
    	}
    	if (h == Rank.ACE.getValue() && l == Rank.KING.getValue()) {
    		return true;
    	}
    	return false;
    }
    
    //Liste des getters classiques
    public String getPlayerId() {
    	return this.playerId;
    }
    public BotProfile getProfile() {
    	return this.profile;
    }    
}
