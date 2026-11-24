"use client";

import { useCallback, useState } from "react";
import type { Point } from "@/lib/types";
import YandexPointMap from "./YandexPointMap";

export type Coordinates = { lat: number; lng: number };

export type PointMapProps = {
  points: Point[];
  focus?: { id: string };
  onPick?: (coordinates: Coordinates) => void;
  selection?: Coordinates;
  showFirst?: boolean;
  compact?: boolean;
};

const yandexKey = process.env.NEXT_PUBLIC_YANDEX_MAPS_API_KEY?.trim();

export default function PointMap(props: PointMapProps) {
  const [unavailable, setUnavailable] = useState(false);
  const showError = useCallback(() => setUnavailable(true), []);

  return <div className={props.compact ? "point-map-shell point-map-compact" : "point-map-shell"}>
    {yandexKey && !unavailable ? <YandexPointMap {...props} apiKey={yandexKey} onUnavailable={showError} />
      : <div className={props.onPick ? "point-editor-map" : undefined}>
        <div className="map map-unavailable" role="status">
          <p>Яндекс Карта недоступна.</p>
          {yandexKey && <button type="button" className="secondary" onClick={() => setUnavailable(false)}>Повторить</button>}
        </div>
      </div>}
  </div>;
}
