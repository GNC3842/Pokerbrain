package pokerbrain.model.history;

import pokerbrain.Debug;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.Player;
import pokerbrain.model.game.PlayerStatus;
import pokerbrain.model.game.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot immuable de l'état d'un joueur à un instant T
 *
 * Utilisé par GameState pour capturer l'état complet de la table après chaque action
 
 * Méthode de travaill: PlayerState.of(Player) copie tous les champs 
 */

public class PlayerState{
  private final String id;
  private final String name;
  private final int stack;
  private final int currentBet;
  private final PlayerStatus status;
  private final Position position;
  private final int seat;
  private final List<Card> holeCards;

  private PlayerState(String id, String name, int stack, int currentBet,PlayerStatus status, Position position, int seat,List<Card> holeCards) {
    this.id = id;
    this.name = name;
    this.stack = stack;
    this.currentBet = currentBet;
    this.status = status;
    this.position = position;
    this.seat = seat;
    this.holeCards = Collections.unmodifiableList(new ArrayList<>(holeCards));
  }

  //Méthode pricnpale : crée un snapshot depuis un Player mutable
  public static PlayerState of(Player joueur) {
    Debug.log(() -> "[PlayerState] Snapshot créé pour " + joueur.getName() + " | Stack: " + joueur.getStack() + " | Cartes: " + joueur.getHoleCards());
    return new PlayerState(
            joueur.getId(), joueur.getName(), joueur.getStack(), joueur.getCurrentBet(),
            joueur.getStatus(), joueur.getPosition(), joueur.getSeat(),
            joueur.getHoleCards()
    );
  }

  public String getId(){return id;}
  public String getName(){return name;}
  public int getStack(){return stack;}
  public int getCurrentbet(){return currentBet;}
  public PlayerStatus getStatus(){return status;}
  public Position getPosition(){return position;}
  public int getSeat(){return seat;}
  public List<Card> getHoleCards(){return holeCards;}

}