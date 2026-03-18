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
	
	public List<Card> dealMultiple(int count){
		if (count < 0) {
			throw new IllegalArgumentException("Count ne peut pas etre négatif");
		}
		if (count > cards.size()) {
			throw new IllegalStateException("Pas assez de cartes");
		}
		
		List<Card> dealt = new ArrayList<>(count);
		for(int i=0;i<count;i++) {
			dealt.add(deal());
		}
		return dealt;
	}
	
	public void removeCards(Collection<Card> toRemove) {
		cards.removeAll(toRemove);
	}
	
	public void keepOnly(Collection<Card> toKeep) {
		cards.retainAll(toKeep);
	}
	
	public int getRemainingSize() {
		return cards.size();
	}
	
	public List<Card> getRemainingCards(){
		return new ArrayList<>(cards);
	}
	
	public List<Card> getDealtCards(){
		return new ArrayList<>(dealtCards);
	}
	
	public Deck copy() {
		Deck copy = new Deck();
		copy.cards = new ArrayList<Card>(this.cards);
		copy.dealtCards = new ArrayList<Card>(this.dealtCards);
		copy.random = new Random();
		return copy;
	}
}
