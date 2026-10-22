package pokerbrain.simulator;

import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;
import java.util.List;

/*
 * Représentation primitive du deck pour le monte carlo
 * Pré-encode les 52 cartes UNE SEULE FOIS au chargement de la classe
 * 
 * ALL_ENCODED[i]  = entier CactusKev de la carte d'index i
 * ALL_INDICES[i]  = i (utilisé pour le mélange Fisher-Yates)
 * 
 * Le tableau available[] maintient les indices des cartes non-mortes
 * Il est mélangé partiellement à chaque itération Monte-Carlo
 */

public class DeckPrimitive {
	//Encodage CactusKev des 52 cartes, index i = rankIndex*4 + suitIndex
	public static final int[] ALL_ENCODED;
	
	static {
		ALL_ENCODED = new int[52];
		Rank[] ranks = Rank.values();
		Suit[] suits = Suit.values();
		for (int r = 0; r < 13; r++) {
			for (int s = 0; s < 4; s++) {
				ALL_ENCODED[r * 4 + s] = CardPrimitiveBridge.encode(new Card(ranks[r], suits[s]));
			}
		}
	}
	
	//Indices des cartes disponibles (non mortes), mélangé en place
    private final int[] available = new int[52];
    private int availableCount;

    public DeckPrimitive() {}
    
    //Prépaper le deck sans les cartes mortes (1 fois par main)
    public void init(int[] deadIndices, int deadCount) {
        // Marquer les cartes mortes
        boolean[] dead = new boolean[52];
        for (int i = 0; i < deadCount; i++) dead[deadIndices[i]] = true;

        availableCount = 0;
        for (int i = 0; i < 52; i++) {
            if (!dead[i]) available[availableCount++] = i;
        }
    }
    
    public void shufflePartial(java.util.concurrent.ThreadLocalRandom rng, int needed) {
        for (int i = 0; i < needed; i++) {
            int j = i + rng.nextInt(availableCount - i);
            int tmp = available[i]; available[i] = available[j]; available[j] = tmp;
        }
    }
    
    //Retourne l'index de la i-eme carte trié
    public int drawnIndex(int i) { 
    	return available[i]; 
    }
    
    //Retourne l'encodage cactusKeb de la ième utilisé
    public int drawnEncoded(int i) { 
    	return ALL_ENCODED[available[i]]; 
    }

    
    //Encode une liste de Cards OO en tableau d'indices primitifs
    public static void encodeCards(List<Card> cards, int[] outIdx, int[] outEnc) {
        for (int i = 0; i < cards.size(); i++) {
            outIdx[i] = CardPrimitiveBridge.indexOf(cards.get(i));
            outEnc[i] = ALL_ENCODED[outIdx[i]];
        }
    }
}
