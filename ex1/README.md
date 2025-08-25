# Fachprojekt Analyseaufgabe 1: Gottklassen
Soll durch Nutzung verschiedener Schwellenwerte Gottklassen erkennen.
Werden drei der vier Schwellenwerte überschritten, wird eine Klasse als Gottklasse erkannt.

## Benutzung (über IntelliJ):
1. Starte im Terminal sbt (mit `sbt`)
2. Starte Programm dann über sbt mit `run -cp=<Dateiname> [Optionen]`, z.B. `run -cp=pdfbox-2.0.24.jar`
3. Programm wird ausgeführt und gibt Ergebnis über das Terminal aus

Zum Testen sind die Dateien pdfbox-2.0.24.jar und HelloWorld.jar mit enthalten.
Verfügbare Optionen können über `run -help` angezeigt werden (oder im Folgenden Abschnitt gelesen werden).
## Optionen:

- `-help`: Zeigt verfügbare Optionen im Terminal.
- `-cp=<Dateiname>`: Java-Bytecode-Datei, die analysiert werden soll (z.B. .jar).
- `-wmc=<Integer>`: Festlegen des Schwellenwerts für WMC (Weighted Methods per Class, Anzahl Methoden in der Klasse).
  Nur nichtnegative Integer erlaubt, Standardwert 100.
- `-tcc=<Dezimalzahl>`: Schwellenwert TCC (Tight Class Cohesion, Verhältnis der Methodenpaare einer Klasse,
  die sich Instanzvariablen teilen). Dezimalzahl muss zwischen 0 und 1 sein, Standardwert 0.33.
- `-atfd=<Integer>`: Schwellenwert ATFD (Access to Foreign Data, Anzahl Felder anderer Klassen,
  auf die zugegriffen wird). Nur nichtnegative Integer erlaubt, Standardwert 8.
- `-nof=<Integer>`: Schwellenwert NOF (Number of Fields, Anzahl Felder/Attribute in der Klasse).
  Nur nichtnegative Integer erlaubt, Standardwert 30.