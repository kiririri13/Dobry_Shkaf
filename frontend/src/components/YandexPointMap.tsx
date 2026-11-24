"use client";

import { useEffect, useRef, useState, type CSSProperties } from "react";
import type { LngLat, YMap, YMapLocationRequest, YMapMarker } from "@yandex/ymaps3-types";
import type { Point } from "@/lib/types";
import type { PointMapProps } from "./PointMap";

type YandexApi = typeof import("@yandex/ymaps3-types");

const cityCenter: LngLat = [60.605, 56.838];
let apiPromise: Promise<YandexApi> | undefined;

function loadYandexMaps(key: string) {
  apiPromise ??= new Promise<YandexApi>((resolve, reject) => {
    const script = document.createElement("script");
    const url = new URL("https://api-maps.yandex.ru/v3/");
    url.search = new URLSearchParams({ apikey: key, lang: "ru_RU" }).toString();
    script.src = url.href;
    script.async = true;
    script.referrerPolicy = "strict-origin-when-cross-origin";
    const timer = window.setTimeout(() => fail(), 15000);

    function fail() {
      window.clearTimeout(timer);
      script.remove();
      reject(new Error("Яндекс Карты недоступны."));
    }

    script.onerror = fail;
    script.onload = () => {
      const api = typeof ymaps3 === "undefined" ? undefined : ymaps3;
      if (!api) return fail();
      void api.ready.then(() => {
        window.clearTimeout(timer);
        resolve(api);
      }, fail);
    };
    document.head.append(script);
  }).catch((error) => {
    apiPromise = undefined;
    throw error;
  });
  return apiPromise;
}

function getLocation(points: Point[], editing: boolean): YMapLocationRequest {
  if (!points.length) return { center: cityCenter, zoom: editing ? 14 : 11 };
  const lats = points.map((point) => point.latitude);
  const lngs = points.map((point) => point.longitude);
  const [south, north] = [Math.min(...lats), Math.max(...lats)];
  const [west, east] = [Math.min(...lngs), Math.max(...lngs)];
  if (south === north && west === east) return { center: [west, south], zoom: 15 };
  return { bounds: [[west, north], [east, south]] };
}

