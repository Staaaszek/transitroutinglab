import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Bus, Clock, Database, LocateFixed, MapPin, Play, Route, Settings2, X } from 'lucide-react';
import './styles.css';

type Feed = { id: string; name: string; stopCount: number; segmentCount: number; tripCount: number };
type Stop = { id: number; name: string; code: string; platformCode: string; lat: number; lon: number };
type EngineParameter = { name: string; label: string; type: 'number' | 'boolean' | 'text'; defaultValue: string | number | boolean; options: unknown[] };
type Engine = { id: string; displayName: string; parameters: EngineParameter[] };
type GeoPoint = { lat: number; lon: number };
type Departure = { routeShortName: string; headsign: string; tripId: number; toStopId: number; toStopName: string; departureSeconds: number };
type RouteLeg = {
  type: 'ride' | 'platform-transfer';
  tripId: number | null;
  routeShortName: string;
  headsign: string;
  fromStopId: number;
  fromStopName: string;
  toStopId: number;
  toStopName: string;
  departureSeconds: number;
  arrivalSeconds: number;
  geometry: GeoPoint[];
};
type RouteResult = {
  id: string;
  summary: { title: string; departureSeconds: number; arrivalSeconds: number; durationSeconds: number; transfers: number };
  legs: RouteLeg[];
  geometry: GeoPoint[];
};
type RoutingResponse = { feedId: string; engineId: string; routes: RouteResult[]; diagnostics: { message: string; visitedStates: number; elapsedMillis: number } };
type TripStop = { stopId: number; stopName: string; arrivalSeconds: number; departureSeconds: number; sequence: number; inLeg: boolean };
type TripStops = { tripId: number; routeShortName: string; headsign: string; stops: TripStop[] };
type SelectedLeg = { routeId: string; legIndex: number; leg: RouteLeg };

const api = {
  feeds: () => json<Feed[]>('/api/feeds'),
  engines: () => json<Engine[]>('/api/routing/engines'),
  stops: (feedId: string, query = '') => json<Stop[]>(`/api/stops?feedId=${encodeURIComponent(feedId)}&query=${encodeURIComponent(query)}&limit=${query ? 25 : 2500}`),
  departures: (feedId: string, stopId: number, dateTime: string) =>
    json<Departure[]>(`/api/stops/${stopId}/departures?feedId=${encodeURIComponent(feedId)}&dateTime=${encodeURIComponent(dateTime)}&limit=14`),
  search: (payload: unknown) => json<RoutingResponse>('/api/routing/search', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }),
  tripStops: (feedId: string, tripId: number, fromStopId: number, toStopId: number) =>
    json<TripStops>(`/api/trips/${tripId}/stops?feedId=${encodeURIComponent(feedId)}&fromStopId=${fromStopId}&toStopId=${toStopId}`)
};

async function json<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText);
  }
  return response.json();
}

