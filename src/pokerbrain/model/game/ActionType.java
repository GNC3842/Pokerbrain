package pokerbrain.model.game;

/*
 * Décrire les types d'actions et 
 * */

public enum ActionType {
	//2 parametre: involveMoney et agressive
	FOLD(false,false),
	CHECK(false,false),
	CALL(true,false),
	BET(true,true),
	RAISE(true,true),
	ALL_IN(true,true);
	
	private final boolean involvesMoney;
    private final boolean aggressive;
    
    //Constructeur
    ActionType(boolean involveMoney, boolean agressive){
    	this.involvesMoney = involveMoney;
    	this.aggressive = agressive;
    }
    
    //getters
    public boolean isAgressive() {
    	return this.aggressive;
    }
    
    public boolean involveMoney() {
    	return this.involvesMoney;
    }
}
