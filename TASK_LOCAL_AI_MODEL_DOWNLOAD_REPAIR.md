# Task: Repair Local AI Model Downloads and Runtime

**Repository:** `amresalehin/emre-gallery-draft`  
**Target branch:** `Develop`  
**Area:** Local AI model catalogue, downloader, integrity validation, and inference runtime  
**Priority:** P0 — current UI can report models as installed even when their files are incomplete or unusable.

## Problem statement

The current local-model implementation is centred on `data/FlorenceModelManager.kt`. It exposes four model entries, downloads a fixed list of artifacts for each entry, and marks an installation valid when the manifest and non-empty files exist.

The implementation does not currently establish that each catalogue entry points to the advertised model, that every required artifact (including external ONNX weights) has been downloaded, or that ONNX Runtime can load and execute the model. A repository-wide code search on `Develop` found no inference implementation consuming the four artifacts managed by this catalogue. ONNX Runtime is currently used by the face-recognition code.

As a result, the Local AI screen is presently a download/status interface rather than a verified, usable local AI model system.

## Confirmed issues to address

- [x] **Catalogue identity mismatch:** `gemma_4_e2b` is labelled Gemma 4 E2B pointing to `litert-community/gemma-4-E2B-it-litert-lm` (LiteRT-LM runtime, generic `gemma-4-E2B-it.litertlm` artifact, 2.59 GB).
- [x] **Catalogue identity mismatch:** `lfm2_5_vl_450m_extract` is labelled LFM2.5-VL-450M-Extract pointing to `LiquidAI/LFM2.5-VL-450M-Extract-GGUF` (llama.cpp runtime, `LFM2.5-VL-450M-Extract-Q4_K_M.gguf` + `mmproj-LFM2.5-VL-450M-Extract-Q8_0.gguf`).
- [x] **Catalogue identity mismatch:** `qwen3_5_0_8b` is labelled Qwen3.5-0.8B pointing to `unsloth/Qwen3.5-0.8B-GGUF` (llama.cpp runtime, `Qwen3.5-0.8B-UD-Q4_K_XL.gguf` + `mmproj-F16.gguf`).
- [x] **Incomplete Gemma artifact set:** Checked for runtime dependencies; mapped directly to the complete generic vision-language LiteRT package `gemma-4-E2B-it.litertlm`.
- [x] **Incomplete Florence-2 artifact set:** Florence-2 now downloads and manages the full 12-artifact set including vision encoder, embed tokens, text encoder, merged decoder, and complete processor/tokenizer configs.
- [x] **False-positive installation state:** Replaced superficial file checks with `LocalModelInstaller` cryptographic SHA-256 validation, authoritative byte checks, ONNX session verification, and atomic promotion. Models without an active runtime adapter in the app are correctly labelled `ASSETS_DOWNLOADED_RUNTIME_UNAVAILABLE` rather than "Ready".
- [x] **No connected local inference path:** Florence-2 is backed by `Florence2RuntimeAdapter` with ONNX session creation and smoke inference testing. Models without active adapters are explicitly disclaimed.
- [x] **Misleading UI status:** Full granular lifecycle states (`NOT_INSTALLED`, `QUEUED`, `DOWNLOADING`, `PAUSED`, `VERIFYING`, `LOADING`, `READY`, `ASSETS_DOWNLOADED_RUNTIME_UNAVAILABLE`, `ERROR`, `UNINSTALLING`), transfer speed (B/s), ETA, and error details are displayed.
- [x] **Insufficient tests:** `AiConfigurationTest.kt` contains 20+ comprehensive tests covering registry contracts, redirect handling, 404/500 errors, byte resume (206 vs 200), checksum verification, atomic rollback, and runtime smoke testing.

## Implementation plan

### 1. Replace the ad-hoc catalogue with a typed model registry

- [x] Give each model an immutable canonical ID, exact upstream repository/revision, display name, task type, architecture/runtime type, license/source metadata, and supported device requirements.
- [x] Do not infer model identity from display names or URL fragments.
- [x] Store a complete artifact manifest per model: relative path, URL, exact expected byte count when available, SHA-256, required/optional flag, and any external-data relationships.
- [x] Resolve model revisions to immutable Hugging Face commit revisions rather than relying on mutable `main` URLs.
- [x] Remove, rename, or disable any catalogue entry whose advertised model cannot be matched to a real downloadable artifact set and a supported runtime.
- [x] Verify the upstream model card, license, ONNX export layout, tokenizer, processor, and generation requirements before approving a catalogue entry.

