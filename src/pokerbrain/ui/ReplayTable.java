package pokerbrain.ui;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Ellipse;
import javafx.util.Duration;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.PlayerStatus;
import pokerbrain.model.game.Position;
import pokerbrain.model.history.GameState;
import pokerbrain.model.history.Hand;
import pokerbrain.model.history.PlayerState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Table de replay : ovale de feutre, sièges placés en polaire autour de l'ovale, mises
 * posées devant les joueurs, bouton dealer, cartes communes au centre.
 *
 * Le replay alignait les joueurs dans une `HBox` : on ne voyait ni qui était en position,
 * ni où était le bouton, alors que c'est l'essentiel de la lecture d'une main. Les sièges
 * sont ici positionnés par `layoutChildren()`, donc la table suit la taille de la fenêtre.
 *
 * Le héro est toujours en bas (angle 90°), les autres joueurs se répartissent dans l'ordre
 * des sièges — la rotation relative est conservée, comme à une vraie table.
 */
public class ReplayTable extends Pane {

    private static final double RAYON_X = 0.40;
    private static final double RAYON_Y = 0.36;

    private final Ellipse feutre = new Ellipse();
    private final HBox boardRow  = new HBox(8);
    private final Label potLabel = new Label();
    private final VBox centre    = new VBox(10);

    private final Map<String, Siege> sieges = new LinkedHashMap<>();
    private final List<CardView> boardViews = new ArrayList<>();
    private int cartesVisibles = 0;

    /** Un siège : identité, cartes, tapis, et la mise posée devant lui. */
    private static final class Siege {
        final VBox boite = new VBox(3);
        final Label nom = new Label();
        final Label position = new Label();
        final Label tapis = new Label();
        final Label mise = new Label();
        final CardView carte1 = new CardView(CardView.Taille.SM);
        final CardView carte2 = new CardView(CardView.Taille.SM);

        Siege() {
            boite.getStyleClass().add("seat");
            boite.setAlignment(Pos.CENTER);
            nom.getStyleClass().add("seat__name");
            position.getStyleClass().add("seat__position");
            tapis.getStyleClass().addAll("seat__stack", "num");
            mise.getStyleClass().addAll("seat__bet", "num");
            HBox cartes = new HBox(4, carte1, carte2);
            cartes.setAlignment(Pos.CENTER);
            boite.getChildren().addAll(nom, position, cartes, tapis, mise);
        }
    }

    public ReplayTable() {
        getStyleClass().add("replay-table");
        setMinSize(420, 300);

        feutre.getStyleClass().add("felt-oval");

        potLabel.getStyleClass().addAll("pot__main", "num");
        for (int i = 0; i < 5; i++) {
            CardView vue = new CardView(CardView.Taille.MD);
            vue.showEmpty();
            boardViews.add(vue);
            boardRow.getChildren().add(vue);
        }
        boardRow.setAlignment(Pos.CENTER);
        centre.setAlignment(Pos.CENTER);
        centre.getChildren().addAll(boardRow, potLabel);

        getChildren().addAll(feutre, centre);
    }

    /** Installe les sièges d'une main. À appeler une fois par main rejouée. */
    public void setHand(Hand hand) {
        getChildren().removeIf(n -> n != feutre && n != centre);
        sieges.clear();
        cartesVisibles = 0;
        boardViews.forEach(CardView::showEmpty);

        if (hand == null) return;

        // Le héro d'abord : il occupe le siège du bas
        List<PlayerState> ordre = new ArrayList<>();
        for (PlayerState ps : hand.getInitialStates()) {
            if (ps.getId().equals(hand.getHeroId())) ordre.add(ps);
        }
        for (PlayerState ps : hand.getInitialStates()) {
            if (!ps.getId().equals(hand.getHeroId())) ordre.add(ps);
        }

        for (PlayerState ps : ordre) {
            Siege siege = new Siege();
            siege.nom.setText(ps.getName());
            siege.position.setText(ps.getPosition() == null ? "" : ps.getPosition().name());
            siege.position.setVisible(ps.getPosition() != null);
            siege.tapis.setText(ps.getStack() + " jetons");
            siege.mise.setText("");
            if (ps.getHoleCards().size() == 2) {
                siege.carte1.showCard(ps.getHoleCards().get(0));
                siege.carte2.showCard(ps.getHoleCards().get(1));
            } else {
                siege.carte1.showBack();
                siege.carte2.showBack();
            }
            if (ps.getPosition() == Position.BTN) {
                siege.boite.getStyleClass().add("seat--button");
            }
            sieges.put(ps.getId(), siege);
            getChildren().add(siege.boite);
        }
        requestLayout();
    }

