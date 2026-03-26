package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;

/*
 * Convertir les objets de Card en entier sur 32 bits (encodage CactusKev)
 * 
 * FORMAT: 
 * 		->8 bits de 0 à 7: nombre prmemier associé au rang: 2 = 2, 3=3, As= 41
 * 		->4 bits de 8 à 11: index du rang (0 pour 2 et 12 pour l'As: 12=0b1100
 * 		->4 bits de 12 à 15: 1 bit allumé par couleur: 1000 = trefle, 0100=carreau,...
 * 		->16 bits de 16 à 31: 1 bit allumé par rang (16+rank index)
 * 
 * EXEMPLE: AS de pique
 * 		-> AS -> nb premier associé = 41 = 0b00101001
 * 		-> AS -> 12 cartes (en terme de puissance croisante): 12 = 0b1100
 * 		-> pique -> 0b0001
 * 		-> 12+16 = 28 donc bit 28 allumé
 * 		---> donc as de pique encodé: 0b00010000000000000001110000101001
 * 
 * Les 8 bits de nombre premier vont permettre d'identifier directement un ensemble de rang (non flush)
 * Les 4 bits de couleurs avec un AND permettent d'identifier si couleur il y a
 * LES 16 bits de masque de rang permettent didentifier si suite il y a pas un OR
 * 
 */

public class CardPrimitiveBridge {
	private CardPrimitiveBridge() {}
	
	//DEfinition des nombres premiers
	static final int[] RANK_PRIMES = {2,3,5,7,11,13,17,19,23,29,31,37,41};
	
	//Definition des masques d'identification de couleurs
	static final int SUIT_CLUBS    = 0x8000; // bit 15
    static final int SUIT_DIAMONDS = 0x4000; // bit 14
    static final int SUIT_HEARTS   = 0x2000; // bit 13
    static final int SUIT_SPADES   = 0x1000; // bit 12
    
    //Définition des positions des masques
    static final int MASK_PRIME      = 0x000000FF; // bits 7-0
    static final int MASK_RANK_INDEX = 0x00000F00; // bits 11-8
    static final int MASK_SUIT       = 0x0000F000; // bits 15-12
    static final int MASK_RANK_BIT   = 0xFFFF0000; // bits 31-16
    
    static final int SHIFT_RANK_INDEX = 8;
    static final int SHIFT_SUIT       = 12;
    static final int SHIFT_RANK_BIT   = 16;
    
    //Convertir une Card en objet 32 bits
    public static int encode(Card carte) {
    	int rankIndex = rankIndexOf(carte.getRank());
    	int prime = RANK_PRIMES[rankIndex];
    	int suitBit = suitBitOf(carte.getSuit());
    	int rankBit = 1 << (SHIFT_RANK_BIT + rankIndex);
    	
    	return prime | (rankIndex << SHIFT_RANK_INDEX) | suitBit | rankBit;
	}
    
    //Calcule l'index d'une carte
    public static int indexOf(Card carte) {
    	return rankIndexOf(carte.getRank())*4 + suitIndexOf(carte.getSuit());
    }
    
    //Convertir un tableau de Card en un tablea encoder
    public static int[] encodeAll(Card[] cartes) {
    	int[] encoded = new int[cartes.length];
        for (int i = 0; i < cartes.length; i++) {
            encoded[i] = encode(cartes[i]);
        }
        return encoded;
    }
    
    //Extraire le nombre premier du rang 
    public static int prime(int encodedCard) {
        return encodedCard & MASK_PRIME;
    }
    
    //Extraire l'index du rang
    public static int rankIndex(int encodedCard) {
        return (encodedCard & MASK_RANK_INDEX) >> SHIFT_RANK_INDEX;
    }
    
    //Extrait le bitmask de couleur (bits 15-12)
    public static int suitBit(int encodedCard) {
        return encodedCard & MASK_SUIT;
    }
    
    //Extrait le bitmask de rang (bits 31-16)
    public static int rankBit(int encodedCard) {
        return (encodedCard & MASK_RANK_BIT) >> SHIFT_RANK_BIT;
    }
    
    //LISTE DES HELPERS
    
    //Rank.getValue() retourne 2-14. On normalise en 0-12
    static int rankIndexOf(Rank rank) {
        return rank.getValue() - 2; // TWO=0, ACE=12
    }
    
    static int suitIndexOf(Suit suit) {
        switch (suit) {
            case CLUBS:    
            	return 0;
            case DIAMONDS: 
            	return 1;
            case HEARTS:   
            	return 2;
            case SPADES:   
            	return 3;
            default: throw new IllegalArgumentException("Suit inconnue : " + suit);
        }
    }
    
    static int suitBitOf(Suit suit) {
        switch (suit) {
            case CLUBS:    
            	return SUIT_CLUBS;
            case DIAMONDS: 
            	return SUIT_DIAMONDS;
            case HEARTS:   
            	return SUIT_HEARTS;
            case SPADES:   
            	return SUIT_SPADES;
            default: throw new IllegalArgumentException("Suit inconnue : " + suit);
        }
    }
    
}
