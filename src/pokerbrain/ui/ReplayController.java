package pokerbrain.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.model.core.Card;

import java.util.List;

// Contrôleur du replay pas-à-pas d'une main enregistrée.
// Les données sont fournies sous forme de List<ReplayStep> par le moteur de jeu.
public class ReplayController {

    @FXML private Label stepRoundLabel;
    @FXML private Label stepActionLabel;
    @FXML private Label stepPotLabel;
    @FXML private Label stepCounterLabel;

    @FXML private VBox timelineContainer;
    @FXML private HBox replayCommunityRow;
    @FXML private VBox equityContainer;

    private ActionTimeline timeline;
    private EquityChart    equityChart;
    private CardView[]     communityCards;

    // Un instantané de la table à un moment précis de la main
    public record ReplayStep(String round, String player, String action,
                             double pot, Card[] board, double heroEquity) {}

    private List<ReplayStep> steps = List.of();
    private int currentStep = 0;

    @FXML
    public void initialize() {
        timeline = new ActionTimeline();
        timelineContainer.getChildren().add(timeline);

        equityChart = new EquityChart();
        equityContainer.getChildren().add(equityChart);

        communityCards = new CardView[5];
        for (int i = 0; i < 5; i++) {
            communityCards[i] = new CardView();
            communityCards[i].showEmpty();
            replayCommunityRow.getChildren().add(communityCards[i]);
        }

        stepCounterLabel.setText("Aucune main chargée");
    }

    public void loadHand(List<ReplayStep> handSteps) {
        steps = handSteps;
        currentStep = 0;
        timeline.clear();
        for (ReplayStep s : steps)
            timeline.addAction(s.round(), s.player(), s.action());
        showStep(0);
    }

    private void showStep(int index) {
        if (steps.isEmpty() || index < 0 || index >= steps.size()) return;
        ReplayStep s = steps.get(index);

        stepRoundLabel.setText(s.round());
        stepActionLabel.setText(s.player() + " : " + s.action());
        stepPotLabel.setText(String.format("Pot : %.1f BB", s.pot()));
        stepCounterLabel.setText("Étape " + (index + 1) + " / " + steps.size());

        for (int i = 0; i < 5; i++) {
            if (s.board() != null && i < s.board().length) communityCards[i].showCard(s.board()[i]);
            else                                            communityCards[i].showEmpty();
        }

        equityChart.setHeroVillain(s.heroEquity());
        timeline.highlightEntry(index);
    }

    @FXML private void onPrevStep()  { if (currentStep > 0)              showStep(--currentStep); }
    @FXML private void onNextStep()  { if (currentStep < steps.size()-1) showStep(++currentStep); }
    @FXML private void onGoToStart() { currentStep = 0;                  showStep(0); }
    @FXML private void onGoToEnd()   { currentStep = steps.size() - 1;   showStep(currentStep); }

    @FXML private void onBackToMenu() { MainController.goToMenu(); }
}
