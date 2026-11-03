package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pokerbrain.analyser.ActionAdvice;
import pokerbrain.model.game.ActionType;

/**
 * Conseil sur une action, en carton structuré.
 *
 * Remplace le `Label` unique qui empilait tout le conseil — score, contexte, raisonnement,
 * EV — dans une seule chaîne multiligne. Le détail structuré produit par l'étape 2
 * (`equity`, `potOdds`, `evDelta`, `outs`, `spr`, `context`, `getAlternatives()`) est enfin
 * lisible : verdict en tête, jauge de score, tuiles de chiffres, puis le raisonnement.
 *
 * Les chiffres non calculés (-1 par convention d'`ActionAdvice`) ne produisent pas de
 * tuile : mieux vaut une tuile en moins qu'un « -1 » affiché comme une valeur.
 */
public class AdviceCard extends VBox {

    private final Label joue        = new Label();
    private final Label recommande  = new Label();
    private final Label scoreLabel  = new Label();
    private final Region jauge      = new Region();
    private final StackPane piste    = new StackPane();
    private final FlowPane tuiles   = new FlowPane(12, 8);
    private final Label raisonnement = new Label();
    private final Label alternatives = new Label();

    public AdviceCard() {
        getStyleClass().addAll("panel", "advice-card");
        setSpacing(10);

        joue.getStyleClass().add("verdict__played");
        recommande.getStyleClass().add("verdict__recommended");
        scoreLabel.getStyleClass().addAll("verdict__score", "num");

        Label fleche = new Label("→");
        fleche.getStyleClass().add("muted");
        HBox entete = new HBox(10, joue, fleche, recommande);
        entete.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(recommande, Priority.ALWAYS);

        HBox ligneScore = new HBox(10, entete, scoreLabel);
        HBox.setHgrow(entete, Priority.ALWAYS);
        ligneScore.setAlignment(Pos.CENTER_LEFT);

        jauge.getStyleClass().add("gauge__fill");
        piste.getStyleClass().add("gauge");
        piste.setAlignment(Pos.CENTER_LEFT);
        piste.getChildren().add(jauge);

        raisonnement.setWrapText(true);
        raisonnement.getStyleClass().add("advice");
        alternatives.getStyleClass().add("faint");

        getChildren().addAll(ligneScore, piste, tuiles, raisonnement, alternatives);
        clear();
    }

    //Message simple : analyse en cours, échec, ou action sans conseil
    public void setMessage(String texte, String severite) {
        clear();
        raisonnement.setText(texte);
        if (severite != null) raisonnement.getStyleClass().add(severite);
    }

    public void setAdvice(ActionAdvice advice) {
        clear();

        if (!advice.isAnalysable()) {
            raisonnement.setText(advice.getReasoning());
            return;
        }

        joue.setText("Joué : " + libelle(advice.getPlayed(), advice.getPlayedAmount()));
        recommande.setText("Conseillé : "
            + libelle(advice.getRecommended(), advice.getRecommendedAmount()));
        joue.setVisible(true);
        recommande.setVisible(true);

        String severite = advice.getScore() >= 80 ? "score--good"
                        : advice.getScore() >= 50 ? "score--mid"
                        : "score--bad";
        scoreLabel.setText(advice.getScore() + "/100");
        scoreLabel.getStyleClass().add(severite);
        piste.setVisible(true);
        jauge.getStyleClass().add(severite.replace("score--", "gauge__fill--"));
        // La jauge suit la largeur disponible : pas de pourcentage en pixels figés
        jauge.prefWidthProperty().bind(piste.widthProperty().multiply(advice.getScore() / 100.0));

        if (!advice.getContext().isEmpty()) {
            tuiles.getChildren().add(tuile("Contexte", advice.getContext()));
        }
        if (advice.getEquity() >= 0) {
            tuiles.getChildren().add(tuile("Équité", pourcent(advice.getEquity())));
        }
        if (advice.getPotOdds() >= 0) {
            tuiles.getChildren().add(tuile("Cote requise", pourcent(advice.getPotOdds())));
        }
        if (advice.getEvDelta() >= 0) {
            tuiles.getChildren().add(tuile("EV perdue",
                String.format("%.1f BB", advice.getEvDelta())));
        }
        if (advice.getOuts() > 0) {
            tuiles.getChildren().add(tuile("Outs", String.valueOf(advice.getOuts())));
        }
        if (advice.getSpr() >= 0) {
            tuiles.getChildren().add(tuile("SPR", String.format("%.1f", advice.getSpr())));
        }

        raisonnement.setText(advice.getReasoning());

        if (!advice.getAlternatives().isEmpty()) {
            StringBuilder ordre = new StringBuilder("Ordre de préférence : ");
            for (int i = 0; i < advice.getAlternatives().size(); i++) {
                if (i > 0) ordre.append(" > ");
                ordre.append(nom(advice.getAlternatives().get(i)));
            }
            alternatives.setText(ordre.toString());
        }
    }

    public void clear() {
        joue.setText("");
        recommande.setText("");
        joue.setVisible(false);
        recommande.setVisible(false);
        scoreLabel.setText("");
        scoreLabel.getStyleClass().setAll("verdict__score", "num");
        jauge.prefWidthProperty().unbind();
        jauge.setPrefWidth(0);
        jauge.getStyleClass().setAll("gauge__fill");
        piste.setVisible(false);
        tuiles.getChildren().clear();
        raisonnement.setText("");
        raisonnement.getStyleClass().setAll("advice");
        alternatives.setText("");
    }

    private static VBox tuile(String intitule, String valeur) {
        Label l = new Label(intitule);
        l.getStyleClass().add("tile__label");
        Label v = new Label(valeur);
        v.getStyleClass().addAll("tile__value", "num");
        v.setWrapText(true);
        VBox box = new VBox(1, l, v);
        box.getStyleClass().add("tile");
        return box;
    }

    private static String pourcent(double ratio) {
        return String.format("%.0f %%", ratio * 100);
    }

    private static String libelle(ActionType type, int montant) {
        if (type == null) return "—";
        return montant > 0 ? nom(type) + " " + montant : nom(type);
    }

    private static String nom(ActionType type) {
        return switch (type) {
            case FOLD   -> "se coucher";
            case CHECK  -> "parole";
            case CALL   -> "suivre";
            case BET    -> "miser";
            case RAISE  -> "relancer";
            case ALL_IN -> "tapis";
        };
    }
}
