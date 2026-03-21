package pokerbrain.model.game;

import pokerbrain.model.core.Card;
import java.util.*;

//Classe de model mutable donc on fait des copies

public class Player {
	private final String id;
	private final String name;
	private double stack;
	private PlayerStatus status;
	private List<Card> holeCard;
	private Position position;
	private int seat;
	private double currentBet;
	private boolean hasActed;
	
	//COnstructeur
	public Player(String id, String name, double stack) {
		
	}
}
