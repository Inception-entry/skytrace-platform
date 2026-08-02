import Cookies from 'js-cookie'

const langKey = 'skytrace-lang';

export function getLang() {
  const langValue = Cookies.get('i18next')
  return langValue || '';
}

export function setLang(key: string) {
  localStorage.setItem(langKey, key);
}

export function removeLang() {
  localStorage.setItem(langKey, '');
}