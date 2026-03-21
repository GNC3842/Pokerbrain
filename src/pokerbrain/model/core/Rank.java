package pokerbrain.model.core;

import java.util.HashMap;
import java.util.Map;

/*Représentation du rang des cartes*/

public enum Rank {
	//Instance
	TWO(2,'2'),
	THREE(3,'3'),
	FOUR(4,'4'),
	FIVE(5,'5'),
	SIX(6,'6'),
	SEVEN(7,'7'),
	EIGHT(8,'8'),
	NINE(9,'9'),
	TEN(10,'T'),
	JACK(11,'J'),
	QUEEN(12,'Q'),
	KING(13,'K'),
	ACE(14,'A');
	
	//Constructeur
	Rank(int value, char symbol){
		this.value = value;
		this.symbol = symbol;
	}
	
	//Description cartes
	private final int value;
	private final char symbol;
	
	//Maps pré-calculées pour complexité en O(1)
	private static final Map<Character,Rank> SYMBOL_MAP = new HashMap<>();
	private static final Map<Integer,Rank> VALUE_MAP = new HashMap<>();
	
	//Méthode d'accès
	public int getValue() {
		return this.value;
	}
		
	public char getSymbol() {
		return this.symbol;
	}
	
	//Initialization de la classe
	static {
		for (Rank rank : values()) {
			SYMBOL_MAP.put(Character.toLowerCase(rank.getSymbol()),rank);
			SYMBOL_MAP.put(Character.toUpperCase(rank.getSymbol()), rank);
			VALUE_MAP.put(rank.getValue(), rank);
		}
	}
	
	//Méthode pour trouver rank depuis symbol/value
	public static Rank fromSymbol(char symbol) {
		Rank rank = SYMBOL_MAP.get(symbol);
		if (rank == null) {
			throw new IllegalArgumentException("symbol ne peut pas etre vide ou null");
		}
		return rank;
	}
	
	public static Rank fromValue(int value) {
		if (value < 2 || value > 14) {
			throw new IllegalArgumentException("value incorrecte");
		}
		Rank rank = VALUE_MAP.get(value);
		return rank;
	}
	
	public static String getDisplayName(char symbol) {
		Rank rank = SYMBOL_MAP.get(Character.toUpperCase(symbol));
		if (rank == null) {
			throw new IllegalArgumentException("Symbol inconnu : " + symbol);
		}
		switch (rank) {
		case ACE:   return "As";
		case KING:  return "Roi";
		case QUEEN: return "Dame";
		case JACK:  return "Valet";
		case TEN:   return "Dix";
		default:    return String.valueOf(rank.getValue());
		}
	}
		@Override
		public String toString() {
			return String.valueOf(this.symbol);
		}
}
