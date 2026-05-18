package pokerbrain.model.history;

import java.util.List;
import java.util.Collections;

/**
 * Permet de naviguer en avant/arrière dans les GameStates d'une main pour rejouer l'action coup par coup dans l'interface de replay
 */

public class GameSnapshot{
  private final Hand hand;
  private final List<GameState> states;
  private int index;

  //Constrcuteur
  public GameSnapshot(Hand hand){
    this.hand = hand;
    this.states = hand.getGameStates();
    this.index = -1;
    System.out.println("[GameSnapshot] Replay initialisé pour la main " + hand.getHandId() + " | " + states.size() + " états disponibles.");//log
  }

  public boolean next(){
    if(index < states.size() -1){
      System.out.println("[GameSnapshot] Navigation AVANT : index " + index + "/" + (states.size()-1));//log
      index++;
      return true;
    }
    return false;
  }

  public boolean previous(){
    if(index > 0){
      System.out.println("[GameSnapshot] Navigation ARRIÈRE : index " + index + "/" + (states.size()-1));//log
      index --;
      return true;
    }
    return false;
  }

  public void goTo(int index){
    if (index < 0 || index >= states.size()) {
        throw new IndexOutOfBoundsException("Index " + index + " hors bornes [0, " + (states.size()-1) + "]");
    }
    this.index = index;
  }

  public void goToStart(){
    index = 0;
  }

  public void goToEnd(){
    index = states.size() -1;
  }

  public GameState current(){
    if (index < 0 || index >= states.size()) {
      throw new IllegalStateException("Pas d'état courant (index=" + index + ")");
    }
    return states.get(index);
  }

  public boolean hasNext(){
    return index < states.size() - 1; 
  }

  public boolean hasPrevious(){
    return index > 0;
  }

  //Getters
  public int getCursor(){
    return index;
  }

  public int getTotal(){
    return states.size();
  }

  public Hand getHand(){
    return hand;
  }

  public List<GameState> getAllStates() { 
    return Collections.unmodifiableList(states); 
  }
}