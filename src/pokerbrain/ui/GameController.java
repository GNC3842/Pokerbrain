package pokerbrain.ui;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import pokerbrain.bot.Bot;
import pokerbrain.engine.PokerGame;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.AppContext;
import pokerbrain.simulator.MonteCarloEngine;
import pokerbrain.model.history.HandRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Répartition des sièges autour de la table (héro toujours en bas) :
 *
 *   2j :        [top: 1]
 *   3j :        [top: 2]
 *   4j : [L:1]  [top: 2]
 *   5j : [L:1]  [top: 2]  [R:1]
 *   6j : [L:1]  [top: 3]  [R:1]
 *   7j : [L:1]  [top: 3]  [R:1]  [bot:1]
 *   8j : [L:1]  [top: 3]  [R:1]  [bot:2]
 */
public class GameController {

    @FXML private Label  roundLabel;
    @FXML private Label  potLabel;

    @FXML private HBox   topRow;
    @FXML private VBox   leftSlot;
    @FXML private HBox   communityRow;
    @FXML private VBox   potContainer;
    @FXML private VBox   rightSlot;
    @FXML private HBox   bottomRow;
    @FXML private VBox   heroSlot;
    @FXML private VBox   timelineContainer;

    @FXML private Button foldButton;
    @FXML private Button checkCallButton;
    @FXML private Button raiseButton;
    @FXML private Slider raiseSlider;
    @FXML private Label  raiseAmountLabel;


    private PlayerView     hero;
    private PotView        potView;
    private CardView[]     communityCards;
    private ActionTimeline timeline;

    // État de la partie
    private PokerGame          game;
    private List<Bot>          bots;
    private Map<String, Bot>   botsByPlayerId = new HashMap<>();
    private Map<String, PlayerView> playerViews = new HashMap<>();
    private int heroPlayerIndex;
    private int smallBlind;
    private int bigBlind;
    private Label equityLabel;
    private HandRecord currentRecord;

