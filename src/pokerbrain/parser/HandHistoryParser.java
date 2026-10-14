package pokerbrain.parser;

import pokerbrain.Debug;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.GameState;
import pokerbrain.model.history.Hand;
import pokerbrain.model.history.PlayerState;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;


public final class HandHistoryParser{
  private static final List<String> SECTION_NAMES = Arrays.asList("HEADER", "PLAYERS", "HOLECARDS", "PREFLOP", "FLOP", "TURN", "RIVER", "SHOWDOWN");

  //Parse un fichier contenant une ou plusieurs mains séparées par --- 
  public static List<Hand> parseFile(Path path) throws IOException, ParseException {
    String content = new String(Files.readAllBytes(path));
    return parseMultiple(content);
  }

  //Parse une chaîne contenant une seule main
  public static Hand parseString(String content) throws ParseException {
    List<Hand> hands = parseMultiple(content);
    if (hands.isEmpty()) {
      throw new ParseException("Aucune main trouvée", 0, "");
    }
    return hands.get(0);
    }

  // Parse une chaîne contenant plusieurs mains séparées par ---
  public static List<Hand> parseMultiple(String content) throws ParseException {
    List<Hand> hands = new ArrayList<>();
    String[] blocks = content.split("\\n---\\s*\\n");
    Debug.log(() -> "[Parser] Blocs bruts détectés dans le fichier : " + blocks.length);
    for (String handReaded : blocks) {
      handReaded = handReaded.trim();
      if (handReaded.isEmpty()) {
        continue;
      }
      hands.add(parseSingleHand(handReaded));
    }
    return hands;
  }

  //Parsing d'une main
  private static Hand parseSingleHand(String blockDeJeu) throws ParseException {
    Debug.log(() -> "[Parser] Début du parsing d'un nouveau bloc de jeu...");
    String[] lines = blockDeJeu.split("\\n");

    Map<String, List<String>> sections = splitSections(lines);
    Debug.log(() -> "[Parser] Sections trouvées dans ce bloc : " + sections.keySet());

    Hand.Builder builder = new Hand.Builder();
    List<Player> players = new ArrayList<>();
    Map<String, Player> byId = new LinkedHashMap<>();
    Map<String, Player> byName = new LinkedHashMap<>();

    parseHeader(sections.getOrDefault("HEADER", Collections.emptyList()), builder);
    parsePlayers(sections.getOrDefault("PLAYERS", Collections.emptyList()), players, byId, byName);
    builder.players(players);

    parseHoleCards(sections.getOrDefault("HOLECARDS", Collections.emptyList()), byId);
    
    // Créer les initialStates APRÈS parseHoleCards pour capturer les cartes
    List<PlayerState> initStates = new ArrayList<>();
    for (Player p : players) {
        initStates.add(PlayerState.of(p));
    }
    builder.initialStates(initStates);

    // Board accumulé au fil des rounds
    List<Card> board = new ArrayList<>();

    if (sections.containsKey("PREFLOP")) {
      parseActions(sections.get("PREFLOP"), BettingRound.PREFLOP, byName, builder, board);
    }
    if (sections.containsKey("FLOP")) {
      parseActions(sections.get("FLOP"), BettingRound.FLOP,byName, builder, board);
    }
    if (sections.containsKey("TURN")) {
      parseActions(sections.get("TURN"), BettingRound.TURN,byName, builder, board);
    }
    if (sections.containsKey("RIVER")) {
      parseActions(sections.get("RIVER"), BettingRound.RIVER, byName, builder, board);
    }

    builder.board(board);

    parseShowdown(sections.getOrDefault("SHOWDOWN", Collections.emptyList()), builder);
    reconstructGameStates(builder.getActions(), players, board,
                          builder.getSmallBlind(), builder.getBigBlind(), builder);
    Debug.log(() -> "[Parser] Fin du parsing du bloc");
    return builder.build();
  }

