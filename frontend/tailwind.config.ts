import type { Config } from "tailwindcss";
import { fontFamily } from "tailwindcss/defaultTheme";

const config: Config = {
  darkMode: ["class"],
  content: [
    "./index.html",
    "./src/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      // ── Responsive breakpoints
      screens: {
        'xs': '480px',
        '3xl': '1920px',
      },

      // ── HMS Clinical Design System
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",

        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },

        // ── Hospital theme colour
        //  The application was built on hardcoded `neutral-*` classes — 2,400+ of
        //  them — and neutral-500/600/700 are what actually carry the brand:
        //  buttons, headers, gradients and focus rings. Rather than rewrite every
        //  call site, those three shades read from CSS variables that the hospital's
        //  chosen colour sets at runtime. Every other shade stays a true grey, so
        //  body text, borders and backgrounds are untouched.
        //
        //  Channel format (`R G B`) rather than hex, because `bg-neutral-600/30` is
        //  used in the codebase and the alpha modifier needs separable channels.
        //  Defaults in index.css reproduce Tailwind's own neutral scale exactly, so
        //  a hospital that has chosen nothing looks precisely as it did before.
        neutral: {
          500: "rgb(var(--brand-500) / <alpha-value>)",
          600: "rgb(var(--brand-600) / <alpha-value>)",
          700: "rgb(var(--brand-700) / <alpha-value>)",
        },

        // ── Clinical status colors (HMS-specific)
        clinical: {
          draft:            { DEFAULT: "#E8F4FD", text: "#1565C0", border: "#90CAF9" },
          settled:          { DEFAULT: "#E8F5E9", text: "#2E7D32", border: "#A5D6A7" },
          "partially-settled": { DEFAULT: "#FFF8E1", text: "#F57F17", border: "#FFE082" },
          refunded:         { DEFAULT: "#FCE4EC", text: "#880E4F", border: "#F48FB1" },
          cancelled:        { DEFAULT: "#FAFAFA", text: "#616161", border: "#BDBDBD" },
          "checked-in":     { DEFAULT: "#E3F2FD", text: "#0D47A1", border: "#90CAF9" },
          "in-consultation":{ DEFAULT: "#EDE7F6", text: "#4527A0", border: "#B39DDB" },
          discharged:       { DEFAULT: "#E8F5E9", text: "#1B5E20", border: "#81C784" },
        },

        // ── Bed status colors
        bed: {
          available:   { DEFAULT: "#E8F5E9", text: "#1B5E20", border: "#81C784" },
          allocated:   { DEFAULT: "#FFF3E0", text: "#E65100", border: "#FFCC80" },
          maintenance: { DEFAULT: "#FAFAFA", text: "#424242", border: "#BDBDBD" },
        },
      },

      fontFamily: {
        sans: ["Inter", "IBM Plex Sans", ...fontFamily.sans],
        mono: ["IBM Plex Mono", ...fontFamily.mono],
      },

      fontSize: {
        "2xs": ["0.625rem", { lineHeight: "0.875rem" }],
      },

      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },

      keyframes: {
        "accordion-down": {
          from: { height: "0" },
          to: { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to: { height: "0" },
        },
        "fade-in": {
          from: { opacity: "0", transform: "translateY(4px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "slide-in-right": {
          from: { transform: "translateX(100%)" },
          to: { transform: "translateX(0)" },
        },
      },

      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
        "fade-in": "fade-in 0.2s ease-out",
        "slide-in-right": "slide-in-right 0.3s ease-out",
      },

      spacing: {
        "sidebar": "240px",
        "topbar": "56px",
      },
    },
  },
  plugins: [
    require("@tailwindcss/forms")({ strategy: "class" }),
    require("@tailwindcss/typography"),
    require("tailwindcss-animate"),
  ],
};

export default config;
