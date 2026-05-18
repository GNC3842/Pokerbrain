package pokerbrain.analyser.leaks;

/* Cette classe définit les différents niveaux de gravité d'un leak
 * 		CRITICAL: leak majeur qui coute bcp de jetons
 * 		WARNING: leak modéré qui mérite attentin
 * 		INFO: tendence observé maus pas forcemment grave
 */

public enum LeakSeverity {
	CRITICAL(3,"Critique"),
	WARNING(2,"Attention"),
	INFO(1,"Info");
	
	private final int weight;
	private final String label;
	
	LeakSeverity(int weight,String label){
		this.weight = weight;
		this.label = label;
	}
	
	public int getWeight() {
		return this.weight;
	}
	
	public String getLabel() {
		return this.label;
	}

}