  /**
   * Rejoue la main pour reconstruire un GameState complet par action.
   *
   * Pourquoi rejouer plutôt qu'écrire plus de choses dans le fichier : tapis, mises et
   * statuts se **déduisent** des tapis initiaux, des blindes et des montants d'action, tous
   * déjà sérialisés. Rien à ajouter au format, rien à resynchroniser entre writer et parser.
   *
   * Ce qui manquait rendait l'analyseur muet sur les mains relues du disque — c'est-à-dire
   * sur toutes : sans `players` ni `currentBetLevel`, `ActionContext.betLevel` valait 0,
   * `facingBet()` était toujours faux, et toute la branche « face à une mise » de
   * `HandAnalyser` ne s'exécutait jamais (SPR nul, multiway invisible, sizing à 0).
   *
   * Les `Player` passés ici sont les objets du parsing : ils sont mutés au fil du rejeu,
   * comme ils le sont en partie réelle. Les `initialStates` ont déjà été figés avant.
   */
  private static void reconstructGameStates(List<Action> actions, List<Player> players,
                                             List<Card> fullBoard,
                                             int sb, int bb, Hand.Builder builder) {
    int pot = postBlinds(players, sb, bb);
    int betLevel = 0;
    for (Player p : players) {
      betLevel = Math.max(betLevel, p.getCurrentBet());
    }

    BettingRound round = BettingRound.PREFLOP;
    for (Action action : actions) {
      // Changement de street : les mises de la street précédente sont déjà dans le pot
      if (action.getRound() != round) {
        round = action.getRound();
        for (Player p : players) p.setCurrentBet(0);
        betLevel = 0;
      }

      Player actor = action.getPlayer();
      if (action.getType() == ActionType.FOLD) {
        actor.setStatus(PlayerStatus.FOLDED);
      } else if (action.getType().involvesMoney()) {
        // getAmount() est un delta : ce que l'action ajoute au pot, pas un total à atteindre
        int delta = Math.min(action.getAmount(), actor.getStack());
        actor.removeFromStack(delta);
        actor.setCurrentBet(actor.getCurrentBet() + delta);
        betLevel = Math.max(betLevel, actor.getCurrentBet());
        pot += delta;
      }

      List<PlayerState> states = new ArrayList<>();
      for (Player p : players) states.add(PlayerState.of(p));

      builder.addGameState(new GameState.Builder()
          .round(round)
          .board(boardForRound(fullBoard, round))
          .totalPot(pot)
          .players(states)
          .currentBetLevel(betLevel)
          .lastAction(action)
          .build());
    }
  }

  /**
   * Replace les blindes, qui ne sont pas des `Action` et n'apparaissent donc pas dans le
   * fichier. Même règle que `ActionContext.blindOf` : en heads-up sans siège SB, c'est le
   * BTN qui poste la petite blinde. Retourne le pot ainsi constitué.
   *
   * Seule la mise de street est posée, **pas** un retrait du tapis : le `Stack` écrit dans
   * la section PLAYERS vient du snapshot initial de `HandRecord`, pris après
   * `PokerGame.startNewHand()` — il est donc déjà net des blindes. Les redéduire ici
   * appauvrissait les blindes d'une blinde dans tous les snapshots.
   */
  private static int postBlinds(List<Player> players, int sb, int bb) {
    boolean hasSmallBlindSeat = players.stream().anyMatch(p -> p.getPosition() == Position.SB);
    boolean headsUp = players.size() == 2 && !hasSmallBlindSeat;
    int pot = 0;
    for (Player p : players) {
      Position pos = p.getPosition();
      int blind = 0;
      if (pos == Position.BB) {
        blind = bb;
      } else if (pos == Position.SB || (headsUp && pos == Position.BTN)) {
        blind = sb;
      }
      if (blind > 0) {
        p.setCurrentBet(blind);
        pot += blind;
      }
    }
    return pot;
  }

  private static List<Card> boardForRound(List<Card> fullBoard, BettingRound round) {
    if (fullBoard == null || fullBoard.isEmpty()) return Collections.emptyList();
    int cards = switch (round) {
      case PREFLOP -> 0;
      case FLOP    -> 3;
      case TURN    -> 4;
      case RIVER   -> 5;
    };
    return new ArrayList<>(fullBoard.subList(0, Math.min(cards, fullBoard.size())));
  }

  //Méthode de découpage en sections (pour récupérer les sections du jeu)
  private static Map<String, List<String>> splitSections(String[] lines) {
    Map<String, List<String>> sections = new LinkedHashMap<>();
    String currentSection = null;

    for (String line : lines) {
      String lineTrimmed = line.trim();
      if (lineTrimmed.isEmpty()){
        continue;
      } 

      if (SECTION_NAMES.contains(lineTrimmed.toUpperCase())) {
        currentSection = lineTrimmed.toUpperCase();
        sections.putIfAbsent(currentSection, new ArrayList<>());
      } else if (currentSection != null) {
        sections.get(currentSection).add(lineTrimmed);
      }
    }
    return sections;
  }