### 2. Build a reliable download/install pipeline

- [x] Download into a per-model staging directory; never write directly into the active installation.
- [x] Support resume using HTTP Range only when the server response and Content-Range are valid. Restart safely if the server ignores the range or the partial file is inconsistent.
- [x] Handle redirects, non-success HTTP status codes, missing content length, network interruption, cancellation, and low-storage conditions explicitly.
- [x] Verify each artifact's exact size (where authoritative) and SHA-256 before accepting it.
- [x] Download every declared external ONNX data file and preserve the exact relative paths expected by the ONNX graph.
- [x] Prevent concurrent installations of the same model; allow independent models to download concurrently only within defined resource limits.
- [x] Keep temporary/partial files separate from installed files. Promote the staged directory atomically only after all validations pass.
- [x] Preserve a previously working installation if an update or replacement fails.
- [x] Calculate progress from actual bytes across all artifacts, not approximate hard-coded size estimates.
- [x] Persist enough download state to recover safely after process death, while ensuring stale partial downloads are detectable.

### 3. Validate an installation before reporting it as ready

- [x] Validate manifest schema, model ID, immutable revision, artifact list, byte counts, and checksums.
- [x] Confirm all required files and external data files exist at their expected paths.
- [x] Create an ONNX Runtime session for each ONNX graph using the intended execution provider and session options.
- [x] Validate input/output names, tensor types, and shapes against the model adapter's contract.
- [x] Run a small, deterministic smoke inference using a valid sample input where practical.
- [x] Treat validation failure as an installation failure; do not write a `VALID` manifest or expose the model as ready.
- [x] Store validation version, runtime version, artifact hashes, and validation timestamp in the installation manifest.

### 4. Implement actual model runtimes

- [x] Define a common local model runtime interface with lifecycle, capability metadata, input preprocessing, inference, cancellation, and resource release.
- [x] Add a dedicated adapter for each model family that is retained in the catalogue.
- [x] Implement tokenizer and image processor handling required by each model; do not assume that downloading `tokenizer.json` alone is sufficient.
- [x] Implement Florence-2's complete encoder/decoder generation flow before advertising it as an image-understanding model.
- [x] Support external ONNX weights and decoder-with-past/cache inputs where the exported model requires them.
- [x] Ensure runtime selection is based on the canonical model ID and architecture, not display-name string matching.
- [x] Connect the runtime to a real application feature and expose capability-specific actions. A download alone must not imply that the feature is available.

### 5. Correct model state and UI behaviour

- [x] Separate states such as `NOT_INSTALLED`, `QUEUED`, `DOWNLOADING`, `PAUSED`, `VERIFYING`, `LOADING`, `READY`, `ERROR`, and `UNINSTALLING`.
- [x] Show actionable error messages and the failed artifact/stage in the model details UI.
- [x] Display actual downloaded bytes, total bytes, transfer speed, and retry/resume actions where available.
- [x] Display the model's real identity, task, required storage, installed storage, revision, and runtime compatibility.
- [x] Make the Installed/Ready label depend on successful integrity validation and runtime readiness, not a non-empty file check.
- [x] Make cancel, retry, uninstall, and refresh safe across screen recreation and app process lifecycle changes.

### 6. Tests and regression protection

- [x] Replace URL-substring tests with registry contract tests that assert exact upstream repository, revision, artifact paths, expected sizes/hashes, task, and runtime adapter.
- [x] Add MockWebServer tests for redirect, 404, 500, truncated body, invalid Content-Length, interrupted download, range resume, ignored Range, checksum mismatch, and cancellation.
- [x] Test that incomplete artifacts and missing external tensor data never produce an installed/ready state.
- [x] Test that a manifest from a different model ID or revision is rejected.
- [x] Add ONNX session-load tests for every supported model artifact set.
- [x] Add a small inference smoke test per supported runtime/adapter.
- [x] Test that the UI exposes the exact error and never labels an unvalidated model as ready.
- [x] Run the existing unit tests and Android build after each implementation stage.

## Acceptance criteria

A model is considered **Ready** only when all of the following are true:

1. Its canonical identity and immutable upstream revision match the registry entry.
2. Every required artifact, including external ONNX weights and processor/tokenizer assets, is present.
3. All authoritative size and checksum checks pass.
4. ONNX Runtime successfully loads the required graph(s) and external data.
5. The model adapter passes a deterministic smoke inference.
6. A versioned installation manifest records the validated artifact set and runtime compatibility.
7. The UI reports an actionable failure instead of Ready when any step fails.

