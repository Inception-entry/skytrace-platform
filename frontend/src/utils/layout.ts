const layoutKey = 'skytrace-layout';

export function getLayout() {
  return localStorage.getItem(layoutKey) || '';
}

export function setLayout(key: string) {
  localStorage.setItem(layoutKey, key);
}

export function removeLayout() {
  localStorage.setItem(layoutKey, '');
}