function App() {
  const [feeds, setFeeds] = useState<Feed[]>([]);
  const [feedId, setFeedId] = useState('');
  const [stops, setStops] = useState<Stop[]>([]);
  const [engines, setEngines] = useState<Engine[]>([]);
  const [engineId, setEngineId] = useState('');
  const [parameters, setParameters] = useState<Record<string, string | number | boolean>>({});
  const [fromQuery, setFromQuery] = useState('');
  const [toQuery, setToQuery] = useState('');
  const [fromStop, setFromStop] = useState<Stop | null>(null);
  const [toStop, setToStop] = useState<Stop | null>(null);
  const [fromSuggestions, setFromSuggestions] = useState<Stop[]>([]);
  const [toSuggestions, setToSuggestions] = useState<Stop[]>([]);
  const [dateTime, setDateTime] = useState(localDateTime(new Date()));
  const [routes, setRoutes] = useState<RouteResult[]>([]);
  const [selectedRouteId, setSelectedRouteId] = useState<string | null>(null);
  const [selectedStop, setSelectedStop] = useState<Stop | null>(null);
  const [departures, setDepartures] = useState<Departure[]>([]);
  const [selectedLeg, setSelectedLeg] = useState<SelectedLeg | null>(null);
  const [tripStops, setTripStops] = useState<TripStops | null>(null);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);

  const selectedEngine = engines.find((engine) => engine.id === engineId);
  const selectedRoute = routes.find((route) => route.id === selectedRouteId) ?? routes[0] ?? null;

  useEffect(() => {
    Promise.all([api.feeds(), api.engines()])
      .then(([loadedFeeds, loadedEngines]) => {
        setFeeds(loadedFeeds);
        setEngines(loadedEngines);
        setFeedId(loadedFeeds[0]?.id ?? '');
        const engine = loadedEngines[0];
        if (engine) {
          setEngineId(engine.id);
          setParameters(defaultParameters(engine));
        }
      })
      .catch((error) => setStatus(String(error.message ?? error)));
  }, []);

  useEffect(() => {
    if (!feedId) return;
    api.stops(feedId)
      .then(setStops)
      .catch((error) => setStatus(String(error.message ?? error)));
    setRoutes([]);
    setSelectedRouteId(null);
    setSelectedStop(null);
    setSelectedLeg(null);
  }, [feedId]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      if (feedId && fromQuery.length >= 2 && !fromStop) {
        api.stops(feedId, fromQuery).then(setFromSuggestions).catch(() => setFromSuggestions([]));
      } else {
        setFromSuggestions([]);
      }
    }, 160);
    return () => window.clearTimeout(handle);
  }, [feedId, fromQuery, fromStop]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      if (feedId && toQuery.length >= 2 && !toStop) {
        api.stops(feedId, toQuery).then(setToSuggestions).catch(() => setToSuggestions([]));
      } else {
        setToSuggestions([]);
      }
    }, 160);
    return () => window.clearTimeout(handle);
  }, [feedId, toQuery, toStop]);

  useEffect(() => {
    const leg = selectedLeg?.leg;
    setTripStops(null);
    if (!feedId || !leg?.tripId) return;
    api.tripStops(feedId, leg.tripId, leg.fromStopId, leg.toStopId).then(setTripStops).catch(() => setTripStops(null));
  }, [feedId, selectedLeg]);

  function changeEngine(nextId: string) {
    const engine = engines.find((item) => item.id === nextId);
    setEngineId(nextId);
    setParameters(engine ? defaultParameters(engine) : {});
  }

  async function selectStop(stop: Stop) {
    setSelectedStop(stop);
    setSelectedLeg(null);
    setDepartures([]);
    try {
      setDepartures(await api.departures(feedId, stop.id, dateTime));
    } catch {
      setDepartures([]);
    }
  }

  async function runSearch() {
    if (!feedId || !engineId || !fromStop || !toStop) {
      setStatus('Wybierz feed, engine, start i cel.');
      return;
    }
    setLoading(true);
    setStatus('');
    try {
      const response = await api.search({ feedId, engineId, fromStopId: fromStop.id, toStopId: toStop.id, dateTime, maxResults: 5, parameters });
      setRoutes(response.routes);
      setSelectedRouteId(response.routes[0]?.id ?? null);
      setSelectedStop(null);
      setSelectedLeg(null);
      setStatus(response.diagnostics.message);
    } catch (error) {
      setStatus(String(error instanceof Error ? error.message : error));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app">
      <TransitMap
        stops={stops}
        selectedStop={selectedStop}
        selectedRoute={selectedRoute}
        selectedLeg={selectedLeg}
        onStopClick={selectStop}
        onLegClick={(leg, legIndex) => selectedRoute && setSelectedLeg({ routeId: selectedRoute.id, legIndex, leg })}
      />

      <aside className="panel">
        <header className="panel-header">
          <div>
            <span>Transit Routing Lab</span>
            <h1>Planer tras</h1>
          </div>
          <Route size={28} />
        </header>

        <label className="field">
          <span><Database size={16} /> Feed danych</span>
          <select value={feedId} onChange={(event) => setFeedId(event.target.value)}>
            {feeds.map((feed) => <option key={feed.id} value={feed.id}>{feed.name}</option>)}
          </select>
          {feeds.find((feed) => feed.id === feedId) && <small>{formatFeed(feeds.find((feed) => feed.id === feedId)!)}</small>}
        </label>

        <StopSearch label="Start" icon={<LocateFixed size={16} />} value={fromQuery} selected={fromStop} suggestions={fromSuggestions} onChange={(value) => { setFromQuery(value); setFromStop(null); }} onSelect={(stop) => { setFromStop(stop); setFromQuery(formatStop(stop)); }} />
        <StopSearch label="Cel" icon={<MapPin size={16} />} value={toQuery} selected={toStop} suggestions={toSuggestions} onChange={(value) => { setToQuery(value); setToStop(null); }} onSelect={(stop) => { setToStop(stop); setToQuery(formatStop(stop)); }} />

        <label className="field">
          <span><Clock size={16} /> Czas startu</span>
          <input type="datetime-local" value={dateTime} onChange={(event) => setDateTime(event.target.value)} />
        </label>

        <label className="field">
          <span><Bus size={16} /> Engine</span>
          <select value={engineId} onChange={(event) => changeEngine(event.target.value)}>
            {engines.map((engine) => <option key={engine.id} value={engine.id}>{engine.displayName}</option>)}
          </select>
        </label>

        {selectedEngine && (
          <section className="params" aria-label="Engine parameters">
            <h2><Settings2 size={16} /> Parametry</h2>
            {selectedEngine.parameters.map((parameter) => (
              <ParameterInput key={parameter.name} parameter={parameter} value={parameters[parameter.name]} onChange={(value) => setParameters((current) => ({ ...current, [parameter.name]: value }))} />
            ))}
          </section>
        )}

        <button className="run" type="button" onClick={runSearch} disabled={loading}>
          <Play size={17} /> {loading ? 'Szukam...' : 'Szukaj tras'}
        </button>

        {status && <div className="status">{status}</div>}

        <section className="routes">
          {routes.map((route) => (
            <RouteCard
              key={route.id}
              route={route}
              active={selectedRoute?.id === route.id}
              selectedLegIndex={selectedLeg?.routeId === route.id ? selectedLeg.legIndex : null}
              onSelect={() => setSelectedRouteId(route.id)}
              onLegClick={(leg, index) => {
                setSelectedRouteId(route.id);
                setSelectedLeg({ routeId: route.id, legIndex: index, leg });
                setSelectedStop(null);
              }}
            />
          ))}
        </section>
      </aside>

      {selectedStop && <DeparturesPanel stop={selectedStop} departures={departures} onClose={() => setSelectedStop(null)} />}
      {selectedLeg && <LegPanel leg={selectedLeg.leg} tripStops={tripStops} onClose={() => setSelectedLeg(null)} />}
    </main>
  );
}

