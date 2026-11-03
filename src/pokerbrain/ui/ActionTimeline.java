package pokerbrain.ui;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Historique des actions de la main, groupé par street. Chaque entrée porte son index dans
 * `setUserData()` pour que le replay puisse la surligner.
 *
 * Habillage : classes `timeline__*` (components.css). Le surlignage ajoute et retire une
 * classe au lieu de réécrire deux chaînes de style quasi identiques.
 */
public class ActionTimeline extends VBox {

    private final VBox entriesBox   = new VBox(2);
    private String     currentRound = "";  // dernière street affichée, pour ne pas répéter l'en-tête
    private int        entryIndex   = 0;

    public ActionTimeline() {
        getStyleClass().add("timeline");
        ScrollPane scroll = new ScrollPane(entriesBox);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(160);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    // Insère un séparateur de street automatiquement lors d'un changement de round
    public void addAction(String round, String player, String action) {
        if (!round.equals(currentRound)) {
            currentRound = round;
            Label header = new Label(round);
            header.getStyleClass().add("timeline__round");
            entriesBox.getChildren().add(header);
        }
        Label entry = new Label(player + " · " + action);
        entry.getStyleClass().add("timeline__entry");
        entry.setUserData(entryIndex++);
        entriesBox.getChildren().add(entry);
    }

    public void highlightEntry(int index) {
        for (var node : entriesBox.getChildren()) {
            if (node instanceof Label l && l.getUserData() instanceof Integer idx) {
                l.getStyleClass().remove("timeline__entry--current");
                if (idx == index) l.getStyleClass().add("timeline__entry--current");
            }
        }
    }

    public void clear() {
        entriesBox.getChildren().clear();
        currentRound = "";
        entryIndex   = 0;
    }
}