    @FXML
    public void initialize() {
        communityCards = new CardView[5];
        for (int i = 0; i < 5; i++) {
            communityCards[i] = new CardView();
            communityCards[i].showEmpty();
            communityRow.getChildren().add(communityCards[i]);
        }

        potView = new PotView();
        potContainer.getChildren().add(potView);

        equityLabel = new Label();
        equityLabel.setStyle("-fx-text-fill: #7fcf9f; -fx-font-size: 14px; -fx-font-weight: bold;");
        potContainer.getChildren().add(equityLabel);

        timeline = new ActionTimeline();
        timelineContainer.getChildren().add(timeline);

        raiseSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int v = (int) Math.round(newVal.doubleValue());
            raiseAmountLabel.setText(v <= 0 ? "0" : v + " j.");
        });

        disableActions();
    }

    // ── Point d'entrée depuis MenuController ─────────────────────────────────

    public void initGame(List<Player> players, int heroIndex, List<Bot> gameBots,
                         int sb, int bb) {
        this.smallBlind      = sb;
        this.bigBlind        = bb;
        this.heroPlayerIndex = heroIndex;
        this.bots            = new ArrayList<>(gameBots);
        for (Bot b : gameBots) botsByPlayerId.put(b.getPlayerId(), b);

        this.game = new PokerGame(players, sb, bb);
        loadPlayers(players, heroIndex);
        startNewHand();
    }

    // ── Chargement initial de la table ────────────────────────────────────────

    public void loadPlayers(List<Player> players, int heroIndex) {
        if (players.size() < 2 || players.size() > 8)
            throw new IllegalArgumentException("Le nombre de joueurs doit être entre 2 et 8.");

        clearAllSlots();
        playerViews.clear();

        hero = new PlayerView();
        hero.setPlayer(players.get(heroIndex));
        heroSlot.getChildren().add(hero);
        playerViews.put(players.get(heroIndex).getId(), hero);

        List<Player> opponents = new ArrayList<>(players);
        opponents.remove(heroIndex);
        distributeOpponents(opponents);
    }

    private void distributeOpponents(List<Player> opp) {
        List<PlayerView> top    = new ArrayList<>();
        List<PlayerView> left   = new ArrayList<>();
        List<PlayerView> right  = new ArrayList<>();
        List<PlayerView> bottom = new ArrayList<>();

        switch (opp.size()) {
            case 1 -> top.add(makeView(opp.get(0)));
            case 2 -> { top.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); }
            case 3 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); }
            case 4 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); right.add(makeView(opp.get(3))); }
            case 5 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); top.add(makeView(opp.get(3))); right.add(makeView(opp.get(4))); }
            case 6 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); top.add(makeView(opp.get(3))); right.add(makeView(opp.get(4))); bottom.add(makeView(opp.get(5))); }
            case 7 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); top.add(makeView(opp.get(3))); right.add(makeView(opp.get(4))); bottom.add(makeView(opp.get(5))); bottom.add(makeView(opp.get(6))); }
        }

        top.forEach(v    -> topRow.getChildren().add(v));
        left.forEach(v   -> leftSlot.getChildren().add(v));
        right.forEach(v  -> rightSlot.getChildren().add(v));
        bottom.forEach(v -> bottomRow.getChildren().add(v));
    }

    private PlayerView makeView(Player player) {
        PlayerView view = new PlayerView();
        view.setPlayer(player);
        playerViews.put(player.getId(), view);
        return view;
    }

    private void clearAllSlots() {
        topRow.getChildren().clear();
        leftSlot.getChildren().clear();
        rightSlot.getChildren().clear();
        bottomRow.getChildren().clear();
        heroSlot.getChildren().clear();
    }

    // ── Boucle de jeu ─────────────────────────────────────────────────────────

    private void startNewHand() {
        try {
            clearHistory();
            for (CardView cv : communityCards) cv.showEmpty();
            game.startNewHand();
            currentRecord = new HandRecord(game, "hero");
            refreshAllPlayerViews();
            updatePotLabel();
            setRound(game.getCurrentRound().name());
            processTurn();
        } catch (Exception e) {
            System.err.println("Impossible de démarrer une nouvelle main : " + e.getMessage());
            MainController.goToMenu();
        }
    }

    // Décide quoi faire selon l'état du jeu : avancer le round, jouer le bot, ou attendre le héro.
    private void processTurn() {
        if (game.isHandOver()) {
            handleHandOver();
            return;
        }

        updatePotLabel();
        setRound(game.getCurrentRound().name());

        if (game.isRoundComplete()) {
            PauseTransition pause = new PauseTransition(Duration.millis(400));
            pause.setOnFinished(e -> advanceRound());
            pause.play();
            return;
        }

        Player current = game.getCurrentPlayer();
        highlightCurrentPlayer(current);

        if (botsByPlayerId.containsKey(current.getId())) {
            disableActions();
            PauseTransition pause = new PauseTransition(Duration.millis(600));
            pause.setOnFinished(e -> runBotTurn(botsByPlayerId.get(current.getId()), current));
            pause.play();
        } else {
            enableActions(game.getValidActions());
            updateHeroEquity();
        }
    }

    private void advanceRound() {
        try {
            BettingRound finished = game.getCurrentRound();
            if (finished == BettingRound.RIVER) {
                handleShowdown();
                return;
            }
            switch (finished) {
                case PREFLOP -> game.dealFlop();
                case FLOP    -> game.dealTurn();
                case TURN    -> game.dealRiver();
                default      -> { return; }
            }
            List<Card> board = game.getBoard();
            revealCommunityCards(board.toArray(new Card[0]));
            setRound(game.getCurrentRound().name());
            updatePotLabel();
            processTurn();
        } catch (Exception e) {
            System.err.println("Erreur avancement round : " + e.getMessage());
        }
    }

    /*private void handleShowdown() {
    	Map<Player, Integer> winnings = game.showdown();
        try {
            // Révéler les cartes de tous les joueurs encore en jeu
            String heroId = game.getPlayers().get(heroPlayerIndex).getId();
            for (Player p : game.getPlayers()) {
                if (!p.isFolded() && !p.isOut()) {
                    PlayerView view = playerViews.get(p.getId());
                    if (view != null) view.setPlayer(p); // affiche les vraies cartes
                }
            }
            // Afficher les gains dans l'historique
            for (Map.Entry<Player, Integer> e : winnings.entrySet()) {
                if (e.getValue() > 0)
                    addActionToHistory("SHOWDOWN", e.getKey().getName(),
                        "Gagne " + e.getValue() + " j.");
            }
            refreshAllPlayerViews(true);
        } catch (Exception e) {
            System.err.println("Erreur showdown : " + e.getMessage());
        }
        // Popup résultat pour showdown
        Player heroPlayer = game.getPlayers().get(heroPlayerIndex);
        int heroWin = winnings.getOrDefault(heroPlayer, 0);
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            heroWin > 0 
                ? javafx.scene.control.Alert.AlertType.INFORMATION
                : javafx.scene.control.Alert.AlertType.WARNING
        );
        alert.setTitle("Fin de la main");
        alert.setHeaderText(heroWin > 0 ? "Vous avez gagné !" : "Vous avez perdu");
        alert.setContentText(heroWin > 0 
            ? "Gains : +" + heroWin + " jetons"
            : "Vous n'avez rien gagné cette main.");
        alert.getDialogPane().setStyle("-fx-background-color: #1a4a2e;");
        alert.showAndWait();
        handleHandOver();
    }*/
    private void handleShowdown() {
        Map<Player, Integer> winnings = new HashMap<>();
        try {
            winnings = game.showdown();
            if (currentRecord != null) {
                SessionData.addHand(currentRecord.buildHand(winnings));
                currentRecord = null;
            }
            String heroId = game.getPlayers().get(heroPlayerIndex).getId();
            for (Player p : game.getPlayers()) {
                if (!p.isFolded() && !p.isOut()) {
                    PlayerView view = playerViews.get(p.getId());
                    if (view != null) view.setPlayer(p);
                }
            }
            for (Map.Entry<Player, Integer> e : winnings.entrySet()) {
                if (e.getValue() > 0)
                    addActionToHistory("SHOWDOWN", e.getKey().getName(),
                        "Gagne " + e.getValue() + " j.");
            }
            refreshAllPlayerViews(true);
        } catch (Exception e) {
            System.err.println("Erreur showdown : " + e.getMessage());
        }

        // Popup résultat (déféré pour éviter le conflit avec l'animation)
        Player heroPlayer = game.getPlayers().get(heroPlayerIndex);
        int heroWin = winnings.getOrDefault(heroPlayer, 0);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                heroWin > 0
                    ? javafx.scene.control.Alert.AlertType.INFORMATION
                    : javafx.scene.control.Alert.AlertType.WARNING
            );
            alert.setTitle("Fin de la main");
            alert.setHeaderText(heroWin > 0 ? "Vous avez gagné !" : "Vous avez perdu");
            alert.setContentText(heroWin > 0
                ? "Gains : +" + heroWin + " jetons"
                : "Vous n'avez rien gagné cette main.");
            alert.showAndWait();
            handleHandOver();
        });
        return; // ← IMPORTANT : on sort de handleShowdown() sans appeler handleHandOver() ici
    }

    private void handleHandOver() {
    	if (currentRecord!= null){
            SessionData.addHand(currentRecord.buildHand());
            currentRecord = null;
        }
        // Popup si le héro gagne par fold des adversaires
    	Player heroPlayer = game.getPlayers().get(heroPlayerIndex);
    	if (!heroPlayer.isFolded() && !heroPlayer.isOut()) {
    	    long activePlayers = game.getPlayers().stream()
    	        .filter(p -> !p.isFolded() && !p.isOut()).count();
    	    if (activePlayers == 1) {
    	        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
    	            javafx.scene.control.Alert.AlertType.INFORMATION);
    	        alert.setTitle("Fin de la main");
    	        alert.setHeaderText("Vous remportez le pot !");
    	        alert.setContentText("Tous les adversaires se sont couchés.");
    	        alert.show(); // ← show() sans Wait, pas de blocage
    	    }
    	}
        disableActions();
        long playersWithChips = game.getPlayers().stream()
            .filter(p -> p.getStack() > 0).count();

        int delaySeconds = playersWithChips < 2 ? 3 : 2;
        PauseTransition pause = new PauseTransition(Duration.seconds(delaySeconds));
        pause.setOnFinished(e -> {
            if (playersWithChips < 2) MainController.goToMenu();
            else                      startNewHand();
        });
        pause.play();
    }

    // ── Bots ──────────────────────────────────────────────────────────────────

    private void runBotTurn(Bot bot, Player current) {
        BettingRound round       = game.getCurrentRound();
        List<Card>   board       = new ArrayList<>(game.getBoard());
        int          totalPot    = game.getTotalPot();
        int          betLevel    = game.getCurrentBetLevel();
        List<ActionType> valid   = new ArrayList<>(game.getValidActions());

        Task<Action> task = new Task<>() {
            @Override
            protected Action call() {
                int activeOpp = (int) game.getPlayers().stream()
                    .filter(p -> !p.isFolded() && !p.isOut() && !p.getId().equals(current.getId()))
                    .count();
                return bot.decide(round, current, board, totalPot, betLevel, bigBlind, valid, activeOpp);
            }
        };

        task.setOnSucceeded(e -> applyBotAction(task.getValue()));
        task.setOnFailed(e -> {
            System.err.println("Bot error: " + task.getException().getMessage());
            applyBotAction(new Action(current, ActionType.FOLD, round));
        });

        Thread t = new Thread(task, "bot-thread");
        t.setDaemon(true);
        t.start();
    }

    private void applyBotAction(Action decision) {
        try {
            Action applied = applyToGame(decision.getType(), decision.getAmount());
            logAction(applied);
            refreshAllPlayerViews(false);
            updatePotLabel();
            processTurn();
        } catch (Exception ex) {
            System.err.println("Erreur application action bot : " + ex.getMessage());
            processTurn();
        }
    }

    // ── Actions du héro ───────────────────────────────────────────────────────

    @FXML
    private void onFold() {
        disableActions();
        applyHeroAction(ActionType.FOLD, 0);
    }

    @FXML
    private void onCall() {
        disableActions();
        List<ActionType> valid = game.getValidActions();
        if (valid.contains(ActionType.CHECK)) applyHeroAction(ActionType.CHECK, 0);
        else                                  applyHeroAction(ActionType.CALL,  0);
    }

    @FXML
    private void onRaise() {
        disableActions();
        int amount = (int) Math.round(raiseSlider.getValue());
        List<ActionType> valid = game.getValidActions();
        if (valid.contains(ActionType.RAISE))       applyHeroAction(ActionType.RAISE,  amount);
        else if (valid.contains(ActionType.BET))    applyHeroAction(ActionType.BET,    amount);
        else if (valid.contains(ActionType.ALL_IN)) applyHeroAction(ActionType.ALL_IN, amount);
    }

    @FXML
    private void onBackToMenu() {
        MainController.goToMenu();
    }

    private void applyHeroAction(ActionType type, int amount) {
        try {
            Action applied = applyToGame(type, amount);
            logAction(applied);
            refreshAllPlayerViews(false);
            updatePotLabel();
            processTurn();
        } catch (Exception ex) {
            System.err.println("Erreur action héro : " + ex.getMessage());
            if (!game.isRoundComplete() && !game.isHandOver())
                enableActions(game.getValidActions());
        }
    }

    // Applique une action au moteur : FOLD/CHECK/CALL n'ont pas de montant.
    /*private Action applyToGame(ActionType type, int amount) {
        if (type == ActionType.BET || type == ActionType.RAISE || type == ActionType.ALL_IN)
            return game.applyAction(type, amount);
        return game.applyAction(type);
    }*/
    private Action applyToGame(ActionType type, int amount){
        Action result;
        if (type == ActionType.BET || type == ActionType.RAISE || type == ActionType.ALL_IN){
            result = game.applyAction(type, amount);
        }
        else{
            result = game.applyAction(type);
        }
        if (currentRecord != null) {
            currentRecord.recordAction(result);
        }
        return result;
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void enableActions(List<ActionType> valid) {
        foldButton.setDisable(!valid.contains(ActionType.FOLD));

        boolean canCheck = valid.contains(ActionType.CHECK);
        boolean canCall  = valid.contains(ActionType.CALL);
        checkCallButton.setDisable(!canCheck && !canCall);
        if (canCall) {
            int callAmt = game.getCallAmount();
            checkCallButton.setText(String.format("Suivre (%d j.)", callAmt));
        } else {
            checkCallButton.setText("Check");
        }

        boolean canRaise = valid.contains(ActionType.RAISE);
        boolean canBet   = valid.contains(ActionType.BET);
        boolean canAllIn = valid.contains(ActionType.ALL_IN);
        raiseButton.setDisable(!canRaise && !canBet && !canAllIn);
        raiseButton.setText(canRaise ? "Relancer" : canBet ? "Miser" : "All-in");

        if (canRaise || canBet) {
            Player heroPlayer = game.getPlayers().get(heroPlayerIndex);
            int minR = game.getMinRaiseTo();
            int maxR = heroPlayer.getStack();
            raiseSlider.setDisable(false);
            raiseSlider.setMin(minR);
            raiseSlider.setMax(Math.max(minR, maxR));
            raiseSlider.setValue(minR);
        } else {
            raiseSlider.setDisable(true);
        }
    }

    private void disableActions() {
        foldButton.setDisable(true);
        checkCallButton.setDisable(true);
        raiseButton.setDisable(true);
        raiseSlider.setDisable(true);
        equityLabel.setText("");
    }

    private int countActiveOpponents() {
        String heroId = game.getPlayers().get(heroPlayerIndex).getId();
        return (int) game.getPlayers().stream()
            .filter(p -> !p.isFolded() && !p.isOut() && !p.getId().equals(heroId))
            .count();
    }

    private void updateHeroEquity() {
        Player heroP = game.getPlayers().get(heroPlayerIndex);
        List<Card> hole = heroP.getHoleCards();
        if (hole.size() != 2) {
            return;
        }
        List<Card> board = new ArrayList<>(game.getBoard());
        int nbOpp = countActiveOpponents();
        new Thread(() -> {
            try {
                MonteCarloEngine engine = AppContext.getEngine();
                MonteCarloEngine.EquityResult result = engine.calculateEquity(hole, board, 3000, Math.max(1, nbOpp));
                double eq = result.equity();
                javafx.application.Platform.runLater(() -> {
                	equityLabel.setText(String.format("Équité : %.1f%%", eq));
                });
            }catch (Exception e) { /* silencieux */ }
        }).start();
    }

    private void refreshAllPlayerViews(boolean revealBotCards) {
        String heroId = game.getPlayers().get(heroPlayerIndex).getId();
        for (Player p : game.getPlayers()) {
            PlayerView view = playerViews.get(p.getId());
            if (view == null) continue;
            view.setPlayer(p);
            if (!revealBotCards && !p.getId().equals(heroId)) view.hideCards();
            view.setFolded(p.isFolded() || p.isOut());
            view.setActive(false);
        }
    }

    private void refreshAllPlayerViews(boolean revealBotCards, Player highlighted) {
        refreshAllPlayerViews(revealBotCards);
        if (highlighted != null) highlightCurrentPlayer(highlighted);
    }

    // Surcharge sans argument (pendant la partie : on ne montre pas les cartes des bots)
    private void refreshAllPlayerViews() {
        refreshAllPlayerViews(false);
    }

    private void highlightCurrentPlayer(Player current) {
        for (Player p : game.getPlayers()) {
            PlayerView view = playerViews.get(p.getId());
            if (view != null)
                view.setActive(current != null && current.getId().equals(p.getId()));
        }
    }

    private void updatePotLabel() {
        updatePot(game.getTotalPot() / (double) bigBlind);
    }

    private void logAction(Action action) {
        addActionToHistory(
            action.getRound().name(),
            action.getPlayer().getName(),
            formatAction(action)
        );
    }

    private String formatAction(Action action) {
        return switch (action.getType()) {
            case FOLD   -> "Fold";
            case CHECK  -> "Check";
            case CALL   -> "Call (" + action.getAmount() + " j.)";
            case BET    -> "Bet " + action.getAmount() + " j.";
            case RAISE  -> "Raise " + action.getAmount() + " j.";
            case ALL_IN -> "All-in (" + action.getAmount() + " j.)";
        };
    }

    // ── API publique (appelée depuis l'extérieur ou le FXML) ──────────────────

    public void revealCommunityCards(Card... cards) {
        for (int i = 0; i < communityCards.length; i++) {
            if (i < cards.length) communityCards[i].showCard(cards[i]);
            else                  communityCards[i].showEmpty();
        }
    }

    public void updatePot(double amountInBB) {
        potView.setPot(amountInBB);
        potLabel.setText(String.format("%.1f BB", amountInBB));
    }

    public void configureRaiseSlider(double heroStack) {
        raiseSlider.setDisable(heroStack <= 0);
        raiseSlider.setMin(0);
        raiseSlider.setMax(heroStack > 0 ? heroStack : 1);
        raiseSlider.setValue(0);
        raiseAmountLabel.setText("0 j.");
    }

    public void setRound(String round)                               { roundLabel.setText(round); }
    public void addActionToHistory(String round, String p, String a) { timeline.addAction(round, p, a); }
    public void clearHistory()                                       { timeline.clear(); }
}
