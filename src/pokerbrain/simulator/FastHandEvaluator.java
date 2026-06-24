package pokerbrain.simulator;

/*
 * Evaluateur de mains sans allocations — CactusKev O(1).
 *
 * eval5() : lookup en 3 étapes (flush, unique5, primes)
 * evaluate7() : 1 flush pass ou 21 combos non-flush
 */

public class FastHandEvaluator {

    private final int[] flushBuf = new int[7]; // positions flush dans enc[]
    private final int[] comboBuf = new int[5]; // 5 positions pour combo non-flush

    public FastHandEvaluator() {}

    public int evaluate7(int[] enc, int[] idx) {
        int flushSuit = detectFlushSuit(enc);
        if (flushSuit != 0) {
            return evaluateFlush7(enc, flushSuit);
        } else {
            return evaluateNonFlush7(enc);
        }
    }

    private static int detectFlushSuit(int[] enc) {
        int cC = 0, cD = 0, cH = 0, cS = 0;
        for (int i = 0; i < 7; i++) {
            int s = enc[i] & CardPrimitiveBridge.MASK_SUIT;
            if      (s == CardPrimitiveBridge.SUIT_CLUBS)    cC++;
            else if (s == CardPrimitiveBridge.SUIT_DIAMONDS) cD++;
            else if (s == CardPrimitiveBridge.SUIT_HEARTS)   cH++;
            else                                              cS++;
        }
        if (cC >= 5) return CardPrimitiveBridge.SUIT_CLUBS;
        if (cD >= 5) return CardPrimitiveBridge.SUIT_DIAMONDS;
        if (cH >= 5) return CardPrimitiveBridge.SUIT_HEARTS;
        if (cS >= 5) return CardPrimitiveBridge.SUIT_SPADES;
        return 0;
    }

    private int evaluateFlush7(int[] enc, int flushSuit) {
        int n = 0;
        for (int i = 0; i < 7; i++) {
            if ((enc[i] & CardPrimitiveBridge.MASK_SUIT) == flushSuit) {
                flushBuf[n++] = i;
            }
        }
        // Insertion sort descending by rank index (bits 8-11 of enc)
        for (int i = 1; i < n; i++) {
            int key = flushBuf[i], keyR = (enc[key] >> 8) & 0xf, j = i - 1;
            while (j >= 0 && ((enc[flushBuf[j]] >> 8) & 0xf) < keyR) {
                flushBuf[j + 1] = flushBuf[j]; j--;
            }
            flushBuf[j + 1] = key;
        }
        return eval5(enc, flushBuf[0], flushBuf[1], flushBuf[2], flushBuf[3], flushBuf[4]);
    }

    private int evaluateNonFlush7(int[] enc) {
        int best = 0;
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 7; j++) {
                int pos = 0;
                for (int k = 0; k < 7; k++) {
                    if (k != i && k != j) comboBuf[pos++] = k;
                }
                int v = eval5(enc, comboBuf[0], comboBuf[1], comboBuf[2], comboBuf[3], comboBuf[4]);
                if (v > best) best = v;
            }
        }
        return best;
    }

    private static int eval5(int[] enc, int a, int b, int c, int d, int e) {
        int q = (enc[a] | enc[b] | enc[c] | enc[d] | enc[e]) >> 16;
        if ((enc[a] & enc[b] & enc[c] & enc[d] & enc[e] & 0xf000) != 0)
            return CactusKevTables.FLUSHES[q];
        int s = CactusKevTables.UNIQUE5[q];
        if (s != 0) return s;
        int prod = (enc[a] & 0xff) * (enc[b] & 0xff) * (enc[c] & 0xff)
                 * (enc[d] & 0xff) * (enc[e] & 0xff);
        return CactusKevTables.PRIMES_MAP.get(prod);
    }
}
