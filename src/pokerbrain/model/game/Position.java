package pokerbrain.model.game;
/**
 * Position d'un joueur à la table, du plus précoce au plus tardif.
 *
 * Le BTN (Button) est le dealer — isDealer() retourne true pour lui.
 * En heads-up (2 joueurs), le BTN joue aussi le rôle de SB (règle WSOP).
 *
 * Ordre d'action preflop  : UTG → UTG1 → UTG2 → MP → LJ → HJ → CO → BTN → SB → BB
 * Ordre d'action postflop : SB → BB → UTG → ... → BTN
 *
 * Positions actives par nombre de joueurs :
 *   2  : BTN, BB
 *   3  : BTN, SB, BB
 *   4  : CO, BTN, SB, BB
 *   5  : HJ, CO, BTN, SB, BB
 *   6  : UTG, HJ, CO, BTN, SB, BB
 *   7  : UTG, UTG1, HJ, CO, BTN, SB, BB
 *   8  : UTG, UTG1, MP, HJ, CO, BTN, SB, BB
 *   9  : UTG, UTG1, MP, LJ, HJ, CO, BTN, SB, BB
 *   10 : UTG, UTG1, UTG2, MP, LJ, HJ, CO, BTN, SB, BB
 */
/*Noms des Positions
 * SB = Small Blinde
 * BB = Big blinde
 * UTG = Under the gun
 * MP/LJ = Middle position
 * HJ = Hijack
 * CO = cut off
 * BTN = Bouton = Dealer
 * */
public enum Position {
	//Définition avec code - nom complet - dealer? - sb? - bg?
	UTG("UTG",  "Under The Gun",   false, false, false),
    UTG1("UTG1", "Under The Gun +1",false, false, false),
    UTG2("UTG2", "Under The Gun +2",false, false, false),
    MP("MP",   "Middle Position", false, false, false),
    LJ("LJ",   "Lojack",          false, false, false),
    HJ("HJ",   "Hijack",          false, false, false),
    CO("CO",   "Cut-Off",         false, false, false),
    BTN("BTN",  "Button / Dealer", true,  false, false),
    SB("SB",   "Small Blind",     false, true,  false),
    BB("BB",   "Big Blind",       false, false, true);
	
	private final String code;
	private final String fullName;
	private final boolean dealer;
	private final boolean smallBlind;
	private final boolean bigBlind;
	
	Position(String code, String fullName, boolean dealer, boolean smallBlind, boolean bigBlind){
		this.code = code;
		this.fullName = fullName;
		this.dealer = dealer;
		this.smallBlind = smallBlind;
		this.bigBlind = bigBlind;
	}
	
	//recuperer code
	public String getCode() {
		return this.code;
	}
	
	
	//Savoir si position importante
	public boolean isDealer() {
		return this.dealer;
	}
	
	public boolean isSmallBlind() {
		return this.smallBlind;
	}
	
	public boolean isBigBlind() {
		return this.bigBlind;
	}
	
	
    /**
     * Rang dans l'ordre de parole de la street, 0 = parle en premier.
     *
     * L'ordre preflop est l'ordre de déclaration de l'énumération (UTG → … → BTN → SB → BB) ;
     * l'ordre postflop le décale de deux crans, ce qui amène SB puis BB devant. Le décalage
     * donne aussi le bon ordre en heads-up, où seuls BTN et BB existent : BTN parle en
     * premier au preflop (il poste la SB) et en dernier ensuite.
     */
    public int speakingRank(boolean preflop) {
        return preflop ? ordinal() : (ordinal() + 2) % values().length;
    }
	
    //toString
    @Override
    public String toString() { return this.code; }
}
