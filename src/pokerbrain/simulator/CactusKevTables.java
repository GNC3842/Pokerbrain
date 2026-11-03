package pokerbrain.simulator;

import pokerbrain.model.core.HandType;

/**
 * Tables de lookup CactusKev — une seule représentation de la force d'une main :
 * la <b>classe d'équivalence canonique 1–7462</b>.
 *
 * <p><b>1 est la main la plus forte</b> (quinte flush royale), 7462 la plus faible
 * (7-5-4-3-2 dépareillé). Comparer deux mains = comparer deux int, le plus PETIT gagne.
 * C'est la convention CactusKev universelle ; elle permet d'indexer directement une
 * table de percentiles ou de noms, ce que l'ancien layout maison 24 bits
 * (type&lt;&lt;20 | kickers) interdisait.
 *
 * <p>Découpage des 7462 classes, dans l'ordre :
 * <pre>
 *      1 –   10  quintes flush (royale = 1, roue = 10)
 *     11 –  166  carrés                      (156)
 *    167 –  322  fulls                       (156)
 *    323 – 1599  couleurs                   (1277)
 *   1600 – 1609  quintes                      (10)
 *   1610 – 2467  brelans                     (858)
 *   2468 – 3325  doubles paires              (858)
 *   3326 – 6185  paires                     (2860)
 *   6186 – 7462  cartes hautes              (1277)
 * </pre>
 *
 * <p>Trois structures, toutes primitives — aucune allocation ni autoboxing à la
 * consultation, contrairement à l'ancien {@code HashMap<Integer,Integer>} qui
 * hachait et déballait un Integer sur chaque main appariée de la boucle chaude :
 * <ul>
 *   <li>{@link #FLUSHES}[masque] : meilleure classe de couleur pour ce masque de rangs.
 *       Défini pour <b>5, 6 et 7 bits</b> : le flop d'une couleur à 6 ou 7 cartes se lit
 *       d'un seul accès, et la quinte flush ne peut plus être ratée.</li>
 *   <li>{@link #UNIQUE5}[masque] : classe d'une main non-flush à 5 rangs distincts
 *       (quinte ou carte haute). 0 si le masque n'a pas exactement 5 bits.</li>
 *   <li>table ouverte produit-de-premiers → classe pour les mains appariées
 *       (cf. {@link #paired(int)}), 4888 entrées dans 16384 cases.</li>
 * </ul>
 *
 * <p>Les tables sont engendrées par <b>combinatoire pure</b> au chargement de la classe :
 * plus aucun appel à {@code engine.HandEvaluator}, donc plus de dépendance
 * {@code simulator → engine} et plus d'ordre défini « de fait » par
 * {@code HandRank.calculateNumericValue()}. L'évaluateur objet redevient ce qu'il
 * doit être : un oracle de test.
 */
final class CactusKevTables {
    private CactusKevTables() {}

    /** Nombre de classes d'équivalence. Les classes valides vont de 1 à CLASS_COUNT. */
    static final int CLASS_COUNT = 7462;

    /** Meilleure classe de couleur pour un masque de rangs de 5 à 7 bits. */
    static final int[] FLUSHES = new int[8192];

    /** Classe d'une main non-flush à 5 rangs distincts ; 0 si le masque n'a pas 5 bits. */
    static final int[] UNIQUE5 = new int[8192];

    /** Type de main de chaque classe — décodage inverse pour l'affichage. */
    static final HandType[] CLASS_TYPE = new HandType[CLASS_COUNT + 1];

    /**
     * Les 5 rangs de chaque classe, 4 bits chacun, du plus significatif au moins
     * (mêmes conventions que les kickers de {@code HandRank} : paires en tête,
     * roue = 5 4 3 2 A). Rang stocké en valeur 2–14.
     */
    static final int[] CLASS_RANKS = new int[CLASS_COUNT + 1];

    // Table ouverte (linear probing) produit de premiers → classe.
    // 4888 clés dans 16384 cases : taux de remplissage 30 %, chaînes de sondage courtes.
    private static final int PAIRED_BITS = 14;
    private static final int PAIRED_MASK = (1 << PAIRED_BITS) - 1;
    private static final int[] PAIRED_KEYS  = new int[1 << PAIRED_BITS];
    private static final int[] PAIRED_CLASS = new int[1 << PAIRED_BITS];

    /** Masques des 10 quintes, de l'As-haute à la roue (ordre de force décroissante). */
    private static final int[] STRAIGHT_MASKS = new int[10];
    /** Valeur de la carte haute de chaque quinte (14 … 6, puis 5 pour la roue). */
    private static final int[] STRAIGHT_HIGH = new int[10];

    private static int next; // prochaine classe à attribuer

