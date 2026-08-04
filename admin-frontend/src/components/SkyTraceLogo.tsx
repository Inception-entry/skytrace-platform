/** Compact mark for the admin shell and login view. */
export function SkyTraceLogo({ size = 28 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden
    >
      <rect width="32" height="32" rx="8" fill="#1677ff" />
      <path
        d="M8 20.5c3.2-1.2 6.4-5.5 8-9.5 1.6 4 4.8 8.3 8 9.5"
        stroke="#fff"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="16" cy="11" r="2.2" fill="#bfdbfe" />
      <path d="M10 23h12" stroke="rgba(255,255,255,0.55)" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}
