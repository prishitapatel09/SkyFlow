interface Props {
  label?: string
}

export default function Spinner({ label = 'Loading…' }: Props) {
  return (
    <div className="loading-block">
      <span className="spinner spinner-dark" /> {label}
    </div>
  )
}
