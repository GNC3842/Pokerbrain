package pokerbrain.model.game;

/*
 * Décrire les types d'actions et 
 * */

public enum ActionType {
	//3 parametres: libellé affichable, involveMoney et agressive
	FOLD("Fold",     false,false),
	CHECK("Check",   false,false),
	CALL("Call",     true,false),
	BET("Bet",       true,true),
	RAISE("Raise",   true,true),
	ALL_IN("All-in", true,true);

	private final String  label;
	private final boolean involvesMoney;
    private final boolean aggressive;

    //Constructeur
    ActionType(String label, boolean involveMoney, boolean agressive){
    	this.label = label;
    	this.involvesMoney = involveMoney;
    	this.aggressive = agressive;
    }

    // Nom affiché dans l'interface : les actions restent en anglais partout,
    // `name()` est réservé à la sérialisation des mains (parser / writer).
    public String getLabel() {
    	return this.label;
    }

    //getters
    public boolean isAgressive() {
    	return this.aggressive;
    }
    
    public boolean involvesMoney() {
    	return this.involvesMoney;
    }
}
