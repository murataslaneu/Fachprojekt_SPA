# Fachprojekt Analyseaufgabe 3: Third Party Library Usage Analyzer

Dieses Projekt analysiert statisch die Nutzung von Third Party Libraries (TPLs) in einem gegebenen Softwareprojekt. Dabei wird ermittelt, wie viele Methoden der eingebundenen Bibliotheken tatsächlich verwendet werden. Die Analyse ist flexibel konfigurierbar und unterstützt verschiedene Call-Graph-Algorithmen.

## Benutzung (über IntelliJ oder sbt)
1. Öffne das Terminal und stelle sicher, dass du im richtigen Verzeichnis bist (z.B. `TUDO-FP-VulnSPA-25-3/ex3`).
2. Starte sbt mit `sbt`.
3. Führe das Programm mit einem der vorbereiteten Konfigurationsdateien aus, z.B.:
   ```
   run -config=config_rta.json
   run -config=config_cha.json -visual
   ```
4. Ergebnisse werden als `result.json` gespeichert. Mit `-visual` wird automatisch eine grafische Darstellung erzeugt.

## Konfiguration
Die Konfiguration erfolgt über JSON-Dateien. Hierbei werden folgende Angaben gemacht:
- `projectJar`: Pfad zur Projekt-JAR-Datei.
- `tplJars`: Liste der TPL-JARs, die analysiert werden sollen.
- `callGraphAlgorithm`: Name des Algorithmus (z.B. `CHA`, `RTA`, `XTA`, `CFA`).
- `outputJson`: Name der Ausgabedatei.

Beispiel:
```json
{
  "projectJar": "TestGuava.jar",
  "tplJars": [
    "lib/guava-33.4.8-jre.jar",
    "lib/gson-2.13.1.jar",
    "lib/junit-4.13.2.jar"
  ],
  "callGraphAlgorithm": "RTA",
  "outputJson": "result.json"
}
```

## Visualisierung
- Nach der Analyse kann eine grafische Darstellung der Ergebnisse erzeugt werden.
- Dazu wird `-visual` beim Aufruf übergeben oder das Visualizer-Modul manuell gestartet.
- Es werden die Coverage (Nutzungsrate) jeder TPL und die Laufzeiten der Analyse dargestellt.

## Optionen
- `-config=<Dateiname>`: JSON-Konfigurationsdatei mit Projekt- und TPL-Informationen.
- `-visual`: Zeigt die grafische Visualisierung der Ergebnisse nach der Analyse.

## Beispielhafte Nutzung
- Analyse mit RTA-Algorithmus ohne Visualisierung:
  ```
  sbt "run -config=config_rta.json"
  ```
- Analyse mit CHA-Algorithmus und automatischer Visualisierung:
  ```
  sbt "run -config=config_cha.json -visual"
  ```

## Hinweise
- Alle TPL-JARs und die Projekt-JAR sollten im gleichen Verzeichnis liegen, um Pfadprobleme zu vermeiden.
- Die Ergebnisse können zur weiteren Auswertung und zum Vergleich in `result.json` gespeichert werden.
- Die Analyse kann durch zusätzliche Call-Graph-Algorithmen erweitert werden (z.B. XTA, CFA).

## Beispielausgabe
- Textuelle Ausgabe (z.B. Coverage- und Laufzeit-Tabellen).
- Grafische Darstellung mit XChart (Coverage- und Zeitdiagramme).
