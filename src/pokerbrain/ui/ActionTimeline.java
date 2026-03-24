package pokerbrain.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

// Historique scrollable des actions de la main, organisé par street (Preflop, Flop, …).
// Chaque entry reçoit un index entier via setUserData() pour pouvoir être surlignée.
public class ActionTimeline extends VBox {

    private final VBox entriesBox   = new VBox(4);
    private String     currentRound = "";  // dernière street affichée, pour éviter les doublons de header
    private int        entryIndex   = 0;

    public ActionTimeline() {
        entriesBox.setPadding(new Insets(8));
        entriesBox.setStyle("-fx-background-color: #0a2e18;");

        ScrollPane scroll = new ScrollPane(entriesBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(340);
        scroll.setStyle("-fx-background-color: #0a2e18; -fx-background-radius: 6px; -fx-border-color: #1a5c32; -fx-border-radius: 6px;");

        getChildren().add(scroll);
    }

    // Insère un séparateur de street automatiquement lors d'un changement de round
    public void addAction(String round, String player, String action) {
        if (!round.equals(currentRound)) {
            currentRound = round;
            Label header = new Label("── " + round + " ──");
            header.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 8 0 2 0;");
            entriesBox.getChildren().add(header);
        }
        Label entry = new Label(player + " : " + action);
        entry.setStyle("-fx-text-fill: #c8d8c8; -fx-font-size: 12px; -fx-padding: 1 0 1 8;");
        entry.setUserData(entryIndex++); // index stocké pour highlight
        entriesBox.getChildren().add(entry);
    }

    public void highlightEntry(int index) {
        for (var node : entriesBox.getChildren()) {
            if (node instanceof Label l && l.getUserData() instanceof Integer idx) {
                l.setStyle(idx == index
                    ? "-fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 1 0 1 8; -fx-background-color: #1e5c32; -fx-background-radius: 3px;"
                    : "-fx-text-fill: #c8d8c8; -fx-font-size: 12px; -fx-padding: 1 0 1 8;");
            }
        }
    }

    public void clear() {
        entriesBox.getChildren().clear();
        currentRound = "";
        entryIndex   = 0;
    }
}
