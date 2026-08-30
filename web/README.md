# MEMORY — Hackathon Judge Website

A premium, monochrome Next.js landing page for the MEMORY Android hackathon project.

## 1. Requirements

- Node.js 20+ recommended
- npm
- Git
- A GitHub repository for the project
- Your Android APK, demo video and Figma/prototype URLs

## 2. Install

```bash
npm install
npm run dev
```

Open `http://localhost:3000`.

## 3. Configure your real links

Copy `.env.example` to `.env.local` and replace the placeholders:

```env
NEXT_PUBLIC_SITE_URL=https://memory.yourdomain.com
NEXT_PUBLIC_GITHUB_URL=https://github.com/YOUR_GITHUB_USERNAME/memory
NEXT_PUBLIC_APK_URL=/downloads/MEMORY.apk
NEXT_PUBLIC_DEMO_URL=https://www.youtube.com/watch?v=YOUR_VIDEO_ID
NEXT_PUBLIC_FIGMA_URL=https://www.figma.com/file/YOUR_FILE
```

Alternatively, edit `lib/site.ts` directly.

## 4. Add the APK

Put the final Android APK at:

`public/downloads/MEMORY.apk`

The site's **Download APK** buttons will then work automatically.

For a hackathon, keep the APK reasonably small and include the version in the filename if you prefer, e.g. `MEMORY-v0.1.apk`, then change `NEXT_PUBLIC_APK_URL` accordingly.

## 5. Add real screenshots

The current page deliberately uses clean product mockups so it runs immediately. For the strongest judge-facing version, replace the three MVP mockups in `app/page.tsx` with screenshots from your actual working Android prototype.

Do not use screenshots of features that are not implemented. Clearly label planned features as planned.

## 6. Add your demo

Use a 45–60 second real screen recording:

1. Capture a photo or voice memory.
2. Show local processing / memory creation.
3. Ask a natural-language question.
4. Show the retrieved answer.
5. End with the local/on-device message.

The **Watch demo** buttons use `NEXT_PUBLIC_DEMO_URL`.

## 7. Test production build

```bash
npm run build
npm start
```

If this succeeds, the app is ready for deployment.

## 8. Deploy on Vercel

### GitHub route

1. Create a GitHub repository.
2. Push this folder to the repository.
3. Go to Vercel and import the repository.
4. Add the environment variables from `.env.local` in the Vercel project settings.
5. Deploy.
6. Add your custom domain if you have one.

### CLI route

```bash
npm install -g vercel
vercel
```

Then follow the prompts. For production:

```bash
vercel --prod
```

## 9. Submission checklist

Before sending the URL to judges:

- [ ] Landing page opens on mobile and desktop.
- [ ] Download APK works.
- [ ] APK installs on a clean Android device.
- [ ] Demo video opens without login.
- [ ] GitHub repository is public if the rules permit it.
- [ ] Figma/prototype link works.
- [ ] No placeholder URLs remain.
- [ ] No feature is described as working if it is only planned.
- [ ] QR code in the site points to the final public URL.
- [ ] Test the URL from an incognito browser / mobile network.

## 10. Recommended judge journey

`PPT QR → MEMORY website → 60-sec demo → Download APK → GitHub`

The website is intentionally a front door, not a replacement for the Android app.
