package pokerbrain.model.history;

import pokerbrain.engine.PokerGame;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

  public void recordAction(Action action) {
    System.out.println("[HandRecord] Enregistrement Action : " + action.getPlayer().getName() + " -> " + action.getType() + " (" + action.getAmount() + ")");//log
    builder.addAction(action);
    List<PlayerState> playerStates = new ArrayList<>();
    for (Player player : game.getPlayers()) {
      playerStates.add(PlayerState.of(player));
    }

    String currentId = null;
    if (!game.isRoundComplete() && !game.isHandOver()) {
      try {
        currentId = game.getCurrentPlayer().getId();
      } catch (Exception e) {
        //Round terminé entre-temps
        System.out.println("[HandRecord] Exception levée: " + e.toString());//log
      }
    }

    GameState gs = new GameState.Builder()
            .round(game.getCurrentRound())
            .board(game.getBoard())
            .totalPot(game.getTotalPot())
            .sidePots(game.getSidePots())
            .players(playerStates)
            .currentPlayerId(currentId)
            .currentBetLevel(game.getCurrentBetLevel())
            .lastAction(action)
            .build();
        builder.addGameState(gs);
    System.out.println("[HandRecord] Snapshot créé - Round: " + game.getCurrentRound() + " | Pot Total: " + game.getTotalPot() + " | Joueurs actifs: " + playerStates.size());//log
  }

  public Hand buildHand(Map<Player, Integer> winnings) {
    builder.board(game.getBoard());
    if (winnings != null) {
      for (Map.Entry<Player, Integer> entry : winnings.entrySet()) {
        builder.addWinning(entry.getKey().getId(), entry.getValue());
      }
    }
    System.out.println("[HandRecord] Finalisation de la main : " + builder.getActions().size() + " actions et " + builder.getGameStates().size() + " snapshots enregistrés.");//log
    return builder.build();
  }

  //Finalise sans informations de gains au cas pù
  public Hand buildHand() {
    return buildHand(null);
  }
}