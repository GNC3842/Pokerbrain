package pokerbrain.ui;

import pokerbrain.Debug;

import pokerbrain.model.history.Hand;
import pokerbrain.parser.HistoryManager;
import java.util.List;

public final class SessionData {
    private SessionData() {}

    private static HistoryManager historyManager;

    public static void initHistory() {
        if (historyManager == null) {
            historyManager = new HistoryManager();
            Debug.log(() -> "[SessionData] Historique : " + historyManager.getHistoryDir());
        }
    }

    public static HistoryManager getHistoryManager() {
        if (historyManager == null) initHistory();
        return historyManager;
    }

    public static void addHand(Hand hand) {
        if (hand == null) return;
        getHistoryManager().saveHand(hand);
    }

    public static List<Hand> getAllHands() {
        return getHistoryManager().loadAllHands();
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
