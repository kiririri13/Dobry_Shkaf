import type { ReactNode } from "react";
import Navigation from "@/components/Navigation";
import { Session } from "@/components/Session";
import "./globals.css";

export const metadata = { title: "Добрый шкаф", description: "Сервис бесплатной передачи вещей" };

export default function Layout({ children }: { children: ReactNode }) {
  return <html lang="ru"><body>
    <Session>
      <div className="site">
        <Navigation />
        <main>{children}</main>
      </div>
    </Session>
  </body></html>;
}
