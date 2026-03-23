package pokerbrain.model.game;

import java.util.*;

public class SidePot {
	private final double amount;
	private final List<Player> eligiblePlayers;
	
	public SidePot(double amount, List<Player> eligiblePlayers) {
		if(amount < 0) {
			throw new IllegalArgumentException("Amount ne peut pas etre négatif");
		}
		if (eligiblePlayers == null || eligiblePlayers.isEmpty()) {
			throw new IllegalArgumentException("SidePot doit avoir au moins un joueur");
		}
		this.amount = amount;
		this.eligiblePlayers = Collections.unmodifiableList(new ArrayList<>(eligiblePlayers));
	}
	
	//Getters comme dans Pot.java
	public double getAmount() {
		return this.amount;
	}
	
	public List<Player> getEligiblePlayers(){
		return this.eligiblePlayers;
	}
	
	public boolean isEligible(Player player) {
		return this.eligiblePlayers.contains(player);
	}
	
	public int getEligibleCount() {
		return this.eligiblePlayers.size();
	}
	
	//methode toString
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("SidePot[");
		sb.append(this.amount).append(" → ");
		this.eligiblePlayers.forEach(p -> sb.append(p.getName()).append(" "));
		sb.append("]");
		return sb.toString();
	}
	
}
