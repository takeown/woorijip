type BrandLogoProps = {
  className?: string;
};

export function BrandLogo({ className }: BrandLogoProps) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="none"
      viewBox="0 0 64 64"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M10 27 32 8l22 19"
        stroke="#047857"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="7"
      />
      <circle cx="24" cy="38" fill="#047857" r="6.5" />
      <circle cx="40" cy="38" fill="#A3B18A" r="6.5" />
      <path
        d="M20 52h24"
        stroke="#44403C"
        strokeLinecap="round"
        strokeWidth="6"
      />
    </svg>
  );
}
