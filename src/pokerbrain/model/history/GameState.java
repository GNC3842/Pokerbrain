package pokerbrain.model.history;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.BettingRound;
import pokerbrain.model.game.SidePot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Snapshot immuable de l'état complet du jeu après une action.
 *
 * Contient : round courant, board, pot total, side pots, état de chaque joueur,joueur courant, niveau de mise, et l'action qui a produit cet état.
 * Construit via me builder comme pour Hand.
 */

public final class GameState{
  private final BettingRound round;
  private final List<Card> board;
  private final int totalPot;
  private final List<SidePot> sidePots;
  private final List<PlayerState> players;
  private final String currentPlayerId;
  private final int currentBetLevel;
  private final Action lastAction;

  private GameState(Builder builder){
    this.round = builder.round;
    this.board = Collections.unmodifiableList(new ArrayList<>(builder.board));
    this.totalPot = builder.totalPot;
    if (builder.sidePots == null){
            this.sidePots = Collections.emptyList();
        } else {
            this.sidePots = Collections.unmodifiableList(new ArrayList<>(builder.sidePots));
        }
    this.players = Collections.unmodifiableList(new ArrayList<>(builder.players));
    this.currentPlayerId = builder.currentPlayerId;
    this.currentBetLevel = builder.currentBetLevel;
    this.lastAction = builder.lastAction; 
  }
  
  public BettingRound getRound(){ return round; }
  public List<Card> getBoard(){ return board; }
  public int getTotalPot(){ return totalPot; }
  public List<SidePot> getSidePots(){ return sidePots; }
  public List<PlayerState> getPlayers() { return players; }
  public int getCurrentBetLevel(){ return currentBetLevel; }
  public static final class Builder {
    private BettingRound round;
    private List<Card> board = new ArrayList<>();
    private int totalPot;
    private List<SidePot> sidePots;
    private List<PlayerState> players = new ArrayList<>();
    private String currentPlayerId;
    private int currentBetLevel;
    private Action lastAction;

    public Builder round(BettingRound tempo){ 
      this.round = tempo; 
      return this; 
    }
    public Builder board(List<Card> tempo){ 
      if(tempo != null){
        this.board = tempo;
      } else {
        this.board = new ArrayList<>();
      }
      return this; 
    }
    public Builder totalPot(int tempo){ 
      this.totalPot = tempo; 
      return this; 
    }
    public Builder sidePots(List<SidePot> tempo){ 
      this.sidePots = tempo; 
      return this; 
    }
    public Builder players(List<PlayerState> tempo){ 
      this.players = tempo; 
      return this; 
    }
    public Builder currentPlayerId(String tempo){ 
      this.currentPlayerId = tempo; 
      return this; 
    }
    public Builder currentBetLevel(int tempo){ 
      this.currentBetLevel = tempo; 
      return this; 
    }
    public Builder lastAction(Action tempo){ 
      this.lastAction = tempo; 
      return this; 
    }

    public GameState build() { 
      return new GameState(this); 
    }
  }
}