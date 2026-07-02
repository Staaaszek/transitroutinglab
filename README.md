# Transit Routing Lab

Szkielet aplikacji do testowania algorytmow wyszukiwania tras w transporcie publicznym.

## Zakres

- Backend: Spring Boot, GTFS static, domyslny feed `krakow` z plikow `GTFS_KRK_A.zip`, `GTFS_KRK_M.zip`, `GTFS_KRK_T.zip`.
- Frontend: React, TypeScript, Vite, Leaflet.
- Mapa: przystanki, klikniecie przystanku pokazuje najblizsze odjazdy.
- Panel: wybor feedu, wybor engine, parametry engine, wyszukiwanie trasy.
- Wyniki: wiele tras w panelu, odcinki kolorowane na mapie, klikniecie odcinka pokazuje rozklad przystankow danego kursu.

## Model algorytmiczny

Engine nie pracuje na DTO z UI. Dostaje `TransitNetwork`, czyli struktury pod szybki random access:

- `List<TransitStop> stops`
- `List<TripSegment> segments`
- `List<TransitTrip> trips`
- `List<List<Integer>> outgoingSegmentsByStop`
- `List<List<Integer>> segmentsByTrip`
- `List<List<PlatformTransfer>> platformTransfersByStop`

Identyfikatory w algorytmach sa indeksami `int`. Zewnetrzne identyfikatory GTFS sa mapowane podczas importu.

Graf przejsc peronowych jest pelna dwukierunkowa klika dla przystankow o tym samym `stop_name` i roznym `stop_id`. Nie ma tu predkosci chodzenia ani dystansu; to tylko domkniecie przejsc w ramach zespolu peronow.

## Dodanie engine

Dodaj klase Springa implementujaca:

```java
pl.edu.pitp.transit.engine.RoutingEngine
```

Minimalny kontrakt:

- `id()` - stabilny identyfikator uzywany przez API.
- `displayName()` - nazwa w UI.
- `parameters()` - schema parametrow renderowana dynamicznie w panelu.
- `findRoutes(RoutingQuery query, TransitNetwork network)` - zwraca warianty tras.

Przykladowy baseline to `DirectTripEngine`. Jest celowo prosty: sprawdza bezposredni kurs i opcjonalnie uzywa kliki peronow.

## API

- `GET /api/feeds`
- `GET /api/stops?feedId=&query=&limit=`
- `GET /api/stops/{stopId}/departures?feedId=&dateTime=&limit=`
- `GET /api/routing/engines`
- `POST /api/routing/search`
- `GET /api/trips/{tripId}/stops?feedId=&fromStopId=&toStopId=`

## Uruchomienie

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend dziala pod `http://localhost:5173` i proxyuje `/api` do `http://localhost:8080`.

## Dane

Backend pobiera GTFS do `data/gtfs/` przy pierwszym starcie. Katalog jest ignorowany przez Git.

Zeby dodac inny feed, dopisz go w `backend/src/main/resources/application.yml`:

```yaml
app:
  gtfs:
    feeds:
      - id: "my-feed"
        name: "My GTFS"
        urls:
          - "https://example.com/gtfs.zip"
        enabled: true
```
