package pokerbrain.analyser.leaks;

/*
 * Structure du leak detecté:
 * 		severity  : gravité (CRITICAL / WARNING / INFO)
 * 		category  : nom de la stat (VPIP, PFR, AF, etc.)
 * 		actual    : valeur mesurée du joueur
 * 		target    : valeur cible TAG optimale
 * 		message   : description lisible de la fuite
 * 		suggestion: conseil concret pour corriger
 */

public final class Leak implements Comparable<Leak> {
	private final LeakSeverity severity;
	private final String category;
	private final double actual;
	private final double target;
	private final String message;
	private final String suggestion;
	
	public Leak(LeakSeverity severity, String category,double actual,double target,String message,String suggestion) {
		this.severity = severity;
		this.category = category;
		this.actual = actual;
		this.target = target;
		this.message = message;
		this.suggestion = suggestion;
	}
	
	// Tri par sévérité décroissante (CRITICAL en premier)
    @Override
    public int compareTo(Leak other) {
        return Integer.compare(other.severity.getWeight(), this.severity.getWeight());
    }

    @Override
    public String toString() {
        return String.format("[%s] %s : %.1f (cible: %.1f) — %s",
            severity.getLabel(), category, actual, target, message);
    }
    
    //LIste les getters
    public LeakSeverity getSeverity() {
    	return this.severity;
    }
    
    public String getCategory() {
    	return this.category;
    }
    
    public double getActual() {
    	return this.actual;
    }
    
    public double getTarget() {
    	return this.target;
    }
    
    public String getMessage() {
    	return this.message;
    }
    
    public String getSuggestion() {
    	return this.suggestion;
    }
}
