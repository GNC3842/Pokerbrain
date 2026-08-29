package pokerbrain.analyser.statistics;

import pokerbrain.model.game.*;
import pokerbrain.model.history.Hand;
import pokerbrain.model.history.PlayerState;

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
 * 3-BET :
 *   Relance preflop alors qu'une seule relance a déjà eu lieu (l'open).
 *   Le dénominateur est le nombre de fois où le joueur a eu l'occasion de le faire,
 *   c'est-à-dire où il a parlé face à un open non encore relancé.
 *
 * AF (Aggression Factor) :
 *   (nombre total de BET + RAISE) / (nombre total de CALL)
 *   Sur TOUTES les streets (pas juste preflop)
 *
 * WTSD (Went To ShowDown) :
 *   Pourcentage de mains où le joueur est allé au showdown
 *   parmi celles où il a joué postflop (VPIP)
 *   Un showdown = le joueur ne s'est pas couché, le board est complet (5 cartes)
 *   et au moins un adversaire est également resté : les cartes ont donc été comparées.
 *
 * WSD (Won at ShowDown) :
 *   Pourcentage de showdowns gagnés
 *
 * INVESTISSEMENT :
 *   Action.getAmount() est bien un delta (le montant réellement ajouté au pot par
 *   cette action, cf. BettingRoundManager.processAction), il est donc cumulable.
 *   En revanche les blindes ne sont pas enregistrées comme des Action : elles sont
 *   ajoutées séparément, sans quoi le gain net serait surévalué de 1 SB ou 1 BB par main.
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
        int threeBetCount = 0;
        int threeBetOpportunities = 0;
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
            boolean didFold = false;
            boolean hadThreeBetSpot = false;
            boolean didThreeBet = false;
            int investedThisHand = postedBlind(hand, playerId);

            // Niveau de relance preflop : 1 = la BB, 2 = l'open, 3 = le 3-bet…
            int preflopRaiseCount = 0;

            for (Action action: hand.getActions()) {
            	ActionType type = action.getType();
            	BettingRound round = action.getRound();
            	boolean isHero = action.getPlayer().getId().equals(playerId);

            	// Les relances adverses doivent être comptées avant d'évaluer
            	// l'occasion de 3-bet du joueur analysé.
            	if (!isHero) {
            		if (round == BettingRound.PREFLOP && type.isAgressive()) {
            			preflopRaiseCount++;
            		}
            		continue;
            	}

            	if(round == BettingRound.PREFLOP) {
            		//Vpip: argent volontaire preflop
            		if (type == ActionType.CALL || type == ActionType.RAISE|| type == ActionType.BET || type == ActionType.ALL_IN) {
                        didVPIP = true;
                    }
                    // PFR
                    if (type.isAgressive()) {
                        didPFR = true;
                    }
                    // 3-bet : exactement une relance devant soi. Se coucher est aussi une
                    // occasion de 3-bet (elle compte au dénominateur). On ne retient que
                    // le premier spot de la main pour ne pas compter deux fois.
                    if (!hadThreeBetSpot && preflopRaiseCount == 1) {
                        hadThreeBetSpot = true;
                        if (type.isAgressive()) {
                            didThreeBet = true;
                        }
                    }
                    if (type.isAgressive()) {
                        preflopRaiseCount++;
                    }
            	}

            	//Compteurs globaux pour AF
            	if (type.isAgressive()) {
                    aggressiveCount++;
                }
                if (type == ActionType.CALL) {
                    callCount++;
                }

                //Investissement (getAmount() est un delta, cf. javadoc de classe)
                if (type.involvesMoney()) {
                    investedThisHand += action.getAmount();
                }

                if (type == ActionType.FOLD) {
                    didFold = true;
                }
            }
            if (didVPIP) {
            	vpipCount ++;
            }
            if(didPFR) {
            	pfrCount ++;
            }
            if (hadThreeBetSpot) {
            	threeBetOpportunities++;
            	if (didThreeBet) {
            		threeBetCount++;
            	}
            }

            totalInvested += investedThisHand;

            //Showdown : cartes réellement comparées, et non « une action à la river »
            if (!didFold && reachedShowdown(hand)) {
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
        return new PlayerStatistics(playerId, playerName, handsPlayed, vpipCount, pfrCount,
                callCount, aggressiveCount, threeBetCount, threeBetOpportunities,
                wentToShowdown, wonAtShowdown, netWinnings, bigBlind);
	}

	/**
	 * Montant de blinde forcée posté par le joueur dans cette main.
	 * Les blindes ne passent pas par une Action, il faut donc les retrouver
	 * depuis la position occupée au moment de la distribution.
	 * En heads-up, c'est le BTN qui poste la small blind (cf. PokerGame.postBlinds).
	 */
	private static int postedBlind(Hand hand, String playerId) {
		Position position = null;
		int stack = Integer.MAX_VALUE;
		boolean hasSmallBlindSeat = false;
		int dealtIn = 0;

		for (PlayerState ps : hand.getInitialStates()) {
			dealtIn++;
			if (ps.getPosition() == Position.SB) {
				hasSmallBlindSeat = true;
			}
			if (ps.getId().equals(playerId)) {
				position = ps.getPosition();
				stack = ps.getStack();
			}
		}
		if (position == null) return 0;

		boolean headsUp = (dealtIn == 2) && !hasSmallBlindSeat;
		if (position == Position.BB) {
			return Math.min(hand.getBigBlind(), stack);
		}
		if (position == Position.SB || (headsUp && position == Position.BTN)) {
			return Math.min(hand.getSmallBlind(), stack);
		}
		return 0;
	}

	/**
	 * Vrai si la main s'est terminée par une confrontation des cartes :
	 * board complet et au moins deux joueurs non couchés.
	 * Remplace l'approximation « une action à la river qui n'est pas un fold »,
	 * qui ratait les all-in précoces et comptait les mains où tout le monde se
	 * couche face à une mise river.
	 */
	private static boolean reachedShowdown(Hand hand) {
		if (hand.getBoard() == null || hand.getBoard().size() < 5) {
			return false;
		}
		Set<String> folded = new HashSet<>();
		for (Action a : hand.getActions()) {
			if (a.getType() == ActionType.FOLD) {
				folded.add(a.getPlayer().getId());
			}
		}
		int stillIn = 0;
		for (Player p : hand.getPlayers()) {
			if (!folded.contains(p.getId())) {
				stillIn++;
			}
		}
		return stillIn >= 2;
	}
}
