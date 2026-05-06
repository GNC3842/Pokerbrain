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
  
}