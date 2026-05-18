package pokerbrain.model.history;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.Player;

import java.time.Instant;
import java.util.*;

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

   private Hand(Builder b) {
      if (b.handId != null ){
         this.handId = b.handId;
      } else {
         this.handId = UUID.randomUUID().toString();
      }
      this.heroId = b.heroId;
      this.tableName = b.tableName;
      this.smallBlind = b.smallBlind;
      this.bigBlind = b.bigBlind;
      if(b.date != null){
         this.date = b.date;
      } else {
         this.date = Instant.now();
      }
      this.players = Collections.unmodifiableList(new ArrayList<>(b.players));
      this.initialStates = Collections.unmodifiableList(new ArrayList<>(b.initialStates));
      this.board = Collections.unmodifiableList(new ArrayList<>(b.board));
      this.actions = Collections.unmodifiableList(new ArrayList<>(b.actions));
      this.gameStates = Collections.unmodifiableList(new ArrayList<>(b.gameStates));
      this.winnings = Collections.unmodifiableMap(new LinkedHashMap<>(b.winnings));
    }

   public String getHandId(){ return handId; }
   public String getHeroId(){ return heroId; }
   public String getTableName() { return tableName; }
   public int getSmallBlind(){ return smallBlind; }
   public int getBigBlind(){ return bigBlind; }
   public Instant getDate(){ return date; }
   public List<Player> getPlayers(){ return players; }
   public List<PlayerState> getInitialStates(){ return initialStates; }
   public List<Card> getBoard(){ return board; }
   public List<Action> getActions(){ return actions; }
   public List<GameState> getGameStates() { return gameStates; }
   public Map<String, Integer> getWinnings(){ return winnings; }
   public int getHeroWinnings() {return winnings.getOrDefault(heroId, 0);}

   public static final class Builder {
      private String handId;
      private String heroId;
      private String tableName = "Table PokerBrain";
      private int smallBlind = 1;
      private int bigBlind = 2;
      private Instant date;
      private List<Player> players = new ArrayList<>();
      private List<PlayerState> initialStates = new ArrayList<>();
      private List<Card> board = new ArrayList<>();
      private List<Action> actions = new ArrayList<>();
      private List<GameState> gameStates = new ArrayList<>();
      private Map<String, Integer> winnings = new LinkedHashMap<>();

      public Builder handId(String tempo){
         this.handId = tempo;
         return this;
      }
      public Builder heroId(String tempo){
         this.heroId = tempo;
         return this; 
      }
      public Builder tableName(String tempo){
         this.tableName = tempo;
         return this;
      }
      public Builder smallBlind(int tempo){ 
         this.smallBlind = tempo;
         return this;
      }
      public Builder bigBlind(int tempo){
         this.bigBlind = tempo;
         return this;
      }
      public Builder date(Instant tempo){
         this.date = tempo;
         return this;
      }
      public Builder players(List<Player> tempo){
         this.players = tempo;
         return this;
      }
      public Builder initialStates(List<PlayerState> tempo) {
         this.initialStates = tempo;
         return this;
      }
      public Builder board(List<Card> tempo){
         this.board = tempo;
         return this;
      }

      public Builder addAction(Action actionTemp) {
         this.actions.add(actionTemp);
         return this;
      }

      public Builder addGameState(GameState gameStateTemp) {
         this.gameStates.add(gameStateTemp);
         return this;
      }

      public Builder addWinning(String playerIdTemp, int amountTemp) {
         this.winnings.merge(playerIdTemp, amountTemp, Integer::sum);
         return this;
      }

      public Hand build() {
         return new Hand(this);
      }
    }
}

