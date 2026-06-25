package pokerbrain.model.core;

import java.util.*;

public class HandRank implements Comparable<HandRank> {
	private final HandType type;
	private final List<Rank> kickers;
	private final long numericValue;
	
	public HandRank(HandType type, List<Rank> kickers) {
		this.type = type;
		this.kickers = new ArrayList<>(kickers);
		this.numericValue = calculateNumericValue();
	}
	
	private long calculateNumericValue() {
		long value = (long) getType().getBaseValue()*10_000_000_000L;
		long[] weights = {100_000_000L,1_000_000L,10_000L,100L,1L};
		List<Rank> kickerList = getKickers();
		
		for (int i=0;i<Math.min(kickerList.size(),5);i++) {
			value += kickerList.get(i).getValue() * weights[i];
		}
		
		return value;
	}
	//Getters
	public HandType getType() {
		return this.type;
	}
	
	public List<Rank> getKickers(){
		return this.kickers;
	}
	
	public long getNumericValue() {
		return this.numericValue;
	}
	//Comparer 2 HandRanks: positif si this plus fort, négatif si plus faible, 0 sinon
	@Override
	public int compareTo(HandRank other) {
		if (other == null) throw new IllegalArgumentException("Impossible de comparer à null");
		return Long.compare(this.getNumericValue(), other.getNumericValue());
	}
	
	//equals et hascode
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof HandRank)) return false;
		return this.numericValue == ((HandRank) o).numericValue;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(this.numericValue);
	}
	
	public static HandRank decode(int encodedValue) {
		HandType type = HandType.fromBaseValue((encodedValue >> 20) & 0xf);
		List<Rank> kickers = List.of(
			Rank.fromValue((encodedValue >> 16) & 0xf),
			Rank.fromValue((encodedValue >> 12) & 0xf),
			Rank.fromValue((encodedValue >> 8)  & 0xf),
			Rank.fromValue((encodedValue >> 4)  & 0xf),
			Rank.fromValue( encodedValue        & 0xf)
		);
		return new HandRank(type, kickers);
	}

	//toString
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getType().getName());
		sb.append(" [");
		List<Rank> k = getKickers();
		for (int i = 0; i < k.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append(k.get(i).getSymbol());
		}
		sb.append("] (value=").append(getNumericValue()).append(")");
		return sb.toString();
	}
}
