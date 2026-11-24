"use client";

import { useLayoutEffect, useRef, type ReactNode } from "react";

export default function UnderlineTabs({ label, className = "", children }: {
  label: string;
  className?: string;
  children: ReactNode;
}) {
  const bar = useRef<HTMLElement>(null);
  const line = useRef<HTMLSpanElement>(null);

  useLayoutEffect(() => {
    const tabs = bar.current;
    const underline = line.current;
    if (!tabs || !underline) return;
    const active = tabs.querySelector('[aria-pressed="true"], [aria-current="page"]');
    if (!active) return;

    const position = () => {
      const area = tabs.getBoundingClientRect();
      const tab = active.getBoundingClientRect();
      underline.style.width = tab.width + "px";
      underline.style.transform = `translate(${tab.left - area.left}px, ${tab.bottom - area.top - 2}px)`;
    };

    position();
    const observer = new ResizeObserver(position);
    observer.observe(tabs);
    tabs.querySelectorAll("button, a").forEach((tab) => observer.observe(tab));
    return () => observer.disconnect();
  });

  return <nav ref={bar} className={`profile-tabs underline-tabs ${className}`} aria-label={label}>
    {children}
    <span ref={line} className="tab-underline" aria-hidden="true" />
  </nav>;
}
