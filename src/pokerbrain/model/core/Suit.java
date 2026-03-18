package pokerbrain.model.core;

import java.util.HashMap;
import java.util.Map;

/*Représentation de la couleur des cartes*/

public enum Suit {
	CLUBS('c', '♣', "Clubs"),
    DIAMONDS('d', '♦', "Diamonds"),
    HEARTS('h', '♥', "Hearts"),
    SPADES('s', '♠', "Spades");
	
	//Constructeur
	Suit(char lettre, char symbol, String name){
		this.code = code;
		this.symbol = symbol;
		this.name = name;
	}
	
	//Descript couleurs
	private final char code;
	private final char symbol;
	private final String name;
	
	//Map précalculé pour complexité en O(1)
	private static final Map<Character,Suit> CODE_MAP = new HashMap<>();
	
	//Méthodes d'accès
	public char getCode() {
		return this.code;
	}
	
	public char getSymbol() {
		return this.getSymbol();
	}
	
	public String getName() {
		return this.name;
	}
	
	//Initialisation des couleurs
	static {
		for (Suit suit : values()) {
			CODE_MAP.put(Character.toLowerCase(suit.getCode()),suit);
			CODE_MAP.put(Character.toUpperCase(suit.getCode()), suit);
		}
	}
	
	//Méthode pour trouver la couleur de puis le code
	public static Suit fromCode(char code) {
		Suit suit = CODE_MAP.get(code);
		if (suit == null) {
			throw new IllegalArgumentException("Code invalide");
		}
		return suit;
	}
}