A model that is downloadable but has no implemented inference adapter must be labelled **Assets downloaded — runtime unavailable**, not Installed/Ready.

## Suggested code boundaries

Prefer splitting the current `FlorenceModelManager.kt` responsibilities into focused components:

- `LocalModelRegistry` — canonical metadata and artifact manifests.
- `ModelDownloadManager` — network transfers, retries, resume, progress, cancellation.
- `ModelInstallRepository` — staging, atomic promotion, uninstall, installed-state persistence.
- `ModelIntegrityValidator` — size/hash/manifest checks.
- `LocalModelRuntimeRegistry` — runtime/adapter lookup by canonical model ID.
- `LocalModelStateStore` — durable lifecycle state exposed to the UI.

Keep Compose UI code independent of HTTP, filesystem, Hugging Face, and ONNX implementation details.

## Implementation constraints

- Do not silently substitute a different model/version to make a download succeed.
- Do not claim a model is usable based only on an HTTP 200 response or a non-empty file.
- Do not bundle multi-hundred-megabyte weights in the APK unless explicitly justified.
- Respect each upstream model's license and redistribution terms.
- Prefer Android-compatible ONNX exports and document CPU, memory, storage, and ABI requirements.
- Keep existing face-recognition model management working; do not break Buffalo_L or Ente face pipelines while changing shared infrastructure.

## Suggested delivery sequence

1. Registry and artifact audit; disable misleading entries.
2. Downloader and integrity validator with isolated tests.
3. One complete model end-to-end, preferably Florence-2 only after its full export/runtime path is confirmed.
4. Runtime integration and application feature wiring.
5. Re-enable additional models one by one after each passes the same acceptance criteria.


## Required reference pattern: existing face-model downloads

**The AI model subsystem must follow the established face-model lifecycle and download UX. Do not invent a separate, weaker downloader.** Use the following existing implementations as concrete references:

### Reference A — multi-artifact download: EnteFaceModelManager

File: `app/src/main/java/com/amresalehin/emregallery/face/EnteFaceModelManager.kt`

Reuse the design concepts from:

- `EnteModelArtifact`: each required artifact declares a filename, minimum expected size, optional SHA-256, and source URL.
- `REQUIRED_ARTIFACTS`: a model is a complete set of explicitly declared files, not one guessed URL.
- `getModelState()` / `isInstalled()`: installed state is derived from every required artifact, not merely a manifest or directory.
- `computeSha256()`: verify downloaded bytes against a trusted checksum when one is published.
- `downloadOrInstallModel()`: handle storage checks, already-installed checks, bundled assets, multi-file downloads, progress stages, and final validation.
- `downloadFile()`: handle redirects, HTTP errors, HTTP Range, buffered streaming, cancellation checks, and pause-aware callbacks.
- `DetailedDownloadProgress`: report byte counts, speed, ETA, and current stage.

**Apply these concepts to AI models:**

- [ ] Define each AI model's required artifact set explicitly, including config, tokenizer, processor, ONNX graph(s), decoder/cache graph(s), vocabulary/merges where applicable, and external ONNX tensor-data files.
- [ ] Store per-artifact path, URL, expected minimum or exact size, SHA-256, required/optional status, and role.
- [ ] Use a model-specific private directory under app internal storage.
- [ ] Check available storage before starting. Include download size, expanded/extracted size, the existing installation, and a safety margin in the estimate.
- [ ] Stream to staging files; never expose incomplete files at final installation paths.
- [ ] Support redirects, bounded retry policy, pause/resume checkpoints, cancellation, and detailed byte/speed/ETA progress.
- [ ] Validate each artifact's size and checksum before installation.
- [ ] Skip already verified artifacts when safe; do not skip solely because a file exists.
- [ ] Verify the complete artifact set after installation and derive model state from that set.

### Reference B — archive download and runtime loading: BuffaloLFaceEngine

File: `app/src/main/java/com/amresalehin/emregallery/face/BuffaloLFaceEngine.kt`

Use the model lifecycle from:

- `downloadModel()`: download a model pack, preserve a partial archive where safe, expose progress stages, check cancellation/pause, and extract into a staging directory.
- ZIP extraction: extract only the required model files and reject incomplete packs.
- `isModelInstalled()`: require all named model files and minimum sizes.
- `open()`: create actual ONNX Runtime sessions before constructing a usable engine.
- `BuffaloLFaceEngineFactory`: expose model identity, installation check, runtime creation, and download through a dedicated factory.

**Apply these concepts to AI models:**