  //Parsing du HEADER
  private static void parseHeader(List<String> lines, Hand.Builder builder) throws ParseException {
    for (String line : lines) {
      String[] parts = line.split(":", 2);
      if (parts.length < 2) {
        continue;
      }
      String key = parts[0].trim();
      String value = parts[1].trim();

      switch (key) {
        case "HandId":
          builder.handId(value);
          Debug.log(() -> "[Parser] HandId identifié : " + value);
          break;
        case "Table":
          builder.tableName(value); 
          break;
        case "SmallBlind":
          builder.smallBlind(Integer.parseInt(value)); 
          break;
        case "BigBlind":
          builder.bigBlind(Integer.parseInt(value)); 
          break;
        case "Hero":
          builder.heroId(value); 
          break;
        case "Date":
          try { builder.date(Instant.parse(value)); }
          catch (Exception e) { /* date optionnelle */ }
                break;
          default: break;
      }
    }
  }

  //Parsing des PLAYERS
  // Format : Player <id>: <nom> - Seat <n> - Stack <s> - Position <p>
  private static void parsePlayers(List<String> lines, List<Player> players,
                                      Map<String, Player> byId,
                                      Map<String, Player> byName) throws ParseException {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      try {
        // Retirer le préfixe "Player "
        String content = line;
        if (content.startsWith("Player ")) {
          content = content.substring(7);
        }

        String[] idAndRest = content.split(":", 2);
        String id = idAndRest[0].trim();
        String rest = idAndRest[1].trim();

        // Séparer par " - "
        String[] parts = rest.split("\\s*-\\s*");
        String name = parts[0].trim();
        int seat = Integer.parseInt(parts[1].replace("Seat", "").trim());
        int stack = Integer.parseInt(parts[2].replace("Stack", "").trim());
        String posStr = parts[3].replace("Position", "").trim();
        Position pos = Position.valueOf(posStr);

        Player p = new Player(id, name, stack);
        p.setSeat(seat);
        p.setPosition(pos);

        players.add(p);
        byId.put(id, p);
        byName.put(name, p);
      } catch (Exception e) {
          throw new ParseException("Format joueur invalide", i, line, e);
        }
    }
  }


  // Parsing des Holes cards au format : <playerId>: <card1> <card2>
  private static void parseHoleCards(List<String> lines, Map<String, Player> byId) throws ParseException {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      try {
        String[] parts = line.split(":", 2);
        String playerId = parts[0].trim();
        String[] cards = parts[1].trim().split("\\s+");

        Player p = byId.get(playerId);
        if (p == null) {
          throw new ParseException("Joueur inconnu : " + playerId, i, line);
        }

        p.receiveHoleCards(Card.fromString(cards[0]),Card.fromString(cards[1]));
      } catch (ParseException parsingException) {
        throw parsingException;
      } catch (Exception e) {
        throw new ParseException("Format holecards invalide", i, line, e);
      }
    }
  }

  //Parsing des actions au format : <nom>: <ACTION> [<montant>] ainsi que "Board: <cartes>" dans FLOP/TURN/RIVER
  private static void parseActions(List<String> lines, BettingRound round,
                                      Map<String, Player> byName,
                                      Hand.Builder builder,
                                      List<Card> board) throws ParseException {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      // Ligne Board:
      if (line.startsWith("Board:")) {
        String cardStr = line.substring(6).trim();
        String[] cardTokens = cardStr.split("\\s+");
        for (String token : cardTokens) {
          board.add(Card.fromString(token));
        }
        continue;
      }

      try {
        String[] parts = line.split(":", 2);
        String playerName = parts[0].trim();
        String[] actionParts = parts[1].trim().split("\\s+");

        Player p = byName.get(playerName);
        if (p == null) {
          throw new ParseException("Joueur inconnu : " + playerName, i, line);
        }

        ActionType type = ActionType.valueOf(actionParts[0].toUpperCase());
        Action action;
        if (type.involvesMoney() && actionParts.length > 1) {
          int amount = Integer.parseInt(actionParts[1]);
          action = new Action(p, type, amount, round);
        } else {
          action = new Action(p, type, round);
        }
        builder.addAction(action);
      } catch (ParseException pe) {
        throw pe;
      } catch (Exception e) {
        throw new ParseException("Format action invalide", i, line, e);
      }
    }
  }

//Parsing du SHOWDOWN
/**
* Format :
*   Winner: <playerId>
*   Amount: <n>
*   (peut se répéter pour split pots)
*/
  private static void parseShowdown(List<String> lines, Hand.Builder builder) {
    String currentWinner = null;

    for (String line : lines) {
      if (line.startsWith("Winner:")) {
        currentWinner = line.substring(7).trim();
      } else if (line.startsWith("Amount:") && currentWinner != null) {
        int amount = Integer.parseInt(line.substring(7).trim());
        builder.addWinning(currentWinner, amount);
        currentWinner = null;
      }
    }
  }
}