export type EcoMethodology = { itemTypes: { category: string; name: string; averageWeightKg: number }[] };

export type ItemComposition = {
  event: { active: boolean } | null;
  lines: { originLineId: string | null; category: string; quantity: number }[];
};

export type EcoRow = { category: string; quantity: string };

export function newEcoRow(): EcoRow {
  return { category: "UNSPECIFIED", quantity: "1" };
}

export default function ItemEcoFields({ itemTypes, rows, onChange }: {
  itemTypes: EcoMethodology["itemTypes"];
  rows: EcoRow[];
  onChange: (rows: EcoRow[]) => void;
}) {
  function changeRow(index: number, values: Partial<EcoRow>) {
    onChange(rows.map((row, i) => i === index ? { ...row, ...values } : row));
  }

  return <>
    <p>Выбери тип и укажи количество. Примерная масса подставится автоматически.</p>
    {rows.map((row, index) => <fieldset className="listing-eco-row" key={index}>
      <legend>Позиция {index + 1}</legend>
      <label>Тип вещи
        <select value={row.category} onChange={(event) => changeRow(index, { category: event.target.value })}>
          <option value="UNSPECIFIED">Другой тип или неизвестно</option>
          {itemTypes.map(({ category, name, averageWeightKg }) => <option key={category} value={category}>
            {name} (≈ {Math.round(averageWeightKg * 1000)} г за шт.)
          </option>)}
          {row.category !== "UNSPECIFIED" && !itemTypes.some(({ category }) => category === row.category) &&
            <option value={row.category}>Другая категория ({row.category})</option>}
        </select>
      </label>
      <label>Количество, шт.
        <input type="number" min="1" max="1000000" step="1" required value={row.quantity}
          onChange={(event) => changeRow(index, { quantity: event.target.value })} />
      </label>
      {rows.length > 1 && <button type="button" className="secondary" onClick={() => onChange(rows.filter((_, i) => i !== index))}>
        Удалить позицию
      </button>}
    </fieldset>)}
    <button type="button" className="secondary" disabled={rows.length >= 50} onClick={() => onChange([...rows, newEcoRow()])}>
      Добавить позицию
    </button>
  </>;
}
