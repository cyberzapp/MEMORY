import { site } from "@/lib/site";
import QR from "@/components/QR";
import { Camera, Mic, Eye, Brain, Search, ArrowRight } from "lucide-react";

const steps = [
  ["01", "Capture", "Take a quick photo or record a short voice note."],
  ["02", "Understand", "MEMORY extracts the useful details from the moment on the device."],
  ["03", "Remember", "It keeps a compact memory with what, where and when it mattered."],
  ["04", "Recall", "Ask naturally later and MEMORY finds the relevant moment."],
];

const useCases = [
  ["WHERE DID I PUT IT?", "Remember where you left a charger, document, tool or other item."],
  ["WHAT WAS THAT?", "Recall a serial number, product detail, diagram, notice or component you saw."],
  ["WHAT HAPPENED?", "Connect intentionally captured photos, voice notes and time into a simple memory timeline."],
  ["REMIND ME", "Turn an important captured moment into a future reminder."],
];

const models = [
  ["SEE", "ML Kit", "Local object detection and OCR."],
  ["HEAR", "On-device speech", "Turns intentional voice captures into searchable text."],
  ["FIND", "MiniLM", "Creates compact semantic representations for meaning-based search."],
  ["REASON", "Gemma 3 1B", "Structures memories and formulates answers from retrieved context."],
];

