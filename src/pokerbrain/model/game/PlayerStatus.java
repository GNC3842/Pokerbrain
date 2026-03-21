package pokerbrain.model.game;

public enum PlayerStatus {
	
	ACTIVE,//Peut encore agir
	FOLDED,//abandon de la main
	ALL_IN,//a all in
	OUT; //plus de jetons :'(
}
