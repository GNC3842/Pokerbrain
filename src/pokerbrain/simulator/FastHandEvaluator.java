package pokerbrain.simulator;

/*
 * Evaluateur des mains sans allocations
 * 
 * -> une insatnce par thread: 
 * 				FastHandEvaluator eval = new FastHandEvaluator(table);
 *   			int value = eval.evaluate7(encodedCards, cardIndices);
 *   
 */

public class FastHandEvaluator {
	
	private final int[] table;
	
	// Buffers internes — alloués UNE FOIS à la construction, réutilisés
    private final int[] flushBuf  = new int[7]; // indices des cartes flush
    private final int[] comboBuf  = new int[5]; // combo courante de 5 indices
    private final int[] sortBuf = new int[7]; //buffer non flush
    
 // Patterns de suites sur 13 bits de rankBitmask (bit 0 = TWO, bit 12 = ACE)
    private static final int[] STRAIGHT_MASKS = {
        0b1_1111_0000_0000, // A-K-Q-J-T
        0b0_1111_1000_0000, // K-Q-J-T-9
        0b0_0111_1100_0000, // Q-J-T-9-8
        0b0_0011_1110_0000, // J-T-9-8-7
        0b0_0001_1111_0000, // T-9-8-7-6
        0b0_0000_1111_1000, // 9-8-7-6-5
        0b0_0000_0111_1100, // 8-7-6-5-4
        0b0_0000_0011_1110, // 7-6-5-4-3
        0b0_0000_0001_1111, // 6-5-4-3-2
        0b1_0000_0000_1111, // A-5-4-3-2 
    };
    
    //Constructeur
    public FastHandEvaluator(int[] lookupTable) {
        if (lookupTable == null || lookupTable.length != LookupTableGenerator.TABLE_SIZE)
            throw new IllegalArgumentException(
                "Lookup table invalide : taille attendue " + LookupTableGenerator.TABLE_SIZE);
        this.table = lookupTable;
    }
    
    //Évalue la meilleure main de 5 parmi 7 cartes
    public int evaluate7(int[] enc, int[] idx) {
        int flushSuit = detectFlushSuit(enc);
        if (flushSuit != 0) {
        	return evaluateFlush7(enc, idx, flushSuit);
        } else {
        	return evaluateNonFlush7(idx);
        }
    }
    
    //Detetction du flush
    private static int detectFlushSuit(int[] enc) {
        int cC = 0; 
        int cD = 0; 
        int cH = 0;
        int cS = 0;
        for (int i = 0; i < 7; i++) {
            int s = enc[i] & CardPrimitiveBridge.MASK_SUIT;
            if(s == CardPrimitiveBridge.SUIT_CLUBS) {
            	cC++;
            }
            else if (s == CardPrimitiveBridge.SUIT_DIAMONDS){
            	cD++;
            }
            else if (s == CardPrimitiveBridge.SUIT_HEARTS){
            	cH++;
            }
            else{
            	cS++;
            }
        }
        if (cC >= 5) {
        	return CardPrimitiveBridge.SUIT_CLUBS;
        }
        if (cD >= 5) {
        	return CardPrimitiveBridge.SUIT_DIAMONDS;
        }
        if (cH >= 5) {
        	return CardPrimitiveBridge.SUIT_HEARTS;
        }
        if (cS >= 5) {
        	return CardPrimitiveBridge.SUIT_SPADES;
        }
        return 0;
    }
    
    private int evaluateFlush7(int[] enc, int[] idx, int flushSuit) {
        int n = 0;
        for (int i = 0; i < 7; i++) {
            if ((enc[i] & CardPrimitiveBridge.MASK_SUIT) == flushSuit) {
                flushBuf[n++] = idx[i];
            }
        }
        // Tri insertion décroissant par rankIndex (idx/4 = rankIndex)
        for (int i = 1; i < n; i++) {
            int key = flushBuf[i], keyR = key / 4, j = i - 1;
            while (j >= 0 && flushBuf[j] / 4 < keyR) { flushBuf[j+1] = flushBuf[j]; j--; }
            flushBuf[j+1] = key;
        }
        // Les 5 premiers sont les meilleurs rangs — les trier croissants pour l'index
        int a = flushBuf[0], b = flushBuf[1], c = flushBuf[2],
            d = flushBuf[3], e = flushBuf[4];
        sort5InPlace(comboBuf, a, b, c, d, e);
        return table[LookupTableGenerator.combinatorialIndex(
            comboBuf[0], comboBuf[1], comboBuf[2], comboBuf[3], comboBuf[4])];
    }
    
    private int evaluateNonFlush7(int[] idx) {
        // 1. Copier les 7 indices dans le buffer temporaire
        for (int i = 0; i < 7; i++) {
            sortBuf[i] = idx[i];
        }
        
        // 2. Trier les 7 cartes UNE SEULE FOIS (Tri par insertion, ultra-rapide pour N=7)
        for (int i = 1; i < 7; i++) {
            int key = sortBuf[i];
            int j = i - 1;
            while (j >= 0 && sortBuf[j] > key) { 
                sortBuf[j+1] = sortBuf[j]; 
                j--; 
            }
            sortBuf[j+1] = key;
        }

        int best = 0;
        // 3. Générer les 21 combinaisons (elles sont DÉJÀ triées !)
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 7; j++) {
                int pos = 0;
                for (int k = 0; k < 7; k++) {
                    if (k != i && k != j) comboBuf[pos++] = sortBuf[k];
                }
                
                // L'APPEL A sort5InPlace A DISPARU.
                // On envoie directement la combinaison dans la table.
                int v = table[LookupTableGenerator.combinatorialIndex(
                    comboBuf[0], comboBuf[1], comboBuf[2], comboBuf[3], comboBuf[4])];
                
                if (v > best) best = v;
            }
        }
        return best;
    }
    
    //Tri 5 entiers dans buff en ordre croissant
    static void sort5InPlace(int[] buf, int a, int b, int c, int d, int e) {
        int t;
        if (a > b) {
        	t=a; 
        	a=b; 
        	b=t;
        }
        if (c > d) { 
        	t=c; 
        	c=d; 
        	d=t; 
        }
        if (a > c) { 
        	t=a; 
        	a=c; 
        	c=t; 
        }
        if (b > d) { 
        	t=b; 
        	b=d; 
        	d=t; 
        }
        if (b > c) { 
        	t=b; 
        	b=c; 
        	c=t; 
        }
        if (a > e) { 
        	t=a; 
        	a=e; 
        	e=t; 
        }
        if (c > e) { 
        	t=c; 
        	c=e; 
        	e=t; 
        }
        if (b > e) { 
        	t=b; 
        	b=e; 
        	e=t; 
        }
        if (d > e) { 
        	t=d; 
        	d=e; 
        	e=t; 
        }
        buf[0]=a; buf[1]=b; buf[2]=c; buf[3]=d; buf[4]=e;
    }
    
    //Detection des suites
    public static int detectStraight(int rankBitmask) {
        for (int i = 0; i < STRAIGHT_MASKS.length; i++) {
            if ((rankBitmask & STRAIGHT_MASKS[i]) == STRAIGHT_MASKS[i]) return i;
        }
        return -1;
    }
}
