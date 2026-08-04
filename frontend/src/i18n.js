import i18next from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import zhData from './locales/zh'
import enData from './locales/en'
import { getLang } from './utils/lang'

void i18next
  .use(LanguageDetector)
  .init({
    debug: false,
    lng: getLang(),
    supportedLngs: ['zh', 'en'],
    fallbackLng: 'zh',
    detection: {
      order: ['localStorage', 'cookie', 'navigator'],
      lookupLocalStorage: 'skytrace-lang',
      lookupCookie: 'i18next',
      caches: ['localStorage', 'cookie'],
    },
    interpolation: {
      escapeValue: false,
    },
    resources: {
      en: { translation: enData },
      zh: { translation: zhData },
    },
  })

export default i18next
