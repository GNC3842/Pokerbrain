package pokerbrain.parser;

/* Exceprion levée pour parsing d'une main
*/

public class ParseException extends Exception{
  private final int numeroLigne;
  private final String contenuLigne;

  //On fait 2 constructeurs pour nois aider a debug quand au moment du parsing
  public ParseException(String message, int numeroLigne, String contenu){
    super("Ligne " + numeroLigne + ": " + message + "--> [" + contenu + "]");
    this.numeroLigne = numeroLigne;
    this.contenuLigne = contenu;
  }

   public ParseException(String message, int numeroLigne, String contenu, Throwable cause){
    super("Ligne " + numeroLigne + ": " + message + "--> [" + contenu + "]", cause);
    this.numeroLigne = numeroLigne;
    this.contenuLigne = contenu;
  }

  public int getLineNumber(){
    return this.numeroLigne;
  }

  public String getRawLine(){
    return this.contenuLigne;
  }
}