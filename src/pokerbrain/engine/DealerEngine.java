package pokerbrain.engine;

import pokerbrain.model.core.*;
import pokerbrain.model.game.*;
import java.util.*;

/**
 * Distribue les cartes selon les règles du Texas Hold'em.
 *
 * Ordre de distribution preflop (règle standard) :
 *   1. Distribuer 1 carte à chaque joueur dans l'ordre (SB → BB → ... → BTN)
 *   2. Distribuer 1 deuxième carte dans le même ordre
 *
 * Aucune carte n'est brûlée avant les cartes fermées : la brûlée n'existe qu'avant chaque
 * carte commune. Le javadoc en annonçait une, le code n'en brûlait pas — et c'est le code
 * qui avait raison.
 *
 * Pour le flop/turn/river : une brûlée, puis distribution sur le board commun.
 */
public class DealerEngine {

	private final Deck deck;
	private final List<Card> board;

	public DealerEngine(Deck deck) {
		this.deck  = Objects.requireNonNull(deck, "Deck ne peut pas être null");
		this.board = new ArrayList<>(5);
	}

	//Distribution des hole cards 
	//Distribue 2 cartes à chaque joueur actif dans l'ordre postflop (SB first)
	 //Les joueurs foldés ou éliminés ne reçoivent pas de cartes
	public void dealHoleCards(List<Player> orderedPlayers) {
		if(orderedPlayers == null || orderedPlayers.isEmpty()) {
			throw new IllegalArgumentException("Players ne peut pas être null");
		}

		List<Player> active = new ArrayList<>();
		for (Player p : orderedPlayers) {
			if (!p.isOut()) active.add(p);
		}

		// Deux passes : 1 carte à chacun, puis 1 autre
		List<Card> firstCards  = new ArrayList<>();
		List<Card> secondCards = new ArrayList<>();

		for (Player p : active) {
			firstCards.add(this.deck.deal());
		}
		for (Player p : active) {
			secondCards.add(this.deck.deal());
		}

		for (int i = 0; i < active.size(); i++) {
			active.get(i).receiveHoleCards(firstCards.get(i), secondCards.get(i));
		}
	}

	//Board
	
	//Distribue le flop : burn + 3 cartes sur le board
	public List<Card> dealFlop() {
		if (!this.board.isEmpty())
			throw new IllegalStateException("Le flop est déjà distribué");

		this.deck.burn();
		for (int i = 0; i < 3; i++) {
			this.board.add(this.deck.deal());
		}
		return getBoard();
	}

	
	//Distribue la turn : burn + 1 carte
	public Card dealTurn() {
		if (this.board.size() != 3)
			throw new IllegalStateException(
				"La turn nécessite exactement 3 cartes au board, actuel : " + this.board.size());

		this.deck.burn();
		Card turn = this.deck.deal();
		this.board.add(turn);
		return turn;
	}

	//Distribue la river : burn + 1 carte
	public Card dealRiver() {
		if (this.board.size() != 4)
			throw new IllegalStateException(
				"La river nécessite exactement 4 cartes au board, actuel : " + this.board.size());

		this.deck.burn();
		Card river = this.deck.deal();
		this.board.add(river);
		return river;
	}

	//Getters
	public List<Card> getBoard() {
		return new ArrayList<>(this.board);
	}

	public Deck getDeck() { return this.deck; }

	public boolean isFlopDealt(){ 
		return this.board.size() >= 3; 
	}
	
	public boolean isTurnDealt(){
		return this.board.size() >= 4;
	}
	public boolean isRiverDealt(){
		return this.board.size() == 5;
	}

	//Réinitialise le board pour une nouvelle main
	public void reset() {
		this.board.clear();
		this.deck.prepareNewHand();
	}
}
