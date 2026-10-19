package pokerbrain.engine;

import pokerbrain.model.core.*;
import pokerbrain.model.game.*;
import java.util.*;

/**
 * Orchestrateur principal d'une partie de Texas Hold'em (2-10 joueurs).
 *
 * Cycle d'une main :
 *   startNewHand() -> postBlinds() -> [preflop] -> dealFlop() -> [flop]
 *   -> dealTurn() -> [turn] -> dealRiver() -> [river] -> showdown()
 *
 * Chaque round d'enchères suit la boucle :
 *   while (!game.isRoundComplete()) {
 *       List<ActionType> valid = game.getValidActions();
 *       game.applyAction(chosen, amount);
 *   }
 */
public class PokerGame {

	//Config
	private final int smallBlind;
	private final int bigBlind;

	//Composants 
	private final List<Player>      players;
	private PositionManager         positionManager;
	private final DealerEngine      dealerEngine;
	private final PotManager        potManager;

	//Etat de la main en cours
	private BettingRound           currentRound;
	private BettingRoundManager    roundManager;
	private List<Action>           handHistory;
	private boolean                handOver;
	// Indice du porteur du bouton dans `players`, la liste stable des sièges : jamais dans
	// une liste filtrée, dont la taille change à chaque élimination.
	private int                    buttonSeat;
	private PositionManager        currentPositionManager;

	//Constructeur
	public PokerGame(List<Player> players, int smallBlind, int bigBlind) {
		if(players == null || players.isEmpty()) {
			throw new IllegalArgumentException("Players ne peut pas être null");
		}
		if (players.size() < 2 || players.size() > 10)
			throw new IllegalArgumentException(
				"Nombre de joueurs invalide : " + players.size() + " (attendu 2-10)");
		if (smallBlind <= 0 || bigBlind <= 0 || bigBlind < smallBlind)
			throw new IllegalArgumentException("Blindes invalides : SB=" + smallBlind + " BB=" + bigBlind);

		this.players         = new ArrayList<>(players);
		this.smallBlind      = smallBlind;
		this.bigBlind        = bigBlind;
		this.positionManager = new PositionManager(players.size());
		this.dealerEngine    = new DealerEngine(new Deck());
		this.potManager      = new PotManager();
		this.buttonSeat      = 0;
		this.handOver        = true;
	}

