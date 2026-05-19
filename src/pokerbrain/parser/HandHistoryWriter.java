package pokerbrain.parser;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.BettingRound;
import pokerbrain.model.history.Hand;
import pokerbrain.model.history.PlayerState;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

/*
* Va convertir un objet Hand en texte que le parser saura relire
* On l'utilise avec HandHistoryWriter.appendToFile(hand,path)
* Et la conversion en String se fait avec serialize(hand)
*/

/**
 * FORMAT DU PARSING (sections séparées par leur nom en majuscules) :
 *
 *   HEADER
 *   HandId: hand-001
 *   Date: 2025-04-01T14:30:00Z
 *   Table: Salle VIP
 *   SmallBlind: 1
 *   BigBlind: 2
 *   Hero: p1
 *
 *   PLAYERS
 *   Player p1: Alice - Seat 1 - Stack 200 - Position BTN
 *   Player p2: Bob   - Seat 2 - Stack 150 - Position SB
 *
 *   HOLECARDS
 *   p1: Ah Kh
 *
 *   PREFLOP
 *   Alice: RAISE 5
 *   Bob:   FOLD
 *
 *   FLOP
 *   Board: Th 7h 2c
 *   ...
 *
 *   SHOWDOWN
 *   Winner: p1
 *   Amount: 16
 *   ---
 *   (main suivante...)
 */

public final class HandHistoryWriter{

  //Ici la fonction de conversion principale avec un StringBuilder pour faciliter le travail comme le texte est un peu long
  public static String serialize(Hand hand){
    System.out.println("[Writer] Sérialisation de la main ID : " + hand.getHandId() + " | Date : " + hand.getDate()); //log
    StringBuilder build = new StringBuilder(); //On l'instancie

    //Portion du header
    build.append("HEADER\n");
    build.append("HandId: ").append(hand.getHandId()).append('\n');
    if( hand.getDate() != null){
      build.append("Date: ").append(hand.getDate().toString()).append('\n');
    }
    if (hand.getTableName() != null){
      build.append("Table: ").append(hand.getTableName()).append('\n');
    }
    build.append("SmallBlind: ").append(hand.getSmallBlind()).append('\n');
    build.append("BigBlind: ").append(hand.getBigBlind()).append('\n');
    if (hand.getHeroId() != null) {
      build.append("Hero: ").append(hand.getHeroId()).append('\n');
    }

    //Portion avec les joeurus
    build.append('\n');
    build.append("PLAYERS\n");
    List<PlayerState> states = hand.getInitialStates();
    if (states != null && !states.isEmpty()) {
      for (PlayerState joueur : states) {
        build.append("Player ").append(joueur.getId()).append(": ");
        build.append(joueur.getName()).append(" - ");
        build.append("Seat ").append(joueur.getSeat()).append(" - ");
        build.append("Stack ").append(joueur.getStack()).append(" - ");
        if(joueur.getPosition() != null){
          build.append("Position ").append(joueur.getPosition().name());
        } else {
          build.append("Position").append("BTN");
        }
        build.append('\n');
      }
    }

    //Portion des cartes des joeurus
    build.append('\n');
    build.append("HOLECARDS\n");
    if (states != null) {
      for (PlayerState joueur : states) {
        List<Card> hole = joueur.getHoleCards();
        if (hole != null && hole.size() == 2) {
            build.append(joueur.getId()).append(": ");
            build.append(hole.get(0).toString()).append(' ');
            build.append(hole.get(1).toString()).append('\n');
        }
      }
    }

    //Rergarder les actions par ROUND
    List<Card> board = hand.getBoard();
    List<Action> actions = hand.getActions();

    writeActionsForRound(build, actions, BettingRound.PREFLOP, board, null);
    writeActionsForRound(build, actions, BettingRound.FLOP, board, flopCards(board));
    writeActionsForRound(build, actions, BettingRound.TURN, board, turnCard(board));
    writeActionsForRound(build, actions, BettingRound.RIVER, board, riverCard(board));

    //>On regarde finalement le SHOWDOWN
    Map<String, Integer> winnings = hand.getWinnings();
    if (winnings != null && !winnings.isEmpty()) {
      build.append('\n');
      build.append("SHOWDOWN\n");
      for (Map.Entry<String, Integer> entry : winnings.entrySet()) {
        build.append("Winner: ").append(entry.getKey()).append('\n');
        build.append("Amount: ").append(entry.getValue()).append('\n');
      }
    }

    //plus qua renvoyer le string
    return build.toString();
  }


  //Methode auxiliaires
  private static void writeActionsForRound(StringBuilder sb, List<Action> actions, BettingRound round, List<Card> board,String boardLine){
    //On vérifie si'l ya bien des actions
    boolean hasActions = false;
    for (Action a : actions){
      if(a.getRound() == round){
        hasActions = true;
        break;
      }
    }
    if (!hasActions && boardLine == null) {
      return;
    }
    sb.append('\n');
    sb.append(round.name()).append('\n');

    // Board (pour FLOP/TURN/RIVER)
    if (boardLine != null) {
      sb.append("Board: ").append(boardLine).append('\n');
    }

    //Actions
    for (Action a : actions) {
      if (a.getRound() != round) continue;
      sb.append(a.getPlayer().getName()).append(": ");
      sb.append(a.getType().name());
      if (a.getType().involvesMoney() && a.getAmount() > 0) {
        sb.append(' ').append(a.getAmount());
      }
      sb.append('\n');
    }
  }

  //FOnctions auxiliriares pour les cartes
  private static String flopCards(List<Card> board) {
    if (board == null || board.size() < 3) {
      return null;
    }
    return board.get(0) + " " + board.get(1) + " " + board.get(2);
  }

  private static String turnCard(List<Card> board) {
    if (board == null || board.size() < 4) {
      return null;
    }
    return board.get(3).toString();
  }

  private static String riverCard(List<Card> board) {
    if (board == null || board.size() < 5){ 
      return null;
    }
    return board.get(4).toString();
  }

  //Ajoute une main à la fin d'un fichier (les mains sont tjrs séparés par ---)
  public static void appendToFile(Hand hand, Path filePath) throws IOException {
    // Créer le dossier parent si nécessaire
    Path parent = filePath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    String content = serialize(hand);
    boolean fileExists = Files.exists(filePath) && Files.size(filePath) > 0;
    System.out.println("[Writer] Écriture dans : " + filePath.toAbsolutePath() + " (Fichier existant : " + fileExists + ")");//log

    try (BufferedWriter writer = Files.newBufferedWriter(filePath,StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      if (fileExists) {
        writer.write("\n---\n\n");
      }
      writer.write(content);
    }
  }

  //Possiblement ajouter une fonction pour écrire plusieurs mains directe dans un fichier
  public static void writeToFile(Hand hand, Path filePath)throws IOException {
    
  }
}