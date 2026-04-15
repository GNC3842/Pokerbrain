package pokerbrain.model.core;

public enum HandType {
	HIGH_CARD(0,"Carte haute"),
	PAIR(1,"Paire"),
	TWO_PAIR(2,"Double paire"),
	THREE_OF_KIND(3,"Brelan"),
	STRAIGHT(4,"Suite"),
	FLUSH(5,"Couleur"),
	FULL_HOUSE(6,"Full"),
	FOUR_OF_KIND(7,"Carré"),
	STRAIGHT_FLUSH(8,"Quinte flush"),
	ROYAL_FLUSH(9,"Quinte flush royale");
	
	private final int baseValue;
	private final String name;
	
	HandType(int baseValue, String name){
		this.baseValue = baseValue;
		this.name = name;
	}
	
	public int getBaseValue() {
		return this.baseValue;
	}
	
	public String getName() {
		return this.name;
	}
	
}