	/**
	 * Démarre une nouvelle main.
	 * Assigne les positions, prépare le deck, distribue les cartes.	 *
	 */
	public void startNewHand() {
		if (!this.handOver)
			throw new IllegalStateException("La main précédente n'est pas terminée");

		// Réinitialisation
		this.potManager.reset();
		this.dealerEngine.reset();
		this.handHistory = new ArrayList<>();
		this.handOver    = false;

		// Joueurs encore en jeu cad stack > 0
		List<Player> activePlayers = new ArrayList<>();
		for (Player p : this.players) {
			p.resetForNewHand();
			if (p.getStack() > 0) {
				activePlayers.add(p);
			} else {
				p.setStatus(PlayerStatus.OUT);
			}
		}

		if (activePlayers.size() < 2)
			throw new IllegalStateException("Pas assez de joueurs avec des jetons");

		// Recréer le PositionManager selon le nombre de joueurs actifs (peut changer si joueur éliminé)
		if (activePlayers.size() != this.positionManager.getPlayerCount()) {
			this.positionManager = new PositionManager(activePlayers.size());
		}

		/*
		 * Rotation du bouton : siège suivant encore pourvu en jetons, dans l'ordre STABLE
		 * de `players`. L'ancien calcul avançait un index modulo `activePlayers.size()`,
		 * une liste qui rétrécit à chaque élimination : le bouton pouvait sauter un siège,
		 * reculer, ou rester deux mains de suite sur le même joueur.
		 */
		for (int step = 1; step <= this.players.size(); step++) {
			int candidat = (this.buttonSeat + step) % this.players.size();
			if (activePlayers.contains(this.players.get(candidat))) {
				this.buttonSeat = candidat;
				break;
			}
		}

		// Joueurs encore en jeu, en partant du bouton
		List<Player> depuisBouton = new ArrayList<>();
		for (int i = 0; i < this.players.size(); i++) {
			Player p = this.players.get((this.buttonSeat + i) % this.players.size());
			if (activePlayers.contains(p)) {
				depuisBouton.add(p);
			}
		}

		/*
		 * `assignPositions` distribue les positions dans l'ordre de parole preflop
		 * (ex. à 4 joueurs : CO, BTN, SB, BB), le BTN n'est donc pas en tête de liste.
		 * On fait tourner la liste pour que le porteur du bouton tombe pile sur le siège
		 * BTN de cette table — sans quoi un changement de nombre de joueurs décale le
		 * bouton d'autant que le rang du BTN a bougé.
		 */
		int nbActifs = depuisBouton.size();
		int rangBouton = this.positionManager.getActivePositions().indexOf(Position.BTN);
		List<Player> fromDealer = new ArrayList<>(Collections.nCopies(nbActifs, (Player) null));
		for (int i = 0; i < nbActifs; i++) {
			fromDealer.set((i + rangBouton) % nbActifs, depuisBouton.get(i));
		}

		// Assigner les positions
		this.positionManager.assignPositions(fromDealer);
		for (int i = 0; i < fromDealer.size(); i++) {
			fromDealer.get(i).setSeat(i);
		}

		// Distribuer les cartes (ordre postflop : SB first)
		List<Player> postflopOrder = this.positionManager.getPostflopOrder(fromDealer);
		this.dealerEngine.dealHoleCards(postflopOrder);

		// Poster les blindes
		postBlinds(fromDealer);

		// Démarrer le round preflop (UTG first)
		List<Player> preflopOrder = this.positionManager.getPreflopOrder(fromDealer);
		startBettingRound(BettingRound.PREFLOP, preflopOrder, this.bigBlind);
	}

	// Poster SB et BB. En heads-up, BTN poste la SB
	private void postBlinds(List<Player> fromDealer) {
		if (this.positionManager.isHeadsUp()) {
			// HU : BTN = SB, BB = BB
			Player btn = fromDealer.get(0);
			Player bb  = fromDealer.get(1);
			this.potManager.addBlind(btn, Math.min(this.smallBlind, btn.getStack()));
			this.potManager.addBlind(bb,  Math.min(this.bigBlind,   bb.getStack()));
		} else {
			// Table normale : SB puis BB dans l'ordre postflop
			Optional<Player> sbPlayer = this.positionManager
					.getPlayerAtPosition(fromDealer, Position.SB);
			Optional<Player> bbPlayer = this.positionManager
					.getPlayerAtPosition(fromDealer, Position.BB);

			sbPlayer.ifPresent(p ->
				this.potManager.addBlind(p, Math.min(this.smallBlind, p.getStack())));
			bbPlayer.ifPresent(p ->
				this.potManager.addBlind(p, Math.min(this.bigBlind, p.getStack())));
		}
	}

	// Progression du board
	
	/**
	 * Distribue le flop et démarre le round de mises correspondant
	 * Doit être appelé après la fin du round preflop
	 */
	public List<Card> dealFlop() {
		assertRoundComplete();
		collectAndNextRound();
		List<Card> flop = this.dealerEngine.dealFlop();
		List<Player> postflop = buildPostflopOrder();
		startBettingRound(BettingRound.FLOP, postflop, 0);
		return flop;
	}

	// Distribue la turn et démarre le round correspondant
	public Card dealTurn() {
		assertRoundComplete();
		collectAndNextRound();
		Card turn = this.dealerEngine.dealTurn();
		startBettingRound(BettingRound.TURN, buildPostflopOrder(), 0);
		return turn;
	}

