# ImageToolbox backend reference snapshot

This directory contains a selected, backend-only source snapshot from
[T8RIN/ImageToolbox](https://github.com/T8RIN/ImageToolbox), fetched from upstream
commit `f78bc23f52d863e97cd0af061c603ac654dedc48` (branch: `master`).

## Scope

The snapshot is for technical study and selective adaptation. It intentionally
excludes screens, Compose UI, navigation, and other presentation code.

Included areas:

- Domain contracts for compression, scaling, and transformation.
- Android image decode, scale, transform, metadata, and compression orchestration.
- Format-specific WebP, AVIF, and JPEG XL encoder backends.
- HEIF and animated JPEG XL Coil decoders.
- Filter-provider and GPU shader execution.
- Background-removal orchestration, backend factory, and generic / flavor backend.
- OpenCV-assisted image stitching and bitmap compositing.
- Tesseract-backed OCR reader.
- PDF manager and file-saving abstractions.

## Important integration warning

These files are **not wired into Emre Gallery** and are **not expected to compile
as a standalone module**. They retain upstream package names and depend on
ImageToolbox-specific domain types, libraries, native binaries, Gradle modules,
DI bindings, and flavor configuration. Treat them as reference implementations.
Before adapting any file, map its dependencies, license notices, threading and
lifecycle assumptions, Android API requirements, and native ABI requirements
to Emre Gallery.

No Emre Gallery production source files or build configuration were changed by
this snapshot.

## Upstream attribution and license

Copyright © 2023 T8RIN. The upstream README identifies the project as licensed
under the Apache License, Version 2.0. See
[upstream README](https://github.com/T8RIN/ImageToolbox/blob/f78bc23f52d863e97cd0af061c603ac654dedc48/README.md)
and [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

The imported source files retain their upstream copyright and license headers.
This directory is a selected source snapshot, not a complete copy of the
upstream project or its dependency/license inventory. Review upstream notices
and third-party licenses before redistributing any adapted build.

## Source layout

The `upstream/` subtree preserves each file's original repository-relative path
to make provenance and dependency tracing easier.
