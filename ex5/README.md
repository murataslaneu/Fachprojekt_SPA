# Fachprojekt Analyseaufgabe 5: Abstrakte Interpretation

Dieses Projekt analysiert den Java-Bytecode eines Projektes und sucht nach "Dead Code",
also Instruktionen im Bytecode, die während der Ausführung von Methoden nie aufgerufen werden können.
Das Ergebnis der Analyse wird über eine JSON-Datei ausgegeben, welches dem geforderten Format entspricht.

Außerdem wurde besonders auf die Benutzbarkeit des Programms geachtet und eine GUI für die Analyse erstellt, über
die man das Programm vollständig bedienen kann (sowohl Starten der Analyse als auch Anschauen der Ergebnisse).
Man kann die Analyse aber auch über das Terminal oder Config-Datei starten und konfigurieren.

## Inhalt
- [Starten der GUI](#starten-der-gui-mit-sbt)
- [Benutzung Analyse über Terminal](#benutzung-der-analyse-mit-sbt-konfiguration-über-terminal)
- [Konfiguration über Terminal + Config-Datei](#konfiguration-über-das-terminal--übergabe-der-config-datei)
- [Konfiguration nur über Terminal](#konfiguration-über-das-terminal--übergabe-der-config-datei)
- [Benutzung der GUI](#benutzung-der-analyse-über-die-gui)
  - [Analysis](#analysis)
  - [Results Viewer](#results-viewer)
- [Tests](#tests)

## Starten der GUI (mit sbt)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex5`).
2. Starte sbt mit `sbt`.
3. Starte die GUI mittels `runMain DeadCodeGUIAppMain`
4. Benutzung der GUI wird im jeweiligen Abschnitt erklärt.
> Für Nutzer empfohlene Art der Nutzung der Analyse!

## Benutzung der Analyse (mit sbt, Konfiguration über Terminal)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex5`).
2. Starte sbt mit `sbt`.
3. Führe das Program mittels `run` aus. Das Programm bietet die Möglichkeit, entweder per Config-Datei
   (mittels `-config=<path/config.json>`) oder per Terminal-Optionen konfiguriert zu werden. Die Erklärung zu den
   Optionen befinden sich in den jeweiligen Abschnitten.
   ```
   runMain DeadCodeDetector -config=example/exampleConfig.json
   runMain DeadCodeDetector -cp=example/ExampleWithDeadCode.jar -interactive -showResults -outputJson=result.json
   ```
4. Die Analyse wird ausgeführt, und das Ergebnis wird als JSON-Datei am angegebenen Pfad ausgegeben 
   (oder standardmäßig in `result.json` im aktuellen Verzeichnis.)
5. Sollte Option `showResults` übergeben worden sein, wird automatisch der Report-Viewer gestartet für das Ergebnis
   dieser Analyse. TODO

### Konfiguration über das Terminal (+ Übergabe der Config-Datei)
Geeignet dafür, wenn man schnell etwas einmal testen möchte. Konfigurierung über die Config-Datei wird aber
wahrscheinlich angenehmer sein.
- **`-config=<json file>`: Pfad zur Config-Datei, wenn man die Analyse über diese konfigurieren möchte.
     Wenn übergeben, werden <ins>alle anderen Optionen</ins>, die über das Terminal übergeben wurden, <ins>ignoriert!</ins>**
- `-help`: Flag, der alle verfügbaren Optionen im Terminal anzeigen lässt und das Programm beendet.
- `-cp=<jar file>`: Notwendiger Pfad zu JAR-Datei, die zum Projekt gehört, welches analysiert werden soll. Kann mehrmal
- `-libcp=<jar file>`: Optionale zugehörige Bibliotheken des zu analysierenden Projekts. Können direkte oder transitive
    Abhängigkeiten sein, oder auch z.B. die Java-Standardbibliothek `rt.jar`, wenn man diese zur Verfügung hat.
- `-completelyLoadLibraries`: Flag, der OPAL alle Bibliotheken (mit `-libcp`) vollständig laden lässt, anstatt
    nur als Interfaces.
- `-interactive`: Flag, der den Nutzer während der Ausführung der Analyse auswählen lässt, welche Domäne für die
    abstrakte Interpretation verwendet werden soll.
- `-showResults`: Flag, der dafür sorgt, dass nach der Analyse direkt die GUI des Programms aufgerufen wird, um sich die
    Analyse-Ergebnisse anzuschauen. Option TODO
- `-outputJson=<output path>`: Optional, dient zur Konfiguration des Pfades, wo der Report im JSON-Format als Datei ausgegeben
      werden soll. Standardmäßig wird eine Datei `result.json` im aktuellen Verzeichnis angelegt, wenn diese Option
      nicht übergeben wird.

Beispiel für einen (nicht unbedingt sinnvollen) Aufruf:
```
runMain DeadCodeDetector -cp=example/MinimalExample.jar -cp=example/ExampleWithDeadCode.jar -libcp=example/rt.jar -interactive -outputJson=example/result.json
```

> **Anmerkung:** Anders als bisher angenommen, erlaubt die AnalysisApplication von OPAL doch noch, auch Dateipfade
> zu übergeben, die Leerzeichen enthalten. Dafür muss die gesamte Option in Anführungszeichen gesetzt werden, z.B.
> `"-cp=somewhere/a path/example.jar"` (und nicht nur der Teil nach dem `-cp`!)

### Konfiguration über JSON-Config-Datei (empfohlen)
Da die Eingabe der Optionen über das Terminal direkt recht nervig sein kann, bietet sich die Konfiguration
über eine JSON-Datei als Config an. Die Configs können dann ebenfalls in der GUI verwendet werden.

- `projectJars`: **Notwendig**, Liste von Strings. Die Strings enthalten die Pfade zu den jar-Dateien (bzw. der jar-Datei) des
Projekts, welches analysiert werden soll.
- `libraryJars`: Optional, Liste von Strings. Die Strings enthalten die Pfade zu den Bibliotheken, die vom zu
  analysierenden Projekt benutzt werden.
- `completelyLoadLibraries`: Optionaler Boolean. Wenn `true`, lädt OPAL die Bibliotheken (übergeben mit `-libraryJars`) vollständig.
  Standardmäßig `false` (OPAL lädt dann die Bibliotheken nur als Interface).
- `interactive`: Optionaler Boolean. Wenn `true`, wird während der Analyse ausgewählt, welche Domäne für die abstrakte
  Interpretation genutzt werden soll. Bei `false` wird einfach automatisch die erste Option gewählt. Standardmäßig `true`.
- `showResults`: Optionaler Boolean. Wenn `true`, wird nach der Analyse automatisch der Report-Viewer für die generierten
  Ergebnisse aufgerufen. Bei `false` beendet sich das Programm nach der Analyse. Standardmäßig `false`. TODO
- `outputJson`: Optionaler String. Ausgabepfad, wo die Analyseergebnisse im json-Format hingeschrieben werden sollen.
  Standardmäßig wird eine Datei `result.json` im aktuellen Verzeichnis angelegt.

Bei Aufruf der Analyse über das Terminal wird die Config über eine einzige Option übergeben:
```
runMain DeadCodeDetector -config=example/exampleConfig.json
```

Beispiel für Config-JSON (nicht unbedingt sinnvoll):
```json
{
  "projectJars": [
    "example/ExampleWithDeadCode.jar",
    "example/MinimalExample.jar"
  ],
  "libraryJars": [
    "example/rt.jar"
  ],
  "interactive": true,
  "showResults": true,
  "outputJson": "example/result.json"
}
```

## Benutzung der Analyse über die GUI

Die GUI bietet Möglichkeit, Analysen zu starten und die Ergebnisse der Analyse anzuschauen.
Das GUI ist in zwei Tabs aufgeteilt, "Analysis" und "Results Viewer".

### Analysis

Man kann über die GUI auswählen, welche JSON-Config-Datei geladen werden soll, die in der Analyse
verwendet werden soll (Syntax für JSON-Datei siehe im vorherigen Abschnitt).
Die Datei kann entweder über ein Dateifenster ausgewählt werden oder automatisch erkannt werden,
sofern diese sich im aktuellen Verzeichnis befindet. Bei der automatischen Erkennung
wählt man die jeweilige gefundene JSON-Datei in der Liste per Doppelklick aus.

Das Auswählen der Domain kann in der GUI gemacht werden. Entweder automatisch (wählt immer die erste Domain aus), 
oder manuell (wähle eine Domain aus der Liste aus), oder manuell mit Auswahl mehrerer Domänen (kann benutzt werden,
um die Ergebnisse der einzelnen Domains zu kombinieren und/oder miteinander zu vergleichen).

Über "Run Analysis" startet man dann die Analyse.

Darunter gibt es ein Log, welches mitteilt, was das Programm gemacht hat.

### Results Viewer

Hat man bei der Analyse die Domain automatisch wählen lassen oder *eine* Domain manuell gewählt,
bekommt man das Analyseergebnis der jeweiligen Analyse als Report visualisiert. Diese enthält eine
Zusammenfassung einzelner Statistiken, eine Visualisierung der Ergebnisse als Graphen und die detaillierten
Ergebnisse, wenn man weiter runterscrollt.

Bei den detaillierten Ergebnissen sieht man die Methoden, die mindestens eine tote Instruktion enthalten.
Klickt man auf eine der Methoden, bekommt man in einer Liste darunter die einzelnen toten Instruktionen der
Methode angezeigt.

## Tests

Dieses Projekt nutzt ScalaTest und enthält Tests, um die (grobe) Funktionsfähigkeit der Analyse zu testen.

### Ausführung (über sbt)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex5`).
2. Führe im Terminal `sbt test` aus.
3. Tests werden ausgeführt. Nach Ausführung wird das Ergebnis der Tests ausgegeben. Es sollten alle Tests
   erfolgreich sein! :)

### Coverage
Ebenfalls wird die Test Coverage mithilfe von sbt-scoverage gemessen. Ausführung ist ähnlich zu
den Tests, aber mit Befehl:
   ```
   sbt clean coverage test coverageReport
   ```
Dieser Befehl räumt erst das Projekt auf. Dann wird der Test ausgeführt mit Messung der Coverage, und abschließend
wird ein Coverage-Report erstellt.

Der Coverage-Report wird (unter anderem) im HTML-Format ausgegeben. Dieser kann sich dann beim
Öffnen der jeweiligen Datei im Browser graphisch angeschaut werden.