	//Distribue la river et démarre le round correspondant
	public Card dealRiver() {
		assertRoundComplete();
		collectAndNextRound();
		Card river = this.dealerEngine.dealRiver();
		startBettingRound(BettingRound.RIVER, buildPostflopOrder(), 0);
		return river;
	}

	//Actions 

	//Applique une action pour le joueur courant
	public Action applyAction(ActionType type, int amount) {
		if (this.roundManager == null || this.roundManager.isComplete())
			throw new IllegalStateException("Aucun round en cours");

		Action action = this.roundManager.processAction(type, amount);
		this.handHistory.add(action);

		/*
		 * Fin anticipée seulement quand plus personne ne PEUT agir :
		 *   - il ne reste qu'un joueur non couché, ou
		 *   - tous les joueurs non couchés sont à tapis.
		 *
		 * L'ancienne condition `countActivePlayers() <= 1` réglait aussi la main quand il
		 * restait un joueur actif devant un tapis adverse qu'il n'avait pas encore égalé :
		 * ni l'un ni l'autre des deux cas ci-dessous ne s'appliquait, `handOver` restait
		 * faux, et la mise non égalée avait déjà filé dans le pot de l'adversaire.
		 */
		if (getInGamePlayers().size() <= 1 || countActivePlayers() == 0) {
			autoAwardPot();
		}
		return action;
	}

	//Raccourci pour FOLD, CHECK, CALL, ALL_IN (pas de montant)
	public Action applyAction(ActionType type) {
		return applyAction(type, 0);
	}

	/**
	 * Showdown
	 * Résout le showdown après la river.
	 * Distribue les pots aux gagnants selon FastHandEvaluator.
	 */
	public Map<Player, Integer> showdown() {
		assertRoundComplete();
		List<Player> inGame = getInGamePlayers();
		this.potManager.collectBets(inGame);
		List<Player> postflopOrder = buildPostflopOrder();
		Map<Player, Integer> winnings = this.potManager.awardPots(
			this.dealerEngine.getBoard(), postflopOrder);
		this.handOver = true;
		return winnings;
	}

	// LISTE DES GETTERS
	
	//Actions légales pour le joueur courant
	public List<ActionType> getValidActions() {
		if (this.roundManager == null || this.roundManager.isComplete())
			return Collections.emptyList();
		return this.roundManager.getValidActions(this.roundManager.getCurrentPlayer());
	}

	public Player getCurrentPlayer() {
		return this.roundManager.getCurrentPlayer();
	}
	
	public int getCurrentBetLevel() {
		if (this.roundManager == null) return 0;
		return this.roundManager.getCurrentBetLevel();
	}
	
	public boolean isRoundComplete() {
		return this.roundManager == null || this.roundManager.isComplete();
	}

	public boolean isHandOver(){ 
		return this.handOver;
	}
	
	public BettingRound getCurrentRound(){
		return this.currentRound;
	}
	
	public List<Card> getBoard(){
		return this.dealerEngine.getBoard();
	}
	
	public int getTotalPot() {
		return this.potManager.getTotalPot();
	}

	/**
	 * Gains reversés aux joueurs pendant la main en cours, abattage ou fin anticipée.
	 * Valable jusqu'au prochain startNewHand(). C'est ce que l'enregistreur doit écrire
	 * dans la main : showdown() ne couvre que l'abattage, pas les mains gagnées par fold.
	 */
	public Map<Player, Integer> getWinnings() {
		return this.potManager.getPayouts();
	}
	
	public List<Action> getHandHistory(){
		return Collections.unmodifiableList(this.handHistory); 
	}
	
	public List<Player> getPlayers(){
		return Collections.unmodifiableList(this.players);
	}

	public List<SidePot> getSidePots(){
		return this.potManager.getFinalisedPots(); 
	}
	
	public int getSmallBlind() {
		return this.smallBlind;
	}

	public int getBigBlind() {
		return this.bigBlind;
	}
	
