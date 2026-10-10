package pokerbrain.simulator;

import pokerbrain.model.core.HandRank;
import pokerbrain.model.core.Rank;

import java.util.ArrayList;
import java.util.List;

/**
 * Évaluateur de mains sans allocation — CactusKev O(1).
 *
 * <p>Rend la <b>classe d'équivalence canonique 1–7462</b> : <b>plus petit = plus fort</b>
 * (1 = quinte flush royale, 7462 = 7-5-4-3-2). Deux mains se comparent donc par un simple
 * {@code <} sur des int, sans décodage.
 *
 * <p>{@link #evaluate7(int[])} travaille en deux chemins :
 * <ul>
 *   <li><b>couleur</b> — un masque de rangs par couleur est accumulé en une passe ; dès
 *       qu'une couleur compte 5 cartes ou plus, un seul accès à la table suffit. Avec
 *       5 cartes assorties sur 7, aucune main non-flush ne peut faire mieux (il ne reste
 *       que 2 cartes hors couleur, de quoi faire un brelan au mieux, jamais un full ni un
 *       carré), donc le retour est immédiat.</li>
 *   <li><b>non-couleur</b> — les 21 façons d'écarter 2 cartes sur 7, pré-calculées, et un
 *       minimum sur les classes obtenues.</li>
 * </ul>
 *
 * <p>Une instance porte un petit tampon : <b>une instance par thread</b>.
 */
public class FastHandEvaluator {

    /** Pire que toute classe réelle : valeur neutre pour un minimum. */
    private static final int NONE = CactusKevTables.CLASS_COUNT + 1;

    /**
     * Les 21 combinaisons de 5 positions parmi 7, à plat (21 × 5 = 105).
     * Pré-calculées pour ne pas reconstruire le complément de {i,j} à chaque tour
     * de la boucle chaude.
     */
    private static final int[] COMBOS_7_CHOOSE_5 = buildCombos();

    /** Masque de rangs par couleur, réutilisé d'un appel à l'autre. */
    private final int[] suitRanks = new int[4];

    public FastHandEvaluator() {}

    /**
     * Classe canonique de la meilleure main de 5 cartes parmi les 7 encodées.
     *
     * @param enc 7 cartes encodées par {@link CardPrimitiveBridge#encode}
     * @return classe dans 1–7462, la plus petite étant la plus forte
     */
    public int evaluate7(int[] enc) {
        suitRanks[0] = 0; suitRanks[1] = 0; suitRanks[2] = 0; suitRanks[3] = 0;
        for (int i = 0; i < 7; i++) {
            int card = enc[i];
            int suit = Integer.numberOfTrailingZeros(card & CardPrimitiveBridge.MASK_SUIT)
                     - CardPrimitiveBridge.SHIFT_SUIT;
            suitRanks[suit] |= card >>> CardPrimitiveBridge.SHIFT_RANK_BIT;
        }
        for (int suit = 0; suit < 4; suit++) {
            int mask = suitRanks[suit];
            if (Integer.bitCount(mask) >= 5) {
                return CactusKevTables.FLUSHES[mask];
            }
        }
        return evaluateNonFlush7(enc);
    }

    private static int evaluateNonFlush7(int[] enc) {
        int best = NONE;
        for (int c = 0; c < COMBOS_7_CHOOSE_5.length; c += 5) {
            int v = eval5(enc,
                COMBOS_7_CHOOSE_5[c],     COMBOS_7_CHOOSE_5[c + 1], COMBOS_7_CHOOSE_5[c + 2],
                COMBOS_7_CHOOSE_5[c + 3], COMBOS_7_CHOOSE_5[c + 4]);
            if (v < best) best = v;
        }
        return best;
    }

    /**
     * Classe d'une main de 5 cartes connue non-flush.
     * UNIQUE5 ne vaut 0 que si le masque n'a pas 5 bits distincts, c'est-à-dire si la
     * main est appariée : on bascule alors sur le produit des nombres premiers de rang.
     */
    private static int eval5(int[] enc, int a, int b, int c, int d, int e) {
        int mask = (enc[a] | enc[b] | enc[c] | enc[d] | enc[e])
                 >>> CardPrimitiveBridge.SHIFT_RANK_BIT;
        int distinct = CactusKevTables.UNIQUE5[mask];
        if (distinct != 0) return distinct;
        return CactusKevTables.paired(
              (enc[a] & 0xff) * (enc[b] & 0xff) * (enc[c] & 0xff)
            * (enc[d] & 0xff) * (enc[e] & 0xff));
    }

    /**
     * Reconstruit un {@link HandRank} affichable depuis une classe canonique
     * (« Full aux Rois »). Hors boucle chaude : alloue.
     */
    public static HandRank toHandRank(int rankClass) {
        if (rankClass < 1 || rankClass > CactusKevTables.CLASS_COUNT) {
            throw new IllegalArgumentException("Classe hors bornes : " + rankClass);
        }
        int packed = CactusKevTables.CLASS_RANKS[rankClass];
        List<Rank> kickers = new ArrayList<>(5);
        for (int shift = 16; shift >= 0; shift -= 4) {
            kickers.add(Rank.fromValue((packed >>> shift) & 0xf));
        }
        return new HandRank(CactusKevTables.CLASS_TYPE[rankClass], kickers);
    }

    private static int[] buildCombos() {
        int[] combos = new int[21 * 5];
        int n = 0;
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 7; j++) {
                for (int k = 0; k < 7; k++) {
                    if (k != i && k != j) combos[n++] = k;
                }
            }
        }
        return combos;
    }
}
