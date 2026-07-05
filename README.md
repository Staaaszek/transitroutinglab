# Transit Routing Lab

Idea projektu to implementacja algorytmu RAPTOR wraz z frontendem i danymi umożliwiającymi uruchomienie go dla krakowskiej komunikacji miejskiej

Frontend, oraz część backendu odpowiedzialna za abstrakcję nad danymi gtfs i komunikację z frontendem zostały w większości wygenerowane przy użyciu Codex GPT 5.5

Implementacja algorytmu RAPTOR została zrobiona samodzielnie

Po nacisnięciu na przystanek można zobaczyć najbliszże odjazdy z niego
Po nacisnięciu na fragemtu trasy na mapie można zobaczyć informacje o danym kursie

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

Backend pobiera GTFS do `data/gtfs/` przy pierwszym starcie.

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