function TransitMap({ stops, selectedStop, selectedRoute, selectedLeg, onStopClick, onLegClick }: {
  stops: Stop[];
  selectedStop: Stop | null;
  selectedRoute: RouteResult | null;
  selectedLeg: SelectedLeg | null;
  onStopClick: (stop: Stop) => void;
  onLegClick: (leg: RouteLeg, legIndex: number) => void;
}) {
  const mapRef = useRef<L.Map | null>(null);
  const visibleStops = useMemo(() => stops.slice(0, 2500), [stops]);

  useEffect(() => {
    const map = L.map('map', { zoomControl: false }).setView([50.0647, 19.945], 12);
    mapRef.current = map;
    L.control.zoom({ position: 'bottomright' }).addTo(map);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap' }).addTo(map);
    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const layer = L.layerGroup().addTo(map);
    visibleStops.forEach((stop) => {
      const active = selectedStop?.id === stop.id;
      const marker = L.circleMarker([stop.lat, stop.lon], {
        radius: active ? 7 : 4,
        color: active ? '#111827' : '#075985',
        fillColor: active ? '#fbbf24' : '#38bdf8',
        fillOpacity: 0.9,
        weight: 1.4
      }).addTo(layer);
      marker.bindTooltip(formatStop(stop));
      marker.on('click', () => onStopClick(stop));
    });
    return () => {
      layer.remove();
    };
  }, [visibleStops, selectedStop, onStopClick]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const layer = L.layerGroup().addTo(map);
    const bounds = L.latLngBounds([]);
    selectedRoute?.legs.forEach((leg, index) => {
      if (leg.geometry.length < 2) return;
      const selected = selectedLeg?.routeId === selectedRoute.id && selectedLeg.legIndex === index;
      const line = L.polyline(leg.geometry.map((point) => [point.lat, point.lon]), {
        color: routeColor(index),
        weight: selected ? 9 : 6,
        opacity: 0.9,
        dashArray: leg.type === 'platform-transfer' ? '5 8' : undefined
      }).addTo(layer);
      line.bindTooltip(leg.type === 'platform-transfer' ? 'peron' : leg.routeShortName, { permanent: true, direction: 'center', className: 'route-label' });
      line.on('click', () => onLegClick(leg, index));
      bounds.extend(line.getBounds());
    });
    if (bounds.isValid()) {
      map.fitBounds(bounds, { paddingTopLeft: [460, 32], paddingBottomRight: [360, 32], maxZoom: 15 });
    }
    return () => {
      layer.remove();
    };
  }, [selectedRoute, selectedLeg, onLegClick]);

  return <div id="map" className="map" />;
}

