package pokerbrain.model.game;

import java.util.*;

/* Assigne les positions aux joueurs selon leur nombre (2 a 10)
 * Le 1er joeur est dealer
 * 
 * SI 2 joeurus uniquement, le dealer agit en 1er preflop et en dernier postflop 
 * (voir WSOP)
 * Pass de SB distincte, le dealer donne SB
 */

public class PositionManager {
	private static final Map<Integer, List<Position>> POSITIONS_BY_COUNT;
	private final int playerCount;
    private final List<Position> activePositions;
    
    // AU démaragge
    static {
        POSITIONS_BY_COUNT = new HashMap<>();
        POSITIONS_BY_COUNT.put(2,  Arrays.asList(Position.BTN, Position.BB));
        POSITIONS_BY_COUNT.put(3,  Arrays.asList(
        		Position.BTN, Position.SB, Position.BB));
        POSITIONS_BY_COUNT.put(4,  Arrays.asList(
            Position.CO, Position.BTN, Position.SB, Position.BB));
        POSITIONS_BY_COUNT.put(5,  Arrays.asList(
            Position.HJ, Position.CO, Position.BTN, Position.SB, Position.BB));
        POSITIONS_BY_COUNT.put(6,  Arrays.asList(
            Position.UTG, Position.HJ, Position.CO, Position.BTN, Position.SB, Position.BB));
        POSITIONS_BY_COUNT.put(7,  Arrays.asList(
            Position.UTG, Position.UTG1, Position.HJ, Position.CO, Position.BTN, Position.SB, Position.BB));
        POSITIONS_BY_COUNT.put(8,  Arrays.asList(
            Position.UTG, Position.UTG1, Position.MP, Position.HJ, Position.CO, Position.BTN, Position.SB, Position.BB));
        POSITIONS_BY_COUNT.put(9,  Arrays.asList(
            Position.UTG, Position.UTG1, Position.MP, Position.LJ, Position.HJ, Position.CO, Position.BTN, Position.SB, Position.BB));
        POSITIONS_BY_COUNT.put(10, Arrays.asList(
            Position.UTG, Position.UTG1, Position.UTG2, Position.MP, Position.LJ, Position.HJ, Position.CO, Position.BTN, Position.SB, Position.BB));
    }
	
	//Constructeur
    public PositionManger(int playerCount) {
    	if (playerCount < 2 || playerCount > 10) {
    		throw new IllegalArgumentException("Nombre de joueurs invalide : " + playerCount + " (attendu 2-10)");
    	}
    	this.playerCount = playerCount;
    	this.activePositions = POSITIONS_BY_COUNT.get(playerCount);
    }
    
    //Methode de gestters utilitaires
    public int getPlayerCount() {
    	return this.playerCount;
    }
    
    public boolean isHeadsUp() {
    	return this.playerCount == 2;
    }
    
    public boolean isFullRing() {
    	return this.playerCount == 10;
    }
    
    public boolean isShortHanded() {
    	return this.playerCount >= 4 && this.playerCount <=6;
    }
    
    //savoir qui est le dealer
    public Optionnal<Player> getDealer(List<Player> players){
    	return getPlayerAtPosition(players, Position.BTN);
    }
    
    // verifie si une position est active pour ce nombre de joueurs.
    public boolean isPositionActive(Position position) {
        return this.activePositions.contains(position);
    }
    
    //Assigner les positions à une liste de joeurus
    public void assignPositions(List<Player> players) {
        if (players == null) throw new IllegalArgumentException("Players ne peut pas être null");
        if (players.size() != this.playerCount) {
            throw new IllegalArgumentException(
                "Nombre de joueurs incorrect : attendu=" + this.playerCount
                + ", reçu=" + players.size());
        }
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setPosition(this.activePositions.get(i));
        }
    }
    
    //Retourne le joeuru occupant une position donnée
    public Optional<Player> getPlayerAtPosition(List<Player> players, Position position) {
        if (!this.activePositions.contains(position)) {
        	return Optional.empty();
        }
        return players.stream()
                .filter(p -> p.getPosition() == position)
                .findFirst();
    }
    
    //Retourne la liste des positions actives:
    public List<Position> getActivePositions() {
        return Collections.unmodifiableList(this.activePositions);
    }
    
    //Renvoie li liste ordonnée des joeurus
    private List<Player> getOrderedPlayers(List<Player> players, List<Position> order) {
        List<Player> ordered = new ArrayList<>();
        for (Position pos : order) {
            for (Player p : players) {
                if (p.getPosition() == pos) {
                    ordered.add(p);
                    break;
                }
            }
        }
        return ordered;
    }
    
    //ordre d'actions en preflop: UTG -> .. 6> BTN -> SB -> BG
    public List<Player> getPreflopOrder(List<Player> players) {
        return getOrderedPlayers(players, this.activePositions);
    }
    
    //Ordre d'actions en postflop: SB->BG->UTG->..->BTN
    public List<Player> getPostflopOrder(List<Player> players){
    	List<Position> postflop = new ArrayList<>();
    	if (this.activePositions.contains(Position.SB)) postflop.add(Position.SB);
        postflop.add(Position.BB);
        for (Position pos : this.activePositions) {
            if (pos != Position.SB && pos != Position.BB && pos != Position.BTN) {
                postflop.add(pos);
            }
        }
        postflop.add(Position.BTN);
        return getOrderedPlayers(players, postflop);
    }
    
    //toString classsique
    @Override
    public String toString() {
        return "PositionManager[" + this.playerCount + " joueurs : " + this.activePositions + "]";
    }
    
}
