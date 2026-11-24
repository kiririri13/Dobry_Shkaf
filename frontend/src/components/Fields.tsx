import type { ReactNode } from "react";
import type { Category, Condition, District } from "@/lib/types";

type SelectProps = { name: string; label: ReactNode; value?: string; required?: boolean };

export function Categories({ options, name, label, value, required, onChange }: SelectProps & { options?: Category[]; onChange?: (value: string) => void }) {
  return <label>{label}<select name={name} value={onChange ? value : undefined} defaultValue={onChange ? undefined : value || ""}
    onChange={onChange ? (event) => onChange(event.target.value) : undefined} required={required}>
    <option value="">{required ? "Выберите категорию" : "Все категории"}</option>
    {options?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
  </select></label>;
}

export function Districts({ options, name, label, value, required, onChange }: SelectProps & { options?: District[]; onChange?: (value: string) => void }) {
  return <label>{label}<select name={name} value={onChange ? value : undefined} defaultValue={onChange ? undefined : value || ""}
    onChange={onChange ? (event) => onChange(event.target.value) : undefined} required={required}>
    <option value="">{required ? "Выберите район" : "Все районы"}</option>
    {options?.map((district) => <option key={district.id} value={district.id}>{district.name}</option>)}
  </select></label>;
}

export function Conditions({ options, name, label, value, placeholder = "Любое состояние" }: SelectProps & { options?: Condition[]; placeholder?: string }) {
  return <label>{label}<select name={name} defaultValue={value || ""}>
    <option value="">{placeholder}</option>
    {options?.map((condition) => <option key={condition.code} value={condition.code}>{condition.label}</option>)}
  </select></label>;
}
