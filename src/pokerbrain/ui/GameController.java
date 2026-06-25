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
import pokerbrain.engine.GameSession;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.AppContext;
import pokerbrain.simulator.MonteCarloEngine;

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

    private GameSession              session;
    private Map<String, PlayerView>  playerViews = new HashMap<>();
    private Label                    equityLabel;

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
            raiseAmountLabel.setText(v <= 0 ? "0" : v + " jetons");});

        disableActions();
    }

    // ── Point d'entrée depuis MenuController ─────────────────────────────────

    public void initGame(List<Player> players, int heroIndex, List<Bot> gameBots,
                         int sb, int bb) {
        session = new GameSession(players, heroIndex, gameBots, sb, bb);
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
            session.startNewHand();
            refreshAllPlayerViews();
            updatePotLabel();
            setRound(session.getGame().getCurrentRound().name());
            processTurn();
        } catch (Exception e) {
            System.err.println("Impossible de démarrer une nouvelle main : " + e.getMessage());
            MainController.goToMenu();
        }
    }

    // Décide quoi faire selon l'état du jeu : avancer le round, jouer le bot, ou attendre le héro.
    private void processTurn() {
        if (session.getGame().isHandOver()) {
            handleHandOver();
            return;
        }

        updatePotLabel();
        setRound(session.getGame().getCurrentRound().name());

        if (session.getGame().isRoundComplete()) {
            PauseTransition pause = new PauseTransition(Duration.millis(400));
            pause.setOnFinished(e -> advanceRound());
            pause.play();
            return;
        }

        Player current = session.getGame().getCurrentPlayer();
        highlightCurrentPlayer(current);

        if (session.isBot(current.getId())) {
            disableActions();
            Bot bot = session.getBot(current.getId()).get();
            PauseTransition pause = new PauseTransition(Duration.millis(600));
            pause.setOnFinished(e -> runBotTurn(bot, current));
            pause.play();
        } else {
            enableActions(session.getGame().getValidActions());
            updateHeroEquity();
        }
    }

    private void advanceRound() {
        try {
            BettingRound finished = session.getGame().getCurrentRound();
            if (finished == BettingRound.RIVER) {
                handleShowdown();
                return;
            }
            switch (finished) {
                case PREFLOP -> session.getGame().dealFlop();
                case FLOP    -> session.getGame().dealTurn();
                case TURN    -> session.getGame().dealRiver();
                default      -> { return; }
            }
            List<Card> board = session.getGame().getBoard();
            revealCommunityCards(board.toArray(new Card[0]));
            setRound(session.getGame().getCurrentRound().name());
            updatePotLabel();
            processTurn();
        } catch (Exception e) {
            System.err.println("Erreur avancement round : " + e.getMessage());
        }
    }

    private void handleShowdown() {
        Map<Player, Integer> winnings = new HashMap<>();
        try {
            winnings = session.getGame().showdown();
            SessionData.addHand(session.buildAndCloseHand(winnings));
            for (Player p : session.getGame().getPlayers()) {
                if (!p.isFolded() && !p.isOut()) {
                    PlayerView view = playerViews.get(p.getId());
                    if (view != null) view.setPlayer(p);
                }
            }
            for (Map.Entry<Player, Integer> e : winnings.entrySet()) {
                if (e.getValue() > 0)
                    addActionToHistory("SHOWDOWN", e.getKey().getName(),
                        "Wins " + e.getValue() + " chips.");
            }
            refreshAllPlayerViews(true);
        } catch (Exception e) {
            System.err.println("Erreur showdown : " + e.getMessage());
        }

        // Popup résultat (déféré pour éviter le conflit avec l'animation)
        Player heroPlayer = session.getGame().getPlayers().get(session.getHeroPlayerIndex());
        int heroWin = winnings.getOrDefault(heroPlayer, 0);

        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                heroWin > 0
                    ? javafx.scene.control.Alert.AlertType.INFORMATION
                    : javafx.scene.control.Alert.AlertType.WARNING
            );
            alert.setTitle("End of hand");
            alert.setHeaderText(heroWin > 0 ? "You win!" : "You lose");
            alert.setContentText(heroWin > 0
                ? "Winnings: +" + heroWin + " chips"
                : "You did not win this hand.");
            alert.showAndWait();
            handleHandOver();
        });
        return; // ← IMPORTANT : on sort de handleShowdown() sans appeler handleHandOver() ici
    }

    private void handleHandOver() {
        SessionData.addHand(session.buildAndCloseHand());
        // Popup si le héro gagne par fold des adversaires
    	Player heroPlayer = session.getGame().getPlayers().get(session.getHeroPlayerIndex());
    	if (!heroPlayer.isFolded() && !heroPlayer.isOut()) {
    	    long activePlayers = session.getGame().getPlayers().stream()
    	        .filter(p -> !p.isFolded() && !p.isOut()).count();
    	    if (activePlayers == 1) {
    	        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
    	            javafx.scene.control.Alert.AlertType.INFORMATION);
    	        alert.setTitle("End of hand");
    	        alert.setHeaderText("You win the pot!");
    	        alert.setContentText("All opponents folded.");
    	        alert.show(); // ← show() sans Wait, pas de blocage
    	    }
    	}
        disableActions();
        long playersWithChips = session.getGame().getPlayers().stream()
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
        Task<Action> task = new Task<>() {
            @Override
            protected Action call() {
                return session.decideBotAction(bot, current);
            }
        };

        task.setOnSucceeded(e -> applyBotAction(task.getValue()));
        task.setOnFailed(e -> {
            System.err.println("Bot error: " + task.getException().getMessage());
            applyBotAction(new Action(current, ActionType.FOLD, session.getGame().getCurrentRound()));
        });

        Thread t = new Thread(task, "bot-thread");
        t.setDaemon(true);
        t.start();
    }

    private void applyBotAction(Action decision) {
        try {
            Action applied = session.applyAction(decision.getType(), decision.getAmount());
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
        List<ActionType> valid = session.getGame().getValidActions();
        if (valid.contains(ActionType.CHECK)) applyHeroAction(ActionType.CHECK, 0);
        else                                  applyHeroAction(ActionType.CALL,  0);
    }

    @FXML
    private void onRaise() {
        disableActions();
        int amount = (int) Math.round(raiseSlider.getValue());
        List<ActionType> valid = session.getGame().getValidActions();
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
            Action applied = session.applyAction(type, amount);
            logAction(applied);
            refreshAllPlayerViews(false);
            updatePotLabel();
            processTurn();
        } catch (Exception ex) {
            System.err.println("Erreur action héro : " + ex.getMessage());
            if (!session.getGame().isRoundComplete() && !session.getGame().isHandOver())
                enableActions(session.getGame().getValidActions());
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void enableActions(List<ActionType> valid) {
        foldButton.setDisable(!valid.contains(ActionType.FOLD));

        boolean canCheck = valid.contains(ActionType.CHECK);
        boolean canCall  = valid.contains(ActionType.CALL);
        checkCallButton.setDisable(!canCheck && !canCall);
        if (canCall) {
            int callAmt = session.getGame().getCallAmount();
            checkCallButton.setText(String.format("Call (%d chips)", callAmt));
        } else {
            checkCallButton.setText("Check");
        }

        boolean canRaise = valid.contains(ActionType.RAISE);
        boolean canBet   = valid.contains(ActionType.BET);
        boolean canAllIn = valid.contains(ActionType.ALL_IN);
        raiseButton.setDisable(!canRaise && !canBet && !canAllIn);
        raiseButton.setText(canRaise ? "Raise" : canBet ? "Bet" : "All-in");

        if (canRaise || canBet) {
            Player heroPlayer = session.getGame().getPlayers().get(session.getHeroPlayerIndex());
            int minR = session.getGame().getMinRaiseTo();
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

    private void updateHeroEquity() {
        Player heroP = session.getGame().getPlayers().get(session.getHeroPlayerIndex());
        List<Card> hole = heroP.getHoleCards();
        if (hole.size() != 2) {
            return;
        }
        List<Card> board = new ArrayList<>(session.getGame().getBoard());
        int nbOpp = session.countActiveOpponents();
        new Thread(() -> {
            try {
                MonteCarloEngine engine = AppContext.getEngine();
                MonteCarloEngine.EquityResult result = engine.calculateEquity(hole, board, 3000, Math.max(1, nbOpp));
                double eq = result.equity();
                javafx.application.Platform.runLater(() -> {
                	equityLabel.setText(String.format("Equity: %.1f%%", eq));
                });
            }catch (Exception e) { /* silencieux */ }
        }).start();
    }

    private void refreshAllPlayerViews(boolean revealBotCards) {
        String heroId = session.getGame().getPlayers().get(session.getHeroPlayerIndex()).getId();
        for (Player p : session.getGame().getPlayers()) {
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
        for (Player p : session.getGame().getPlayers()) {
            PlayerView view = playerViews.get(p.getId());
            if (view != null)
                view.setActive(current != null && current.getId().equals(p.getId()));
        }
    }

    private void updatePotLabel() {
        int pot = session.getGame().getTotalPot();
        potView.setPot(pot);
        potLabel.setText(pot + " chips");
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
        potLabel.setText(String.format("%.1f chips", amountInBB));
    }

    public void configureRaiseSlider(double heroStack) {
        raiseSlider.setDisable(heroStack <= 0);
        raiseSlider.setMin(0);
        raiseSlider.setMax(heroStack > 0 ? heroStack : 1);
        raiseSlider.setValue(0);
        raiseAmountLabel.setText("0");
    }

    public void setRound(String round)                               { roundLabel.setText(round); }
    public void addActionToHistory(String round, String p, String a) { timeline.addAction(round, p, a); }
    public void clearHistory()                                       { timeline.clear(); }
}
