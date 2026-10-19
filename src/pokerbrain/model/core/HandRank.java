package pokerbrain.model.core;

import java.util.*;

public class HandRank implements Comparable<HandRank> {
	private final HandType type;
	private final List<Rank> kickers;
	private final long numericValue;
	
	public HandRank(HandType type, List<Rank> kickers) {
		this.type = type;
		this.kickers = new ArrayList<>(kickers);
		this.numericValue = calculateNumericValue();
	}
	
	private long calculateNumericValue() {
		long value = (long) getType().getBaseValue()*10_000_000_000L;
		long[] weights = {100_000_000L,1_000_000L,10_000L,100L,1L};
		List<Rank> kickerList = getKickers();
		
		for (int i=0;i<Math.min(kickerList.size(),5);i++) {
			value += kickerList.get(i).getValue() * weights[i];
		}
		
		return value;
	}
	//Getters
	public HandType getType() {
		return this.type;
	}
	
	public List<Rank> getKickers(){
		return this.kickers;
	}
	
	public long getNumericValue() {
		return this.numericValue;
	}

	/**
	 * Description lisible en français : « Full aux Rois par les Dames », « Couleur à l'As ».
	 *
	 * Les kickers sont rangés du plus significatif au moins significatif, comme les produit
	 * `FastHandEvaluator.toHandRank` : le carré puis son kicker, le brelan du full puis sa
	 * paire, la paire haute puis la paire basse puis le kicker, et pour une quinte la carte
	 * haute d'abord (la roue se lit 5 4 3 2 A, donc « suite au 5 »).
	 */
	public String describe() {
		List<Rank> k = getKickers();
		if (k.isEmpty()) return getType().getName();
		Rank premier = k.get(0);

		switch (getType()) {
			case ROYAL_FLUSH:    return "Quinte flush royale";
			case STRAIGHT_FLUSH: return "Quinte flush " + au(premier);
			case FOUR_OF_KIND:   return "Carré " + de(premier);
			case FULL_HOUSE:     return k.size() > 3
				? "Full aux " + pluriel(premier) + " par les " + pluriel(k.get(3))
				: "Full aux " + pluriel(premier);
			case FLUSH:          return "Couleur " + au(premier);
			case STRAIGHT:       return "Suite " + au(premier);
			case THREE_OF_KIND:  return "Brelan " + de(premier);
			case TWO_PAIR:       return k.size() > 2
				? "Double paire, " + pluriel(premier) + " et " + pluriel(k.get(2))
				: "Double paire aux " + pluriel(premier);
			case PAIR:           return "Paire " + de(premier);
			default:             return "Hauteur " + Rank.getDisplayName(premier.getSymbol());
		}
	}

	//« au Roi », « à l'As » — l'élision est le seul cas particulier
	private static String au(Rank rank) {
		return rank == Rank.ACE ? "à l'As" : "au " + Rank.getDisplayName(rank.getSymbol());
	}

	//« de Rois », « d'As »
	private static String de(Rank rank) {
		return rank == Rank.ACE ? "d'As" : "de " + pluriel(rank);
	}

	private static String pluriel(Rank rank) {
		String nom = Rank.getDisplayName(rank.getSymbol());
		//As et Dix sont invariables, les chiffres s'écrivent tels quels
		if (rank == Rank.ACE || rank == Rank.TEN || rank.getValue() <= 9) return nom;
		return nom + "s";
	}
	//Comparer 2 HandRanks: positif si this plus fort, négatif si plus faible, 0 sinon
	@Override
	public int compareTo(HandRank other) {
		if (other == null) throw new IllegalArgumentException("Impossible de comparer à null");
		return Long.compare(this.getNumericValue(), other.getNumericValue());
	}
	
	//equals et hascode
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof HandRank)) return false;
		return this.numericValue == ((HandRank) o).numericValue;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(this.numericValue);
	}
	
	// Le décodage d'un entier vers un HandRank affichable appartient désormais à
	// FastHandEvaluator.toHandRank(int) : la classe canonique 1-7462 n'est pas
	// auto-descriptive, elle demande les tables inverses du simulateur.

	//toString
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getType().getName());
		sb.append(" [");
		List<Rank> k = getKickers();
		for (int i = 0; i < k.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append(k.get(i).getSymbol());
		}
		sb.append("] (value=").append(getNumericValue()).append(")");
		return sb.toString();
	}
}
