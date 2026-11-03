package pokerbrain.engine;

import java.util.*;
import pokerbrain.model.core.*;
import pokerbrain.model.game.*;
import pokerbrain.simulator.CardPrimitiveBridge;
import pokerbrain.simulator.FastHandEvaluator;

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

	/**
	 * Tout ce qui est reversé à un joueur pendant la main : pots gagnés à l'abattage,
	 * pots pris sans opposition, et mises non égalées rendues à leur propriétaire.
	 *
	 * Sert à construire le `Hand.winnings` enregistré sur disque. Sans cela, les mains
	 * gagnées par fold général — la majorité — étaient enregistrées sans aucun gain :
	 * `totalInvested` était compté mais pas `totalWon`, donc le BB/100 était
	 * structurellement négatif et le LeakDetector annonçait « winrate négatif » à un
	 * joueur gagnant.
	 *
	 * Les mises rendues en font partie à dessein : elles ont été comptées dans
	 * l'investissement du joueur, il faut donc les lui recréditer pour que le net soit juste.
	 */
	private final Map<Player, Integer> payouts;

	//Constructeur
	public PotManager() {
		this.roundContributions = new LinkedHashMap<>();
		this.finalisedPots      = new ArrayList<>();
		this.payouts            = new LinkedHashMap<>();
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
			pay(eligible.get(0), amount);
		} else {
			this.finalisedPots.add(new SidePot(amount,eligible));
		}
	}

	//Crédite un joueur et garde la trace du versement
	private void pay(Player player, int amount) {
		if (amount <= 0) return;
		player.addToStack(amount);
		this.payouts.merge(player, amount, Integer::sum);
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
    
    /**
     * Distribution des gains à l'abattage.
     * Le jeton résiduel (odd chip) va au gagnant le plus proche de la SB.
     *
     * Les listes d'éligibles des side pots sont filtrées des joueurs couchés : elles ont
     * été figées au round qui les a créées, et un joueur qui se couche à la turn y restait
     * éligible au pot du flop. Mesuré avant correctif : 12 pots sur 104 abattages
     * remportés par un joueur couché.
     */
    public Map<Player,Integer> awardPots(List<Card> board, List<Player> orderedPlayers){
    	for (SidePot sidePot : this.finalisedPots) {
    		List<Player> eligible = stillInHand(sidePot.getEligiblePlayers());
    		if (eligible.isEmpty()) {
    			// Ne devrait pas arriver : un pot dont tous les éligibles se sont couchés
    			// est remporté par le dernier joueur en jeu via PokerGame.autoAwardPot.
    			// Garde-fou pour ne jamais laisser des jetons au sol.
    			eligible = stillInHand(orderedPlayers);
    			if (eligible.isEmpty()) continue;
    		}

    		List<Player> winners = findWinners(eligible, board);
    		if(winners.isEmpty()) {
    			continue;
    		}
    		int pot = sidePot.getAmount();
    		int share = pot / winners.size();
    		int oddChip = pot % winners.size();

    		for (Player winner : winners) {
    			pay(winner, share);
    		}

    		//L'odd chip va au premier gagnant dans l'ordre SB first
    		if (oddChip > 0) {
                pay(findFirstInOrder(winners, orderedPlayers), oddChip);
            }
    	}
    	return getPayouts();
    }

    //Joueurs encore en lice : ni couchés, ni éliminés
    private static List<Player> stillInHand(List<Player> players) {
    	List<Player> result = new ArrayList<>();
    	for (Player p : players) {
    		if (!p.isFolded() && !p.isOut()) result.add(p);
    	}
    	return result;
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
    
    /**
     * Gagnants d'un pot, via l'évaluateur rapide (classe canonique CactusKev 1-7462,
     * la PLUS PETITE est la plus forte). Remplace l'évaluateur objet, qui allouait
     * une ArrayList, une LinkedHashMap et un HandRank par combinaison de 5 cartes.
     *
     * Le board doit être complet et chaque joueur éligible doit avoir ses deux cartes :
     * c'est l'invariant garanti par PokerGame.showdown() et autoAwardPot(). Le rompre
     * signifie un pot mal attribué, donc on lève plutôt que de deviner.
     */
    private List<Player> findWinners(List<Player> players, List<Card> board){
    	if (board == null || board.size() != 5) {
    		throw new IllegalStateException("Showdown sur un board incomplet : "
    			+ (board == null ? 0 : board.size()) + " carte(s) au lieu de 5");
    	}

    	int[] cards = new int[7];
    	for (int i = 0; i < 5; i++) {
    		cards[i] = CardPrimitiveBridge.encode(board.get(i));
    	}
    	FastHandEvaluator evaluator = new FastHandEvaluator();

    	int bestClass = Integer.MAX_VALUE;
    	List<Player> winners = new ArrayList<>();

    	for(Player p : players) {
    		List<Card> hole = p.getHoleCards();
    		if (hole.size() != 2) {
    			throw new IllegalStateException("Showdown : " + p.getName() + " a "
    				+ hole.size() + " carte(s) fermée(s) au lieu de 2");
    		}
    		cards[5] = CardPrimitiveBridge.encode(hole.get(0));
    		cards[6] = CardPrimitiveBridge.encode(hole.get(1));

    		int rankClass = evaluator.evaluate7(cards);
    		if (rankClass < bestClass) {
    			bestClass = rankClass;
    			winners.clear();
    			winners.add(p);
    		} else if(rankClass == bestClass) {
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

    /** Tout ce qui a été reversé aux joueurs depuis le début de la main. */
    public Map<Player,Integer> getPayouts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.payouts));
    }

    /** Remet un joueur dans le pot courant : utilisé par le règlement anticipé d'une main. */
    void payDirect(Player player, int amount) {
        pay(player, amount);
    }

    public void reset() {
        this.roundContributions.clear();
        this.finalisedPots.clear();
        this.payouts.clear();
    }
}