export default function YandexPointMap({ points, focus, onPick, selection, showFirst, compact, apiKey, onUnavailable }: PointMapProps & {
  apiKey: string;
  onUnavailable: () => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const activeMap = useRef<YMap | null>(null);
  const markers = useRef(new Map<string, { marker: YMapMarker; button: HTMLButtonElement }>());
  const [map, setMap] = useState<{ instance: YMap; api: YandexApi }>();
  const [selectedId, setSelectedId] = useState<string | null>();
  const [hint, setHint] = useState<{ name: string; x: number; y: number; below: boolean }>();
  const [locationError, setLocationError] = useState("");
  const activeId = selectedId === undefined && showFirst ? points[0]?.id : selectedId;
  const selectedPoint = points.find((point) => point.id === activeId);
  const editing = Boolean(onPick);
  const initialView = useRef({ points, editing });

  useEffect(() => {
    const element = container.current;
    if (!element) return;
    let cancelled = false;
    let instance: YMap | undefined;
    void loadYandexMaps(apiKey).then((api) => {
      if (cancelled) return;
      instance = new api.YMap(element, {
        location: getLocation(initialView.current.points, initialView.current.editing),
        margin: compact ? [34, 18, 30, 18] : [48, 30, 36, 42],
      });
      instance.addChild(new api.YMapDefaultSchemeLayer({}));
      instance.addChild(new api.YMapDefaultFeaturesLayer({}));
      activeMap.current = instance;
      setMap({ instance, api });
    }).catch(() => {
      if (!cancelled) onUnavailable();
    });

    return () => {
      cancelled = true;
      activeMap.current = null;
      instance?.destroy();
    };
  }, [apiKey, compact, onUnavailable]);

  useEffect(() => {
    if (!map) return;
    const { instance, api } = map;
    instance.setLocation(getLocation(points, editing));
    setHint(undefined);
    const elements = markers.current;
    for (const point of points) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "point-map-pin";
      button.setAttribute("aria-label", point.name);

      function showHint() {
        const area = container.current?.getBoundingClientRect();
        if (!area) return;
        const pin = button.getBoundingClientRect();
        const below = pin.top - area.top < area.height / 2;
        setHint({
          name: point.name,
          x: pin.left + pin.width / 2 - area.left,
          y: below ? pin.bottom - area.top + 6 : pin.top - area.top - 6,
          below,
        });
      }

      button.addEventListener("pointerenter", showHint);
      button.addEventListener("focus", showHint);
      button.addEventListener("pointerleave", () => setHint(undefined));
      button.addEventListener("blur", () => setHint(undefined));
      button.addEventListener("click", () => {
        setSelectedId(point.id);
        setHint(undefined);
      });
      const marker = new api.YMapMarker({
        coordinates: [point.longitude, point.latitude],
        blockEvents: true,
      }, button);
      elements.set(point.id, { marker, button });
      instance.addChild(marker);
    }
    return () => {
      if (activeMap.current === instance) elements.forEach(({ marker }) => instance.removeChild(marker));
      elements.clear();
    };
  }, [map, points, editing]);

  useEffect(() => {
    if (!map) return;
    const { instance, api } = map;
    const listener = new api.YMapListener({
      layer: "any",
      onClick(object, event) {
        if (object?.type === "marker") return;
        setSelectedId(null);
        const [lng, lat] = event.coordinates;
        onPick?.({ lat: Number(lat.toFixed(6)), lng: Number(lng.toFixed(6)) });
      },
    });
    instance.addChild(listener);
    return () => { if (activeMap.current === instance) instance.removeChild(listener); };
  }, [map, onPick]);

  useEffect(() => {
    if (!map || !selection) return;
    const { instance, api } = map;
    const pin = document.createElement("span");
    pin.className = "point-map-pin";
    pin.dataset.state = "selected";
    pin.setAttribute("aria-hidden", "true");
    const marker = new api.YMapMarker({ coordinates: [selection.lng, selection.lat], zIndex: 2 }, pin);
    instance.addChild(marker);
    return () => { if (activeMap.current === instance) instance.removeChild(marker); };
  }, [map, selection]);

  useEffect(() => {
    if (!map || !focus) return;
    const point = points.find((entry) => entry.id === focus.id);
    if (!point) return;
    map.instance.setLocation({ center: [point.longitude, point.latitude], zoom: Math.max(14, map.instance.zoom) });
    setSelectedId(point.id);
    container.current?.scrollIntoView({ block: "nearest" });
  }, [focus, map, points]);

  useEffect(() => {
    for (const point of points) {
      const active = point.id === activeId;
      const pin = markers.current.get(point.id);
      if (!pin) continue;
      pin.button.dataset.state = point.temporarilyClosed ? "closed" : active ? "selected" : "open";
      pin.button.setAttribute("aria-pressed", String(active));
      pin.marker.update({ zIndex: active ? 1 : 0 });
    }
  }, [map, points, activeId]);

  function locate() {
    if (!map) return;
    setLocationError("");
    const failed = () => {
      if (activeMap.current === map.instance) setLocationError("Не удалось определить местоположение. Выберите район вручную.");
    };
    if (!navigator.geolocation) return failed();
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      if (activeMap.current !== map.instance) return;
      map.instance.setLocation({ center: [coords.longitude, coords.latitude], zoom: 14 });
    }, failed, { timeout: 5000 });
  }

  const pointCard = selectedPoint && <div className="yandex-map-popup">
    <button type="button" className="point-popup-close" aria-label="Закрыть карточку пункта" onClick={() => setSelectedId(null)}>
      <svg viewBox="0 0 16 16" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <path d="m4 4 8 8M12 4l-8 8" />
      </svg>
    </button>
    <div className="point-popup-card">
      <img className={selectedPoint.photoUrl ? "point-popup-photo" : "point-popup-photo point-popup-placeholder"}
        src={selectedPoint.photoUrl || "/design/map.svg"} alt="" />
      <div>
        <a href={"/points/" + encodeURIComponent(selectedPoint.id)}>{selectedPoint.name}</a>
        <p>{selectedPoint.address}</p>
        <span className="badge">{selectedPoint.temporarilyClosed ? "Временно закрыт" : selectedPoint.hours}</span>
      </div>
    </div>
  </div>;

  return <section className={editing ? "point-editor-map" : undefined} aria-label="Карта пунктов">
    <div className="map yandex-map">
      <div className="yandex-map-surface" ref={container} />
      {!map && <p className="yandex-map-loading" role="status">Загружается Яндекс Карта…</p>}
      {map && <>
        <div className="yandex-map-zoom">
          <button type="button" className="secondary" aria-label="Приблизить" onClick={() => map.instance.setLocation({ zoom: Math.min(19, map.instance.zoom + 1) })}>+</button>
          <button type="button" className="secondary" aria-label="Отдалить" onClick={() => map.instance.setLocation({ zoom: Math.max(0, map.instance.zoom - 1) })}>−</button>
        </div>
        {!compact && pointCard}
      </>}
    </div>
    {hint && <span className={hint.below ? "point-map-hint below" : "point-map-hint"} aria-hidden="true"
      style={{ "--hint-x": hint.x + "px", top: hint.y } as CSSProperties}>{hint.name}</span>}
    {compact && map && pointCard}
    {!compact && <div className="map-location">
      <button type="button" className="secondary" disabled={!map} onClick={locate}>Мое местоположение</button>
      {locationError && <p role="status">{locationError}</p>}
    </div>}
  </section>;
}
