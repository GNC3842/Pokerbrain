package pokerbrain.ui;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.engine.EquityCalculator;
import pokerbrain.model.core.Card;

import java.util.ArrayList;
import java.util.List;

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

    @FXML private Button calculateButton;

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
        });
        toggleRangeMode.selectedProperty().addListener((obs, o, selected) -> {
            if (!selected && !toggleHandMode.isSelected()) { toggleHandMode.setSelected(true); return; }
            villainHandBox.setVisible(!selected);
            villainHandBox.setManaged(!selected);
            villainRangeBox.setVisible(selected);
            villainRangeBox.setManaged(selected);
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

        if (toggleRangeMode.isSelected()) {
            errorLabel.setText("Le mode range n'est pas encore disponible.");
            return;
        }

        try {
            Card c1 = parseCard(heroCard1Field.getText(), "Carte 1 héro");
            Card c2 = parseCard(heroCard2Field.getText(), "Carte 2 héro");
            if (c1.equals(c2)) { errorLabel.setText("Les deux cartes du héro doivent être différentes."); return; }

            Card v1 = parseCard(villainCard1Field.getText(), "Carte 1 villain");
            Card v2 = parseCard(villainCard2Field.getText(), "Carte 2 villain");
            if (v1.equals(v2)) { errorLabel.setText("Les deux cartes du villain doivent être différentes."); return; }

            List<Card> heroHand    = List.of(c1, c2);
            List<Card> villainHand = List.of(v1, v2);
            List<Card> board       = parseBoard();

            // Vérifier qu'aucune carte n'apparaît deux fois
            List<Card> all = new ArrayList<>(heroHand);
            all.addAll(villainHand);
            all.addAll(board);
            for (int i = 0; i < all.size(); i++)
                for (int j = i + 1; j < all.size(); j++)
                    if (all.get(i).equals(all.get(j)))
                        throw new IllegalArgumentException("Carte en double : " + all.get(i));

            calculateButton.setDisable(true);
            calculateButton.setText("Calcul en cours…");
            equityChart.reset();

            Task<double[]> task = new Task<>() {
                @Override protected double[] call() {
                    return EquityCalculator.calculate(heroHand, villainHand, board);
                }
            };
            task.setOnSucceeded(e -> {
                double[] r       = task.getValue();
                double heroEq    = r[0] + r[2] / 2.0;
                double villainEq = r[1] + r[2] / 2.0;
                heroEquityLabel.setText(String.format("%.1f%%", heroEq));
                villainEquityLabel.setText(String.format("%.1f%%", villainEq));
                tieLabel.setText(String.format("%.1f%%", r[2]));
                equityChart.setHeroVillain(heroEq);
                calculateButton.setDisable(false);
                calculateButton.setText("Calculer l'équité");
            });
            task.setOnFailed(e -> {
                errorLabel.setText("Erreur : " + task.getException().getMessage());
                calculateButton.setDisable(false);
                calculateButton.setText("Calculer l'équité");
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();

        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private List<Card> parseBoard() {
        TextField[] fields = { board1Field, board2Field, board3Field, board4Field, board5Field };
        List<Card> board = new ArrayList<>();
        boolean gap = false;
        for (TextField f : fields) {
            String txt = f.getText().trim();
            if (txt.isEmpty()) { gap = true; continue; }
            if (gap) throw new IllegalArgumentException("Board incomplet : remplissez les cases dans l'ordre.");
            board.add(parseCard(txt, "Board"));
        }
        if (board.size() == 1 || board.size() == 2)
            throw new IllegalArgumentException("Board invalide : entrez 0, 3, 4 ou 5 cartes.");
        return board;
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