	/**
	 * Montant minimum légal pour un raise (total à atteindre, pas le delta).
	 * Délègue au BettingRoundManager qui connaît lastRaiseAmount.
	 */
	public int getMinRaiseTo() {
		if (this.roundManager == null) return this.bigBlind * 2;
		return this.roundManager.getMinRaiseTo();
	}

	public int getCallAmount() {
		if (this.roundManager == null || this.roundManager.isComplete()) return 0;
		return this.potManager.getCallAmount(this.roundManager.getCurrentPlayer());
	}

	//Helpers privés
	private void startBettingRound(BettingRound round, List<Player> ordered, int currentBet) {
		this.currentRound = round;
		this.roundManager = new BettingRoundManager(round, this.potManager, this.bigBlind);
		this.roundManager.start(ordered, currentBet);
	}

	private void collectAndNextRound() {
		this.potManager.collectBets(getInGamePlayers());
		for (Player p : this.players) {
			p.setHasActed(false);
		}
	}

	private void assertRoundComplete() {
		if (!isRoundComplete()) {
			throw new IllegalStateException("Le round " + this.currentRound + " n'est pas terminé");
		}
	}

	/** Construit l'ordre postflop (SB → BB → ... → BTN) avec joueurs actifs. */
	private List<Player> buildPostflopOrder() {
		List<Player> inGame = getInGamePlayers();
		// Réutiliser PositionManager avec l'ordre actuel des joueurs
		List<Player> ordered = new ArrayList<>();
		// Postflop order : SB first
		for (Position pos : Arrays.asList(
				Position.SB, Position.BB,
				Position.UTG, Position.UTG1, Position.UTG2,
				Position.MP, Position.LJ, Position.HJ,
				Position.CO, Position.BTN)) {
			for (Player p : inGame) {
				if (p.getPosition() == pos){
					ordered.add(p); break;
				}
			}
		}
		// En heads-up SB n'existe pas → fallback
		if (ordered.isEmpty()) {
			ordered.addAll(inGame);
		}
		return ordered;
	}

	private List<Player> getInGamePlayers() {
		List<Player> result = new ArrayList<>();
		for (Player p : this.players) {
			if (!p.isOut() && !p.isFolded()) {
				result.add(p);
			}
		}
		return result;
	}

	private long countActivePlayers() {
		return this.players.stream().filter(Player::isActive).count();
	}

	/**
	 * Gère la fin anticipée d'une main :
	 *
	 * Cas 1 — un seul joueur non foldé (actif ou all-in) : il remporte tout
	 * Cas 2 — tous les joueurs non foldés sont all-in : le board est complété
	 *          automatiquement et un showdown est résolu sans nouvelles mises
	 */
	private void autoAwardPot() {
		List<Player> inGame = getInGamePlayers(); // non foldés, non out
		// Collecter les mises du round courant
		this.potManager.collectBets(inGame);

		if (inGame.size() == 1) {
			// Cas 1 : un seul joueur restant → il remporte tous les pots.
			// Passer par payDirect pour que le gain soit enregistré dans la main :
			// un addToStack muet laissait Hand.winnings vide sur toutes les mains
			// gagnées par fold, soit la majorité.
			Player winner = inGame.get(0);
			for (SidePot sp : this.potManager.getFinalisedPots()) {
				this.potManager.payDirect(winner, sp.getAmount());
			}
			this.handOver = true;

		} else if (countActivePlayers() == 0 && inGame.size() > 1) {
			// Cas 2 : tous restants sont all-in → compléter le board et showdown
			while (!this.dealerEngine.isRiverDealt()) {
				if (!this.dealerEngine.isFlopDealt()) {
					this.dealerEngine.dealFlop();
				} else if (!this.dealerEngine.isTurnDealt()) {
					this.dealerEngine.dealTurn();
				} else {
					this.dealerEngine.dealRiver();
				}
			}
			// Résoudre le showdown avec le board complet
			this.potManager.awardPots(this.dealerEngine.getBoard(), inGame);
			this.handOver = true;
		}
	}
}