    static {
        for (int high = 12; high >= 4; high--) {   // index de rang : As(12) … 6(4)
            int i = 12 - high;
            STRAIGHT_MASKS[i] = 0b11111 << (high - 4);
            STRAIGHT_HIGH[i]  = high + 2;
        }
        STRAIGHT_MASKS[9] = (1 << 12) | 0b1111;    // roue : A 5 4 3 2
        STRAIGHT_HIGH[9]  = 5;

        build();
        extendFlushesTo6And7Cards();
    }

    /**
     * Consultation d'une main appariée par le produit de ses 5 nombres premiers de rang.
     * Renvoie 0 si le produit est inconnu — ce qui ne peut arriver que sur un bug
     * d'encodage, jamais sur une main de 5 cartes légale.
     */
    static int paired(int product) {
        for (int i = hash(product); PAIRED_KEYS[i] != 0; i = (i + 1) & PAIRED_MASK) {
            if (PAIRED_KEYS[i] == product) return PAIRED_CLASS[i];
        }
        return 0;
    }

    private static int hash(int product) {
        return (product * 0x9E3779B1) >>> (32 - PAIRED_BITS);
    }

    private static void putPaired(int product, int rankClass) {
        int i = hash(product);
        while (PAIRED_KEYS[i] != 0) {
            if (PAIRED_KEYS[i] == product)
                throw new IllegalStateException("Produit de premiers en double : " + product);
            i = (i + 1) & PAIRED_MASK;
        }
        PAIRED_KEYS[i]  = product;
        PAIRED_CLASS[i] = rankClass;
    }

    // ─────────────────────────────── Génération ───────────────────────────────

    private static void build() {
        next = 1;

        // 1–10 : quintes flush, de la royale à la roue
        for (int i = 0; i < 10; i++) {
            HandType type = (i == 0) ? HandType.ROYAL_FLUSH : HandType.STRAIGHT_FLUSH;
            FLUSHES[STRAIGHT_MASKS[i]] = registerStraight(type, STRAIGHT_HIGH[i]);
        }

        // 11–166 : carrés, rang du carré décroissant puis kicker décroissant
        for (int quad = 12; quad >= 0; quad--) {
            for (int kicker = 12; kicker >= 0; kicker--) {
                if (kicker == quad) continue;
                putPaired(pow(quad, 4) * prime(kicker),
                    register(HandType.FOUR_OF_KIND, quad, quad, quad, quad, kicker));
            }
        }

        // 167–322 : fulls, rang du brelan décroissant puis rang de la paire
        for (int trips = 12; trips >= 0; trips--) {
            for (int pair = 12; pair >= 0; pair--) {
                if (pair == trips) continue;
                putPaired(pow(trips, 3) * pow(pair, 2),
                    register(HandType.FULL_HOUSE, trips, trips, trips, pair, pair));
            }
        }

        // 323–1599 : couleurs non quintes, masque décroissant = force décroissante
        forEachFiveRankMask(false, HandType.FLUSH);

        // 1600–1609 : quintes
        for (int i = 0; i < 10; i++) {
            UNIQUE5[STRAIGHT_MASKS[i]] = registerStraight(HandType.STRAIGHT, STRAIGHT_HIGH[i]);
        }

        // 1610–2467 : brelans, rang du brelan puis les deux kickers décroissants
        for (int trips = 12; trips >= 0; trips--) {
            for (int k0 = 12; k0 >= 0; k0--) {
                if (k0 == trips) continue;
                for (int k1 = k0 - 1; k1 >= 0; k1--) {
                    if (k1 == trips) continue;
                    putPaired(pow(trips, 3) * prime(k0) * prime(k1),
                        register(HandType.THREE_OF_KIND, trips, trips, trips, k0, k1));
                }
            }
        }

        // 2468–3325 : doubles paires, grosse paire puis petite paire puis kicker
        for (int high = 12; high >= 1; high--) {
            for (int low = high - 1; low >= 0; low--) {
                for (int kicker = 12; kicker >= 0; kicker--) {
                    if (kicker == high || kicker == low) continue;
                    putPaired(pow(high, 2) * pow(low, 2) * prime(kicker),
                        register(HandType.TWO_PAIR, high, high, low, low, kicker));
                }
            }
        }

        // 3326–6185 : paires, rang de la paire puis les trois kickers décroissants
        for (int pair = 12; pair >= 0; pair--) {
            for (int k0 = 12; k0 >= 0; k0--) {
                if (k0 == pair) continue;
                for (int k1 = k0 - 1; k1 >= 0; k1--) {
                    if (k1 == pair) continue;
                    for (int k2 = k1 - 1; k2 >= 0; k2--) {
                        if (k2 == pair) continue;
                        putPaired(pow(pair, 2) * prime(k0) * prime(k1) * prime(k2),
                            register(HandType.PAIR, pair, pair, k0, k1, k2));
                    }
                }
            }
        }

        // 6186–7462 : cartes hautes, même ordre que les couleurs
        forEachFiveRankMask(true, HandType.HIGH_CARD);

        if (next - 1 != CLASS_COUNT) {
            throw new IllegalStateException(
                "Génération incomplète : " + (next - 1) + " classes au lieu de " + CLASS_COUNT);
        }
    }

