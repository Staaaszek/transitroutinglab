# Plan implementacji algorytmow

## Etap 1: baseline i walidacja danych

- Utrzymac `DirectTripEngine` jako smoke test dla UI i API.
- Dodac test importera GTFS na malym fixture: stop, trip, segment, kalendarz, klika peronow.
- Dodac endpoint diagnostyczny modelu: liczba stopow, segmentow, kursow, klik peronow.

## Etap 2: earliest arrival

- Engine etykietowy po czasie, bez heurystyk.
- Stan: stop id, czas dotarcia, poprzedni segment lub transfer peronowy.
- Relaksacja:
  - odjazdy z `outgoingSegmentsByStop[stop]` po czasie,
  - `platformTransfersByStop[stop]` jako zerokosztowe przejscie w ramach zespolu.
- Wynik: top K wariantow po czasie przyjazdu.

## Etap 3: wielokryterialnosc

- Kryteria: czas przyjazdu, liczba przesiadek, czas oczekiwania, liczba odcinkow.
- Etykiety Pareto per stop.
- Parametry engine: limity etykiet per stop, maksymalna liczba przesiadek, okno czasowe.

## Etap 4: RAPTOR / CSA

- CSA: sortowane segmenty po `departureSeconds`.
- RAPTOR: rundy po liczbie przesiadek, grupowanie po route/trip.
- Oba engine korzystaja z tego samego `TransitNetwork`, ale moga zbudowac prywatne indeksy preprocessingowe w konstruktorze albo lazy cache per feed.

## Etap 5: eksperymenty

- Porownanie engine po `SearchDiagnostics`: visited states, elapsed millis, liczba tras.
- Eksport wynikow do JSON pod raport.
- Profile dla roznych godzin i par przystankow.
