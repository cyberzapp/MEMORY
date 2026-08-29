# MEMORY

### A private, on-device memory layer for the physical world.

> **Capture once. Remember later. Keep it on-device.**

MEMORY is a phone-first personal memory system that turns intentionally captured photos and voice notes into compact, searchable memories.

Instead of forcing users to remember where they saw, placed, or learned something, MEMORY lets the phone understand the captured moment — extracting objects, text, time, and context — and later retrieve that information through natural-language queries.

The core memory pipeline is designed to run locally on the Android device, minimizing cloud dependency and reducing the need to permanently store large amounts of raw media.

---

## Why MEMORY?

Our phones are excellent at storing **media**, but poor at remembering **context**.

You may remember:

- seeing a serial number on a device
- putting a charger inside a drawer
- seeing a particular component somewhere
- reading something important on a document
- capturing a moment but forgetting why it mattered

Later, you remember the **question**, but not the answer:

> "Where did I put my laptop charger?"

> "What was the serial number I saw yesterday?"

> "Where did I see that component?"

> "What happened around 9 AM?"

Traditional photo galleries and notes make the user search through files or manually maintain information.

MEMORY explores a different approach:

> **Turn captured moments into semantic memories that can be retrieved by meaning rather than filenames.**

---

# What MEMORY Does

### 1. Capture a Memory

Users intentionally create a memory using:

- 📸 A photo
- 🎙️ A voice note
- 📝 Text/context provided by the user

Example:

> "I kept my laptop charger in the second drawer."

---

### 2. Understand the Capture Locally

The device extracts useful information such as:

- objects
- visible text
- serial numbers
- spoken context
- timestamp
- available location/context metadata

The goal is to transform a large media file into a compact representation of what mattered.

Example:

```text
Captured Media
      ↓
Laptop + Charger + Desk
      ↓
"I placed the laptop charger in the second drawer."
      ↓
Structured Memory
````

---

### 3. Create a Compact Memory

Instead of treating the original image as the memory itself, MEMORY creates a structured representation containing relevant information.

Example:

```text
OBJECT
Laptop Charger

ACTION
Placed

LOCATION
Second Drawer

CONTEXT
Study Desk

TIME
09:06 AM
```

The memory is also converted into a semantic embedding for later retrieval.

---

### 4. Recall Naturally

Users can ask questions in normal language:

> **"Where is the thing I use to charge my laptop?"**

MEMORY performs local semantic retrieval and finds the most relevant memories.

Example response:

> **Your USB-C charger was last recorded in the second drawer of your study desk.**

The user does not need to remember the exact wording used during capture.

---

### 5. Turn Memories Into Reminders

A memory can optionally become a future task.

Example:

> "Remind me tomorrow at 9 AM to check the documents in this drawer."

The planned reminder layer allows MEMORY to connect:

```text
Memory
   +
Time
   ↓
Reminder
```

---

# Core Architecture

```text
                         MEMORY
                            │
                     CAPTURE LAYER
                            │
                ┌───────────┴───────────┐
                ↓                       ↓
             📸 PHOTO                🎙️ VOICE
                │                       │
                ↓                       ↓
        ML Kit Object Detection   Speech-to-Text
                │                       │
                ↓                       ↓
              Objects               Transcript
                │                       │
                └───────────┬───────────┘
                            ↓
                           OCR
                            │
                            ↓
                    RAW EVIDENCE BUNDLE
                            │
                            ↓
                     GEMMA 3 1B
                    Memory Extraction
                            │
                            ↓
                 STRUCTURED MEMORY
                            │
                 ┌──────────┴──────────┐
                 ↓                     ↓
          Embedding Model          Metadata
          all-MiniLM-L6-v2             │
                 │                     │
                 ↓                     │
           384-D Vector                │
                 │                     │
                 └──────────┬──────────┘
                            ↓
                     LOCAL MEMORY STORE
                            │
                            ↓
                      LOCAL INDEX
                            │
                            ↓
                     NATURAL QUERY
                            │
                            ↓
                    Query Embedding
                            │
                            ↓
                  Vector Similarity
                            │
                            ↓
                     Top-K Memories
                            │
                            ↓
                      GEMMA 3 1B
                    Local Recall Layer
                            │
                            ↓
                       ANSWER
```

---

# Query Flow

For a question such as:

> **"Where's the thing I use to charge my laptop?"**

MEMORY does not send the entire memory database to an LLM.

Instead:

```text
User Query
    ↓
all-MiniLM-L6-v2
    ↓
Query Vector
    ↓
Local Vector Similarity
    ↓
Top-K Relevant Memories
    ↓
Gemma 3 1B
    ↓
