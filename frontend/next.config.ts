import type { NextConfig } from "next";

const backend = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");

const config: NextConfig = {
  devIndicators: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: backend + "/api/:path*" }];
  },
};

export default config;
