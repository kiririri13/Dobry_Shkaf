type Props = { loading?: boolean; error?: string; retry?: () => void };

export default function RequestStatus({ loading, error, retry }: Props) {
  if (loading) return <p role="status">Загрузка…</p>;
  if (!error) return null;
  return <div className="error" role="alert">
    <p>{error}</p>
    {retry && <button type="button" className="secondary" onClick={retry}>Повторить</button>}
  </div>;
}
