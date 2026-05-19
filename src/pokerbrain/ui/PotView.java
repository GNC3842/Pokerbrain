package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// Affiche le pot principal et les éventuels side pots (joueurs all-in).
public class PotView extends VBox {

    private final Label mainPotLabel = new Label("POT : 0 ");
    private final HBox  sidePotsBox  = new HBox(10);

    public PotView() {
        setAlignment(Pos.CENTER);
        setSpacing(4);
        mainPotLabel.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 16px; -fx-font-weight: bold;");
        sidePotsBox.setAlignment(Pos.CENTER);
        getChildren().addAll(mainPotLabel, sidePotsBox);
    }

    public void setPot(double amount) {
    	mainPotLabel.setText("POT : " + (int) amount + " jetons");
    }

    // Side pot créé quand un joueur est all-in
    public void addSidePot(double amount, String players) {
    	Label label = new Label("Side : " + (int) amount + " jetons (" + players + ")");
        label.setStyle("-fx-text-fill: #a09070; -fx-font-size: 11px;");
        sidePotsBox.getChildren().add(label);
    }

    public void clearSidePots() { sidePotsBox.getChildren().clear(); }

    public void reset() { clearSidePots(); setPot(0); }
}
