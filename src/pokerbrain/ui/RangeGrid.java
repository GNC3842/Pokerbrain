package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.bot.RangeMap;
import pokerbrain.model.core.Rank;
import pokerbrain.model.game.ActionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Grille de range 13 × 13 interactive : paires sur la diagonale, assorties au-dessus,
 * dépareillées en dessous — la disposition standard.
 *
 * Active le mode « Range » du calculateur, mort depuis le début faute de vue :
 * `RangeMap` avait déjà la bonne structure et `MonteCarloEngine` sait calculer une équité
 * contre une range depuis l'étape 2.
 */
public class RangeGrid extends VBox {

    private final List<Rank> rangs = new ArrayList<>();
    private final Button[][] cellules = new Button[13][13];
    private final boolean[][] choisies = new boolean[13][13];
    private final Label compteur = new Label();

    public RangeGrid() {
        getStyleClass().add("range-grid");
        setSpacing(6);

        for (Rank r : Rank.values()) rangs.add(r);
        rangs.sort((a, b) -> b.getValue() - a.getValue()); // A en haut à gauche

        GridPane grille = new GridPane();
        grille.setHgap(2);
        grille.setVgap(2);

        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < 13; j++) {
                final int li = i, co = j;
                Button cellule = new Button(nomCombinaison(i, j));
                cellule.getStyleClass().addAll("range__cell", typeDe(i, j));
                cellule.setMaxWidth(Double.MAX_VALUE);
                cellule.setOnAction(e -> basculer(li, co));
                cellules[i][j] = cellule;
                grille.add(cellule, j, i);
            }
        }

        Button tout = new Button("Tout");
        tout.getStyleClass().addAll("btn", "btn--ghost");
        tout.setOnAction(e -> remplir(true));
        Button rien = new Button("Rien");
        rien.getStyleClass().addAll("btn", "btn--ghost");
        rien.setOnAction(e -> remplir(false));
        compteur.getStyleClass().add("muted");

        HBox barre = new HBox(8, tout, rien, compteur);
        barre.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(grille, barre);
        majCompteur();
    }

    /**
     * Range sélectionnée, prête pour `MonteCarloEngine` via `asVillainRange()`.
     * Les cellules retenues sont marquées CALL : le moteur ne regarde que « différent de
     * FOLD », la distinction call/raise n'a pas de sens pour un filtre d'équité.
     */
    public RangeMap toRangeMap() {
        RangeMap map = new RangeMap();
        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < 13; j++) {
                if (!choisies[i][j]) continue;
                Rank haut = rangs.get(Math.min(i, j));
                Rank bas  = rangs.get(Math.max(i, j));
                if (i == j)      map.setPair(haut, ActionType.CALL);
                else if (j > i)  map.setSuited(haut, bas, ActionType.CALL);
                else             map.setOffsuit(haut, bas, ActionType.CALL);
            }
        }
        return map;
    }

    public boolean isEmpty() {
        for (boolean[] ligne : choisies) {
            for (boolean c : ligne) if (c) return false;
        }
        return true;
    }

    private void basculer(int i, int j) {
        choisies[i][j] = !choisies[i][j];
        appliquer(i, j);
        majCompteur();
    }

    private void remplir(boolean valeur) {
        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < 13; j++) {
                choisies[i][j] = valeur;
                appliquer(i, j);
            }
        }
        majCompteur();
    }

    private void appliquer(int i, int j) {
        cellules[i][j].getStyleClass().remove("range__cell--on");
        if (choisies[i][j]) cellules[i][j].getStyleClass().add("range__cell--on");
    }

    /**
     * Part des combinaisons du jeu couverte par la sélection : une paire vaut 6
     * combinaisons, une assortie 4, une dépareillée 12, sur 1326 au total. C'est le
     * chiffre que les joueurs lisent, pas le nombre de cases cochées.
     */
    private void majCompteur() {
        int combinaisons = 0;
        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < 13; j++) {
                if (!choisies[i][j]) continue;
                combinaisons += (i == j) ? 6 : (j > i ? 4 : 12);
            }
        }
        compteur.setText(String.format("%.1f %% des mains", combinaisons * 100.0 / 1326));
    }

    private String nomCombinaison(int i, int j) {
        char haut = rangs.get(Math.min(i, j)).getSymbol();
        char bas  = rangs.get(Math.max(i, j)).getSymbol();
        if (i == j) return "" + haut + bas;
        return "" + haut + bas + (j > i ? "s" : "o");
    }

    private static String typeDe(int i, int j) {
        if (i == j) return "range__cell--pair";
        return j > i ? "range__cell--suited" : "range__cell--offsuit";
    }
}