Natural-Language Answer
```

The retrieval stage is intentionally separated from generative reasoning.

### Why?

Vector similarity is cheap mathematical computation.

There is no reason to invoke a generative model just to determine which memories are semantically similar.

The LLM is used only after relevant memories have been retrieved.

---

# On-Device AI Stack

| Capability              | Technology                                                  | Purpose                                            |
| ----------------------- | ----------------------------------------------------------- | -------------------------------------------------- |
| Object Detection        | ML Kit Object Detection                                     | Fast local object detection                        |
| OCR                     | ML Kit Text Recognition                                     | Extract visible text and serial numbers            |
| Voice → Text            | Android SpeechRecognizer / on-device speech where available | Convert voice memories into text                   |
| Semantic Embeddings     | `all-MiniLM-L6-v2`                                          | Convert memories and queries into semantic vectors |
| Memory Extraction       | Gemma 3 1B                                                  | Convert raw evidence into structured memory        |
| Semantic Retrieval      | Local vector similarity                                     | Retrieve relevant memories without an LLM          |
| Natural-Language Recall | Gemma 3 1B                                                  | Generate an answer from retrieved memories         |
| Local Storage           | SQLite / local storage layer                                | Store memory metadata and representations          |

### Model footprint

The system is designed around small, mobile-oriented models rather than a single large AI model.

The current target includes:

* lightweight ML Kit perception
* quantized `all-MiniLM-L6-v2`
* quantized Gemma 3 1B
* local vector retrieval

Actual memory consumption and inference latency depend on model quantization, runtime backend, Android version, and device hardware.

---

# Phone-First Design

MEMORY is not a web application moved onto a phone.

The phone is the primary sensing and computing platform.

### 📷 Camera

Captures the physical context around the user.

### 🎙️ Microphone

Captures spoken context and intentional voice memories.

### ⚡ On-device AI acceleration

Enables local inference for perception, embeddings, and language processing.

### 💾 Local storage

Maintains the user's private memory index on-device.

### 📍 Device context

Timestamp and optional location can provide additional context to memories.

### 📳 Notifications

Planned reminder functionality can bring relevant memories back to the user at the right time.

---

# Privacy by Design

MEMORY is designed around a simple principle:

> **Your memories should not need to leave your phone.**

The core pipeline is designed for local processing:

```text
Capture
   ↓
On-device AI
   ↓
Structured Memory
   ↓
Local Embedding
   ↓
Local Storage
   ↓
Local Retrieval
   ↓
Local Response
```

The long-term goal is to avoid sending personal photos, voice recordings, and memory queries to a remote server for the core experience.

---

# Memory Instead of Media

A central design principle of MEMORY is **data minimization**.

A high-resolution photograph may contain several megabytes of data, while the useful information extracted from it can be represented much more compactly.

For example:

```text
Original Image
     ↓
Laptop
Charger
Desk
Time
Context
     ↓
Structured Memory
     +
Embedding
     +
Small Evidence Thumbnail
```

Once the memory has been successfully created and verified, the product can minimize or remove unnecessary high-resolution source media.

> **The goal is to preserve what mattered, not indefinitely preserve every byte of the original capture.**

During development, raw captures may be retained temporarily for debugging and validation.

---

# Current MVP

The current prototype focuses on validating the fundamental memory loop.

### Currently implemented

* [x] Photo-based memory capture
* [x] Voice-based memory capture
* [x] On-device object/text processing
* [x] Memory extraction
* [x] Local semantic representation
* [x] Semantic memory retrieval
* [x] Natural-language recall
* [x] Local-first memory architecture

### Planned next

* [ ] Android notification-based reminders
* [ ] One-tap quick capture
* [ ] Home-screen shortcut/widget
* [ ] Android Assistant / App Actions integration
* [ ] Improved spatial and temporal context
* [ ] Memory timeline
* [ ] More robust offline speech recognition
* [ ] Device-specific AI acceleration
* [ ] Performance and thermal benchmarking
* [ ] Smarter media minimization

---

# Example User Journey

### Capture

User photographs their desk.

```text
📸
Laptop
Charger
Notebook
Desk
```

MEMORY processes the capture locally.

---

### Memory

```text
Study Desk
09:04 AM

Laptop
USB-C Charger
Notebook
```

---

### Recall

Later, the user asks:

> **"Where did I put my charger?"**

MEMORY retrieves the relevant memory.

```text
USB-C Charger

Last recorded:
Study Desk
09:04 AM

