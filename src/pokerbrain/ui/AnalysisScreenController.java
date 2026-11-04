package pokerbrain.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import pokerbrain.analyser.ActionAdvice;
import pokerbrain.analyser.HandAnalyser;
import pokerbrain.bot.RangeMap;
import pokerbrain.engine.EquityCalculator;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.BettingRound;
import pokerbrain.model.game.PlayerStatus;
import pokerbrain.model.history.GameState;
import pokerbrain.model.history.Hand;
import pokerbrain.model.history.PlayerState;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Écran d'analyse à deux modes : rejouer une main enregistrée, ou explorer un spot libre.
 *
 * Remplace `ReplayController` et `AnalysisController`, qui affichaient chacun un board,
 * des cartes et une équité sans rien partager. Le mode spot gagne au passage l'équité
 * contre une range, écrite dans le moteur depuis l'étape 2 et branchée nulle part.
 */
public class AnalysisScreenController {

    private static final int ITERATIONS_RANGE = 20_000;

    @FXML private ToggleButton modeReplay;
    @FXML private ToggleButton modeSpot;
    @FXML private HBox replayPane;
    @FXML private HBox spotPane;
    @FXML private HBox navBar;

    // Mode replay
    @FXML private VBox      timelineBox;
    @FXML private StackPane tableBox;
    @FXML private VBox      curveBox;
    @FXML private VBox      adviceBox;
    @FXML private Label     handSummaryLabel;
    @FXML private Label     stepCounterLabel;
    @FXML private Button    playButton;
    @FXML private StackPane recapOverlay;
    @FXML private Label     recapTitle;
    @FXML private Label     recapDetail;
    @FXML private VBox      recapBox;

    // Mode spot
    @FXML private VBox   pickersBox;
    @FXML private VBox   spotChartBox;
    @FXML private Label  heroEquityLabel;
    @FXML private Label  villainEquityLabel;
    @FXML private Label  tieLabel;
    @FXML private Label  methodLabel;
    @FXML private Label  errorLabel;
    @FXML private Button calculateButton;

    private ScoredTimeline timeline;
    private ReplayTable    table;
    private EquityCurve    curve;
    private AdviceCard     adviceCard;

    private CardPicker  heroPicker, boardPicker, villainPicker;
    private RangeGrid   rangeGrid;
    private ToggleButton villainHandMode, villainRangeMode;
    private EquityChart spotChart;

    private Hand hand;
    private List<Action>    actions;
    private List<GameState> states;
    private Map<Integer, ActionAdvice> adviceMap = new HashMap<>();
    private int     currentStep = 0;
    private boolean analysisPending;
    private String  analysisError;
    private Timeline lecture;

    @FXML
    public void initialize() {
        construireReplay();
        construireSpot();

        modeReplay.selectedProperty().addListener((o, a, selected) -> {
            if (!selected && !modeSpot.isSelected()) { modeSpot.setSelected(true); return; }
            afficherMode(selected);
        });
        modeSpot.selectedProperty().addListener((o, a, selected) -> {
            if (!selected && !modeReplay.isSelected()) { modeReplay.setSelected(true); return; }
            afficherMode(!selected);
        });

        Hand selectionnee = SessionData.getSelectedHand();
        if (selectionnee != null) {
            loadHand(selectionnee);
        } else {
            // Pas de main choisie : l'écran reste utile en mode spot
            stepCounterLabel.setText("Aucune main sélectionnée");
            adviceCard.setMessage("Jouez une partie, ou passez en « Explorer un spot ».", null);
            modeSpot.setSelected(true);
        }

        installerClavier();
    }

    // ── Construction ─────────────────────────────────────────────────────────

    private void construireReplay() {
        timeline = new ScoredTimeline();
        timeline.setOnSelect(this::afficherEtape);
        VBox.setVgrow(timeline, Priority.ALWAYS);
        timelineBox.getChildren().add(timeline);

        table = new ReplayTable();
        tableBox.getChildren().add(table);

        curve = new EquityCurve();
        VBox.setVgrow(curve, Priority.ALWAYS);
        curveBox.getChildren().add(curve);

        adviceCard = new AdviceCard();
        VBox.setVgrow(adviceCard, Priority.ALWAYS);
        adviceBox.getChildren().add(adviceCard);
    }

