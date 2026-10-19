# Stadion — symulacja wejścia kibiców

Projekt na Programowanie Współbieżne (zadanie PW-18). Symulacja wejścia kibiców na finał Ligi Mistrzów:
każdy kibic to osobny wątek, który przychodzi pod stadion, staje w kolejce, przechodzi drobiazgową
kontrolę na jednym z trzech stanowisk i wchodzi na trybuny. Na stadionie może przebywać jednocześnie
maksymalnie **K** osób.

Całość ma okno Swing: tabelę ze statusem każdego kibica, podgląd obłożenia stanowisk oraz przyciski
**START**, **PAUZA / WZNÓW** i **STOP / RESET**.

## Szybki start

Potrzebny jest JDK 11 lub nowszy (projekt testowany na JDK 27).

```bash
javac -encoding UTF-8 -d out src/stadion/*.java
java -cp out stadion.StadionGUI
```

Uruchamiaj z katalogu głównego projektu — program czyta i zapisuje `resources/config.properties`.

## Parametry

Wszystkie parametry siedzą w `resources/config.properties`:

| Klucz | Znaczenie | Domyślnie |
| --- | --- | --- |
| `K` | maksymalna liczba osób na stadionie | `15` |
| `LiczbaKibicow` | ile wątków-kibiców startuje | `40` |
| `LiczbaStanowisk` | liczba stanowisk kontroli | `3` |
| `MaxOsobNaStanowisku` | ile osób mieści jedno stanowisko | `3` |
| `MaxPrzepuszczen` | ile osób kibic może przepuścić w kolejce | `5` |

`K` i `LiczbaKibicow` można zmienić w okienku przed startem (i wtedy trafiają z powrotem do pliku).
Pozostałe trzy parametry są stałe zgodnie z treścią zadania — zmienia się je tylko w pliku, przed
uruchomieniem programu. Wartości z pliku są wczytywane raz, przy starcie aplikacji.

## Jak to działa

### Życie kibica

1. **Przychodzi** — wątek startuje z losowym opóźnieniem do 5 s (nie wszyscy naraz).
2. **Kolejka** — zgłasza się do monitora i czeka, aż będzie miejsce na stadionie i na wolnym stanowisku.
3. **Kontrola** — spędza na stanowisku 1,5–3,5 s (symulacja przeszukania).
4. **Stadion** — zwalnia stanowisko i „siedzi” na trybunach 4–10 s. Cały ten czas zajmuje jedno
   z **K** miejsc.
5. **Wyjście** — zwalnia miejsce i kończy wątek.

Drużyna (Real Madryt / Borussia Dortmund) jest losowana przy starcie, więc każdy przebieg wygląda
inaczej.

### Monitor

Cały współdzielony stan (`StadionMonitor`) jest chroniony jednym `ReentrantLock`em z włączonym
trybem sprawiedliwym (`new ReentrantLock(true)`), więc wątki nie są wygłodzone. Na zamku wiszą trzy
warunki:

| Warunek | Kto czeka | Kto budzi |
| --- | --- | --- |
| `wejscieNaStadion` | kibic, gdy na stadionie jest już K osób | kibic wychodzący (`opuscStadion`) |
| `stanowiskoDostepne` | kibic, gdy nie ma dla niego wolnego stanowiska | zwolnienie stanowiska, wyjście ze stadionu |
| `pauseCondition` | wszystkie wątki po kliknięciu PAUZY | wyłączenie pauzy lub STOP |

### Zasady bezpieczeństwa i gdzie są wymuszone

| Wymóg z zadania | Realizacja |
| --- | --- |
| max K osób na stadionie | licznik `obecniNaStadionie`, zwiększany przy wejściu, zmniejszany przy wyjściu |
| kontrola tylko na 3 stanowiskach | lista `stanowiska` o rozmiarze `LiczbaStanowisk` |
| max 3 osoby na stanowisku | `mozeWejscNaStanowisko()` sprawdza rozmiar listy osób |
| na stanowisku tylko jedna drużyna | stanowisko „należy” do drużyny pierwszego kibica, dopóki jest zajęte |
| kibic może przepuścić max 5 osób | licznik `liczbaPrzepuszczen` i blokada wejścia, gdy ktoś przed nami ma już 5 |

Ostatni punkt w praktyce: gdy kibic wchodzi na stanowisko, wszystkim stojącym przed nim w kolejce
zwiększa się licznik przepuszczeń — bo to oni zostali wyprzedzeni. Kibic z licznikiem `>= 5` blokuje
wejście pozostałym, więc po najbliższym przebudzeniu wchodzi pierwszy. To rozwiązanie „miękkie”:
kolejność gwarantuje sprawiedliwy zamek plus `signalAll()`, a nie sztywna kolejka FIFO.

### Pauza i stop

`safeSleep()` śpi porcjami po 100 ms i przy każdej porcji sprawdza stan monitora, dzięki czemu pauza
działa również w trakcie „kontroli” i „meczu”. STOP ustawia flagę, budzi wszystkie warunki i przerywa
wątki (`interrupt()`); kolejny START tworzy nowy monitor i nowy zestaw kibiców.

## Struktura projektu

```
src/stadion/
  Kibic.java           # wątek kibica: kolejka -> kontrola -> stadion -> wyjście
  StadionMonitor.java  # cały stan współdzielony i synchronizacja
  StadionGUI.java      # okno Swing, tabela statusów, konfiguracja z pliku
resources/
  config.properties    # domyślne parametry symulacji
Sprawozdanie_Stadion_Projekt_Patryk_Różański_WCY24IY1S1.odt
```

## Znane ograniczenia

- Konfiguracja jest zapisywana bez komentarzy (`Properties.store`), więc nagłówek pliku znika po
  pierwszym uruchomieniu.
- Pola `K` i `Liczba Kibiców` nie są walidowane — wpisanie czegoś innego niż liczba kończy się
  wyjątkiem przy kliknięciu START.
- GUI czyta stan monitora bez blokady (tylko podgląd), więc pojedyncze odświeżenie może pokazać
  stan „w połowie” zmiany. Na logikę symulacji to nie wpływa.