    /** Met la table à l'état d'un instant : tapis, mises, joueurs couchés, pot, acteur. */
    public void showState(GameState etat, String acteurId) {
        if (etat != null) {
            potLabel.setText("Pot " + etat.getTotalPot());
            for (PlayerState ps : etat.getPlayers()) {
                Siege siege = sieges.get(ps.getId());
                if (siege == null) continue;
                siege.tapis.setText(ps.getStack() + " jetons");
                siege.mise.setText(ps.getCurrentbet() > 0 ? "mise " + ps.getCurrentbet() : "");
                boolean dehors = ps.getStatus() == PlayerStatus.FOLDED
                              || ps.getStatus() == PlayerStatus.OUT;
                siege.boite.setOpacity(dehors ? 0.35 : 1.0);
            }
        }
        for (var entree : sieges.entrySet()) {
            entree.getValue().boite.getStyleClass().remove("seat--active");
            if (entree.getKey().equals(acteurId)) {
                entree.getValue().boite.getStyleClass().add("seat--active");
            }
        }
    }

    /**
     * Affiche le board de l'instant. Les cartes nouvellement retournées sont animées
     * (écrasement puis redéploiement horizontal) : le passage d'une street se voit, au
     * lieu d'apparaître d'un coup entre deux clics.
     */
    public void setBoard(List<Card> board) {
        int cible = (board == null) ? 0 : board.size();
        for (int i = 0; i < boardViews.size(); i++) {
            CardView vue = boardViews.get(i);
            if (i < cible) {
                boolean nouvelle = i >= cartesVisibles;
                Card carte = board.get(i);
                if (nouvelle) retourner(vue, carte);
                else          vue.showCard(carte);
            } else {
                vue.showEmpty();
            }
        }
        cartesVisibles = cible;
    }

    private void retourner(CardView vue, Card carte) {
        vue.showBack();
        ScaleTransition fermeture = new ScaleTransition(Duration.millis(110), vue);
        fermeture.setFromX(1);
        fermeture.setToX(0.02);
        fermeture.setInterpolator(Interpolator.EASE_IN);
        fermeture.setOnFinished(e -> vue.showCard(carte));

        ScaleTransition ouverture = new ScaleTransition(Duration.millis(140), vue);
        ouverture.setFromX(0.02);
        ouverture.setToX(1);
        ouverture.setInterpolator(Interpolator.EASE_OUT);

        new SequentialTransition(fermeture, ouverture).play();
    }

    @Override
    protected void layoutChildren() {
        double l = getWidth(), h = getHeight();
        double cx = l / 2, cy = h / 2;
        double rx = l * RAYON_X, ry = h * RAYON_Y;

        feutre.setCenterX(cx);
        feutre.setCenterY(cy);
        feutre.setRadiusX(rx);
        feutre.setRadiusY(ry);

        layoutInArea(centre, cx - l / 4, cy - h / 8, l / 2, h / 4, 0,
                     javafx.geometry.HPos.CENTER, javafx.geometry.VPos.CENTER);

        int n = sieges.size();
        if (n == 0) return;
        int i = 0;
        for (Siege siege : sieges.values()) {
            Region boite = siege.boite;
            double angle = Math.PI / 2 + i * 2 * Math.PI / n; // héro en bas, sens horaire
            double x = cx + rx * 1.02 * Math.cos(angle);
            double y = cy + ry * 1.05 * Math.sin(angle);
            double bl = boite.prefWidth(-1), bh = boite.prefHeight(-1);
            boite.resizeRelocate(clamp(x - bl / 2, 0, l - bl), clamp(y - bh / 2, 0, h - bh), bl, bh);
            i++;
        }
    }

    //Garde le siège dans la table, même si la fenêtre est plus petite que le siège
    private static double clamp(double v, double min, double max) {
        if (max <= min) return min;
        return Math.max(min, Math.min(v, max));
    }
}
