package pokerbrain.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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

    @FXML private HBox heroCardsBox;
    @FXML private HBox boardCardsBox;
    @FXML private VBox equityChartContainer;

    @FXML private Label heroEquityLabel;
    @FXML private Label villainEquityLabel;
    @FXML private Label tieLabel;
    @FXML private Label errorLabel;

    private EquityChart equityChart;
    private CardView    heroView1, heroView2;
    private CardView[]  boardViews;

    @FXML
    public void initialize() {
        equityChart = new EquityChart();
        equityChartContainer.getChildren().add(equityChart);

        heroView1 = new CardView();
        heroView2 = new CardView();
        heroCardsBox.getChildren().addAll(heroView1, heroView2);

        boardViews = new CardView[5];
        for (int i = 0; i < 5; i++) {
            boardViews[i] = new CardView();
            boardCardsBox.getChildren().add(boardViews[i]);
        }

        // Prévisualisation en temps réel à chaque frappe clavier
        heroCard1Field.textProperty().addListener((obs, o, val) -> previewCard(heroView1, val));
        heroCard2Field.textProperty().addListener((obs, o, val) -> previewCard(heroView2, val));

        TextField[] boardFields = { board1Field, board2Field, board3Field, board4Field, board5Field };
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            boardFields[i].textProperty().addListener((obs, o, val) -> previewCard(boardViews[idx], val));
        }
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
            // TODO : calcul Monte Carlo
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

    @FXML private void onBackToMenu() { MainController.goToMenu(); }
}
