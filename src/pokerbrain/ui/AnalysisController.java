package pokerbrain.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.model.core.Card;

// Panneau d'analyse d'équité : l'utilisateur saisit les cartes et un range adverse,
// puis lance un calcul Monte Carlo (à implémenter).
public class AnalysisController {

    @FXML private TextField heroCard1Field;
    @FXML private TextField heroCard2Field;
    @FXML private TextField board1Field, board2Field, board3Field, board4Field, board5Field;
    @FXML private TextField rangeField;
    @FXML private TextField villainCard1Field;
    @FXML private TextField villainCard2Field;

    @FXML private HBox heroCardsBox;
    @FXML private HBox boardCardsBox;
    @FXML private HBox villainCardsBox;
    @FXML private VBox equityChartContainer;
    @FXML private VBox villainHandBox;
    @FXML private VBox villainRangeBox;

    @FXML private ToggleButton toggleHandMode;
    @FXML private ToggleButton toggleRangeMode;

    @FXML private Label heroEquityLabel;
    @FXML private Label villainEquityLabel;
    @FXML private Label tieLabel;
    @FXML private Label errorLabel;

    private EquityChart equityChart;
    private CardView    heroView1, heroView2;
    private CardView    villainView1, villainView2;
    private CardView[]  boardViews;

    @FXML
    public void initialize() {
        equityChart = new EquityChart();
        equityChartContainer.getChildren().add(equityChart);

        heroView1 = new CardView();
        heroView2 = new CardView();
        heroCardsBox.getChildren().addAll(heroView1, heroView2);

        villainView1 = new CardView();
        villainView2 = new CardView();
        villainCardsBox.getChildren().addAll(villainView1, villainView2);

        boardViews = new CardView[5];
        for (int i = 0; i < 5; i++) {
            boardViews[i] = new CardView();
            boardCardsBox.getChildren().add(boardViews[i]);
        }

        // Prévisualisation en temps réel à chaque frappe clavier
        heroCard1Field.textProperty().addListener((obs, o, val) -> previewCard(heroView1, val));
        heroCard2Field.textProperty().addListener((obs, o, val) -> previewCard(heroView2, val));
        villainCard1Field.textProperty().addListener((obs, o, val) -> previewCard(villainView1, val));
        villainCard2Field.textProperty().addListener((obs, o, val) -> previewCard(villainView2, val));

        TextField[] boardFields = { board1Field, board2Field, board3Field, board4Field, board5Field };
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            boardFields[i].textProperty().addListener((obs, o, val) -> previewCard(boardViews[idx], val));
        }

        // Bascule entre mode "main exacte" et "range"
        toggleHandMode.selectedProperty().addListener((obs, o, selected) -> {
            if (!selected && !toggleRangeMode.isSelected()) { toggleRangeMode.setSelected(true); return; }
            villainHandBox.setVisible(selected);
            villainHandBox.setManaged(selected);
            villainRangeBox.setVisible(!selected);
            villainRangeBox.setManaged(!selected);
            updateToggleStyles();
        });
        toggleRangeMode.selectedProperty().addListener((obs, o, selected) -> {
            if (!selected && !toggleHandMode.isSelected()) { toggleHandMode.setSelected(true); return; }
            villainHandBox.setVisible(!selected);
            villainHandBox.setManaged(!selected);
            villainRangeBox.setVisible(selected);
            villainRangeBox.setManaged(selected);
            updateToggleStyles();
        });
    }

    private void previewCard(CardView view, String notation) {
        if (notation.length() == 2) {
            try { view.showCard(Card.fromString(notation)); errorLabel.setText(""); }
            catch (IllegalArgumentException e) { view.showEmpty(); }
        } else {
            view.showEmpty();
        }
    }

    @FXML
    private void onCalculate() {
        errorLabel.setText("");
        try {
            Card c1 = parseCard(heroCard1Field.getText(), "Carte 1 héro");
            Card c2 = parseCard(heroCard2Field.getText(), "Carte 2 héro");
            if (c1.equals(c2)) { errorLabel.setText("Les deux cartes doivent être différentes."); return; }

            if (toggleHandMode.isSelected()) {
                Card v1 = parseCard(villainCard1Field.getText(), "Carte 1 villain");
                Card v2 = parseCard(villainCard2Field.getText(), "Carte 2 villain");
                if (v1.equals(v2)) { errorLabel.setText("Les deux cartes du villain doivent être différentes."); return; }
                // TODO : calcul Monte Carlo main vs main
            } else {
                String range = rangeField.getText();
                if (range == null || range.isBlank()) { errorLabel.setText("Range adverse vide."); return; }
                // TODO : calcul Monte Carlo main vs range
            }

            errorLabel.setText("Calcul d'équité non encore implémenté.");
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private Card parseCard(String notation, String fieldName) {
        if (notation == null || notation.isBlank())
            throw new IllegalArgumentException(fieldName + " : notation vide.");
        try {
            return Card.fromString(notation.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " invalide : « " + notation + " »");
        }
    }

    private void updateToggleStyles() {
        String active   = "-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 12px;" +
                          "-fx-background-radius: 4px 0 0 4px; -fx-border-color: #8e44ad; -fx-border-radius: 4px 0 0 4px;";
        String inactive = "-fx-background-color: #0a2e18; -fx-text-fill: #a09070; -fx-font-size: 12px;" +
                          "-fx-background-radius: 0 4px 4px 0; -fx-border-color: #2a7a4e; -fx-border-radius: 0 4px 4px 0;";
        if (toggleHandMode.isSelected()) {
            toggleHandMode.setStyle(active);
            toggleRangeMode.setStyle(inactive);
        } else {
            toggleHandMode.setStyle(inactive.replace("0 4px 4px 0", "4px 0 0 4px"));
            toggleRangeMode.setStyle(active.replace("4px 0 0 4px", "0 4px 4px 0"));
        }
    }

    @FXML private void onBackToMenu() { MainController.goToMenu(); }
}
