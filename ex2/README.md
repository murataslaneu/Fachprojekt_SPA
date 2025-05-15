# Fachprojekt Analyseaufgabe 2: Aufruf kritischer Methoden
Der `CriticalMethodsDetector` soll Stellen in einem Softwareprojekt erkennen, wo Methodenaufrufe getätigt werden,
die potenziell sicherheitsrelevant sind. Standardmäßig wird nach Aufrufen von `System.getSecuritymanager` und
`System.setSecurityMananger` gesucht. Man kann aber auch eigene Methoden über eine Textdatei übergeben, nach denen
ebenfalls gesucht werden soll.

## Benutzung (über IntelliJ):
1. Öffne das Terminal und stelle sicher, im richtigen Verzeichnis (`TUDO-FP-VulnSPA-25-3/ex2`) zu sein.
2. Starte im Terminal sbt (mit `sbt`)
3. Starte das Programm dann über sbt mit `run -cp=<Dateiname> [Optionen]`, z.B.
     `run -cp=pdfbox-2.0.24.jar -include=ExampleInclude.txt`
4. Programm wird ausgeführt und gibt Ergebnis aus.

Verfügbare Optionen können über `run -help` angezeigt werden (oder im Folgenden Abschnitt gelesen werden).

> **Anmerkung:** Alle Dateien, die in der Analyse verwendet werden sollen, möglichst im selben Verzeichnis ablegen. Stelle
außerdem sicher, dass die Dateinamen keine Leerzeichen enthalten. Grund dafür ist, dass die `AnalysisApplication` von
OPAL bei der Parameterübergabe schlecht mit Leerzeichen umgeht, was sich leider nicht wirklich beheben lässt.

## Optionen:

- `-help`: Zeigt verfügbare Optionen im Terminal und beendet anschließend das Programm.
- `-cp=<Dateiname>`: Java-Bytecode-Datei, die analysiert werden soll (z.B. .jar).
- `-include=<Textdatei-Name>`: Textdatei, die weitere Methodennamen enthält, auf die geachtet werden soll. Syntax für 
     die Textdatei siehe im folgenden Abschnitt.
- `-ignoreSecurityManager`: Flag, der die standardmäßig hinzugefügten Methoden `System.getSecuritymanager` und
  `System.setSecurityMananger` aus den Methoden, auf die geachtet werden soll, entfernt.

## Syntax für die include-Textdatei:
- Eine Raute `#` **am Anfang der Zeile** wird als Kommentar gewertet. Die Zeile wird dann ignoriert.
    Beispielzeile: `# This is a comment line.`.
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
