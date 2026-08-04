const themeKey = 'skytrace-theme'

export function getTheme() {
  return localStorage.getItem(themeKey) || ''
}

export function setTheme(key: string) {
  localStorage.setItem(themeKey, key)
}

export function removeTheme() {
  localStorage.removeItem(themeKey)
}
