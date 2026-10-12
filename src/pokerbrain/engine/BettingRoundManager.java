package pokerbrain.engine;

import pokerbrain.model.game.*;
import java.util.*;

/**
 * Gère un tour d'enchères complet avec montants en jetons entiers (int)
 */

public class BettingRoundManager {

    private final BettingRound round;
    private final PotManager   potManager;
    private final int          bigBlind;

    private List<Player> players;
    private int          currentIndex;
    private boolean      roundComplete;
    private int          currentBetLevel;
    private int          lastRaiseAmount; // montant de la dernière relance (pour min-raise)

    public BettingRoundManager(BettingRound round, PotManager potManager, int bigBlind) {
        this.round       = Objects.requireNonNull(round);
        this.potManager  = Objects.requireNonNull(potManager);
        this.bigBlind    = bigBlind;
        this.roundComplete = false;
    }

    //Au début
    public void start(List<Player> orderedPlayers, int currentBet) {
        Objects.requireNonNull(orderedPlayers, "Players ne peut pas être null");
        this.players         = new ArrayList<>(orderedPlayers);
        this.currentBetLevel = currentBet;
        this.lastRaiseAmount = currentBet; // en preflop = BB, en postflop = BB
        for (Player p : this.players) {
        	p.setHasActed(false);
        }
        this.currentIndex    = findFirstToAct();
        this.roundComplete   = isRoundComplete();
    }
    
    //Traitement dune action
    public Action processAction(ActionType type, int amount) {
        if (this.roundComplete) {
        	throw new IllegalStateException("Le round " + this.round + " est déjà terminé");
        }

        Player current = getCurrentPlayer();
        validateAction(current, type, amount);

        int actualAmount = 0;

        switch (type) {
            case FOLD:
                current.setStatus(PlayerStatus.FOLDED);
                break;

            case CHECK:
                break;

            case CALL: {
                int callAmount = this.potManager.getCallAmount(current);
                actualAmount   = Math.min(callAmount, current.getStack());
                this.potManager.addBet(current, actualAmount);
                break;
            }

            case BET: {
                if (amount < this.bigBlind)
                    throw new IllegalArgumentException(
                        "Bet minimum = big blind (" + this.bigBlind + "), reçu : " + amount);
                // `amount` est un TOTAL à atteindre sur la street, comme pour RAISE.
                // BET n'est proposé que quand personne n'a misé (currentBetLevel == 0),
                // donc la mise déjà engagée est nulle et le delta égale le total — la
                // soustraction est là pour que la convention reste vraie si ça change.
                actualAmount = Math.min(amount - current.getCurrentBet(), current.getStack());
                this.potManager.addBet(current, actualAmount);
                this.lastRaiseAmount = actualAmount;
                this.currentBetLevel = this.potManager.getHighestBet();
                reopenAction(this.currentIndex);
                break;
            }

            case RAISE: {
                // Min raise = currentBetLevel + lastRaiseAmount (règle standard Hold'em)
                int minRaiseTo = this.currentBetLevel + Math.max(this.lastRaiseAmount, this.bigBlind);
                if (amount < minRaiseTo)
                    throw new IllegalArgumentException(
                        "Raise minimum à " + minRaiseTo + ", reçu : " + amount);
                int callAmount  = this.potManager.getCallAmount(current);
                int raiseBy     = amount - this.currentBetLevel;
                int totalNeeded = callAmount + raiseBy;
                actualAmount    = Math.min(totalNeeded, current.getStack());
                this.potManager.addBet(current, actualAmount);
                this.lastRaiseAmount = raiseBy;
                this.currentBetLevel = this.potManager.getHighestBet();
                reopenAction(this.currentIndex);
                break;
            }

            case ALL_IN: {
                actualAmount = current.getStack();
                this.potManager.addBet(current, actualAmount);
                int newHighest = this.potManager.getHighestBet();
                if (newHighest > this.currentBetLevel) {
                    int raiseBy   = newHighest - this.currentBetLevel;
                    int fullRaise = Math.max(this.lastRaiseAmount, this.bigBlind);
                    // Le niveau monte toujours : les autres doivent égaler le tapis.
                    this.currentBetLevel = newHighest;
                    if (raiseBy >= fullRaise) {
                        this.lastRaiseAmount = raiseBy;
                        reopenAction(this.currentIndex);
                    }
                }
                break;
            }
        }

        current.setHasActed(true);
        Action action;
        if(type.involvesMoney()) {
        	action = new Action(current, type, actualAmount, this.round);
        } else {
        	action = new Action(current, type, this.round);
        }
        advance();
        return action;
    }

    //Navigation
    private void advance() {
        if (isRoundComplete()) { this.roundComplete = true; return; }
        int start = (this.currentIndex + 1) % this.players.size();
        for (int i = 0; i < this.players.size(); i++) {
            int idx = (start + i) % this.players.size();
            if (mustAct(this.players.get(idx))) {
            	this.currentIndex = idx; return;
            }
        }
        this.roundComplete = true;
    }

