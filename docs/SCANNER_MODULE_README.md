Module 'Détecter scanner' — gedavocat-springboot

Status (2026-02-24)
- The server-side scanner-detection module and the front-end "Détecter scanner" UI were removed from the application.
- The application still provides a "Scanner" / import UI: call the client helper `openScanner(uploadUrl, redirectUrl)` (implemented in `/js/scanner.js`) to open a modal for selecting/uploading scanned files towards the upload endpoints (for example `/documents/case/{caseId}/upload-ajax`).

If you want the scanner-detection module removed permanently from the codebase, delete the files under `src/main/java/com/scanner/detector/` and `src/main/java/com/gedavocat/service/ScannerDetectionService.java` (placeholders are currently present).

If you'd like, I can also remove this README or convert it to an implementation note; tell me which you prefer.