function StopSearch({ label, icon, value, selected, suggestions, onChange, onSelect }: {
  label: string;
  icon: React.ReactNode;
  value: string;
  selected: Stop | null;
  suggestions: Stop[];
  onChange: (value: string) => void;
  onSelect: (stop: Stop) => void;
}) {
  return (
    <label className="field search-field">
      <span>{icon} {label}</span>
      <input value={value} onChange={(event) => onChange(event.target.value)} placeholder="Nazwa przystanku" />
      {!selected && suggestions.length > 0 && (
        <div className="suggestions">
          {suggestions.map((stop) => <button key={stop.id} type="button" onClick={() => onSelect(stop)}>{formatStop(stop)}</button>)}
        </div>
      )}
    </label>
  );
}

function ParameterInput({ parameter, value, onChange }: {
  parameter: EngineParameter;
  value: string | number | boolean | undefined;
  onChange: (value: string | number | boolean) => void;
}) {
  if (parameter.type === 'boolean') {
    return (
      <label className="toggle">
        <input type="checkbox" checked={Boolean(value)} onChange={(event) => onChange(event.target.checked)} />
        <span>{parameter.label}</span>
      </label>
    );
  }
  return (
    <label className="field compact">
      <span>{parameter.label}</span>
      <input type={parameter.type === 'number' ? 'number' : 'text'} value={String(value ?? '')} onChange={(event) => onChange(parameter.type === 'number' ? Number(event.target.value) : event.target.value)} />
    </label>
  );
}

function RouteCard({ route, active, selectedLegIndex, onSelect, onLegClick }: {
  route: RouteResult;
  active: boolean;
  selectedLegIndex: number | null;
  onSelect: () => void;
  onLegClick: (leg: RouteLeg, index: number) => void;
}) {
  return (
    <article className={`route-card ${active ? 'active' : ''}`}>
      <button type="button" className="route-summary" onClick={onSelect}>
        <span><b>{route.summary.title}</b><small>{formatSeconds(route.summary.departureSeconds)} - {formatSeconds(route.summary.arrivalSeconds)}</small></span>
        <strong>{Math.round(route.summary.durationSeconds / 60)} min</strong>
      </button>
      {active && (
        <div className="legs">
          {route.legs.map((leg, index) => (
            <button key={`${route.id}-${index}`} type="button" className={`leg ${selectedLegIndex === index ? 'selected' : ''}`} onClick={() => onLegClick(leg, index)}>
              <i style={{ background: routeColor(index) }} />
              <span>{leg.type === 'platform-transfer' ? 'Zmiana peronu' : `Linia ${leg.routeShortName}`}<small>{leg.fromStopName}{' -> '}{leg.toStopName}</small></span>
              <time>{formatSeconds(leg.departureSeconds)}</time>
            </button>
          ))}
        </div>
      )}
    </article>
  );
}