Evidence:
Beside your laptop
```

---

# Future Interaction

The long-term goal is to make memory capture nearly frictionless.

Instead of opening the application and navigating through menus, users should be able to trigger specific capture actions through Android-native interactions.

For example:

> **"Hey Google, ask MEMORY to remember this."**

MEMORY could immediately open a quick capture flow.

Later:

> **"Ask MEMORY where I put my laptop charger."**

The assistant invokes MEMORY, which performs local semantic retrieval and returns the relevant memory.

This functionality is a planned integration and is not required for the current core MVP.

---

# What Makes MEMORY Different?

MEMORY is not intended to be:

* another photo gallery
* another note-taking application
* a cloud AI chatbot
* an always-recording life logger

The core idea is:

### **Intentional capture → semantic understanding → compact memory → local recall**

Instead of asking:

> "Which file contains this?"

MEMORY aims to answer:

> **"What do I remember about this?"**

---

# Design Principles

### 1. Local First

Core memory operations should work without requiring a cloud backend.

### 2. User Controlled

The user decides what becomes a memory.

### 3. Context Over Files

Memories should represent meaning, not filenames.

### 4. Small Models, Smart Pipeline

Use specialized lightweight models where possible and generative AI only where reasoning is required.

### 5. Data Minimization

Preserve useful information while minimizing unnecessary raw media.

### 6. Phone as the AI Engine

Camera, microphone, sensors, storage and local compute are fundamental to the product.

---

# Performance Goals

The project will be benchmarked on real Android hardware rather than relying only on theoretical model specifications.

Key metrics:

| Metric                  | Target            |
| ----------------------- | ----------------- |
| Memory creation latency | Measure on-device |
| Query latency           | Measure on-device |
| Peak RAM usage          | Measure on-device |
| Model storage footprint | Measure           |
| Offline functionality   | Validate          |
| Thermal behavior        | Benchmark         |
| Battery impact          | Benchmark         |
| 6 GB RAM compatibility  | Validate          |
| 8 GB RAM compatibility  | Validate          |

The goal is to use lightweight models on lower-memory devices while taking advantage of higher-performance iQOO hardware where available.

---

# Roadmap

```text
PHASE 1 — CORE MEMORY
Photo / Voice
      ↓
AI Extraction
      ↓
Local Memory
      ↓
Semantic Recall

             ✅ CURRENT MVP


PHASE 2 — CONTEXT
Time
Location
Timeline
Evidence


PHASE 3 — QUICK ACCESS
Home-screen shortcut
Quick Capture
Notifications


PHASE 4 — ASSISTANT
Android Assistant
Voice-triggered capture
Voice-triggered recall


PHASE 5 — DEVICE OPTIMIZATION
NPU acceleration
Quantization
Latency optimization
Thermal profiling
6/8 GB device testing
```

---

# Hackathon Focus

MEMORY is being developed as a **phone-first, on-device AI system** for the iQOO Hackathon.

The objective is not simply to demonstrate an AI model.

The objective is to demonstrate how a high-performance Android device can become a **private personal memory engine** by combining:

* real-time mobile perception
* on-device language processing
* semantic embeddings
* local retrieval
* local storage
* natural-language interaction
* device hardware acceleration

The 30-hour build will focus on turning the current core MVP into a polished, performant Android experience and validating the system on the competition hardware.

---

# Tech Stack

### Android

* Android
* CameraX / Android Camera APIs
* Android local storage
* Android notifications
* Android voice / Assistant integration

### AI / ML

* Google ML Kit
* Gemma 3 1B
* `all-MiniLM-L6-v2`
* LiteRT / mobile inference runtime
* On-device speech recognition

### Data

* SQLite
* Local vector representations
* Metadata-based memory index

> The exact runtime/backend configuration may evolve during device benchmarking.

---

# Project Status

**Current status: Working MVP**

The current prototype demonstrates the fundamental loop:

```text
Capture
   ↓
Understand
   ↓
Create Memory
   ↓
Store Locally
   ↓
Ask Later
   ↓
Recall
```

The next development stage focuses on improving the capture experience, adding reminders and quick-access interactions, integrating Android-native invocation, and optimizing the on-device AI pipeline for real hardware.

---

# Privacy Statement

MEMORY is designed as a privacy-first system.

The project does not require a cloud database for its core memory workflow. The intended architecture keeps sensitive captured information, embeddings, and retrieval on the user's device wherever technically practical.

Users explicitly choose what to capture.

MEMORY is **not designed as an always-on surveillance or continuous life-recording system.**

---

# Contributing

This project is currently being developed as a hackathon prototype.

Contributions, technical feedback, model optimization ideas, and Android performance experiments are welcome.

---

# License

Add your chosen license here.

For example:

`MIT License`

---

## MEMORY

> **Your phone remembers the files.
> MEMORY remembers what mattered.**

```
