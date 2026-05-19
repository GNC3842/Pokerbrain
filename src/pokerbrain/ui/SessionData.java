package pokerbrain.ui;

import pokerbrain.model.history.Hand;
import pokerbrain.parser.HistoryManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SessionData {
    private SessionData() {}

    private static HistoryManager historyManager;

    public static void initHistory() {
        if (historyManager == null) {
            historyManager = new HistoryManager();
            System.out.println("[SessionData] Historique : " + historyManager.getHistoryDir());
        }
    }

    public static HistoryManager getHistoryManager() {
        if (historyManager == null) initHistory();
        return historyManager;
    }

    private static final List<Hand> sessionHands = new ArrayList<>();

    public static void addHand(Hand hand) {
        if (hand == null) return;
        sessionHands.add(hand);
        getHistoryManager().saveHand(hand);
    }

    public static List<Hand> getSessionHands() {
        return Collections.unmodifiableList(sessionHands);
    }

    public static List<Hand> getAllHands() {
        // Les mains en mémoire ont les GameStates, celles du disque non
        // On fusionne : mains du disque + mains de la session pas encore sur disque
        List<Hand> fromDisk = getHistoryManager().loadAllHands();
        
        // Remplacer les mains du disque par celles en mémoire (même handId) 
        // car les versions mémoire ont les GameStates
        java.util.Map<String, Hand> merged = new java.util.LinkedHashMap<>();
        for (Hand h : fromDisk) {
            merged.put(h.getHandId(), h);
        }
        for (Hand h : sessionHands) {
            merged.put(h.getHandId(), h); // écrase la version disque
        }
        return new java.util.ArrayList<>(merged.values());
    }

    public static int getTotalHandCount() {
        return getHistoryManager().countAllHands();
    }

    private static Hand selectedHand = null;
    public static Hand getSelectedHand(){ 
        return selectedHand; 
    }
    public static void setSelectedHand(Hand h) { 
        selectedHand = h; 
    }
}