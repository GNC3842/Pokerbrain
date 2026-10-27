package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import pokerbrain.analyser.ActionAdvice;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.BettingRound;

import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Frise des actions de la main, groupée par street, chaque action portant un point coloré
 * selon son score. On voit d'un coup d'œil où la main a dérapé ; le survol donne l'aperçu
 * du conseil, le clic saute à l'étape.
 *
 * Remplace l'`ActionTimeline` passive du replay, qui n'affichait que du texte et ne
 * montrait rien de l'analyse.
 */
public class ScoredTimeline extends VBox {

    private final VBox lignes = new VBox(2);
    private IntConsumer onSelect = i -> { };
    private int courant = -1;

    public ScoredTimeline() {
        getStyleClass().add("timeline");
        ScrollPane scroll = new ScrollPane(lignes);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(140);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    public void setOnSelect(IntConsumer onSelect) {
        this.onSelect = (onSelect == null) ? i -> { } : onSelect;
    }

    /**
     * (Re)construit la frise. Appelée une fois à l'ouverture de la main, puis à nouveau
     * quand l'analyse de fond se termine : les points n'ont leur couleur qu'à ce
     * moment-là, et c'est normal — l'analyse prend quelques centaines de millisecondes.
     */
    public void build(List<Action> actions, Map<Integer, ActionAdvice> conseils, String heroId) {
        lignes.getChildren().clear();
        if (actions == null) return;

        BettingRound streetAffichee = null;
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (action.getRound() != streetAffichee) {
                streetAffichee = action.getRound();
                Label entete = new Label(streetAffichee.getLabel());
                entete.getStyleClass().add("timeline__round");
                lignes.getChildren().add(entete);
            }

            ActionAdvice conseil = (conseils == null) ? null : conseils.get(i);
            boolean estHero = heroId != null && heroId.equals(action.getPlayer().getId());

            Circle point = new Circle(4.5);
            point.getStyleClass().addAll("dot", classePour(conseil, estHero));

            Label texte = new Label(action.getPlayer().getName() + " · " + decrire(action));
            texte.getStyleClass().add("timeline__entry");

            HBox ligne = new HBox(8, point, texte);
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.getStyleClass().add("timeline__row");
            ligne.setUserData(i);

            if (conseil != null && conseil.isAnalysable()) {
                Tooltip apercu = new Tooltip(conseil.getScore() + "/100 — " + conseil.getReasoning());
                apercu.setWrapText(true);
                apercu.setMaxWidth(360);
                apercu.setShowDelay(Duration.millis(250));
                Tooltip.install(ligne, apercu);
            }

            final int index = i;
            ligne.setOnMouseClicked(e -> onSelect.accept(index));
            lignes.getChildren().add(ligne);
        }
        setCurrent(courant);
    }

    public void setCurrent(int index) {
        this.courant = index;
        for (var noeud : lignes.getChildren()) {
            if (noeud instanceof HBox ligne && ligne.getUserData() instanceof Integer i) {
                ligne.getStyleClass().remove("timeline__row--current");
                if (i == index) ligne.getStyleClass().add("timeline__row--current");
            }
        }
    }

    public void clear() {
        lignes.getChildren().clear();
        courant = -1;
    }

    //Le point n'est coloré que pour les actions du héro : les autres n'ont pas de score
    private static String classePour(ActionAdvice conseil, boolean estHero) {
        if (!estHero) return "dot--other";
        if (conseil == null || !conseil.isAnalysable()) return "dot--unknown";
        if (conseil.getScore() >= 80) return "dot--good";
        if (conseil.getScore() >= 50) return "dot--mid";
        return "dot--bad";
    }

    private static String decrire(Action action) {
        String nom = switch (action.getType()) {
            case FOLD   -> "se couche";
            case CHECK  -> "parole";
            case CALL   -> "suit";
            case BET    -> "mise";
            case RAISE  -> "relance";
            case ALL_IN -> "tapis";
        };
        return action.getType().involvesMoney() && action.getAmount() > 0
            ? nom + " " + action.getAmount()
            : nom;
    }
}
