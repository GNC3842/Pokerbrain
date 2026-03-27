package pokerbrain.bot;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.game.*;
//import pokerbrain.simulator.MonteCarloEngine;

import java.util.*;

/*
 * Configration des bots, leur comportement dépend du profil fourni
 */

public class Bot {
	private final String playerId;
    private final BotProfile profile;
    private final Random rng;
    private final double equity = 0.5;
    
    
    //Constructeur sans montecarlo engine
    public Bot(String playerId, BotProfile profile) {
    	if(playerId.isEmpty() || playerId == null) {
    		throw new IllegalArgumentException("playerId ne peut pas etre null");
    	}
        this.playerId = playerId;
        if(profile == null) {
        	throw new IllegalArgumentException("profile ne peut pas etre null");
        }
        this.profile = profile;
        this.rng = new Random();
    }
    
    //Décision principale: retourne l'action a faire
    public Action decide(BettingRound round,Player me,List<Card> board,int totalPot,int currentBetLevel,List<ActionType> validActions) {
    	if (validActions == null || validActions.isEmpty()) {
    		throw new IllegalArgumentException("Aucune action valide");
    	}
    	if (round == BettingRound.PREFLOP) {
    		return decidePreflopFromHeatmap(me, currentBetLevel, round, validActions);
    		} else {
    		return decidePostflop(me, board, totalPot, currentBetLevel, round, validActions);
    	}
    }
    
    //Lecture diercte de la heatmap en preflop
    private Action decidePreflopFromHeatmap(Player me,int currentBetLevel,BettingRound round, List<ActionType> valid) {
    	//Regarder ses 2 cartes
    	//Regarde le profile associé pour savoir quoi faire avec la main
    	
    	//Adaptation aux relances: si HeatMap indique RAISE mais que qlq à déja raise
    	//le bot relance si main premium sinon CALL
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
