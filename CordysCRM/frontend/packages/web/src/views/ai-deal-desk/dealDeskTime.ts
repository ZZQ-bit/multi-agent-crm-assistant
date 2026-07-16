export function formatDealDeskClockTime(date = new Date()) {
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

export function formatDealDeskSessionTime(date = new Date(), now = new Date()) {
  const clockTime = formatDealDeskClockTime(date);
  const targetStart = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  yesterday.setDate(yesterday.getDate() - 1);

  if (targetStart === todayStart) return `今天 ${clockTime}`;
  if (targetStart === yesterday.getTime()) return `昨天 ${clockTime}`;

  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${month}-${day} ${clockTime}`;
}
