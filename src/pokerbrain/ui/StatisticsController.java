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
            addLeak(null, "Aucune donnée",
                "Jouez une partie pour voir vos statistiques.\n"
                + "Les mains sont sauvegardées dans " + SessionData.getHistoryManager().getHistoryDir());
            return;
        }

        // La big blind vient des mains jouées, pas d'une constante : sinon le BB/100
        // et le gain total sont exprimés dans une unité qui n'est pas celle de la table.
        int bigBlind = hands.get(hands.size() - 1).getBigBlind();
        // L'id du héro est celui que la main a enregistré, pas la constante "hero" : un
        // autre id rendait des statistiques toutes nulles sans la moindre erreur.
        String heroId = hands.get(hands.size() - 1).getHeroId();
        PlayerStatistics stats = PlayerStatisticsCalculator.calculate(hands, heroId, bigBlind);
        // -1 quand le joueur n'a jamais eu l'occasion de 3-better : afficher 0 % ferait
        // croire à une fuite là où il n'y a simplement pas de donnée.
        double threeBet = stats.hasThreeBetSample() ? stats.getThreeBet() : -1;
        loadStats(stats.getVPIP(), stats.getPFR(), threeBet,
                  stats.getWTSD(), stats.getWSD(), stats.getBBPer100(),
                  stats.getHandsPlayed(), stats.getTotalWinnings() / (double) Math.max(1, bigBlind));

        clearLeaks();
        List<Leak> leaks = LeakDetector.detect(stats);
        for (Leak leak : leaks) {
            addLeak(leak.getSeverity(),
                    leak.getCategory() + " — " + leak.getSeverity().getLabel(),
                    leak.getMessage() + "\n" + leak.getSuggestion());
        }
        if (leaks.isEmpty()) {
            addLeak(null, "Aucune fuite détectée", "Votre jeu semble solide.");
        }
    }

    public void loadStats(double vpip, double pfr, double threeBet,
                          double wtsd, double wsd, double bbPer100,
                          int handsPlayed, double totalGain) {
        vpipLabel.setText(String.format("%.0f%%", vpip));
        pfrLabel.setText(String.format("%.0f%%", pfr));
        threeBetLabel.setText(threeBet < 0 ? "—" : String.format("%.0f%%", threeBet));
        wtsdLabel.setText(String.format("%.0f%%", wtsd));
        wsdLabel.setText(String.format("%.0f%%", wsd));
        bbPer100Label.setText(String.format("%+.1f", bbPer100));
        // Gagnant ou perdant : la couleur vient de la palette, pas d'un code en dur
        bbPer100Label.getStyleClass().setAll("stat-tile__value", "num",
                                             bbPer100 >= 0 ? "gain" : "loss");

        handsPlayedLabel.setText(String.valueOf(handsPlayed));
        totalGainLabel.setText(String.format("%+.1f BB", totalGain));

        var chartData = new LinkedHashMap<String, Double>();
        chartData.put("VPIP", vpip); chartData.put("PFR", pfr);
        chartData.put("3-Bet", Math.max(0, threeBet));
        chartData.put("WTSD", wtsd); chartData.put("W$SD", wsd);
        winrateChart.setEquities(chartData);
    }

    /**
     * Ajoute une fuite au panneau. La sévérité ne choisit plus une couleur en dur mais une
     * classe CSS (`leak--info` / `--moderate` / `--critical`, définies dans screens.css),
     * qui ne porte que le liseré gauche : le texte reste à pleine lisibilité.
     * `severite == null` = aucun problème détecté.
     */
    public void addLeak(LeakSeverity severite, String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("leak__title");

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("leak__detail");

        VBox box = new VBox(4, titleLabel, descLabel);
        box.getStyleClass().addAll("leak", classePourSeverite(severite));
        leaksContainer.getChildren().add(box);
    }

    private static String classePourSeverite(LeakSeverity severite) {
        if (severite == null) return "leak--info";
        return switch (severite) {
            case CRITICAL -> "leak--critical";
            case WARNING  -> "leak--moderate";
            case INFO     -> "leak--info";
        };
    }

    public void clearLeaks() { leaksContainer.getChildren().clear(); }

    @FXML private void onLoadData()    { clearLeaks(); loadFromHistory();}
    @FXML private void onBackToMenu()  { MainController.goToMenu(); }
}
