# Fachprojekt Analyseaufgabe 7: Analyse-Applikation

Dieses Projekt fasst alle bisherigen Analysen als eine große Analyse-Applikation zusammen,
sodass alle Analysen über ein einziges Programm und einer einzigen Json-Config konfiguriert
und nacheinander automatisiert ausgeführt werden können.

## Inhalt

- [Ausführung](#ausführung)
  - [Direkt über sbt](#direkt-über-sbt)
  - Kompilieren und Ausführen über `java -jar`
- CLI-Parameter (Starten über Konsole)
- JSON-Config + Ausgabe für die Analysen
  - Grundoptionen
  - Struktur der Analyseausgabe
  1. GodClassDetector (ex1)
  2. CriticalMethodsDetector (ex2)
  3. TPLUsageAnalyzer (ex3)
  4. CriticalMethodsRemover (ex4.1)
  5. TPLMethodsRemover (ex4.2)
  6. DeadCodeDetector (ex5)
  7. ArchitectureValidator (ex6)
- Tests

## Ausführung

### Direkt über sbt

1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex7`).
2. (Erstelle oder passe ggf. eine Json-Config an.)
3. Starte Programm über `sbt run`, beim Spezifizieren einer Config-Datei z.B. `sbt "run -config=config.json"`
4. Das Programm sollte ausgeführt werden. Die Ausgabe wird in dem Ordner ausgegeben, 
   der in der Config-Json spezifiziert wird, standardmäßig der Ordner *analysis* im aktuellen Verzeichnis.

> JVM-Optionen wie z.B. der zugewiesene RAM können über die *.jvmopts*-Datei angepasst werden.

### Kompilieren und Ausführen über `java -jar`

#### Kompilieren
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis zu sein (`TUDO-FP-VulnSPA-25-3/ex7`).
2. 
