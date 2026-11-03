package pokerbrain.model.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.List;
import java.util.Collection;

/*Représente un deck de 52 cartes*/


public class Deck {
	private List<Card> cards;
	private List<Card> dealtCards;
	private Random random;
	
	public Deck() {
		this.cards = new ArrayList<>(52);
		this.dealtCards = new ArrayList<>();
		this.random = new Random();
		reset();
	}
	
	public Deck(long seed) {
		this.cards = new ArrayList<>(52);
		this.dealtCards = new ArrayList<>();
		this.random = new Random(seed);
		reset();
	}
	
	//Réinitialisé le deck dans l'ordre
	public void reset() {
		cards.clear();
		dealtCards.clear();
		for (Suit suit : Suit.values()) {
			for (Rank rank : Rank.values()) {
				cards.add(new Card(rank,suit));
			}
		}
		
	}
	
	//Melange le deck avec génér 
	public void shuffle() {
		Collections.shuffle(cards,random);
	}
	
	//Préparer le deck pour nouvelle main
	public void prepareNewHand() {
		reset();
		shuffle();
	}
	
	public Card deal() {
		if (cards.isEmpty()) {
			throw new IllegalStateException("Deck vide");
		}
		Card card = cards.remove(cards.size()-1);
		dealtCards.add(card);
		return card;
	}
	
	
	public void removeCards(Collection<Card> toRemove) {
		cards.removeAll(toRemove);
	}
	
	
	public void burn() {
		if (cards.isEmpty()) {
			throw new IllegalStateException("Impossible de brûler une carte : deck vide");
		}
		Card burned = cards.remove(cards.size() - 1);
		dealtCards.add(burned);
	}
	
	
	public List<Card> getRemainingCards(){
		return new ArrayList<>(cards);
	}
	
	
}
