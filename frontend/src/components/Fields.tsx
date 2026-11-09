import type { Category, Condition, District } from "@/lib/types";

type SelectProps = { name: string; label: string; value?: string; required?: boolean };

export function Categories({ options, name, label, value, required }: SelectProps & { options?: Category[] }) {
  return <label>{label}<select name={name} defaultValue={value || ""} required={required}>
    <option value="">{required ? "Выберите категорию" : "Все категории"}</option>
    {options?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
  </select></label>;
}

export function Districts({ options, name, label, value, required }: SelectProps & { options?: District[] }) {
  return <label>{label}<select name={name} defaultValue={value || ""} required={required}>
    <option value="">{required ? "Выберите район" : "Все районы"}</option>
    {options?.map((district) => <option key={district.id} value={district.id}>{district.name}</option>)}
  </select></label>;
}

export function Conditions({ options, name, label, value }: SelectProps & { options?: Condition[] }) {
  return <label>{label}<select name={name} defaultValue={value || ""}>
    <option value="">Любое состояние</option>
    {options?.map((condition) => <option key={condition.code} value={condition.code}>{condition.label}</option>)}
  </select></label>;
}
