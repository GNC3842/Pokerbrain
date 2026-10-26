package pokerbrain.ui;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.analyser.ActionAdvice;
import pokerbrain.analyser.HandAnalyser;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.Action;
import pokerbrain.model.history.GameState;
import pokerbrain.model.history.Hand;
import pokerbrain.model.game.BettingRound;
import pokerbrain.model.history.PlayerState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplayController {

    @FXML private Label stepRoundLabel;
    @FXML private Label stepActionLabel;
    @FXML private Label stepPotLabel;
    @FXML private Label stepCounterLabel;
    @FXML private VBox  timelineContainer;
    @FXML private HBox  replayCommunityRow;
    @FXML private VBox  equityContainer;

    private ActionTimeline timeline;
    private EquityChart    equityChart;
    private CardView[]     communityCards;
    private Label          adviceLabel;
    private CardView heroCard1, heroCard2;

    private Hand               hand;
    private List<Action>       actions;
    private List<GameState>    states;
    private Map<Integer, ActionAdvice> adviceMap;
    private int currentStep = 0;
    private boolean analysisPending;
    private String  analysisError;
    private String  handSummary;

    @FXML
    public void initialize() {
        timeline = new ActionTimeline();
        timelineContainer.getChildren().add(timeline);

        equityChart = new EquityChart();
        equityContainer.getChildren().add(equityChart);

        adviceLabel = new Label();
        adviceLabel.setWrapText(true);
        adviceLabel.getStyleClass().add("advice");
        equityContainer.getChildren().add(adviceLabel);

        communityCards = new CardView[5];
        for (int i = 0; i < 5; i++) {
            communityCards[i] = new CardView();
            communityCards[i].showEmpty();
            replayCommunityRow.getChildren().add(communityCards[i]);
        }
        
        // Cartes du héro
        HBox heroCardsBox = new HBox(6);
        heroCard1 = new CardView();
        heroCard2 = new CardView();
        heroCardsBox.getChildren().addAll(
            etiquette("Vos cartes"),
            heroCard1, heroCard2
        );
        equityContainer.getChildren().add(0, heroCardsBox);

        Hand selected = SessionData.getSelectedHand();
        if (selected != null) {
            loadHand(selected);
        } else {
            stepCounterLabel.setText("Aucune main sélectionnée");
            adviceLabel.setText("Jouez une partie puis revenez ici.");
        }
    }

    public void loadHand(Hand h) {
        this.hand    = h;
        this.actions = h.getActions();
        this.states  = h.getGameStates();
        this.currentStep = 0;
        
        // Afficher les cartes du héro depuis les initialStates
        if (h.getHeroId() != null && h.getInitialStates() != null) {
            for (pokerbrain.model.history.PlayerState ps : h.getInitialStates()) {
                if (ps.getId().equals(h.getHeroId()) && ps.getHoleCards().size() == 2) {
                    heroCard1.showCard(ps.getHoleCards().get(0));
                    heroCard2.showCard(ps.getHoleCards().get(1));
                    break;
                }
            }
        }

        timeline.clear();
        for (Action a : actions) {
            String actionStr = a.getType().involvesMoney()
                ? a.getType() + " " + a.getAmount()
                : a.getType().toString();
            timeline.addAction(a.getRound().getLabel(), a.getPlayer().getName(), actionStr);
        }

        adviceMap = new HashMap<>();
        startAnalysis(h);

     // Afficher le résultat du showdown : gagnant + cartes des joueurs
        Map<String, Integer> winnings = h.getWinnings();
        StringBuilder resultText = new StringBuilder();

        if (winnings != null && !winnings.isEmpty()) {
            resultText.append("🏆 Résultat :\n");
            for (Map.Entry<String, Integer> e : winnings.entrySet()) {
                if (e.getValue() > 0)
                    resultText.append("  ").append(e.getKey()).append(" gagne ").append(e.getValue()).append(" jetons\n");
            }
        }

        // Cartes des joueurs au showdown (ceux qui ne se sont pas couchés)
        if (h.getInitialStates() != null) {
            resultText.append("\n🃏 Cartes révélées :\n");
            // Trouver les joueurs qui ont atteint le showdown (non foldés)
            java.util.Set<String> foldedIds = new java.util.HashSet<>();
            for (Action a : actions) {
                if (a.getType() == pokerbrain.model.game.ActionType.FOLD)
                    foldedIds.add(a.getPlayer().getId());
            }
            for (PlayerState ps : h.getInitialStates()) {
                if (!foldedIds.contains(ps.getId()) && ps.getHoleCards().size() == 2) {
                    String marker = (winnings != null && winnings.getOrDefault(ps.getId(), 0) > 0) ? " ★" : "";
                    // Main finale en clair : le décodeur existait sans appelant
                    pokerbrain.model.core.HandRank rank =
                        pokerbrain.simulator.FastHandEvaluator.describeBestHand(
                            ps.getHoleCards(), h.getBoard());
                    resultText.append("  ").append(ps.getName()).append(" : ")
                        .append(ps.getHoleCards().get(0)).append(" ")
                        .append(ps.getHoleCards().get(1));
                    if (rank != null) {
                        resultText.append(" — ").append(rank.describe());
                    }
                    resultText.append(marker).append("\n");
                }
            }
        }

        // Conservé pour être réaffiché sur les étapes sans conseil, au lieu d'être
        // écrasé par le premier showStep().
        handSummary = resultText.toString();
        showStep(0);
    }

    /**
     * Lance l'analyse sur un thread de fond.
     *
     * Elle tournait auparavant dans initialize(), donc sur le thread JavaFX, avec
     * 10 000 itérations Monte-Carlo par action : l'écran de replay gelait plusieurs
     * secondes à l'ouverture. Le Task rend la main immédiatement, et les conseils
     * apparaissent dès qu'ils sont prêts.
     */
    private void startAnalysis(Hand h) {
        Task<Map<Integer, ActionAdvice>> task = new Task<>() {
            @Override
            protected Map<Integer, ActionAdvice> call() {
                Map<Integer, ActionAdvice> result = new HashMap<>();
                // Une instance par tâche : le MonteCarloEngine n'est pas partageable entre threads
                for (ActionAdvice adv : new HandAnalyser().analyse(h)) {
                    result.put(adv.getActionIndex(), adv);
                }
                return result;
            }
        };

        task.setOnSucceeded(e -> {
            adviceMap = task.getValue();
            analysisPending = false;
            showStep(currentStep);
        });
        task.setOnFailed(e -> {
            analysisPending = false;
            // L'échec doit se voir : le silence faisait passer une analyse plantée
            // pour une main sans le moindre conseil à donner.
            Throwable cause = task.getException();
            analysisError = (cause == null) ? "cause inconnue" : cause.toString();
            System.err.println("[Replay] Analyse interrompue : " + analysisError);
            showStep(currentStep);
        });

        analysisPending = true;
        analysisError = null;
        Thread worker = new Thread(task, "hand-analyser");
        worker.setDaemon(true);
        worker.start();
    }

    private void showStep(int index) {
        if (actions == null || actions.isEmpty() || index < 0 || index >= actions.size()) return;

        Action action = actions.get(index);

        stepRoundLabel.setText(action.getRound().getLabel());
        String actionStr = action.getType().involvesMoney()
            ? action.getType() + " " + action.getAmount()
            : action.getType().toString();
        stepActionLabel.setText(action.getPlayer().getName() + " : " + actionStr);

        // Board : depuis GameState si disponible, sinon depuis Hand.getBoard() + round
        if (index < states.size()) {
            GameState gs = states.get(index);
            stepPotLabel.setText("Pot " + gs.getTotalPot());
            List<Card> board = gs.getBoard();
            for (int i = 0; i < 5; i++) {
                if (board != null && i < board.size()) communityCards[i].showCard(board.get(i));
                else communityCards[i].showEmpty();
            }
        } else {
            // Fallback : pas de GameState (main chargée depuis le disque)
            // Montrer le board selon le round de l'action courante
            List<Card> fullBoard = hand.getBoard();
            BettingRound round = action.getRound();
            int cardsToShow = 0;
            if (round == BettingRound.FLOP) cardsToShow = 3;
            else if (round == BettingRound.TURN) cardsToShow = 4;
            else if (round == BettingRound.RIVER) cardsToShow = 5;
            for (int i = 0; i < 5; i++) {
                if (fullBoard != null && i < cardsToShow && i < fullBoard.size())
                    communityCards[i].showCard(fullBoard.get(i));
                else
                    communityCards[i].showEmpty();
            }
            stepPotLabel.setText("Pot —");
        }

        stepCounterLabel.setText("Étape " + (index + 1) + " / " + actions.size());
        timeline.highlightEntry(index);

        showAdvice(index);
    }

    private void showAdvice(int index) {
        ActionAdvice advice = (adviceMap == null) ? null : adviceMap.get(index);

        if (advice == null) {
            if (analysisPending) {
                setAdviceText("Analyse en cours…", null);
            } else if (analysisError != null) {
                setAdviceText("L'analyse de cette main a échoué : " + analysisError, "score--bad");
            } else {
                // Pas de conseil sur cette action (ce n'est pas une action du héro) :
                // on remet le résumé de la main plutôt qu'un panneau vide.
                setAdviceText(handSummary == null ? "" : handSummary, null);
            }
            return;
        }

        // Action non analysable : afficher le motif plutôt qu'un score trompeur à 0/100
        if (!advice.isAnalysable()) {
            setAdviceText(advice.getReasoning(), null);
            return;
        }

        String severite = advice.getScore() >= 80 ? "score--good"
                        : advice.getScore() >= 50 ? "score--mid"
                        : "score--bad";

        StringBuilder text = new StringBuilder();
        text.append("Score : ").append(advice.getScore()).append("/100\n");
        if (!advice.getContext().isEmpty()) {
            text.append(advice.getContext()).append('\n');
        }
        text.append(advice.getReasoning());
        if (advice.getEvDelta() > 0) {
            text.append(String.format("%nEV perdue : %.1f BB", advice.getEvDelta()));
        }

        setAdviceText(text.toString(), severite);

        if (advice.getEquity() >= 0) {
            equityChart.setHeroVillain(advice.getEquity() * 100);
        }
    }

    /**
     * Pose le texte du conseil et sa classe de sévérité (`score--good` / `--mid` / `--bad`
     * de theme.css) au lieu d'une couleur en dur : l'échelle d'analyse est définie une
     * seule fois, dans la palette.
     */
    private void setAdviceText(String text, String severite) {
        adviceLabel.setText(text);
        adviceLabel.getStyleClass().setAll("advice");
        if (severite != null) adviceLabel.getStyleClass().add(severite);
    }

    private static Label etiquette(String texte) {
        Label label = new Label(texte);
        label.getStyleClass().add("muted");
        return label;
    }

    @FXML private void onPrevStep()  { if (currentStep > 0) showStep(--currentStep); }
    @FXML private void onNextStep()  { if (actions != null && currentStep < actions.size() - 1) showStep(++currentStep); }
    @FXML private void onGoToStart() { currentStep = 0; showStep(0); }
    @FXML private void onGoToEnd()   { if (actions != null) { currentStep = actions.size() - 1; showStep(currentStep); } }

    @FXML private void onBackToMenu() { MainController.goToMenu(); }
}