    /**
     * Parcourt les 1277 masques de 5 rangs distincts qui ne forment pas une quinte, du
     * plus fort au plus faible. Le masque vu comme entier ordonne exactement comme la
     * comparaison des rangs triés décroissants : le bit le plus haut domine.
     *
     * @param unique5 true pour remplir UNIQUE5 (cartes hautes), false pour FLUSHES (couleurs)
     */
    private static void forEachFiveRankMask(boolean unique5, HandType type) {
        for (int mask = 8191; mask >= 31; mask--) {
            if (Integer.bitCount(mask) != 5 || isStraightMask(mask)) continue;
            int[] ranks = ranksOf(mask);
            int rankClass = register(type, ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]);
            if (unique5) {
                UNIQUE5[mask] = rankClass;
            } else {
                FLUSHES[mask] = rankClass;
            }
        }
    }

    /**
     * Étend FLUSHES aux masques de 6 et 7 bits : la meilleure couleur de n cartes
     * assorties est le minimum sur ses sous-ensembles de 5. Sans cette extension il
     * fallait trier les cartes de la couleur et garder les 5 plus hautes — ce qui ratait
     * la quinte flush dès que la main en comptait 6 ou 7 (9h8h7h6h5h + Ah lue « couleur
     * à l'As » au lieu de « quinte flush au 9 »).
     */
    private static void extendFlushesTo6And7Cards() {
        for (int mask = 0; mask < 8192; mask++) {
            int bits = Integer.bitCount(mask);
            if (bits != 6 && bits != 7) continue;

            int best = CLASS_COUNT + 1;
            // Retirer 1 bit (6 cartes) ou 2 bits (7 cartes) de toutes les façons possibles
            for (int drop = mask; drop != 0; drop &= drop - 1) {
                int a = Integer.lowestOneBit(drop);
                if (bits == 6) {
                    best = Math.min(best, FLUSHES[mask ^ a]);
                } else {
                    for (int drop2 = drop & (drop - 1); drop2 != 0; drop2 &= drop2 - 1) {
                        best = Math.min(best, FLUSHES[mask ^ a ^ Integer.lowestOneBit(drop2)]);
                    }
                }
            }
            FLUSHES[mask] = best;
        }
    }

    // ─────────────────────────────── Helpers ───────────────────────────────

    private static int register(HandType type, int r0, int r1, int r2, int r3, int r4) {
        int rankClass = next++;
        CLASS_TYPE[rankClass] = type;
        // Rangs stockés en valeur 2–14, soit l'index + 2
        CLASS_RANKS[rankClass] = ((r0 + 2) << 16) | ((r1 + 2) << 12) | ((r2 + 2) << 8)
                               | ((r3 + 2) << 4)  |  (r4 + 2);
        return rankClass;
    }

    /** Enregistre une quinte depuis la valeur de sa carte haute. La roue se lit 5 4 3 2 A. */
    private static int registerStraight(HandType type, int highValue) {
        int rankClass = next++;
        CLASS_TYPE[rankClass] = type;
        if (highValue == 5) {
            CLASS_RANKS[rankClass] = (5 << 16) | (4 << 12) | (3 << 8) | (2 << 4) | 14;
        } else {
            CLASS_RANKS[rankClass] = (highValue << 16) | ((highValue - 1) << 12)
                                   | ((highValue - 2) << 8) | ((highValue - 3) << 4)
                                   |  (highValue - 4);
        }
        return rankClass;
    }

    /** Index des rangs d'un masque, du plus fort au plus faible. */
    private static int[] ranksOf(int mask) {
        int[] ranks = new int[Integer.bitCount(mask)];
        int n = 0;
        for (int r = 12; r >= 0; r--) {
            if ((mask & (1 << r)) != 0) ranks[n++] = r;
        }
        return ranks;
    }

    private static boolean isStraightMask(int mask) {
        for (int straight : STRAIGHT_MASKS) {
            if (mask == straight) return true;
        }
        return false;
    }

    private static int prime(int rankIndex) {
        return CardPrimitiveBridge.RANK_PRIMES[rankIndex];
    }

    private static int pow(int rankIndex, int exponent) {
        int p = prime(rankIndex);
        int result = 1;
        for (int i = 0; i < exponent; i++) result *= p;
        return result;
    }
}