    private void construireSpot() {
        heroPicker    = new CardPicker("Votre main", 2);
        boardPicker   = new CardPicker("Board", 5);
        villainPicker = new CardPicker("Main de l'adversaire", 2);
        rangeGrid     = new RangeGrid();

        villainHandMode  = new ToggleButton("Main exacte");
        villainRangeMode = new ToggleButton("Range");
        villainHandMode.getStyleClass().add("btn");
        villainRangeMode.getStyleClass().add("btn");
        villainHandMode.setSelected(true);
        HBox bascule = new HBox(0, villainHandMode, villainRangeMode);

        villainHandMode.setOnAction(e -> choisirModeAdversaire(true));
        villainRangeMode.setOnAction(e -> choisirModeAdversaire(false));

        VBox blocAdversaire = new VBox(8, bascule, villainPicker, rangeGrid);
        blocAdversaire.getStyleClass().add("panel");
        rangeGrid.setVisible(false);
        rangeGrid.setManaged(false);

        VBox blocHero = new VBox(8, heroPicker);
        blocHero.getStyleClass().add("panel");
        VBox blocBoard = new VBox(8, boardPicker);
        blocBoard.getStyleClass().add("panel");

        pickersBox.getChildren().addAll(blocHero, blocBoard, blocAdversaire);

        spotChart = new EquityChart();
        spotChartBox.getChildren().add(spotChart);

        // Une carte prise quelque part est grisée partout ailleurs : plus de doublon
        // possible, donc plus de validation manuelle de doublons à écrire.
        Runnable majCartesMortes = () -> {
            Set<Card> prises = new HashSet<>();
            prises.addAll(heroPicker.getSelection());
            prises.addAll(boardPicker.getSelection());
            prises.addAll(villainPicker.getSelection());
            heroPicker.setDeadCards(prises);
            boardPicker.setDeadCards(prises);
            villainPicker.setDeadCards(prises);
            errorLabel.setText("");
        };
        heroPicker.setOnChange(majCartesMortes);
        boardPicker.setOnChange(majCartesMortes);
        villainPicker.setOnChange(majCartesMortes);
    }

    private void choisirModeAdversaire(boolean mainExacte) {
        villainHandMode.setSelected(mainExacte);
        villainRangeMode.setSelected(!mainExacte);
        villainPicker.setVisible(mainExacte);
        villainPicker.setManaged(mainExacte);
        rangeGrid.setVisible(!mainExacte);
        rangeGrid.setManaged(!mainExacte);
    }

    private void afficherMode(boolean replay) {
        replayPane.setVisible(replay);
        replayPane.setManaged(replay);
        spotPane.setVisible(!replay);
        spotPane.setManaged(!replay);
        navBar.setVisible(replay);
        navBar.setManaged(replay);
        if (!replay) { arreterLecture(); masquerRecap(); }
    }

    /**
     * Navigation clavier : ←/→ pour avancer pas à pas, Début/Fin pour les extrémités,
     * Espace pour la lecture automatique. Le raccourci est posé sur la scène, une fois
     * qu'elle existe — un contrôleur FXML est construit avant d'y être attaché.
     */
    private void installerClavier() {
        timelineBox.sceneProperty().addListener((o, ancienne, scene) -> {
            if (scene != null) brancherTouches(scene);
        });
        if (timelineBox.getScene() != null) brancherTouches(timelineBox.getScene());
    }

