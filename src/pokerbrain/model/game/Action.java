package pokerbrain.model.game;

/*
 * Action d'un joueur lors d'un tour d'enchere
 * amout > 0 si involvemoney*/

public class Action {
	
	private final Player player;
	private final int amount;
	private final ActionType type;
	private final BettingRound round;
	
	//Constructeur complet
	public Action(Player player, ActionType type, int amount, BettingRound round) {
		if(player == null) {
			throw new IllegalArgumentException("Player ne peut pas etre null");
		}
		if(type == null) {
			throw new IllegalArgumentException("Type ne peut pas etre null");
		}
		if(round == null) {
			throw new IllegalArgumentException("Round ne peut pas etre null");
		}
		this.player = player;
		this.type = type;
		this.round = round;
		
		if(type.involveMoney() && amount <= 0) {
			throw new IllegalArgumentException("Amount doit etre positif pour"+type);
		}
		if (!type.involveMoney() && amount!=0) {
			throw new IllegalArgumentException("Amount doit etre null pour cette action:" + type);
		}
		this.amount = amount;
	}
	
	
	//Constructeur raccourci sans amount pour FOLD ET CHECK
	public Action(Player player, ActionType type,BettingRound round) {
		this(player,type,0,round);
	}
	
	//Liste des getters classiques
	public ActionType getType() {
		return this.type;
	}
	
	public Player getPlayer() {
		return this.player;
	}
	
	public int getAmount() {
		return this.amount;
	}
	
	public BettingRound getRound() {
		return this.round;
	}
	
	//toString
	@Overrride
	public String toString() {
		if(this.getType().involveMoney()) {
			return getRound()+ " - "+ getPlayer().getName()+": "+getType()+" "+getAmount();
		}
		return getRound()+ " - "+ getPlayer().getName()+": "+getType();
	}
	
	
}