function DeparturesPanel({ stop, departures, onClose }: { stop: Stop; departures: Departure[]; onClose: () => void }) {
  return (
    <aside className="side-panel">
      <header><div><span>Odjazdy</span><h2>{formatStop(stop)}</h2></div><button type="button" onClick={onClose} aria-label="Zamknij"><X size={18} /></button></header>
      <div className="list">
        {departures.length === 0 && <p className="empty">Brak najblizszych odjazdow.</p>}
        {departures.map((departure, index) => (
          <div className="departure" key={`${departure.tripId}-${departure.departureSeconds}-${index}`}>
            <b>{departure.routeShortName}</b>
            <span>{departure.headsign || departure.toStopName}</span>
            <time>{formatSeconds(departure.departureSeconds)}</time>
          </div>
        ))}
      </div>
    </aside>
  );
}

function LegPanel({ leg, tripStops, onClose }: { leg: RouteLeg; tripStops: TripStops | null; onClose: () => void }) {
  return (
    <aside className="side-panel course-panel">
      <header><div><span>{leg.type === 'platform-transfer' ? 'Przejscie' : 'Kurs'}</span><h2>{leg.type === 'platform-transfer' ? 'Zmiana peronu' : `Linia ${leg.routeShortName}`}</h2></div><button type="button" onClick={onClose} aria-label="Zamknij"><X size={18} /></button></header>
      <div className="course-stops">
        {leg.type === 'platform-transfer' && (
          <>
            <CourseStop sequence={1} name={leg.fromStopName} seconds={leg.departureSeconds} active />
            <CourseStop sequence={2} name={leg.toStopName} seconds={leg.arrivalSeconds} active />
          </>
        )}
        {leg.type === 'ride' && !tripStops && <p className="empty">Laduje przystanki kursu...</p>}
        {tripStops?.stops.map((stop) => <CourseStop key={`${stop.stopId}-${stop.sequence}`} sequence={stop.sequence} name={stop.stopName} seconds={stop.departureSeconds} active={stop.inLeg} />)}
      </div>
    </aside>
  );
}

function CourseStop({ sequence, name, seconds, active }: { sequence: number; name: string; seconds: number; active: boolean }) {
  return <div className={`course-stop ${active ? 'in-leg' : ''}`}><b>{sequence}</b><span>{name}</span><time>{formatSeconds(seconds)}</time></div>;
}

function defaultParameters(engine: Engine) {
  return Object.fromEntries(engine.parameters.map((parameter) => [parameter.name, parameter.defaultValue]));
}

function formatStop(stop: Stop) {
  const suffix = stop.platformCode || stop.code;
  return suffix ? `${stop.name} ${suffix}` : stop.name;
}

function formatFeed(feed: Feed) {
  return `${feed.stopCount.toLocaleString('pl-PL')} przystankow, ${feed.tripCount.toLocaleString('pl-PL')} kursow, ${feed.segmentCount.toLocaleString('pl-PL')} odcinkow`;
}

function localDateTime(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function formatSeconds(value: number) {
  const seconds = ((value % 86400) + 86400) % 86400;
  return `${String(Math.floor(seconds / 3600)).padStart(2, '0')}:${String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')}`;
}

function routeColor(index: number) {
  return ['#0f766e', '#dc2626', '#2563eb', '#9333ea', '#ca8a04', '#0891b2'][index % 6];
}

createRoot(document.getElementById('root')!).render(<App />);
