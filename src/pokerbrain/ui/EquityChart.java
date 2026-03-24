package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

// Graphique en barres représentant les équités (ex. Héro vs Villain, ou stats VPIP/PFR/…).
// Les barres sont proportionnelles au pourcentage, plafonnées à MAX_HEIGHT pixels.
public class EquityChart extends VBox {

    private static final double MAX_HEIGHT = 110.0;
    private static final String[] COLORS   = { "#2ecc71", "#e74c3c", "#3498db", "#f39c12" };

    private final HBox barsArea   = new HBox(16);
    private final HBox labelsArea = new HBox(16);

    public EquityChart() {
        setSpacing(4);
        barsArea.setAlignment(Pos.BOTTOM_CENTER);
        barsArea.setPrefHeight(MAX_HEIGHT + 20);
        labelsArea.setAlignment(Pos.CENTER);
        getChildren().addAll(barsArea, labelsArea);
    }

    public void setEquities(Map<String, Double> equities) {
        barsArea.getChildren().clear();
        labelsArea.getChildren().clear();

        int i = 0;
        for (var entry : equities.entrySet()) {
            String color  = COLORS[i % COLORS.length];
            double pct    = entry.getValue();
            double height = Math.max(4, pct / 100.0 * MAX_HEIGHT);

            VBox bar = new VBox();
            bar.setPrefSize(60, height);
            bar.setMaxHeight(height);
            bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4px 4px 0 0;");

            Label pctLabel = new Label(String.format("%.1f%%", pct));
            pctLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");

            VBox column = new VBox(3, pctLabel, bar);
            column.setAlignment(Pos.BOTTOM_CENTER);
            column.setPrefWidth(70);

            Label nameLabel = new Label(entry.getKey());
            nameLabel.setPrefWidth(70);
            nameLabel.setAlignment(Pos.CENTER);
            nameLabel.setStyle("-fx-text-fill: #a09070; -fx-font-size: 11px;");

            barsArea.getChildren().add(column);
            labelsArea.getChildren().add(nameLabel);
            i++;
        }
    }

    public void reset() {
        barsArea.getChildren().clear();
        labelsArea.getChildren().clear();
    }

    public void setHeroVillain(double heroEquity) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Héro", heroEquity);
        m.put("Villain", 100.0 - heroEquity);
        setEquities(m);
    }
}
