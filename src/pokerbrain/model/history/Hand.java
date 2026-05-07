package pokerbrain.model.history;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.Player;



/**
 *Enregistrement immuable d'une main complète jouée
 *
 *Contient toutes les métadonnées (blindes, table, date), les joueurs et leurs cartes initiales, la séquence d'actions, les snapshots GameState,et les gains finaux

    On utilise un builder comme immuable
 USAGE :
 *   Hand hand = new Hand.Builder()
 *       .handId("hand-001")
 *       .heroId("hero")
 *       .smallBlind(1).bigBlind(2)
 *       .addAction(action1)
 *       .addGameState(gs1)
 *       .addWinning("hero", 15)
 *       .build();
 */

public final class Hand{
   private final String handId;
   private final String heroId;
   private final String tableName;
   private final int smallBlind;
   private final int bigBlind;
   private final Instant date;
   private final List<Player> players;
   private final List<PlayerState> initialStates;
   private final List<Card> board;
   private final List<Action> actions;
   private final List<GameState> gameStates;
   private final Map<String, Integer> winnings; // on associe le playerId avec la somme gagnée
   
}

