package pokerbrain.model.game;

import java.util.*;

/* Pot en cours de construction pendnat une main: 
 * est donc mutable: PotManager s'oocupe de Pot et SidePot
 */

public class Pot {
	private int amount;
	private final List<Player> eligiblePlayers;
	
	//COnstructeur
	public Pot(List<Player> eligiblePlayers) {
		if(eligiblePlayers == null || eligiblePlayers.isEmpty()) {
			throw new IllegalArgumentException("Il doit au moins y avoir 1 joueur dans 1 pot");
		}
		this.amount = 0;
		this.eligiblePlayers = new ArrayList<>(eligiblePlayers);
	}
	
	//Ajouter au pot
	public void addAmount(int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Amount ne peut pas etre négatif");
		}
		this.amount = this.amount + amount;
	}
	
	//Récupere le pot
	public int getPot() {
		return this.amount;
	}
	
	//Récupere les joeurus qui participent au pot
	public List<Player> getEligiblePlayer(){
		return new ArrayList<>(this.eligiblePlayers);
	}
	
	//Savoir si un joueur est éligible dans ce pot
	public boolean isEligible(Player player) {
		return this.eligiblePlayers.contains(player);
	}
	
	//Récupere nombre de joueurs éligibles
	public int getEligibleCount() {
		return this.eligiblePlayers.size();
	}
	
	//Transformer ce pot en un sidePot immuable à la fin d'une macnhe
	public SidePot toSidePot() {
		return new SidePot(this.amount, this.eligiblePlayers);
	}
	
	//methode toString
	@Override
	public String toString() {
		return "Pot[" + this.amount + " → " + this.eligiblePlayers.size() + " joueurs]";
	}	
}
