package pokerbrain.parser;

import pokerbrain.Debug;

import pokerbrain.model.history.Hand;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

/**
 * Gère la persistence des mains sur disque, organisées par jou
 *
 * STRUCTURE DES FICHIERS :
 *   ~/.pokerbrain/history/
 *   ├── 2026-02-15.txt    ← toutes les mains du 15 février
 *   ├── 2026-04-16.txt    ← toutes les mains du 16 avril
 *   
 *
 * Chaque fichier contient N mains au format PokerBrain séparées par "---"
 * HandHistoryParser les relit, HandHistoryWriter les écrit
 *
 * USAGE :
 *   HistoryManager mgr = new HistoryManager();
 *
 *   // Sauvegarder une main (append au fichier du jour)
 *   mgr.saveHand(hand);
 *
 *   // Charger toutes les mains de tous les jours
 *   List<Hand> allHands = mgr.loadAllHands();
 *
 *   // Charger les mains d'un jour précis
 *   List<Hand> today = mgr.loadHandsForDate(LocalDate.now());
 *
 *   // Lister les fichiers disponibles
 *   List<LocalDate> dates = mgr.getAvailableDates();
 */
public final class HistoryManager {

  private static final String DIR_NAME = ".pokerbrain";
  private static final String HISTORY_DIR = "history";

  private final Path historyDir;

  //Constructeur qui va créer le dossier d'historique dans le home de l'utilisateur.
  public HistoryManager() {
    String userHome = System.getProperty("user.home");
    this.historyDir = Paths.get(userHome, DIR_NAME, HISTORY_DIR);
    Debug.log(() -> "[HistoryManager] Dossier d'historique initialisé et valide : " + historyDir.toAbsolutePath());
    try {
      Files.createDirectories(historyDir);
    } catch (IOException e) {
      System.err.println("[HistoryManager] Impossible de créer " + historyDir + " : " + e.getMessage());//log
    }
  }


  //Principe de sauvegarde
  /**
  * Sauvegarde une main dans le fichier du jour correspondant à sa date de jouabilité
  * Si la main n'a pas de date, utilise la date courante
  * Le fichier est créé s'il n'existe pas, sinon la main est ajoutée à la fin
  */
  public void saveHand(Hand hand) {
    if (hand == null) return;

    LocalDate date = extractDate(hand);
    Path filePath = getFileForDate(date);
    Debug.log(() -> "[HistoryManager] Tentative de sauvegarde de la main " + hand.getHandId() + " pour la date : " + date);

    try {
      HandHistoryWriter.appendToFile(hand, filePath);
    } catch (IOException e) {
      System.err.println("[HistoryManager] Erreur sauvegarde main " + hand.getHandId()
        + " dans " + filePath + " : " + e.getMessage());
    }
  }

  //Charge toutes les mains de tous les fichiers d'historique triés par date
  public List<Hand> loadAllHands() {
    List<Hand> allHands = new ArrayList<>();

    List<Path> files = listHistoryFiles();
    Debug.log(() -> "[HistoryManager] Chargement global : " + files.size() + " fichiers historiques trouvés (.txt).");
    for (Path file : files) {
      List<Hand> hands = loadFile(file);
      allHands.addAll(hands);
    }
    Debug.log(() -> "[HistoryManager] Fin du chargement global. Total de mains chargées en mémoire : " + allHands.size());
    return allHands;
  }

  //Charger les mains d'une certaines dates
  public List<Hand> loadHandsForDate(LocalDate date) {
    Path filePath = getFileForDate(date);
    if (!Files.exists(filePath)){ 
      return Collections.emptyList();
    }
    return loadFile(filePath);
  }

  //Charger les mains d'un fichier donné
  private List<Hand> loadFile(Path file) {
    try {
      return HandHistoryParser.parseFile(file);
    } catch (Exception e) {
      System.err.println("[HistoryManager] Erreur lecture " + file + " : " + e.getMessage());
      return Collections.emptyList();
    }
  }

  //Informations des mains
  public List<LocalDate> getAvailableDates() {
    List<LocalDate> dates = new ArrayList<>();

    for (Path file : listHistoryFiles()) {
      String name = file.getFileName().toString();
      if (name.endsWith(".txt")) {
        String dateStr = name.substring(0, name.length() - 4);
        try {
          dates.add(LocalDate.parse(dateStr));
        } catch (Exception e) {
          //ingorer si nom pas ok
        }
      }
    }
    Collections.sort(dates);
    return dates;
  }

  //Compter le nombre total de mains sauvegardées sur disque.
  public int countAllHands() {
    int count = 0;
    for (Path file : listHistoryFiles()) {
      try {
        String content = new String(Files.readAllBytes(file));
        // Compter les blocs séparés par ---
        String[] blocks = content.split("\\n---\\s*\\n");
        for (String block : blocks) {
          if (!block.trim().isEmpty()) count++;
        }
      } catch (IOException e) {
        // Ignorer les fichiers illisibles
      }
    }
    return count;
  }

  //Renvoie le chemin du dossier d'historiqu
  public Path getHistoryDir() {
    return historyDir;
  }

  //Helpers

  //Nom du fichier pour une date : "2026-04-21.txt"
  private Path getFileForDate(LocalDate date) {
    return historyDir.resolve(date.toString() + ".txt");
  }

  //Extrait la date d'une Hand. Fallback : aujourd'hui.
  private LocalDate extractDate(Hand hand) {
    Instant instant = hand.getDate();
      if (instant == null) {
        return LocalDate.now();
      }
    return instant.atZone(ZoneId.systemDefault()).toLocalDate();
  }

  //Liste tous les fichiers .txt dans le dossier d'historique, triés par nom.
  private List<Path> listHistoryFiles() {
    List<Path> files = new ArrayList<>();
    if (!Files.isDirectory(historyDir)) {
      return files;
    }

    try (Stream<Path> stream = Files.list(historyDir)) {
      stream.filter(p -> p.toString().endsWith(".txt"))
        .sorted()
          .forEach(files::add);
    } catch (IOException e) {
      System.err.println("[HistoryManager] Erreur listing " + historyDir + " : " + e.getMessage());
    }
    return files;
  }
}
