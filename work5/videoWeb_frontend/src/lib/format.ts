export function formatCount(value: number | undefined) {
  const count = value ?? 0;
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1)}w`;
  }
  return `${count}`;
}

export function formatDate(value?: string | null) {
  if (!value) {
    return '暂无时间';
  }

  const maybeDate = new Date(value);
  if (Number.isNaN(maybeDate.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(maybeDate);
}

export function toTimestampInput(value?: string | null) {
  if (!value) {
    return '';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }

  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function dateInputToTimestamp(value: string) {
  if (!value) {
    return undefined;
  }
  return new Date(value).getTime();
}
