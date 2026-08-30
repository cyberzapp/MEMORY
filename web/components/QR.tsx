"use client";

import { QRCodeSVG } from "qrcode.react";

export default function QR({ value }: { value: string }) {
  return <QRCodeSVG value={value} size={156} bgColor="#ffffff" fgColor="#111111" level="M" includeMargin />;
}
