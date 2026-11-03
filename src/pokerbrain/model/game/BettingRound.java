package pokerbrain.model.game;

public enum BettingRound {
    PREFLOP("Préflop"),
    FLOP("Flop"),
    TURN("Turn"),
    RIVER("River");

    private final String label;

    BettingRound(String label) {
        this.label = label;
    }

    //Nom affichable : l'interface est en français, `name()` n'y a pas sa place
    public String getLabel() {
        return this.label;
    }
}
