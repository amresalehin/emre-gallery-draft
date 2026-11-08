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

- [ ] **Catalogue identity mismatch:** `gemma_4_e2b` is labelled Gemma 4 E2B but its URLs point to `onnx-community/gemma-3-1b-it-ONNX`.
- [ ] **Catalogue identity mismatch:** `lfm2_5_vl_450m_extract` is labelled LFM2.5-VL-450M-Extract but its URLs point to `onnx-community/LFM2-350M-ONNX`, which is not the advertised vision-language model.
- [ ] **Catalogue identity mismatch:** `qwen3_5_0_8b` is labelled Qwen3.5-0.8B but its URLs point to `onnx-community/Qwen2.5-0.5B`.
- [ ] **Incomplete Gemma artifact set:** the configured ONNX graph must be checked for external tensor data dependencies. The current manifest contains only `model_q4.onnx` and does not declare a companion external-data artifact.
- [ ] **Incomplete Florence-2 artifact set:** the catalogue downloads an encoder, tokenizer files, and config, but does not declare the decoder/generation and processor assets needed for a complete Florence-2 inference pipeline.
- [ ] **False-positive installation state:** `isInstalled()` checks only for a manifest and non-empty files. The downloader writes a `VALID` manifest without checksums, expected-size checks, ONNX session creation, or inference smoke tests.
- [ ] **No connected local inference path:** a repository search found no consumer of the downloaded Gemma, LFM, Qwen, or Florence artifact files. Do not expose a model as usable until a matching runtime/adapter exists.
- [ ] **Misleading UI status:** the model details dialog displays only “Installed & Validated” or “Not installed”; it hides the actual error, download, verification, and installation states.
- [ ] **Insufficient tests:** `AiConfigurationTest.kt` validates labels and URL string patterns, not the identity, completeness, integrity, compatibility, or executable behaviour of the artifacts.

## Implementation plan

### 1. Replace the ad-hoc catalogue with a typed model registry

- [ ] Give each model an immutable canonical ID, exact upstream repository/revision, display name, task type, architecture/runtime type, license/source metadata, and supported device requirements.
- [ ] Do not infer model identity from display names or URL fragments.
- [ ] Store a complete artifact manifest per model: relative path, URL, exact expected byte count when available, SHA-256, required/optional flag, and any external-data relationships.
- [ ] Resolve model revisions to immutable Hugging Face commit revisions rather than relying on mutable `main` URLs.
- [ ] Remove, rename, or disable any catalogue entry whose advertised model cannot be matched to a real downloadable artifact set and a supported runtime.
- [ ] Verify the upstream model card, license, ONNX export layout, tokenizer, processor, and generation requirements before approving a catalogue entry.

### 2. Build a reliable download/install pipeline

- [ ] Download into a per-model staging directory; never write directly into the active installation.
- [ ] Support resume using HTTP Range only when the server response and Content-Range are valid. Restart safely if the server ignores the range or the partial file is inconsistent.
- [ ] Handle redirects, non-success HTTP status codes, missing content length, network interruption, cancellation, and low-storage conditions explicitly.
- [ ] Verify each artifact's exact size (where authoritative) and SHA-256 before accepting it.
- [ ] Download every declared external ONNX data file and preserve the exact relative paths expected by the ONNX graph.
- [ ] Prevent concurrent installations of the same model; allow independent models to download concurrently only within defined resource limits.
- [ ] Keep temporary/partial files separate from installed files. Promote the staged directory atomically only after all validations pass.
- [ ] Preserve a previously working installation if an update or replacement fails.
- [ ] Calculate progress from actual bytes across all artifacts, not approximate hard-coded size estimates.
- [ ] Persist enough download state to recover safely after process death, while ensuring stale partial downloads are detectable.

### 3. Validate an installation before reporting it as ready

- [ ] Validate manifest schema, model ID, immutable revision, artifact list, byte counts, and checksums.
- [ ] Confirm all required files and external data files exist at their expected paths.
- [ ] Create an ONNX Runtime session for each ONNX graph using the intended execution provider and session options.
- [ ] Validate input/output names, tensor types, and shapes against the model adapter's contract.
- [ ] Run a small, deterministic smoke inference using a valid sample input where practical.
- [ ] Treat validation failure as an installation failure; do not write a `VALID` manifest or expose the model as ready.
- [ ] Store validation version, runtime version, artifact hashes, and validation timestamp in the installation manifest.

### 4. Implement actual model runtimes

- [ ] Define a common local model runtime interface with lifecycle, capability metadata, input preprocessing, inference, cancellation, and resource release.
- [ ] Add a dedicated adapter for each model family that is retained in the catalogue.
- [ ] Implement tokenizer and image processor handling required by each model; do not assume that downloading `tokenizer.json` alone is sufficient.
- [ ] Implement Florence-2's complete encoder/decoder generation flow before advertising it as an image-understanding model.
- [ ] Support external ONNX weights and decoder-with-past/cache inputs where the exported model requires them.
- [ ] Ensure runtime selection is based on the canonical model ID and architecture, not display-name string matching.
- [ ] Connect the runtime to a real application feature and expose capability-specific actions. A download alone must not imply that the feature is available.

### 5. Correct model state and UI behaviour

- [ ] Separate states such as `NOT_INSTALLED`, `QUEUED`, `DOWNLOADING`, `PAUSED`, `VERIFYING`, `LOADING`, `READY`, `ERROR`, and `UNINSTALLING`.
- [ ] Show actionable error messages and the failed artifact/stage in the model details UI.
- [ ] Display actual downloaded bytes, total bytes, transfer speed, and retry/resume actions where available.
- [ ] Display the model's real identity, task, required storage, installed storage, revision, and runtime compatibility.
- [ ] Make the Installed/Ready label depend on successful integrity validation and runtime readiness, not a non-empty file check.
- [ ] Make cancel, retry, uninstall, and refresh safe across screen recreation and app process lifecycle changes.

### 6. Tests and regression protection

- [ ] Replace URL-substring tests with registry contract tests that assert exact upstream repository, revision, artifact paths, expected sizes/hashes, task, and runtime adapter.
- [ ] Add MockWebServer tests for redirect, 404, 500, truncated body, invalid Content-Length, interrupted download, range resume, ignored Range, checksum mismatch, and cancellation.
- [ ] Test that incomplete artifacts and missing external tensor data never produce an installed/ready state.
- [ ] Test that a manifest from a different model ID or revision is rejected.
- [ ] Add ONNX session-load tests for every supported model artifact set.
- [ ] Add a small inference smoke test per supported runtime/adapter.
- [ ] Test that the UI exposes the exact error and never labels an unvalidated model as ready.
- [ ] Run the existing unit tests and Android build after each implementation stage.

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
