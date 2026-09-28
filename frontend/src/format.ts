export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('es-ES')
}

export function formatPrice(value: number): string {
  return `${value.toFixed(2)} €`
}