    private void brancherTouches(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (!replayPane.isVisible()) return;
            switch (e.getCode()) {
                case LEFT  -> onPrevStep();
                case RIGHT -> onNextStep();
                case HOME  -> onGoToStart();
                case END   -> onGoToEnd();
                case SPACE -> onTogglePlay();
                default    -> { return; }
            }
            e.consume();
        });
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.SPACE) e.consume();
        });
    }

    // ── Mode replay ──────────────────────────────────────────────────────────

    public void loadHand(Hand h) {
        this.hand = h;
        this.actions = h.getActions();
        this.states = h.getGameStates();
        this.currentStep = 0;
        this.adviceMap = new HashMap<>();

        table.setHand(h);
        timeline.build(actions, adviceMap, h.getHeroId());
        handSummaryLabel.setText("Analyse en cours…");
        lancerAnalyse(h);
        afficherEtape(0);
    }

    private void lancerAnalyse(Hand h) {
        Task<Map<Integer, ActionAdvice>> tache = new Task<>() {
            @Override
            protected Map<Integer, ActionAdvice> call() {
                Map<Integer, ActionAdvice> resultat = new HashMap<>();
                // Une instance par tâche : HandAnalyser porte un MonteCarloEngine, qui
                // n'est pas partageable entre threads
                for (ActionAdvice adv : new HandAnalyser().analyse(h)) {
                    resultat.put(adv.getActionIndex(), adv);
                }
                return resultat;
            }
        };

        tache.setOnSucceeded(e -> {
            adviceMap = tache.getValue();
            analysisPending = false;
            timeline.build(actions, adviceMap, h.getHeroId());
            curve.setPoints(equitesParEtape());
            handSummaryLabel.setText(resumeDeMain());
            afficherEtape(currentStep);
        });
        tache.setOnFailed(e -> {
            analysisPending = false;
            Throwable cause = tache.getException();
            analysisError = (cause == null) ? "cause inconnue" : cause.toString();
            System.err.println("[Analyse] interrompue : " + analysisError);
            handSummaryLabel.setText("");
            afficherEtape(currentStep);
        });

        analysisPending = true;
        analysisError = null;
        Thread worker = new Thread(tache, "hand-analyser");
        worker.setDaemon(true);
        worker.start();
    }

    private void afficherEtape(int index) {
        if (actions == null || actions.isEmpty()) return;
        currentStep = Math.max(0, Math.min(index, actions.size() - 1));

        Action action = actions.get(currentStep);
        GameState etat = (currentStep < states.size()) ? states.get(currentStep) : null;

        table.setBoard(boardDeLEtape(etat, action.getRound()));
        table.showState(etat, action.getPlayer().getId());

        stepCounterLabel.setText("Étape " + (currentStep + 1) + " / " + actions.size());
        timeline.setCurrent(currentStep);
        curve.setCurrent(currentStep);

        // Dernière action de la main : on referme la boucle par le récapitulatif, comme la
        // table de jeu le fait en fin de main
        if (currentStep == actions.size() - 1 && actions.size() > 1) montrerRecap();
        else                                                        masquerRecap();

        ActionAdvice conseil = adviceMap.get(currentStep);
        if (conseil != null) {
            adviceCard.setAdvice(conseil);
        } else if (analysisPending) {
            adviceCard.setMessage("Analyse en cours…", null);
        } else if (analysisError != null) {
            adviceCard.setMessage("L'analyse de cette main a échoué : " + analysisError, "score--bad");
        } else {
            adviceCard.setMessage("Action d'un adversaire : pas de conseil sur ce coup.", null);
        }
    }

    /**
     * Board visible à cette étape. Le snapshot est préféré quand il existe, sinon le round
     * de l'action décide du nombre de cartes — jamais le board final, qui laisserait voir
     * la river dès le flop.
     */
    private List<Card> boardDeLEtape(GameState etat, BettingRound round) {
        if (etat != null && etat.getBoard() != null && !etat.getBoard().isEmpty()) {
            return etat.getBoard();
        }
        int attendues = switch (round) {
            case PREFLOP -> 0;
            case FLOP    -> 3;
            case TURN    -> 4;
            case RIVER   -> 5;
        };
        List<Card> complet = hand.getBoard();
        if (complet == null || attendues == 0) return List.of();
        return complet.subList(0, Math.min(attendues, complet.size()));
    }

    private List<Double> equitesParEtape() {
        List<Double> equites = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            ActionAdvice conseil = adviceMap.get(i);
            equites.add(conseil != null && conseil.isAnalysable() ? conseil.getEquity() : -1.0);
        }
        return equites;
    }

    /**
     * Résumé de la main : score moyen des coups du héro et coup le plus coûteux.
     *
     * La fuite dominante ne s'y trouve pas, et c'est volontaire : un leak se mesure sur une
     * session (plancher à 10 mains dans `LeakDetector`), pas sur une main. Elle est sur
     * l'écran Statistiques.
     */
    private String resumeDeMain() {
        int total = 0, nombre = 0, pireScore = 101, pireIndex = -1;
        double pireEv = 0;
        for (var entree : adviceMap.entrySet()) {
            ActionAdvice a = entree.getValue();
            if (!a.isAnalysable()) continue;
            total += a.getScore();
            nombre++;
            if (a.getScore() < pireScore) {
                pireScore = a.getScore();
                pireIndex = entree.getKey();
                pireEv = a.getEvDelta();
            }
        }
        if (nombre == 0) return "Aucun coup du héro n'a pu être analysé dans cette main.";

        StringBuilder resume = new StringBuilder();
        resume.append("Score moyen ").append(total / nombre).append("/100 sur ")
              .append(nombre).append(nombre > 1 ? " coups." : " coup.");
        if (pireIndex >= 0 && pireScore < 80) {
            Action pire = actions.get(pireIndex);
            resume.append(" Coup le plus coûteux : ").append(pire.getRound().getLabel())
                  .append(", ").append(pireScore).append("/100");
            if (pireEv > 0) resume.append(String.format(" (−%.1f BB)", pireEv));
            resume.append('.');
        }
        int gain = hand.getHeroWinnings();
        if (gain > 0) resume.append(" Main gagnée : +").append(gain).append(" jetons.");
        return resume.toString();
    }

    /**
     * Récapitulatif de la main rejouée : board, mains qui sont allées au bout, gains, et le
     * résumé d'analyse déjà calculé pour le panneau de gauche.
     */
    private void montrerRecap() {
        int gain = hand.getHeroWinnings();
        recapTitle.setText(gain > 0 ? "Main gagnée" : "Main perdue");
        recapDetail.setText(analysisPending ? "Analyse en cours…" : resumeDeMain());

        recapBox.getChildren().clear();
        List<Card> board = hand.getBoard();
        if (board != null && !board.isEmpty()) ligneRecap("Board · " + cartes(board));

        // Les cartes fermées ne sont dites que pour ceux qui n'ont pas couché : le dernier
        // snapshot porte les statuts de fin de main.
        GameState fin = states.isEmpty() ? null : states.get(states.size() - 1);
        if (fin != null) {
            for (PlayerState ps : fin.getPlayers()) {
                if (ps.getStatus() == PlayerStatus.FOLDED || ps.getHoleCards().isEmpty()) continue;
                ligneRecap(ps.getName() + " · " + cartes(ps.getHoleCards()));
            }
        }
        for (var e : hand.getWinnings().entrySet()) {
            if (e.getValue() > 0) ligneRecap(nomDe(e.getKey()) + " remporte " + e.getValue() + " jetons");
        }

        recapOverlay.setVisible(true);
        recapOverlay.setManaged(true);
    }

    private void ligneRecap(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("timeline__entry");
        recapBox.getChildren().add(l);
    }

    private String nomDe(String playerId) {
        for (PlayerState ps : hand.getInitialStates())
            if (ps.getId().equals(playerId)) return ps.getName();
        return playerId;
    }

    private static String cartes(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) sb.append(sb.isEmpty() ? "" : " ").append(c);
        return sb.toString();
    }

    private void masquerRecap() {
        recapOverlay.setVisible(false);
        recapOverlay.setManaged(false);
    }

    @FXML private void onCloseRecap() { masquerRecap(); }

    @FXML private void onPrevStep()  { arreterLecture(); afficherEtape(currentStep - 1); }
    @FXML private void onNextStep()  { afficherEtape(currentStep + 1); }
    @FXML private void onGoToStart() { arreterLecture(); afficherEtape(0); }
    @FXML private void onGoToEnd()   { arreterLecture(); if (actions != null) afficherEtape(actions.size() - 1); }

    //Lecture automatique pas-à-pas, qui s'arrête d'elle-même à la fin de la main
    @FXML
    private void onTogglePlay() {
        if (lecture != null) { arreterLecture(); return; }
        if (actions == null || actions.isEmpty()) return;

        lecture = new Timeline(new KeyFrame(Duration.seconds(1.4), e -> {
            if (currentStep >= actions.size() - 1) arreterLecture();
            else afficherEtape(currentStep + 1);
        }));
        lecture.setCycleCount(Timeline.INDEFINITE);
        lecture.play();
        playButton.setText("Pause");
    }

    private void arreterLecture() {
        if (lecture != null) {
            lecture.stop();
            lecture = null;
        }
        playButton.setText("Lecture");
    }

    // ── Mode spot libre ──────────────────────────────────────────────────────

    @FXML
    private void onCalculate() {
        errorLabel.setText("");
        List<Card> hero  = heroPicker.getSelection();
        List<Card> board = boardPicker.getSelection();

        if (hero.size() != 2) {
            errorLabel.setText("Choisissez vos deux cartes.");
            return;
        }
        boolean mainExacte = villainHandMode.isSelected();
        List<Card> villain = villainPicker.getSelection();
        if (mainExacte && villain.size() != 2) {
            errorLabel.setText("Choisissez les deux cartes de l'adversaire, ou passez en mode range.");
            return;
        }
        RangeMap range = mainExacte ? null : rangeGrid.toRangeMap();
        if (!mainExacte && range.isEmpty()) {
            errorLabel.setText("Sélectionnez au moins une main dans la range.");
            return;
        }

        calculateButton.setDisable(true);
        calculateButton.setText("Calcul en cours…");

        Task<double[]> tache = new Task<>() {
            @Override
            protected double[] call() {
                if (mainExacte) {
                    // Énumération exacte : le vrai chiffre, pas une estimation
                    return EquityCalculator.calculate(hero, villain, board);
                }
                MonteCarloEngine.EquityResult r = new MonteCarloEngine()
                    .calculateEquity(hero, board, ITERATIONS_RANGE, 1, range.asVillainRange());
                return new double[] { r.winPct(), r.lossPct(), r.tiePct() };
            }
        };

        tache.setOnSucceeded(e -> {
            double[] r = tache.getValue();
            double heroEq    = r[0] + r[2] / 2.0;
            double villainEq = r[1] + r[2] / 2.0;
            heroEquityLabel.setText(String.format("%.1f %%", heroEq));
            villainEquityLabel.setText(String.format("%.1f %%", villainEq));
            tieLabel.setText(String.format("%.1f %%", r[2]));
            spotChart.setHeroVillain(heroEq);
            methodLabel.setText(mainExacte
                ? "Énumération exacte de tous les boards restants."
                : "Monte-Carlo, " + ITERATIONS_RANGE + " tirages dans la range choisie.");
            finCalcul();
        });
        tache.setOnFailed(e -> {
            Throwable cause = tache.getException();
            errorLabel.setText("Calcul impossible : "
                + (cause == null ? "cause inconnue" : cause.getMessage()));
            finCalcul();
        });

        Thread worker = new Thread(tache, "equity-spot");
        worker.setDaemon(true);
        worker.start();
    }

    private void finCalcul() {
        calculateButton.setDisable(false);
        calculateButton.setText("Calculer l'équité");
    }

    @FXML
    private void onBackToMenu() {
        arreterLecture();
        MainController.goToMenu();
    }
}
