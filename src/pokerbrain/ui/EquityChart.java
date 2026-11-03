package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Barres d'équité (héro / partage / adversaire, ou statistiques).
 *
 * Les couleurs viennent de la palette via des classes CSS (`chart-bar--hero`,
 * `--villain`, `--neutral`) : le tableau de quatre couleurs en dur, dont le vert et le
 * rouge saturés, a disparu.
 */
public class EquityChart extends VBox {

    private static final double HAUTEUR_MAX = 110.0;
    private static final List<String> SERIES =
        List.of("chart-bar--hero", "chart-bar--villain", "chart-bar--neutral");

    private final HBox barsArea   = new HBox(16);
    private final HBox labelsArea = new HBox(16);

    public EquityChart() {
        setSpacing(4);
        barsArea.setAlignment(Pos.BOTTOM_CENTER);
        barsArea.setPrefHeight(HAUTEUR_MAX + 20);
        labelsArea.setAlignment(Pos.CENTER);
        getChildren().addAll(barsArea, labelsArea);
    }

    public void setEquities(Map<String, Double> equities) {
        reset();

        int i = 0;
        for (var entry : equities.entrySet()) {
            double pct    = entry.getValue();
            double hauteur = Math.max(4, pct / 100.0 * HAUTEUR_MAX);

            Region bar = new Region();
            bar.getStyleClass().addAll("chart-bar", SERIES.get(i % SERIES.size()));
            bar.setPrefSize(56, hauteur);
            bar.setMinHeight(hauteur);
            bar.setMaxHeight(hauteur);

            Label pctLabel = new Label(String.format("%.1f %%", pct));
            pctLabel.getStyleClass().addAll("chart__value", "num");

            VBox colonne = new VBox(3, pctLabel, bar);
            colonne.setAlignment(Pos.BOTTOM_CENTER);
            colonne.setPrefWidth(70);

            Label nameLabel = new Label(entry.getKey());
            nameLabel.getStyleClass().add("chart__name");
            nameLabel.setPrefWidth(70);
            nameLabel.setAlignment(Pos.CENTER);

            barsArea.getChildren().add(colonne);
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
        m.put("Adversaire", 100.0 - heroEquity);
        setEquities(m);
    }
}