export default function Home() {
  return (
    <main>
      <header className="nav shell">
        <a className="brand" href="#top" aria-label="MEMORY home">MEMORY<span>•</span></a>
        <nav>
          <a href="#how">How it works</a>
          <a href="#mvp">MVP</a>
          <a href="#tech">Technology</a>
          <a href="#try">Try it</a>
        </nav>
        <a className="nav-cta" href={site.apk}>Download APK <span>↗</span></a>
      </header>

      <section id="top" className="hero shell">
        <div className="hero-copy">
          <p className="eyebrow">A PRIVATE, ON-DEVICE MEMORY LAYER</p>
          <h1>You saw it.<br />You knew it mattered.<br /><em>Then you forgot.</em></h1>
          <p className="hero-text">{site.description}</p>
          <div className="actions">
            <a className="button dark" href="#mvp">See the working MVP <span>↓</span></a>
            {/* <a className="button light" href={site.demo} target="_blank" rel="noreferrer">Watch 60-sec demo <span>↗</span></a> */}
          </div>
          <div className="trust-row"><span>PHOTO</span><i /> <span>VOICE</span><i /> <span>LOCAL AI</span><i /> <span>LOCAL MEMORY</span></div>
        </div>

        <div className="hero-device" aria-label="MEMORY product preview">
          <img src="/screens/Home-screen.png" alt="Home Screen" className="phone-screen-image" />
          <div className="float-card card-a"><span>CAPTURED</span><strong>Laptop + charger</strong><small>09:04 AM</small></div>
          <div className="float-card card-b"><span>LOCAL</span><strong>No cloud required</strong><small>for the core memory loop</small></div>
        </div>
      </section>

      <section className="statement section shell">
        <p className="eyebrow">THE PROBLEM</p>
        <h2>Our phones save the moment.<br /><span>They don't save why it mattered.</span></h2>
        <div className="question-grid">
          <div><b>“Where did I put my charger?”</b><small>You remember having it. Not where you left it.</small></div>
          <div><b>“What was that serial number?”</b><small>You saw it once. Now you need it again.</small></div>
          <div><b>“Where did I see that component?”</b><small>The photo exists somewhere in your gallery.</small></div>
          <div><b>“What happened around 9 AM?”</b><small>The context is scattered across different places.</small></div>
        </div>
      </section>

      <section id="how" className="section soft">
        <div className="shell">
          <p className="eyebrow">THE SIMPLE IDEA</p>
          <div className="split-head"><h2>Just tell your phone:<br /><em>“Remember this.”</em></h2><p>No folders. No tagging. No long forms. Capture the moment and move on. MEMORY does the organizing locally.</p></div>
          <div className="steps">
            {steps.map(([num, title, text]) => <article className="step" key={num}><span>{num}</span><h3>{title}</h3><p>{text}</p></article>)}
          </div>
        </div>
      </section>

      <section id="mvp" className="section shell">
        <div className="section-top"><div><p className="eyebrow">WORKING MVP</p><h2>The core memory loop<br />already works.</h2></div>{/* <a className="text-link" href={site.demo} target="_blank" rel="noreferrer">Watch the demo ↗</a> */}</div>
        <div className="mvp-grid">
          <div className="mvp-card"><img src="/screens/CAPTURE.png" alt="Capture a memory" className="mvp-screenshot" /><div className="card-caption"><span>01</span><div><b>CAPTURE</b><p>Save a moment intentionally.</p></div></div></div>
          <div className="mvp-card"><img src="/screens/UNDERSTAND.png" alt="Useful context extracted" className="mvp-screenshot" /><div className="card-caption"><span>02</span><div><b>UNDERSTAND</b><p>Find what matters in the capture.</p></div></div></div>
          <div className="mvp-card"><img src="/screens/RECALL.png" alt="Ask naturally when you need it" className="mvp-screenshot" /><div className="card-caption"><span>03</span><div><b>RECALL</b><p>Ask naturally when you need it.</p></div></div></div>
        </div>
        <div className="built-strip"><strong>BUILT NOW</strong><span>Photo capture</span><i /> <span>Voice capture</span><i /> <span>AI extraction</span><i /> <span>Local memory</span><i /> <span>Semantic recall</span></div>
      </section>

      <section className="section dark-section">
        <div className="shell">
          <p className="eyebrow">WHAT MEMORY IS FOR</p>
          <h2>Useful moments shouldn't<br /><em>disappear into your gallery.</em></h2>
          <div className="use-grid">{useCases.map(([title, text]) => <article key={title}><span>+</span><h3>{title}</h3><p>{text}</p></article>)}</div>
        </div>
      </section>

      <section id="tech" className="section shell">
        <p className="eyebrow">ON-DEVICE AI</p>
        <div className="split-head"><h2>A local pipeline,<br /><em>not one giant model.</em></h2><p>Specialized models handle the cheap, repeatable work. A small local language model is used when the system actually needs reasoning.</p></div>
        <div className="model-grid">{models.map(([label, model, text]) => <article key={label}><span>{label}</span><h3>{model}</h3><p>{text}</p></article>)}</div>
        <div className="pipeline"><div><Camera size={20}/><Mic size={20}/><b>Capture</b></div><i><ArrowRight size={16}/></i><div><Eye size={20}/><b>Understand</b></div><i><ArrowRight size={16}/></i><div><Brain size={20}/><b>Create memory</b></div><i><ArrowRight size={16}/></i><div><Search size={20}/><b>Recall locally</b></div></div>
        <p className="tech-note"><b>No cloud required for the core memory loop.</b> The exact runtime footprint and acceleration path are validated on real Android hardware.</p>
      </section>

      <section className="section phone-section">
        <div className="shell phone-layout">
          <div><p className="eyebrow">WHY A PHONE?</p><h2>The phone isn't where MEMORY runs.<br /><em>The phone is MEMORY.</em></h2><p className="large-copy">The camera sees the moment. The microphone hears the context. Local compute understands it. Storage keeps the memory close.</p></div>
          <div className="hardware-grid"><div><b>CAMERA</b><span>Sees what you want to remember.</span></div><div><b>MICROPHONE</b><span>Captures spoken context.</span></div><div><b>LOCAL COMPUTE</b><span>Understands without a round trip to a server.</span></div><div><b>STORAGE</b><span>Keeps the memory index on-device.</span></div></div>
        </div>
      </section>

      <section className="section shell">
        <p className="eyebrow">30-HOUR CITY BATTLE</p>
        <div className="split-head"><h2>We aren't starting<br /><em>from zero.</em></h2><p>The risky part—the core capture, understanding and recall loop—is already working. The next 30 hours make it fast, effortless and device-optimized.</p></div>
        <div className="roadmap">
          <article className="done"><span>NOW</span><h3>Core memory loop</h3><p>Capture → understand → remember → recall</p><b>✓ WORKING MVP</b></article>
          {["0–6 HRS|Make capture instant|One-tap photo and voice capture.", "6–12 HRS|Bring memories back|Scheduled reminders and notifications.", "12–18 HRS|Add more context|Time, location and a simple memory timeline.", "18–24 HRS|Make it hands-free|Android Assistant / App Actions.", "24–30 HRS|Make it fast on iQOO|RAM, latency, thermals and local AI benchmarking."].map(item => { const [time, title, text] = item.split("|"); return <article key={time}><span>{time}</span><h3>{title}</h3><p>{text}</p></article> })}
        </div>
      </section>

      <section id="try" className="section try-section">
        <div className="shell try-layout">
          <div><p className="eyebrow">TRY THE MVP</p><h2>Capture once.<br /><em>Remember later.</em></h2><p>Watch the short demo or install the Android build to experience the current MVP.</p><div className="actions"><a className="button white" href={site.apk}>Download APK ↗</a><a className="button outline-white" href={site.github} target="_blank" rel="noreferrer">View GitHub ↗</a></div></div>
          <div className="qr-card"><QR value={site.siteUrl} /><strong>Scan to open MEMORY</strong></div>
        </div>
      </section>

      <footer className="footer shell"><div><a className="brand" href="#top">MEMORY<span>•</span></a><p>Never lose the context again.</p></div><div className="footer-links"><a href={site.github} target="_blank" rel="noreferrer">GitHub ↗</a><a href={site.demo} target="_blank" rel="noreferrer">Demo ↗</a><a href={site.figma} target="_blank" rel="noreferrer">Prototype ↗</a></div><small>Built for Android · Privacy-first · On-device AI</small></footer>
    </main>
  );
}
