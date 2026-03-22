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
		if (id == null) {
			throw new IllegalArgumentException("id ne peut pas etre null");
		}
		if (name == null) {
			throw new IllegalArgumentException("name ne peut pas etre null");
		}
		if (stack < 0) {
			throw new IllegalArgumentException("stack ne peut pas etre négatif" + stack);
		}
		
		this.id = id;
		this.name = name;
		this.stack = stack;
		
		this.status = PlayerStatus.ACTIVE;
		this.holeCard = new ArrayList<>();
		this.currentBet = 0;
		this.hasActed = false;
	}
	
	//Tous les getters
	public String getId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public double stack() {
		return this.stack;
	}
	
	public PlayerStatus getStatus() {
		return this.status;
	}
	
	public List<Card> getHoleCards(){
		return new ArrayList<>(holeCards);
	}
	
	public double getCurrentBet() {
		return this.currentBet;
	}
	
	public Position getPosition() {
		return this.position;
	}
	
	public int getSeat() {
		return this.seat;
	}
	
	public boolean hasActed() {
		return this.hasActed;
	}
	
	//Liste des setters
	public void setStack(double stack) {
		if (stack < 0) {
			throw new IllegalArgumentException("Stack ne peut pas être négatif : " + stack);
		}
		this.stack = stack;
	}
	
	public void setStatus(PlayerStatus status) {
		if (status == null) {
			throw new IllegalArgumentException("Status ne peut pas etre null");
		}
		this.status = status;
	}
	
	public void setPosition(Position position) {
		if (position == null) {
			throw new IllegalArgumentException("Poisition ne peut pas etre null");
		}
		this.position = position;
	}
	
	public void setSeat(int seat) {
		if (seat < 0) {
			throw new IllegalArgumentException("Seat ne peut pas etre négatif");
		}
		this.seat = seat;
	}
	
	public void setCurrentBet(double bet) {
		if (bet < 0) {
			throw new IllegalArgumentException("Bet ne peut pas etre négatif");
		}
		this.currentBet = bet;
	}
	
	public void setHasActed(boolean hasActed) {
        this.hasActed = hasActed;
    }
	
	//Recevoir des cartes
	
	//Nettoyer les cartes
	
	//AJouter des jetons a la stack
	
	//Retirer des jetons à la stack
	
	//Renvoyer l'état du joeuru
	
	//Réinitialiser l'etat du joueur après une nouvelle main
	
	//toString classique
}
