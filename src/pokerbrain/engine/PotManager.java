package pokerbrain.engine;

import java.util.*;
import pokerbrain.model.core.*;
import pokerbrain.model.game.*;

/*
 * Gere les contributions au pot principal (celui du tour en cours) et les side pots
 * Un joueur ALL_IN ne peut gagner que la part du pot à laquelle il a contribué
 * Pour chaque palier de ALL8IN on crée un pot avec les joueurs élgibles
 * 
 * Exemple si 
 * A (satck 100) ALL IN
 * B (stack 300) ALL IN
 * C (stack 500) ALL IN
 * Alors un side pot avec 300 contient B et C
 * 		 un side pot avec 100 contient A, B et C
 */

public class PotManager {
	//montant investi par le joueur dans le round en cours
	//réinitialisé à chaque BettingRound
	private final Map<Player, Integer> roundContributions;
	
	//Side pot déja calculés
	private final List<SidePot> finalisedPots;
	
	//Constructeur
	public PotManager() {
		this.roundContributions = new LinkedHashMap<>();
		this.finalisedPots      = new ArrayList<>();
	}
	
	//Enregistrer les mises
	public void addBet(Player player, int amount) {
		if (player == null) {
			throw new IllegalArgumentException("Player ne peut pas etre null");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("Amount ne peut pas etre négatif");
		}
		player.removeFromStack(amount);
		this.roundContributions.merge(player, amount, Integer::sum);
		player.setCurrentBet(this.roundContributions.getOrDefault(player, 0));
	}
	
	
	public void addBlind(Player player, int amount) {
		addBet(player,amount);
	}
	
	//Calcul des side pots
	public void collectBets(List<Player> nonFoldedPlayers) {
		if (nonFoldedPlayers == null || nonFoldedPlayers.isEmpty()) {
			return;
		}
		if (this.roundContributions.isEmpty()) {
			return;
		}
		//Player ayant contribué pendant ce tour trié par contribution croissante
		List<Player> allInsThisRound = new ArrayList<>();
		for(Player p : this.roundContributions.keySet()) {
			if(p.isAllIn()) {
				allInsThisRound.add(p);
			}
		}
		allInsThisRound.sort(Comparator.comparingInt(p->this.roundContributions.getOrDefault(p,0)));
		
		int alreadyTaken = 0;
		
		for(Player allIn : allInsThisRound) {
			int cap = this.roundContributions.getOrDefault(allIn, 0);
			if(cap <= alreadyTaken) {
				continue;
			}
			int potAmount = 0;
			for(int c : this.roundContributions.values()) {
				potAmount += Math.min(c,cap) - Math.min(c, alreadyTaken);
			}
			if(potAmount >0) {
				List<Player> eligible = buildEligible(nonFoldedPlayers,cap);
				awardOrRegister(eligible,potAmount);
			}
			alreadyTaken = cap;
		}
		
		//Pot principal
		final int taken = alreadyTaken;
		int mainAmount = 0;
		for(int c : this.roundContributions.values()) {
			mainAmount += Math.max(0, c - taken);
		}
		if (mainAmount > 0) {
            List<Player> eligible = buildEligibleMain(nonFoldedPlayers, taken);
            awardOrRegister(eligible, mainAmount);
        }
		
		// Réinitialiser le round courant
        this.roundContributions.clear();
        for (Player p : nonFoldedPlayers) {
        	p.setCurrentBet(0);
        }
	}
	
	//Si 1 seul éligible → retour immédiat. Sinon → side pot finalisé
	private void awardOrRegister(List<Player> eligible,int amount) {
		if(eligible.isEmpty()) {
			return;
		}
		if(eligible.size() == 1) {
			eligible.get(0).addToStack(amount);
		} else {
			this.finalisedPots.add(new SidePot(amount,eligible));
		}
	}
	
	//Eligibles pour un palier all-in : contribution >= cap CE round
	private List<Player> buildEligible(List<Player> nonFolded, int cap){
		List<Player> result = new ArrayList<>();
		for(Player p : nonFolded) {
			if(this.roundContributions.getOrDefault(p, 0) >= cap) {
				result.add(p);
			}
		}
		return result;
	}
	
	//Eligibles pour le pot principal : contribution > alreadyTaken CE round. 
    private List<Player> buildEligibleMain(List<Player> nonFolded, int alreadyTaken) {
        List<Player> result = new ArrayList<>();
        for (Player p : nonFolded) {
            if (this.roundContributions.getOrDefault(p, 0) > alreadyTaken) result.add(p);
        }
        return result;
    }
    
    //Distribution des gains:
    //Le jeton résiduel si jeton résiduel il y a (odd chip) va au gagnant le plus proche de la SB
    public Map<Player,Integer> awardPots(List<Card> board, List<Player> orderedPlayers){
    	Map<Player, Integer> winnings = new LinkedHashMap<>();
    	
    	for (SidePot sidePot : this.finalisedPots) {
    		List<Player> winners = findWinners(sidePot.getEligiblePlayers(),board);
    		if(winners.isEmpty()) {
    			continue;
    		}
    		int pot = sidePot.getAmount();
    		int share = pot / winners.size();
    		int oddChip = pot % winners.size();
    		
    		for (Player winner : winners) {
    			winner.addToStack(share);
    			winnings.merge(winner, share, Integer::sum);
    		}
    		
    		//Loddchip va au premier gagnant dans l'ordre SB first
    		if (oddChip > 0) {
                Player oddWinner = findFirstInOrder(winners, orderedPlayers);
                oddWinner.addToStack(oddChip);
                winnings.merge(oddWinner, oddChip, Integer::sum);
            }
    	}
    	return winnings;
    }
    
    //Trouver le 1er joueur dans target
    private Player findFirstInOrder(List<Player> targets, List<Player> orderedPlayers) {
    	for (Player p : orderedPlayers) {
    		if (targets.contains(p)) {
    			return p;
    		}
    	}
    	return targets.get(0);
    }
    
    private List<Player> findWinners(List<Player> players, List<Card> board){
    	HandRank bestRank = null;
    	List<Player> winners = new ArrayList<>();
    	
    	for(Player p : players) {
    		List<Card> sevenCards = new ArrayList<>(board);
    		sevenCards.addAll(p.getHoleCards());
    		HandRank rank = HandEvaluator.evaluateBestHand(sevenCards);
    		int cmp = bestRank == null ? 1 : rank.compareTo(bestRank);
    		if (cmp > 0) {
    			bestRank = rank;
    			winners.clear();
    			winners.add(p);
    		} else if(cmp == 0) {
    			winners.add(p);
    		}
    	}
    	return winners;
    }
    
    //LISTE DES GETTERS CLASSIQUE
    public int getTotalPot() {
    	int finalised = this.finalisedPots.stream().mapToInt(SidePot::getAmount).sum();
        int current   = this.roundContributions.values().stream().mapToInt(Integer::intValue).sum();
        return finalised + current;
    }
    
    public int getCurrentRoundTotal() {
        return this.roundContributions.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<SidePot> getFinalisedPots() {
        return Collections.unmodifiableList(this.finalisedPots);
    }

    public int getHighestBet() {
        return this.roundContributions.values().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
    }

    public int getCallAmount(Player player) {
        int highest = getHighestBet();
        int already = this.roundContributions.getOrDefault(player, 0);
        return Math.max(0, highest - already);
    }

    public void reset() {
        this.roundContributions.clear();
        this.finalisedPots.clear();
    }
}
