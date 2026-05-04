package pokerbrain.analyser.statistics;

import pokerbrain.model.game.*;
import pokerbrain.model.history.Hand;

import java.util.*;

/**
 * Calcule les statistiques d'un joueur à partir d'une liste de Hand
 *
 * RÈGLES DE COMPTAGE :
 *
 * VPIP (Voluntarily Put money In Pot) :
 *   On compte une main comme VPIP si le joueur a mis de l'argent preflop
 *   VOLONTAIREMENT, c'est-à-dire hors blindes forcées
 *   - CALL preflop -> VPIP (même si c'est juste compléter la BB)
 *   - RAISE preflop -> VPIP
 *   - ALL_IN preflop -> VPIP
 *   - CHECK (BB qui check) -> PAS VPIP
 *   - FOLD -> PAS VPIP
 *
 * PFR (Pre-Flop Raise) :
 *   Toute action RAISE ou BET ou ALL_IN en preflop
 *
 * AF (Aggression Factor) :
 *   (nombre total de BET + RAISE) / (nombre total de CALL)
 *   Sur TOUTES les streets (pas juste preflop)
 *
 * WTSD (Went To ShowDown) :
 *   Pourcentage de mains où le joueur est allé au showdown
 *   parmi celles où il a joué postflop (VPIP)
 *
 * WSD (Won at ShowDown) :
 *   Pourcentage de showdowns gagnés
 */

public class PlayerStatisticsCalculator {
	
	//Constructeur vide
	private PlayerStatisticsCalculator() {}
	
	public static PlayerStatistics calculate(List<Hand> hands, String playerId, int bigBlind) {
		String playerName = playerId;
        int handsPlayed = 0;
        int vpipCount = 0;
        int pfrCount = 0;
        int aggressiveCount = 0;
        int callCount = 0;
        int wentToShowdown = 0;
        int wonAtShowdown = 0;
        int totalInvested = 0;
        int totalWon = 0;
        
        for (Hand hand : hands) {
        	//On vérifie que le joueur a bien participé à la main
        	boolean isInHand = false;
        	for (Player p : hand.getPlayers()) {
        		if(p.getId().equals(playerId)) {
        			isInHand = true;
        			playerName = p.getName();
        			break;
        		}
        	}
        	if (!isInHand) continue;
        	handsPlayed ++;
        	
        	// Analyser les actions du joueur dans cette main
            boolean didVPIP = false;
            boolean didPFR  = false;
            boolean reachedShowdown = false;
            int investedThisHand = 0;
            
            for (Action action: hand.getActions()) {
            	if(!action.getPlayer().getId().equals(playerId)) continue;
            	ActionType type = action.getType();
            	BettingRound round = action.getRound();
            	
            	//Vpip: argent volontaire preflop
            	if(round == BettingRound.PREFLOP) {
            		if (type == ActionType.CALL || type == ActionType.RAISE|| type == ActionType.BET || type == ActionType.ALL_IN) {
                        didVPIP = true;
                    }
                    // PFR
                    if (type.isAgressive()) {
                        didPFR = true;
                    }
            	}
            	
            	//COmpteaurs globaux pour AF
            	if (type.isAgressive()) {
                    aggressiveCount++;
                }
                if (type == ActionType.CALL) {
                    callCount++;
                }
                
                //Investissement
                if (type.involvesMoney()) {
                    investedThisHand += action.getAmount();
                }
                
                //Détection river (approximation showdown)
                if (round == BettingRound.RIVER && type != ActionType.FOLD) {
                    reachedShowdown = true;
                }
            }
            if (didVPIP) {
            	vpipCount ++;
            }
            if(didPFR) {
            	pfrCount ++;
            }
            
            totalInvested += investedThisHand;
            
            //Showdown
            if (reachedShowdown) {
                wentToShowdown++;
                int winnings = hand.getWinnings().getOrDefault(playerId, 0);
                if (winnings > 0) {
                    wonAtShowdown++;
                }
            }
            // Gains
            int winAmount = hand.getWinnings().getOrDefault(playerId, 0);
            totalWon += winAmount;
        }
        
        //On peut maintenant calculer sur toutes ces mains
        int netWinnings = totalWon - totalInvested;
        return new PlayerStatistics(playerId, playerName,handsPlayed, vpipCount, pfrCount,
                aggressiveCount, callCount,wentToShowdown, wonAtShowdown,netWinnings, bigBlind);
	}

}
