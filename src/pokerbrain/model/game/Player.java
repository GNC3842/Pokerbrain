package pokerbrain.model.game;

import pokerbrain.model.core.Card;
import java.util.*;

//Classe de model mutable donc on fait des copies

public class Player {
	private final String id;
	private final String name;
	private int stack;
	private PlayerStatus status;
	private List<Card> holeCards;
	private Position position;
	private int seat;
	private int currentBet;
	private boolean hasActed;
	
	//COnstructeur
	public Player(String id, String name, int stack) {
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
		this.holeCards = new ArrayList<>();
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
	
	public int getStack() {
		return this.stack;
	}
	
	public PlayerStatus getStatus() {
		return this.status;
	}
	
	public List<Card> getHoleCards(){
		return new ArrayList<>(holeCards);
	}
	
	public int getCurrentBet() {
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
	public void setStack(int stack) {
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
	
	public void setCurrentBet(int bet) {
		if (bet < 0) {
			throw new IllegalArgumentException("Bet ne peut pas etre négatif");
		}
		this.currentBet = bet;
	}
	
	public void setHasActed(boolean hasActed) {
        this.hasActed = hasActed;
    }
	
	//Recevoir des cartes
	public void receiveHoleCards(Card c1, Card c2) {
		if (c1 == null) {
			throw new IllegalArgumentException("Carte1 ne peut pas etre null");
		}
		if (c2 == null) {
			throw new IllegalArgumentException("Carte2 ne peut pas etre null");
		}
		this.holeCards = new ArrayList<>();
		this.holeCards.add(new Card(c1.getRank(),c1.getSuit()));
		this.holeCards.add(new Card(c2.getRank(),c2.getSuit()));
	}
	
	//Nettoyer les cartes
	public void clearHoleCards() {
		this.holeCards = new ArrayList<>();
	}
	
	//AJouter des jetons a la stack
	public void addToStack(int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Amount ne peut pas etre négatif");
		}
		this.stack = this.stack + amount;
		if(this.getStatus() == PlayerStatus.ALL_IN && this.stack > 0) {
			this.setStatus(PlayerStatus.ACTIVE);
		}
	}
	
	//Retirer des jetons à la stack
	public void removeFromStack(int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Amount ne peut pas etre négatif");
		}
		if (amount > this.stack) {
			throw new IllegalArgumentException("Stack insuffisant");
		}
		this.stack = this.stack - amount;
		if (this.stack == 0) {
			this.setStatus(PlayerStatus.ALL_IN);
		}
	}
	
	//Renvoyer l'état du joeuru
	public boolean isActive() {
		return this.getStatus()== PlayerStatus.ACTIVE;
	}
	
	public boolean isAllIn() {
		return this.getStatus() == PlayerStatus.ALL_IN;
	}
	
	public boolean isFolded() {
		return this.getStatus() == PlayerStatus.FOLDED;
	}
	
	public boolean isOut() {
		return this.getStatus() == PlayerStatus.OUT;
	}
	
	public boolean canAct() {
		return this.isActive() && !hasActed();
	}
	
	//Réinitialiser l'etat du joueur après une nouvelle main
	public void resetForNewHand() {
		if (!isOut()) {
			this.setStatus(PlayerStatus.ACTIVE);
		}
		this.clearHoleCards();
		this.setCurrentBet(0);
		this.setHasActed(false);
		this.position = null;
	}
	//toString classique
	@Override
	public String toString() {
		return getName() + " (stack=" + getStack() + ", status=" + getStatus()
        + (getPosition() != null ? ", pos=" + getPosition() : "") + ")";
	}
}


