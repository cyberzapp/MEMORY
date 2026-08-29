# MEMORY

### A private, on-device memory layer for the physical world.

> **Capture once. Remember later. Keep it on-device.**

MEMORY is a phone-first personal memory system that turns intentionally captured photos and voice notes into compact, searchable memories.

Instead of forcing users to remember where they saw, placed, or learned something, MEMORY lets the phone understand the captured moment — extracting objects, text, time, and context — and later retrieve that information through natural-language queries.

The core memory pipeline is designed to run locally on the Android device, minimizing cloud dependency and reducing the need to permanently store large amounts of raw media.

---

## Getting Started & Installation Guide

This project is built to run entirely on an Android device. You can build and install it from any major desktop OS (Windows, macOS, or Linux).

### System Requirements

#### For the Host Machine (Mac, Windows, Linux)
* **OS**: macOS, Windows (10/11), or Linux.
* **IDE**: [Android Studio](https://developer.android.com/studio) (Ladybug or newer recommended).
* **JDK**: Java 17 (bundled with recent Android Studio versions).
* **Python**: Python 3.9+ (used to download the quantized ML models).
* **Graphics / Virtualization**: 
  * If testing via **Android Emulator**: Your host machine should support hardware virtualization (Intel HAXM, AMD Hypervisor, Windows Hyper-V, or Apple Virtualization Framework for Apple Silicon). A dedicated Graphics Card (NVIDIA/AMD) or Apple Silicon GPU is highly recommended to accelerate the emulator UI and ML kit operations via `Hardware (Host)` GPU mode.
  * If testing on a **Physical Device**: No specific desktop GPU is required. The host machine is only used to compile the APK.

#### For the Android Device
* **OS**: Android 9.0 (API 28) or higher.
* **Hardware**: A mid-range or flagship processor (Snapdragon, MediaTek, Tensor, etc.) with NPU or GPU acceleration is highly recommended for smooth on-device AI inference (LiteRT and ML Kit).

### Step-by-Step Setup

**1. Clone the repository**
```bash
git clone https://github.com/cyberzapp/MEMORY.git
cd MEMORY
```

**2. Download the On-Device AI Models**
MEMORY relies on quantized ML models (like `all-MiniLM-L6-v2`) that run entirely on the device. You must download them into the app's `assets` folder before building.
```bash
cd python
# Create a virtual environment (optional but recommended)
python -m venv venv
source venv/bin/activate  # On Windows use: venv\Scripts\activate

# Install the required Hugging Face downloader
pip install huggingface_hub

# Run the download script
python download_models.py
```
*Note: You will be prompted to enter a [Hugging Face Access Token](https://huggingface.co/settings/tokens). A free read-only token is sufficient.*

**3. Open and Build in Android Studio**
* Open Android Studio and select **Open**.
* Navigate to the `MEMORY` folder (the root directory containing `build.gradle.kts` and `settings.gradle.kts`) and open it.
* Allow Gradle to sync and download all Android dependencies.
* Connect your physical Android device (with USB Debugging enabled) or start an Android Emulator.
* Click the **Run** (▶) button to install the app.

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

### 2. Understand the Capture Locally
The device extracts useful information such as objects, visible text, serial numbers, spoken context, timestamp, and available location metadata.
The goal is to transform a large media file into a compact representation of what mattered.

### 3. Create a Compact Memory
Instead of treating the original image as the memory itself, MEMORY creates a structured representation containing relevant information. It is also converted into a semantic embedding (vector) for later retrieval.

### 4. Recall Naturally
Users can ask questions in normal language:
> **"Where is the thing I use to charge my laptop?"**

MEMORY performs local semantic retrieval and finds the most relevant memories.
Example response:
> **Your USB-C charger was last recorded in the second drawer of your study desk.**

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
                     GEMMA 3 1B (Planned)
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
```

---

# On-Device AI Stack

| Capability              | Technology                                                  | Purpose                                            |
| ----------------------- | ----------------------------------------------------------- | -------------------------------------------------- |
| Object Detection        | ML Kit Object Detection                                     | Fast local object detection                        |
| OCR                     | ML Kit Text Recognition                                     | Extract visible text and serial numbers            |
| Voice → Text            | Android SpeechRecognizer / on-device speech where available | Convert voice memories into text                   |
| Semantic Embeddings     | `all-MiniLM-L6-v2`                                          | Convert memories and queries into semantic vectors |
| Memory Extraction       | Gemma 3 1B (Stubbed/Fallback)                               | Convert raw evidence into structured memory        |
| Semantic Retrieval      | Local vector similarity                                     | Retrieve relevant memories without an LLM          |
| Local Storage           | Room / SQLite                                               | Store memory metadata and representations          |

### Model footprint
The system is designed around small, mobile-oriented models rather than a single large AI model. 
Actual memory consumption and inference latency depend on model quantization, runtime backend (CPU/GPU/NPU via LiteRT delegates), Android version, and device hardware.

---

# Privacy by Design

MEMORY is designed around a simple principle:
> **Your memories should not need to leave your phone.**

The core pipeline is designed for local processing. The long-term goal is to avoid sending personal photos, voice recordings, and memory queries to a remote server for the core experience. 

---

# Current MVP

The current prototype focuses on validating the fundamental memory loop.

### Currently implemented
* [x] Photo-based memory capture
* [x] Voice-based memory capture
* [x] On-device object/text processing
* [x] Local semantic representation
* [x] Semantic memory retrieval
* [x] Local-first memory architecture

### Planned next
* [ ] Android notification-based reminders
* [ ] One-tap quick capture
* [ ] Full Gemma 3 integration for natural-language extraction/recall
* [ ] Improved spatial and temporal context
* [ ] Device-specific AI acceleration (NPU/GPU delegates)

---

# License
MIT License

---

## MEMORY
> **Your phone remembers the files. MEMORY remembers what mattered.**
