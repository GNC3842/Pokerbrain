# PokerBrain

  

Application de poker Texas Hold'em avec bots configurables, simulateur Monte Carlo, et analyseur de leaks.

  

## Fonctionnalités



### Jouer une partie

Affrontez 1 à 7 bots aux profils variés (TAG, Fish, LAG) autour d'une table de poker. 
Note: il faut choisir le nombre de bots avant de lancer (celui-ci ne s'affiche pas dans la case mais une fois sélectionné c'est bon).  

### Replay avec conseils

Rejouez une main pas-à-pas avec des conseils détaillés à chaque action : le HandAnalyser évalue chaque coup joué par rapport à la stratégie TAG optimale (preflop) et aux pot odds + équité MC (postflop). Chaque action reçoit un score sur 100 et une explication en français.

  

### Statistiques et détection de leaks

Consultez vos statistiques (VPIP, PFR, AF, WTSD, WSD, BB/100) et recevez des alertes automatiques sur les fuites détectées : VPIP trop élevé, PFR trop bas, jeu trop passif, etc.

### Calculateur d'équité
[a compléter]


## Prérequis


-  **Java 21** ou supérieur (OpenJDK ou Oracle JDK)

-  **JavaFX 21** (inclus dans le JDK ou ajouté via Maven/Gradle)

-  **Maven 3.8+** pour la compilation

  

## Installation et lancement

  

### Avec Maven

```bash

# Cloner le projet

git  clone git@gitlabense.imtbs-tsp.eu:projetdev/pokerbrain.git

cd  pokerbrain

# Compiler

mvn  clean  compile

 
# Lancer

mvn  javafx:run

```

  

### Avec IntelliJ IDEA

  

1. Importer le projet comme projet Maven

2. Configurer le JDK 21 dans Project Structure

3. Classe principale : `pokerbrain.MainApp`

4. Ajouter les VM options si nécessaire :

```

--module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml

```

  

### Avec Eclipse

  

1. Importer comme projet Maven existant

2. Clic droit → Run As → Java Application → `pokerbrain.MainApp`

3. Si erreur JavaFX, ajouter dans Run Configuration → Arguments → VM :

```

--module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml
```

 
  

