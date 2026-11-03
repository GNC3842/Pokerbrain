package pokerbrain.engine;

import pokerbrain.model.core.*;
import java.util.*;

/**
 * Calcule la meilleure main de 5 cartes parmi 5, 6 ou 7 cartes.
 *
 * <p><b>Oracle de test — plus aucun appelant en production.</b> Le showdown
 * ({@link PotManager}), l'énumération exacte ({@link EquityCalculator}) et le Monte-Carlo
 * passent tous par {@code simulator.FastHandEvaluator}. Cette implémentation-ci est lente
 * et alloue beaucoup (une {@code ArrayList}, une {@code LinkedHashMap} et un
 * {@code HandRank} par combinaison de 5 cartes), mais elle est écrite de façon directement
 * lisible depuis les règles du poker : c'est précisément ce qui en fait une bonne
 * référence. {@code HandEvaluatorTest} compare le rapide à celle-ci.
 *
 * <p>À ne pas rebrancher dans un chemin chaud : garder un seul évaluateur de production.
 */
public final class HandEvaluator {

	private HandEvaluator() {}

	//Évalue la meilleure main parmi les cartes fournies (5, 6 ou 7 cartes).
	public static HandRank evaluateBestHand(List<Card> cards) {
		if (cards == null) throw new IllegalArgumentException("Cards ne peut pas être null");
		if (cards.size() < 5 || cards.size() > 7)
			throw new IllegalArgumentException("Nombre de cartes invalide : " + cards.size() + " (attendu 5-7)");
		if (cards.size() == 5) return evaluateFiveCards(cards);

		// Génère C(n,5) en excluant 2 cartes parmi n → 21 combos pour n=7
		HandRank best = null;
		int n = cards.size();
		for (int i = 0; i < n - 1; i++) {
			for (int j = i + 1; j < n; j++) {
				List<Card> five = new ArrayList<>(5);
				for (int k = 0; k < n; k++) {
					if (k != i && k != j) five.add(cards.get(k));
				}
				HandRank hr = evaluateFiveCards(five);
				if (best == null || hr.compareTo(best) > 0) best = hr;
			}
		}
		return best;
	}

	private static HandRank evaluateFiveCards(List<Card> five) {
		// Rangs triés décroissant
		List<Rank> sortedRanks = new ArrayList<>();
		for (Card c : five) sortedRanks.add(c.getRank());
		sortedRanks.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

		boolean flush    = five.stream().map(Card::getSuit).distinct().count() == 1;
		boolean straight = isStraight(sortedRanks);
		Rank straightHi  = straight ? getStraightHigh(sortedRanks) : null;

		// Fréquences triées par (freq desc, rang desc)
		Map<Rank, Integer> freqMap = new LinkedHashMap<>();
		for (Rank r : sortedRanks) freqMap.merge(r, 1, Integer::sum);
		List<Map.Entry<Rank, Integer>> entries = new ArrayList<>(freqMap.entrySet());
		entries.sort((a, b) -> {
			int cmp = b.getValue() - a.getValue();
			return cmp != 0 ? cmp : b.getKey().getValue() - a.getKey().getValue();
		});

		int maxFreq = entries.get(0).getValue();
		int groups  = entries.size();
		List<Rank> kickers = buildKickers(entries);

		if (flush && straight) {
			if (straightHi == Rank.ACE)
				return new HandRank(HandType.ROYAL_FLUSH,
					rankList(Rank.ACE, Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN));
			return new HandRank(HandType.STRAIGHT_FLUSH, straightKickers(straightHi));
		}
		if (maxFreq == 4)
			return new HandRank(HandType.FOUR_OF_KIND, kickers);
		if (maxFreq == 3 && groups == 2)
			return new HandRank(HandType.FULL_HOUSE, kickers);
		if (flush)
			return new HandRank(HandType.FLUSH, sortedRanks);
		if (straight)
			return new HandRank(HandType.STRAIGHT, straightKickers(straightHi));
		if (maxFreq == 3)
			return new HandRank(HandType.THREE_OF_KIND, kickers);
		if (maxFreq == 2 && groups == 3)
			return new HandRank(HandType.TWO_PAIR, kickers);
		if (maxFreq == 2)
			return new HandRank(HandType.PAIR, kickers);
		return new HandRank(HandType.HIGH_CARD, sortedRanks);
	}

	/**
	 * Construit 5 kickers dans l'ordre (fréquence desc, rang desc).
	 *
	 * Exemples :
	 *   Full House A-A-A-K-K → [(A,3),(K,2)]       → [A, A, A, K, K]
	 *   Two Pair   Q-Q-J-J-A → [(Q,2),(J,2),(A,1)] → [Q, Q, J, J, A]
	 *   Pair       K-K-A-Q-J → [(K,2),(A,1),(Q,1)] → [K, K, A, Q, J]
	 */
	private static List<Rank> buildKickers(List<Map.Entry<Rank, Integer>> entries) {
		List<Rank> kickers = new ArrayList<>(5);
		for (Map.Entry<Rank, Integer> e : entries) {
			for (int j = 0; j < e.getValue() && kickers.size() < 5; j++) {
				kickers.add(e.getKey());
			}
		}
		return kickers;
	}

	// Vérifie suite normale + roue (A-2-3-4-5).
	private static boolean isStraight(List<Rank> sortedDesc) {
		boolean normal = true;
		for (int i = 0; i < 4; i++) {
			if (sortedDesc.get(i).getValue() - sortedDesc.get(i + 1).getValue() != 1) {
				normal = false; break;
			}
		}
		if (normal) return true;
		// Roue : [A, 5, 4, 3, 2]
		return sortedDesc.get(0) == Rank.ACE
			&& sortedDesc.get(1) == Rank.FIVE
			&& sortedDesc.get(2) == Rank.FOUR
			&& sortedDesc.get(3) == Rank.THREE
			&& sortedDesc.get(4) == Rank.TWO;
	}

	// Carte haute d'une suite. Roue → 5 (pas As)
	private static Rank getStraightHigh(List<Rank> sortedDesc) {
		if (sortedDesc.get(0) == Rank.ACE && sortedDesc.get(1) == Rank.FIVE)
			return Rank.FIVE;
		return sortedDesc.get(0);
	}

	/**
	 * Calcule les 5 kickers d'une suite à partir de la carte haute.
	 * Roue (A-2-3-4-5) : high=5 → [5, 4, 3, 2, A].
	 * Suite normale K-high  → [K, Q, J, T, 9].
	 */
	private static List<Rank> straightKickers(Rank high) {
		List<Rank> kickers = new ArrayList<>(5);
		if (high == Rank.FIVE) {
			// Roue : A-2-3-4-5
			kickers.add(Rank.FIVE);
			kickers.add(Rank.FOUR);
			kickers.add(Rank.THREE);
			kickers.add(Rank.TWO);
			kickers.add(Rank.ACE);
		} else {
			int val = high.getValue();
			for (int i = 0; i < 5; i++) {
				kickers.add(Rank.fromValue(val - i));
			}
		}
		return kickers;
	}

	private static List<Rank> rankList(Rank r1, Rank r2, Rank r3, Rank r4, Rank r5) {
		List<Rank> list = new ArrayList<>(5);
		list.add(r1); list.add(r2); list.add(r3); list.add(r4); list.add(r5);
		return list;
	}
}
