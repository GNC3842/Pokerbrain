package pokerbrain.model.history;

import pokerbrain.engine.PokerGame;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.Player;

/**
 * Enregistreur de main en temps réel — bridge entre PokerGame et Hand.Builder.
 *
 * Branché sur PokerGame, il capture chaque action et chaque GameState au fil du jeu et buildHand() produit l'objet immuable à la fin
 *
 * USAGE :
 *   HandRecord recorder = new HandRecord(game, "hero-id");
 *   // ... après chaque action dans le jeu ...
 *   recorder.recordAction(action);
 *   // ... à la fin de la main ...
 *   Hand hand = recorder.buildHand(winnings);
 */

public final class HandRecord{
  private final Hand.Builder builder;
  private final PokerGame game;

  //Constructeur
  public HandRecord(PokerGame game, String heroId) {
    Objects.requireNonNull(game, "Game ne peut pas être null");
    this.game = game;
    this.builder = new Hand.Builder().heroId(heroId).smallBlind(game.getSmallBlind()).bigBlind(game.getBigBlind());

    // Snapshot initial des joueurs
    List<PlayerState> initStates = new ArrayList<>();
    for (Player player : game.getPlayers()) {
      initStates.add(PlayerState.of(player));
    }
    this.builder.initialStates(initStates);
    this.builder.players(new ArrayList<>(game.getPlayers()));
  }
}