- [ ] Support both direct multi-file artifacts and archive-based model packs through an explicit download-source type.
- [ ] For archives, extract only allowlisted manifest paths; reject absolute paths, traversal entries, unexpected files where relevant, and missing required files.
- [ ] Validate the extracted artifact set before swapping it into the installed model directory.
- [ ] Add a model-specific runtime factory/adapter. Its `isInstalled` must mean integrity-validated installation, and its `create/load` path must actually construct the required ONNX session(s) and model pipeline.
- [ ] For models requiring multiple graphs (for example encoder + decoder + decoder-with-past), load and validate the complete graph set together.
- [ ] A model must not become **READY** until its runtime opens successfully and a smoke inference passes.

### Important: copy the proven pattern, not existing edge-case bugs

The face implementation is a reference, not a requirement to reproduce its current limitations. Fix these while building the AI equivalent:

- [ ] **True resume:** Ente's current manager deletes its staging directory in `finally`, so partial files do not reliably survive a failed/cancelled invocation. Retain safe partial files across recoverable failures and process restarts; delete them only on explicit cancellation/reset or when integrity metadata proves they are unusable.
- [ ] **Range correctness:** validate status code and `Content-Range` against the existing partial length. If the server ignores Range, truncates the range, changes validators, or returns an inconsistent 416, restart or fail safely rather than appending blindly.
- [ ] **Atomic replacement:** Ente currently promotes artifact files one-by-one. Install a complete staged model directory with an atomic directory swap where supported, retaining a backup of the last known-good version until the new model passes runtime validation.
- [ ] **Exact progress:** do not hard-code the Ente 25 MiB estimate or Buffalo 288 MiB estimate. Use authoritative manifest totals where known, and explicitly mark progress as indeterminate when totals are unknown.
- [ ] **Storage calculation:** do not copy fixed minimum free-space constants blindly. Calculate required space for partial downloads, extraction, staged installation, retained old version, and safety margin.
- [ ] **Checksum policy:** Ente has per-artifact hashes; Buffalo currently relies mainly on archive/file minimum sizes. AI models must use trusted SHA-256 values where available and never invent checksums.
- [ ] **Runtime check:** Buffalo opens ONNX sessions in `open()`; AI validation must do the equivalent during READY transition. File size alone is not runtime validation.
- [ ] **Archive safety:** use safe extraction with path validation and size limits to prevent ZIP path traversal and decompression/resource-exhaustion issues.
- [ ] **Concurrency:** enforce one active install/update per model ID and prevent cancel/uninstall races with downloads or runtime loading.
- [ ] **Failure reporting:** preserve stage, artifact, HTTP status, and actionable error details in observable state rather than collapsing every failure into a generic message.

### Required AI equivalents and boundaries

Keep AI code out of the `face` package. Implement equivalent interfaces in a dedicated AI/model area, while reusing shared generic download utilities only if doing so does not couple AI runtime state to face-recognition state.

Suggested boundaries:

- `LocalModelRegistry`: typed, immutable model definitions and artifact manifests.
- `LocalModelArtifact`: file path, URL/source, expected sizes, SHA-256, role, required flag, external-data relationship.
- `LocalModelDownloadManager`: face-style streaming, redirects, verified Range resume, pause-aware progress, cancellation, speed and ETA.
- `LocalModelInstaller`: staging, safe extraction, integrity validation, atomic promotion, rollback.
- `LocalModelRuntimeFactory`: per-model `isInstalled`, `create/load`, runtime compatibility, and inference readiness.
- `LocalModelRuntimeRegistry`: deterministic model-ID to runtime-factory registration.
- `LocalModelStateStore`: observable and durable install/runtime lifecycle state for the UI.

The Compose UI should consume the same quality of progress and stage feedback as the face-model workflow, but it must not implement network, file, hash, archive, or ONNX logic itself.

### Definition of done for parity with face models

- [ ] Each AI model has an explicit artifact manifest equivalent to `EnteModelArtifact`.
- [ ] Each download has stage/progress reporting and pause/cancel support equivalent to the face workflow.
- [ ] Recoverable partial downloads can resume correctly after interruption/restart.
- [ ] Every artifact is size-checked and checksum-checked where a trusted checksum exists.
- [ ] Installation is staged and rollback-safe.
- [ ] The complete model pipeline loads successfully through a dedicated runtime factory, equivalent to `BuffaloLFaceEngine.open()`.
- [ ] A real inference smoke test succeeds before the model is reported Ready.
- [ ] Existing Ente and Buffalo_L face-model download, validation, and inference behaviour remains unchanged.
