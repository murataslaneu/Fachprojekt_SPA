# Fachprojekt Analyseaufgabe 5: Abstrakte Interpretation

Dieses Projekt analysiert den Java-Bytecode eines Projektes und sucht nach "Dead Code",
also Instruktionen im Bytecode, die während der Ausführung von Methoden nie aufgerufen werden können.

Die Analyse selbst ist konfigurierbar über das Terminal oder über eine Config-JSON-Datei,
wobei das Einstellen über die Config-Datei wahrscheinlich angenehmer ist.
Das Ergebnis der Analyse wird über eine JSON-Datei ausgegeben, welches dem geforderten Format entspricht.

Außerdem wurde besonders auf die Benutzbarkeit des Programms geachtet und ein Report-Viewer als Terminal-Applikation
implementiert (GUI wäre der Idealfall gewesen, doch dafür hat die Zeit gefehlt).
Im Report-Viewer wird das Ergebnis aus der generierten JSON-Datei anschaulicher als in der JSON-Datei selbst
dargestellt und man selbst interaktiv auswählen, was man vom Report sich angucken möchte.

## Benutzung der Analyse (über IntelliJ und sbt)
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex5`).
2. Starte sbt mit `sbt`.
3. Führe das Program mittels `run` aus. Das Programm bietet die Möglichkeit, entweder per Config-Datei
   (mittels `-config=<path/config.json>`) oder per Terminal-Optionen konfiguriert zu werden. Die Erklärung zu den
   Optionen befinden sich in den nächsten Abschnitten.
   ```
   run -config=example/exampleConfig.json
   run -cp=example/ExampleWithDeadCode.jar -interactive -showResults -outputJson=result.json
   ```
4. Die Analyse wird ausgeführt, und das Ergebnis wird als JSON-Datei am angegebenen Pfad ausgegeben 
   (oder standardmäßig in `result.json` im aktuellen Verzeichnis.)
5. Sollte Option `showResults` übergeben worden sein, wird automatisch der Report-Viewer gestartet für das Ergebnis
   dieser Analyse.

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
- `-showResults`: Flag, der dafür sorgt, dass nach der Analyse direkt der Report Viewer aufgerufen wird, um sich die
    Analyse-Ergebnisse anzuschauen.
- `-outputJson=<output path>`: Optional, dient zur Konfiguration der Report im JSON-Format als Datei ausgegeben
      werden soll. Standardmäßig wird eine Datei `result.json` im aktuellen Verzeichnis angelegt, wenn diese Option
      nicht übergeben wird.

Beispiel für einen (nicht unbedingt sinnvollen) Aufruf:
```
run -cp=example/MinimalExample.jar -cp=example/ExampleWithDeadCode.jar -libcp=example/rt.jar -interactive -outputJson=example/result.json
```

> **Anmerkung:** Anders als bisher angenommen, erlaubt die AnalysisApplication von OPAL doch noch, auch Dateipfade
> zu übergeben, die Leerzeichen enthalten. Dafür muss die gesamte Option in Anführungszeichen gesetzt werden, z.B.
> `"-cp=somewhere/a path/example.jar"` (und nicht nur der Teil nach dem `-cp`!)

### Konfiguration über JSON-Config-Datei (empfohlen)
Da die Eingabe der Optionen über das Terminal direkt recht nervig sein kann, bietet sich die Konfiguration
über eine JSON-Datei als Config an.

- `projectJars`: **Notwendig**, Liste von Strings. Die Strings enthalten die Pfade zu den jar-Dateien (bzw. der jar-Datei) des
Projekts, welches analysiert werden soll.
- `libraryJars`: Optional, Liste von Strings. Die Strings enthalten die Pfade zu den Bibliotheken, die vom zu
  analysierenden Projekt benutzt werden.
- `completelyLoadLibraries`: Optionaler Boolean. Wenn `true`, lädt OPAL die Bibliotheken (übergeben mit `-libraryJars`) vollständig.
  Standardmäßig `false` (OPAL lädt dann die Bibliotheken nur als Interface).
- `interactive`: Optionaler Boolean. Wenn `true`, wird während der Analyse ausgewählt, welche Domäne für die abstrakte
  Interpretation genutzt werden soll. Bei `false` wird einfach automatisch die erste Option gewählt. Standardmäßig `true`.
- `showResults`: Optionaler Boolean. Wenn `true`, wird nach der Analyse automatisch der Report-Viewer für die generierten
  Ergebnisse aufgerufen. Bei `false` beendet sich das Programm nach der Analyse. Standardmäßig `true`.
- `outputJson`: Optionaler String. Ausgabepfad, wo die Analyseergebnisse im json-Format hingeschrieben werden sollen.
  Standardmäßig wird eine Datei `result.json` im aktuellen Verzeichnis angelegt.

Bei Aufruf der Analyse wird die Config über eine einzige Option übergeben:
```
run -config=example/exampleConfig.json
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