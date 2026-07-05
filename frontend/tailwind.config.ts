import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        border: "hsl(var(--border))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        muted: "hsl(var(--muted))",
        "muted-foreground": "hsl(var(--muted-foreground))",
        brand: "hsl(var(--brand))",
        "brand-foreground": "hsl(var(--brand-foreground))",
        "comp-bg": "hsl(var(--comp-bg))",
        destructive: "hsl(var(--destructive))",
      },
      boxShadow: {
        panel: "0 16px 40px rgba(22, 36, 62, 0.08)",
      },
    },
  },
  plugins: [],
} satisfies Config;
