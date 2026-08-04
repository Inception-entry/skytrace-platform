import Cookies from 'js-cookie'

const langKey = 'skytrace-lang'
const cookieKey = 'i18next'

export type LangKey = 'zh' | 'en'

export function normalizeLang(raw: string | null | undefined): LangKey {
  if (!raw) return 'zh'
  const lower = raw.toLowerCase()
  if (lower.startsWith('en')) return 'en'
  return 'zh'
}

export function getLang(): LangKey {
  return normalizeLang(
    localStorage.getItem(langKey) || Cookies.get(cookieKey) || 'zh',
  )
}

export function setLang(key: string) {
  const lang = normalizeLang(key)
  localStorage.setItem(langKey, lang)
  Cookies.set(cookieKey, lang, { expires: 365 })
}

export function removeLang() {
  localStorage.removeItem(langKey)
  Cookies.remove(cookieKey)
}
