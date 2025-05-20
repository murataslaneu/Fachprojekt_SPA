# Fachprojekt Analyseaufgabe 2: Aufruf kritischer Methoden
Der `CriticalMethodsDetector` soll Stellen in einem Softwareprojekt erkennen, wo Methodenaufrufe getätigt werden,
die potenziell sicherheitsrelevant sind. Standardmäßig wird nach Aufrufen von `System.getSecuritymanager` und
`System.setSecurityMananger` gesucht. Man kann aber auch eigene Methoden über eine Textdatei übergeben, nach denen
ebenfalls gesucht werden soll.

## Benutzung (über IntelliJ)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis (`TUDO-FP-VulnSPA-25-3/ex2`) zu sein.
2. Starte im Terminal sbt (mit `sbt`)
3. Starte das Programm dann über sbt mit `run -cp=<Dateiname> [Optionen]`, z.B.
     `run -cp=joda-time-2.14.0.jar -include=ExampleInclude.txt`
4. Programm wird ausgeführt und gibt Ergebnis aus.

Verfügbare Optionen können über `run -help` angezeigt werden (oder im Folgenden Abschnitt gelesen werden).

> **Anmerkung:** Alle Dateien, die in der Analyse verwendet werden sollen, möglichst im selben Verzeichnis ablegen. Stelle
außerdem sicher, dass die Dateinamen keine Leerzeichen enthalten. Grund dafür ist, dass die `AnalysisApplication` von
OPAL bei der Parameterübergabe schlecht mit Leerzeichen umgeht, was sich leider nicht wirklich beheben lässt.

Zum Ausprobieren sind einige Dateien mitgegeben:

**ExampleWithSecurityManager**: Sehr kleines Testprojekt ohne besonderen Nutzen, was die Methoden `getSecurityManager`
  und `setSecurityManager` von `java.lang.System` aufruft.
- Beispiel 1 (ohne Unterdrückungen): `run -cp=ExampleWithSecurityManager.jar -include=ExampleInclude.txt`
- Beispiel 2 (mit Unterdrückungen): `run -cp=ExampleWithSecurityManager.jar -include=ExampleInclude.txt -suppress=ExampleSuppression.txt`

**joda-time-2.14.0**: Relativ kleine, real benutzte Java-Bibliothek zum Darstellen von Daten und Zeit in Java, genutzt
  vor Java 8. Besitzt eine main-Funktion, die als Entry Point genutzt werden kann.
  Quelle: https://github.com/JodaOrg/joda-time
- Beispiel 1: `run -cp=joda-time-2.14.0.jar -include=ExampleInclude.txt`
- Beispiel 2 (mit entryPoints): `run -cp=joda-time-2.14.0.jar -include=ExampleInclude.txt -entryPoints=ExampleEntryPoints.txt`
- Beispiel 3 (entryPoints + Unterdrückungen): `run -cp=joda-time-2.14.0.jar -include=ExampleInclude.txt -entryPoints=ExampleEntryPoints.txt  -suppress=ExampleSuppression.txt`
- Beispiel 4 (entryPoints + ApplicationEntryPoints): `run -cp=joda-time-2.14.0.jar -include=ExampleInclude.txt -entryPoints=ExampleEntryPoints.txt -includeApplicationEntries`

**pdfbox-3.0.5.jar**: Java-Bibliothek zum Arbeiten mit PDF-Dokumenten. Besitzt **keine** main-Funktion, von daher ist
  hier die Verwendung von benutzerdefinierten Entry Points besonders interessant.
  Quelle: https://github.com/apache/pdfbox
- Beispiel: `run -cp=pdfbox-3.0.5.jar -include=ExampleInclude.txt -entryPoints=ExampleEntryPoints.txt`

## Optionen
### Standard-Optionen
- `-help`: Zeigt verfügbare Optionen im Terminal und beendet anschließend das Programm.
- `-cp=<Dateiname>`: Java-Bytecode-Datei, die analysiert werden soll (z.B. .jar).
- `-libcp=<OrderOderDatei>`: Ordner mit zusätzlichen Libraries, um den Call Graphen bei Fehlern ggf.
      weiter zu vervollständigen. Optional, kann auch weggelassen werden.

