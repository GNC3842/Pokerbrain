package pokerbrain.simulator;

import pokerbrain.model.core.*;
import pokerbrain.engine.HandEvaluator;
import java.util.ArrayList;
import java.util.List;

/*
 * Va générer au démarage la Lookup Table de 2 598 960 entrées (C(52,5))
 * 
 */

public class LookupTableGenerator {
	
	private LookupTableGenerator() {}
	
	//Stocké la taille
	public static final int TABLE_SIZE = 2_598_960; // C(52,5)
	
	//Table des C(n,k) précalculé avec triangle de pascal
	private static final int[][] C = precomputeBinomials(52, 5);
	private static int[][] precomputeBinomials(int maxN, int maxK) {
        int[][] c = new int[maxN + 1][maxK + 1];
        for (int n = 0; n <= maxN; n++) {
            c[n][0] = 1;
            for (int k = 1; k <= Math.min(n, maxK); k++) {
                c[n][k] = c[n - 1][k - 1] + c[n - 1][k];
            }
        }
        return c;
    }
	
	/**
     * Génère et retourne la Lookup Table complète.
     * À appeler une seule fois au démarrage de l'application.
     *
     * Le tableau retourné contient, à l'index combinatoire de chaque main,
     * une valeur entière directement comparable : table[a] > table[b] ssi
     * la main A bat la main B.
     */
	public static int[] generate() {
		int[] table = new int[TABLE_SIZE];
		Card[] allCards = buildAllCards();
		//Enumere toutes les combinaisons de C(52,5)
		for(int i0=0;i0<48;i0++) {
			for(int i1=i0;i1<49;i1++) {
				for(int i2=i1;i2<50;i2++) {
					for(int i3=i2;i3<51;i3++) {
						for(int i4=i3;i4<52;i4++) {
							int idx = combinatorialIndex(i0, i1, i2, i3, i4);
							List<Card> five = new ArrayList<>(5);
							five.add(allCards[i0]);
							five.add(allCards[i1]);
							five.add(allCards[i2]);
							five.add(allCards[i3]);
							five.add(allCards[i4]);
							
							HandRank hr = HandEvaluator.evaluateBestHand(five);
							
							int encoded_value = (hr.getType().getBaseValue() << 20)
									| (hr.getKickers().get(0).getValue()<<16)
									| (hr.getKickers().get(1).getValue() << 12)
									| (hr.getKickers().get(2).getValue() << 8)
									| (hr.getKickers().get(3).getValue() << 4)
									| (hr.getKickers().get(4).getValue());
							
							table[idx] = encoded_value;
						}
					}
				}
			}
		}
		return table;
	}
	
	 /*Calcule l'index unique d'une combinaison de 5 cartes.
     *
     * PRÉCONDITION : i0 < i1 < i2 < i3 < i4, tous dans [0, 51].
     * La formule est C(i0,1) + C(i1,2) + C(i2,3) + C(i3,4) + C(i4,5).
     *
     * Cette bijection garantit un index unique dans [0, 2_598_959].
     * Complexité : O(1) — uniquement des additions de valeurs précalculées.
     */
	public static int combinatorialIndex(int i0, int i1, int i2, int i3, int i4) {
        return C[i0][1] + C[i1][2] + C[i2][3] + C[i3][4] + C[i4][5];
    }
	
	//Construction canonique du deck Index =rankIndex * 4 + suitIndex
	//Defini dans CardPrimitiveBridge
	public static Card[] buildAllCards() {
        Card[] cards = new Card[52];
        Rank[] ranks = Rank.values();   // TWO … ACE (13 valeurs)
        Suit[] suits = Suit.values();   // CLUBS, DIAMONDS, HEARTS, SPADES
        for (int r = 0; r < 13; r++) {
            for (int s = 0; s < 4; s++) {
                cards[r * 4 + s] = new Card(ranks[r], suits[s]);
            }
        }
        return cards;
    }


}
