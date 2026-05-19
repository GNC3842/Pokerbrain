package pokerbrain.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import pokerbrain.analyser.leaks.Leak;
import pokerbrain.analyser.leaks.LeakDetector;
import pokerbrain.analyser.leaks.LeakSeverity;
import pokerbrain.analyser.statistics.PlayerStatistics;
import pokerbrain.analyser.statistics.PlayerStatisticsCalculator;
import pokerbrain.model.history.Hand;

import java.util.LinkedHashMap;
import java.util.List;

// Affiche les statistiques de jeu d'un joueur (VPIP, PFR, …) et détecte les fuites de jeu.
// Les données sont injectées par le moteur via loadStats() après analyse des mains.
public class StatisticsController {

    @FXML private Label vpipLabel, pfrLabel, threeBetLabel, wtsdLabel, wsdLabel, bbPer100Label;
    @FXML private Label handsPlayedLabel, totalGainLabel, biggestHandLabel;
    @FXML private VBox  leaksContainer;
    @FXML private VBox  statsChartContainer;

    private EquityChart winrateChart;

    @FXML
    public void initialize() {
        winrateChart = new EquityChart();
        statsChartContainer.getChildren().add(winrateChart);
        loadFromHistory();
    }

    private void loadFromHistory() {
        List<Hand> hands = SessionData.getAllHands();

        if (hands.isEmpty()) {
            handsPlayedLabel.setText("0");
            totalGainLabel.setText("Aucune main");
            biggestHandLabel.setText("—");
            addLeak(false, "Aucune donnée",
                "Jouez une partie pour voir vos statistiques.\n"
                + "Les mains sont sauvegardées dans " + SessionData.getHistoryManager().getHistoryDir());
            return;
        }

        PlayerStatistics stats = PlayerStatisticsCalculator.calculate(hands, "hero", 10);
        loadStats(stats.getVPIP(), stats.getPFR(), 0,
                  stats.getWTSD(), stats.getWSD(), stats.getBBPer100(),
                  stats.getHandsPlayed(), stats.getTotalWinnings());

        clearLeaks();
        List<Leak> leaks = LeakDetector.detect(stats);
        for (Leak leak : leaks) {
            addLeak(leak.getSeverity() != LeakSeverity.INFO,
                    leak.getCategory() + " — " + leak.getSeverity().getLabel(),
                    leak.getMessage() + "\n" + leak.getSuggestion());
        }
        if (leaks.isEmpty()) {
            addLeak(false, "Aucun leak détecté", "Votre jeu semble solide !");
        }
    }

    public void loadStats(double vpip, double pfr, double threeBet,
                          double wtsd, double wsd, double bbPer100,
                          int handsPlayed, double totalGain) {
        vpipLabel.setText(String.format("%.0f%%", vpip));
        pfrLabel.setText(String.format("%.0f%%", pfr));
        threeBetLabel.setText(String.format("%.0f%%", threeBet));
        wtsdLabel.setText(String.format("%.0f%%", wtsd));
        wsdLabel.setText(String.format("%.0f%%", wsd));
        bbPer100Label.setText(String.format("%+.1f", bbPer100));
        // BB/100 en vert si positif, rouge si négatif
        bbPer100Label.setStyle("-fx-text-fill: " + (bbPer100 >= 0 ? "#2ecc71" : "#e74c3c")
                + "; -fx-font-size: 24px; -fx-font-weight: bold;");

        handsPlayedLabel.setText(String.valueOf(handsPlayed));
        totalGainLabel.setText(String.format("%+.1f BB", totalGain));

        var chartData = new LinkedHashMap<String, Double>();
        chartData.put("VPIP", vpip); chartData.put("PFR", pfr); chartData.put("3-Bet", threeBet);
        chartData.put("WTSD", wtsd); chartData.put("W$SD", wsd);
        winrateChart.setEquities(chartData);
    }

    // Ajoute une fuite (warning=true) ou un bon point (warning=false) dans le panneau de conseils
    public void addLeak(boolean warning, String title, String description) {
        String color = warning ? "#e67e22" : "#2ecc71";

        Label titleLabel = new Label((warning ? "⚠" : "✓") + "  " + title);
        titleLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #a09070; -fx-font-size: 12px;");

        VBox box = new VBox(4, titleLabel, descLabel);
        box.setStyle("-fx-background-color: #0f3020; -fx-background-radius: 6px;"
                + "-fx-border-color: " + color + "; -fx-border-radius: 6px;"
                + "-fx-border-width: 0 0 0 3px; -fx-padding: 8 12 8 12;");
        leaksContainer.getChildren().add(box);
    }

    public void clearLeaks() { leaksContainer.getChildren().clear(); }

    @FXML private void onLoadData()    { clearLeaks(); loadFromHistory();}
    @FXML private void onBackToMenu()  { MainController.goToMenu(); }
}