    /**
     * Un joueur doit encore parler s'il est actif et que, soit il n'a pas encore parlé
     * dans ce tour, soit la mise a monté depuis et il lui reste des jetons à égaler.
     *
     * Le second cas est indispensable : sans lui, le round se terminait dès qu'il ne
     * restait qu'un joueur actif, même s'il devait encore payer un tapis adverse. Le
     * joueur n'avait jamais la parole, et sa mise non égalée partait dans le pot de
     * l'adversaire à tapis sans qu'aucun abattage n'ait lieu.
     *
     * `advance` et `isRoundComplete` partagent ce prédicat : ils ne peuvent pas se
     * contredire (un round « incomplet » dont personne n'est désigné pour parler).
     */
    private boolean mustAct(Player p) {
        if (!p.isActive()) return false;
        //Il reste des jetons à égaler : il garde la parole, même seul contre des tapis
        if (this.potManager.getCallAmount(p) > 0) return true;
        if (countActive() <= 1) return false;
        return !p.hasActed();
    }

    private long countActive() {
        return this.players.stream().filter(Player::isActive).count();
    }

    private void reopenAction(int aggressorIdx) {
        for (int i = 0; i < this.players.size(); i++) {
            if (i != aggressorIdx && this.players.get(i).isActive()) {
                this.players.get(i).setHasActed(false);
            }
        }
    }

    //Validation 
    private void validateAction(Player player, ActionType type, int amount) {
        List<ActionType> valid = getValidActions(player);
        if (!valid.contains(type))
            throw new IllegalArgumentException(
                "Action invalide " + type + " pour " + player.getName()
                + ". Actions valides : " + valid);
    }

    public List<ActionType> getValidActions(Player player) {
        List<ActionType> valid = new ArrayList<>();
        if (!player.isActive()) return valid;

        int callAmount = this.potManager.getCallAmount(player);
        int stack      = player.getStack();
        int minRaiseTo = getMinRaiseTo();
        boolean canReRaise = !(callAmount > 0 && player.hasActed());

        valid.add(ActionType.FOLD);
        if (callAmount == 0) {
        	valid.add(ActionType.CHECK);
        }
        if (callAmount > 0 && stack > callAmount) {
        	valid.add(ActionType.CALL);
        }
        /*
         * BET ouvre l'enchère, RAISE la relance, et les deux prennent un TOTAL à
         * atteindre sur la street. Le partage se fait donc sur `currentBetLevel`, pas sur
         * `callAmount` : la BB preflop ne doit rien (callAmount == 0) alors qu'une mise de
         * 10 est déjà en jeu. En lui proposant BET, son « je relance à 20 » ajoutait 20 à
         * ses 10 et la mettait à 30. Elle reçoit maintenant RAISE, dont le minimum
         * (`getMinRaiseTo`) tient compte de ce qu'elle a déjà engagé.
         */
        if (this.currentBetLevel == 0 && stack >= this.bigBlind) {
        	valid.add(ActionType.BET);
        }
        if (this.currentBetLevel > 0 && canReRaise
        		&& stack >= callAmount + (minRaiseTo - this.currentBetLevel)) {
        	valid.add(ActionType.RAISE);
        }
        if (stack > 0 && (canReRaise || stack <= callAmount)) {
        	valid.add(ActionType.ALL_IN);
        }
        return valid;
    }

    //Fin de round : plus personne ne peut ni ne doit parler
    private boolean isRoundComplete() {
        for (Player p : this.players) {
            if (mustAct(p)) return false;
        }
        return true;
    }

    private int findFirstToAct() {
        for (int i = 0; i < this.players.size(); i++) {
            if (this.players.get(i).isActive()) {
            	return i;
            }
        }
        return 0;
    }
    
    //Getters
    public Player getCurrentPlayer() {
        if (this.roundComplete) {
        	throw new IllegalStateException("Le round est terminé");
        }
        return this.players.get(this.currentIndex);
    }

    public boolean isComplete(){ 
    	return this.roundComplete; 
    }
    public BettingRound getRound(){
    	return this.round;
    }
    public int getCurrentBetLevel(){
    	return this.currentBetLevel;
    }

    public List<Player> getActivePlayers() {
        List<Player> active = new ArrayList<>();
        for (Player p : this.players) {
            if (p.isActive() || p.isAllIn()) active.add(p);
        }
        return active;
    }
    
    /**
     * Montant minimum pour un raise légal (total à atteindre, pas le delta).
     * Formule : currentBetLevel + max(lastRaiseAmount, bigBlind).
     * Exposé pour que GameSession calcule le bon minimum pour le slider héro.
     */
    public int getMinRaiseTo() {
        return this.currentBetLevel + Math.max(this.lastRaiseAmount, this.bigBlind);
    }
}
