export const site = {
  name: "MEMORY",
  tagline: "Never lose the context again.",
  description:
    "A private, on-device memory layer for the physical world. Capture a moment, let your phone understand it, and find it again when you need it.",
  github: process.env.NEXT_PUBLIC_GITHUB_URL || "https://github.com/YOUR_GITHUB_USERNAME/memory",
  apk: process.env.NEXT_PUBLIC_APK_URL || "/downloads/MEMORY.apk",
  demo: process.env.NEXT_PUBLIC_DEMO_URL || "https://www.youtube.com/watch?v=YOUR_VIDEO_ID",
  figma: process.env.NEXT_PUBLIC_FIGMA_URL || "https://www.figma.com/file/YOUR_FILE",
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000"
};