### Hinzufügen kritischer Methoden
- `-include=<Textdatei-Name>`: Textdatei, die weitere Methoden enthält, auf die geachtet werden soll. Syntax für 
      die Textdatei siehe im folgenden Abschnitt. Wird diese Option nicht übergeben, 
      wird standardmäßig von `java.lang.System` nach `getSecurityManager` und `setSecurityManager` geguckt.

### Warnungen unterdrücken
- `-suppress=<Textdatei-Name>`: Textdatei, die hinzugefügt werden kann, um einzelne Warnungen zu unterdrücken.

### Einstiegspunkte
- `-entryPoints=<Textdatei-Name>`: Textdatei, die Methoden enthält, die von der Analyse als Einstiegspunkte angesehen
      werden sollen. Syntax ist dieselbe wie bei der `-include`-Textdatei
- `-includeApplicationEntries`: Flag, der angibt, ob die main-Methoden des Programms als Einstiegsmethoden angesehen
      werden sollen. Standardmäßig an, sofern Option `-entryPoints` nicht übergeben. Sollte `-entryPoints` übergeben
      werden, ist diese Option standardmäßig aus.
- `-includeApplicationWithJREEntries`: Erweitert `-includeApplicationEntries` damit, dass auch die main-Methoden der
      Java Runtime Environment (JRE) als Einstiegspunkte angesehen werden, sofern vorhanden. **Achtung: Diese Option und
    `-includeApplicationEntries` schließen sich gegenseitig aus. Bitte nur einen der beiden Optionen übergeben!**

### Algorithmus-Wahl
- `-alg=<Algorithmus>`: Auswählen des Algorithmus, der zur Generierung des Call-Graphen verwendet werden soll.
      Zur Verfügung stehen: `CHA`, `RTA`, `XTA`, und `CTA` (sortiert nach Geschwindigkeit). CHA ist am schnellsten, kann aber
      die meisten False Positives enthalten. CTA ist am langsamsten, ist dafür aber sehr präzise und enthält nur wenige
      False positives. Alle Algorithmen sollten aber keine False negatives erzeugen (vorausgesetzt, man übergibt auch
      alle verwendeten Bibliotheken dem Programm über -libcp, sofern welche verwendet wurden). Der verwendete
      Standard-Algorithmus ist `RTA`.

## Syntax für die include-Textdatei
- Eine Raute `//` **am Anfang der Zeile** wird als Kommentar gewertet. Die Zeile wird dann ignoriert.
    Beispielzeile: `// This is a comment line.`.
- Starte zuerst mit dem fully qualified name der Klasse, die kritische Methoden enthält, nach denen der Detector
    suchen soll. Füge nach dem Namen ein Doppelpunkt `:` hinzu. Beispielzeile: `java.lang.System`.
- Füge anschließend auf den folgenden Zeilen die Methodennamen der Klasse hinzu, die als kritisch angesehen
    werden sollen. Die Parameterlister der Methode darf nicht mit enthalten sein. Beispielzeile: `getSecurityManager`
- Nachdem alle Methoden der Klasse hinzugefügt wurden, **muss die nächste Zeile leer gelassen werden**. Auf diese Weise
    sagst du dem Programm, dass alle Methoden für die Klasse hinzugefügt wurden. Anmerkung: Kommentarzeilen werden
    nicht als Leerzeilen gewertet.
- Nach der Leerzeile kannst du weitere Klassen und die zugehörigen Methoden hinzufügen.
- Beachte, dass alle Zeilen beliebig mit Leerzeichen und Tabs eingerückt werden können, um die Lesbarkeit der Datei
    zu verbessern. Einen Effekt beim Einlesen hat dies aber nicht.

## Syntax für die suppress-Textdatei
- Jede Zeile stellt eine Warnung dar, die unterdrückt werden soll.
- Muster: `<aufrufendeKlasse>#<aufrufendeMethode> -> <klasseKritischerMethode>#<kritischeMethode>`
- Erste Hälfte: Fully qualified name der Klasse, dann `#`, Methodenname.
- Anschließend folgt ein Pfeil `->`.
- Zweite Hälfte: Fully qualified name der Klasse, die die kritische Methode enthält, dann `#`, dann Name der
    kritischen Methode.

## Syntax für die entryPoints-Textdatei
- Dieselbe wie für die include-Textdatei

> Anmerkung: Möchte man bei der include oder suppress-Textdatei als Methode den Konstruktor der Klasse verwenden, kann
    man für den Methodennamen `<init>` eingeben.
