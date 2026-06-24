package pokerbrain.simulator;

import pokerbrain.engine.HandEvaluator;
import pokerbrain.model.core.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Tables de lookup CactusKev pour l'évaluation O(1) de mains de 5 cartes.
 *
 * Trois structures :
 *   FLUSHES[q]  : mains flush, q = OR(rank_bits) >> 16 des 5 cartes (13 bits)
 *   UNIQUE5[q]  : mains sans paire et sans flush, même index
 *   PRIMES_MAP  : mains avec paire(s), clé = produit des 5 nombres premiers de rang
 *
 * Générées au chargement de la classe en ~5 ms (5 K évaluations)
 * au lieu des ~5 s du LookupTableGenerator (2,6 M évaluations).
 */
final class CactusKevTables {
    private CactusKevTables() {}

    static final int[] FLUSHES  = new int[8192];
    static final int[] UNIQUE5  = new int[8192];
    static final HashMap<Integer, Integer> PRIMES_MAP;

    static {
        PRIMES_MAP = new HashMap<>(6000);
        build();
    }

    private static void build() {
        Rank[] ranks = Rank.values();   // TWO(idx 0) … ACE(idx 12)
        Suit[] suits = Suit.values();   // CLUBS, DIAMONDS, HEARTS, SPADES
        int[]  p     = CardPrimitiveBridge.RANK_PRIMES;

        // --- 5 rangs distincts (1 287 combinaisons) ---
        for (int r0 = 0;  r0 < 9;  r0++)
        for (int r1 = r0+1; r1 < 10; r1++)
        for (int r2 = r1+1; r2 < 11; r2++)
        for (int r3 = r2+1; r3 < 12; r3++)
        for (int r4 = r3+1; r4 < 13; r4++) {
            int q = (1<<r0)|(1<<r1)|(1<<r2)|(1<<r3)|(1<<r4);

            // Flush : toutes piques
            FLUSHES[q] = encode(HandEvaluator.evaluateBestHand(List.of(
                new Card(ranks[r0], suits[3]),
                new Card(ranks[r1], suits[3]),
                new Card(ranks[r2], suits[3]),
                new Card(ranks[r3], suits[3]),
                new Card(ranks[r4], suits[3]))));

            // Non-flush rangs distincts : couleurs mélangées (≤ 2 de la même)
            UNIQUE5[q] = encode(HandEvaluator.evaluateBestHand(List.of(
                new Card(ranks[r0], suits[0]),
                new Card(ranks[r1], suits[1]),
                new Card(ranks[r2], suits[0]),
                new Card(ranks[r3], suits[1]),
                new Card(ranks[r4], suits[2]))));
        }

        // --- Paire (2 860 types) ---
        for (int pair = 0; pair < 13; pair++)
        for (int k0   = 0; k0   < 13; k0++) { if (k0 == pair) continue;
        for (int k1 = k0+1; k1  < 13; k1++) { if (k1 == pair) continue;
        for (int k2 = k1+1; k2  < 13; k2++) { if (k2 == pair) continue;
            PRIMES_MAP.put(p[pair]*p[pair]*p[k0]*p[k1]*p[k2],
                encode(HandEvaluator.evaluateBestHand(List.of(
                    new Card(ranks[pair], suits[0]),
                    new Card(ranks[pair], suits[1]),
                    new Card(ranks[k0],   suits[2]),
                    new Card(ranks[k1],   suits[3]),
                    new Card(ranks[k2],   suits[0])))));
        }}}

        // --- Double paire (858 types) ---
        for (int p0 = 0;    p0 < 12; p0++)
        for (int p1 = p0+1; p1 < 13; p1++)
        for (int kk = 0;    kk < 13; kk++) {
            if (kk == p0 || kk == p1) continue;
            PRIMES_MAP.put(p[p0]*p[p0]*p[p1]*p[p1]*p[kk],
                encode(HandEvaluator.evaluateBestHand(List.of(
                    new Card(ranks[p0], suits[0]),
                    new Card(ranks[p0], suits[1]),
                    new Card(ranks[p1], suits[2]),
                    new Card(ranks[p1], suits[3]),
                    new Card(ranks[kk], suits[0])))));
        }

        // --- Brelan (858 types) ---
        for (int tr = 0; tr < 13; tr++)
        for (int k0 = 0; k0 < 12; k0++) { if (k0 == tr) continue;
        for (int k1 = k0+1; k1 < 13; k1++) { if (k1 == tr) continue;
            PRIMES_MAP.put(p[tr]*p[tr]*p[tr]*p[k0]*p[k1],
                encode(HandEvaluator.evaluateBestHand(List.of(
                    new Card(ranks[tr], suits[0]),
                    new Card(ranks[tr], suits[1]),
                    new Card(ranks[tr], suits[2]),
                    new Card(ranks[k0], suits[3]),
                    new Card(ranks[k1], suits[0])))));
        }}

        // --- Full house (156 types) ---
        for (int tr = 0; tr < 13; tr++)
        for (int pa = 0; pa < 13; pa++) {
            if (pa == tr) continue;
            PRIMES_MAP.put(p[tr]*p[tr]*p[tr]*p[pa]*p[pa],
                encode(HandEvaluator.evaluateBestHand(List.of(
                    new Card(ranks[tr], suits[0]),
                    new Card(ranks[tr], suits[1]),
                    new Card(ranks[tr], suits[2]),
                    new Card(ranks[pa], suits[3]),
                    new Card(ranks[pa], suits[0])))));
        }

        // --- Carré (156 types) ---
        for (int qu = 0; qu < 13; qu++)
        for (int kk = 0; kk < 13; kk++) {
            if (kk == qu) continue;
            PRIMES_MAP.put(p[qu]*p[qu]*p[qu]*p[qu]*p[kk],
                encode(HandEvaluator.evaluateBestHand(List.of(
                    new Card(ranks[qu], suits[0]),
                    new Card(ranks[qu], suits[1]),
                    new Card(ranks[qu], suits[2]),
                    new Card(ranks[qu], suits[3]),
                    new Card(ranks[kk], suits[0])))));
        }
    }

    static int encode(HandRank hr) {
        List<Rank> k = hr.getKickers();
        return (hr.getType().getBaseValue() << 20)
            | (k.get(0).getValue() << 16)
            | (k.get(1).getValue() << 12)
            | (k.get(2).getValue() << 8)
            | (k.get(3).getValue() << 4)
            |  k.get(4).getValue();
    }
}
