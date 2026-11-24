import UnderlineTabs from "./UnderlineTabs";

export const profileSections = ["Объявления", "История", "Отзывы", "Вклад в экологию"] as const;

export default function ProfileTabs({ active, own, onChange }: {
  active: number;
  own?: boolean;
  onChange: (section: number) => void;
}) {
  return <UnderlineTabs label="Разделы профиля" className="profile-sections">
    {profileSections.map((label, index) => <button type="button" key={label} aria-pressed={active === index} onClick={() => onChange(index)}>
      {index === 0 ? own ? "Мои объявления" : "Объявления пользователя" : label}
    </button>)}
  </UnderlineTabs>;
}
