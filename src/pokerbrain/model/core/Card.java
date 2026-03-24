package pokerbrain.model.core;

/*Représentation dune carte */

import java.util.Objects;

public class Card implements Comparable<Card> {
	private final Rank rank;
	private final Suit suit;
	
	//Constructeur
	public Card(Rank rank, Suit suit) {
		this.rank = rank;
		this.suit = suit;
	}
	
	//Méthode création de carte depuis notation standard
	//Ah, As, 9s
	public static Card fromString(String notation) {
		if (notation == null || notation.length() != 2) {
			throw new IllegalArgumentException("Notation de carte invalide");
		}
		Rank rank = Rank.fromSymbol(notation.charAt(0));
		Suit suit = Suit.fromCode(notation.charAt(1));
		
		return new Card(rank,suit);
	}
	
	//Méthode d'accès
	public Rank getRank() {
		return this.rank;
	}
	
	public Suit getSuit() {
		return this.suit;
	}
	
	@Override
	public String toString() {
		return ""+this.rank.getSymbol()+this.suit.getCode();
	}
	
	public String toSymbolString() {
		return ""+this.rank.getSymbol()+this.suit.getSymbol();
	}
	
	//Comparer 2 cartes par leur rangs
	//Si other est plus petit: valeur positive
	//Si other est plus grand: valeur négative
	//Sinon renvoie 0
	@Override
	public int compareTo(Card other) {
		return Integer.compare(this.rank.getValue(), other.rank.getValue());
	}
	
	//Egalité stricte
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Card)) {
			return false;
		}
		Card other = (Card) o;
		return rank == other.getRank() && suit == other.getSuit(); 
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(rank,suit);
	}
	
	//Vérifier si 2 cartes ont la meme couleur
	public boolean isSameSuit(Card other) {
		return this.suit == other.suit;
	}
	
	//Vérifie si 2 cartes ont le meme rang
	public boolean isSameRank(Card other) {
		return this.getRank() == other.getRank();
	}
	
}
