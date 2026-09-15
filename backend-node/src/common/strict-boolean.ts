/**
 * Query/body 布尔必须显式 true/false。
 * 不要用 Boolean(value)：Boolean('false') === true。
 */
export function strictBoolean(value: unknown): unknown {
  if (value === true || value === 'true') {
    return true
  }
  if (value === false || value === 'false') {
    return false
  }
  return value
}
