import "./globals.css";
import { Outfit } from "next/font/google";

const outfit = Outfit({ subsets: ["latin", "latin-ext"], weight: ["400", "500", "600", "700"] });

export const metadata = {
  title: "Nanas' Kitchens",
  description: "Home-cooked cultural food from real kitchens near you.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={outfit.className}>{children}</body>
    </html>
  );
}
