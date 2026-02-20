/**
 * GedAvocat Document Scanner v2
 * ==============================
 * Deux modes accessibles via onglets :
 *
 *  📷 CAMÉRA   — Flux webcam (PC) ou caméra arrière (mobile), capture multi-pages,
 *                compilation jsPDF, envoi AJAX.
 *
 *  📁 FICHIER  — Import glisser-déposer ou sélection de fichiers :
 *                • Images (JPEG, PNG, TIFF, BMP, WebP) → compilées en PDF
 *                • PDF  → envoyé directement
 *                Idéal pour scanner physique (imprimante multifonction) :
 *                l'utilisateur scanne avec Windows Scan / NAPS2, enregistre le
 *                fichier, puis l'importe ici.
 *
 * Usage :
 *   openScanner('/documents/case/ABC/upload-ajax', '/cases/ABC');
 */
(function () {
    'use strict';

    // ── État global ───────────────────────────────────────────────────────────
    let mediaStream     = null;
    let capturedImages  = [];   // mode caméra : dataURL JPEG
    let importedFiles   = [];   // mode fichier : File objects
    let uploadUrl       = null;
    let redirectUrl     = null;
    let facingMode      = 'environment';
    let activeTab       = 'camera'; // 'camera' | 'file'

    // ── Helpers CSRF ──────────────────────────────────────────────────────────
    function getCsrfHeaders() {
        const token  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
        if (token && header) return { [header]: token };
        return {};
    }

    // ── Construction du modal avec 2 onglets ─────────────────────────────────
    function buildModal() {
        if (document.getElementById('gedScannerModal')) return;

        const html = `
<div id="gedScannerModal" class="modal fade" tabindex="-1" aria-labelledby="gedScannerLabel" aria-hidden="true" style="z-index:1070">
  <div class="modal-dialog modal-xl modal-dialog-centered">
    <div class="modal-content border-0 shadow-lg">

      <!-- Header -->
      <div class="modal-header text-white border-0 py-2" style="background:linear-gradient(135deg,#667eea,#764ba2)">
        <h5 class="modal-title d-flex align-items-center gap-2" id="gedScannerLabel">
          <i class="fas fa-camera"></i>
          <span>Scanner / Importer un document</span>
        </h5>
        <div class="ms-auto d-flex align-items-center gap-2">
          <small id="ged-scan-label" class="opacity-75"></small>
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
        </div>
      </div>

      <!-- Onglets -->
      <div class="border-bottom bg-light px-3 pt-2 pb-0">
        <ul class="nav nav-tabs border-0" id="gedScannerTabs">
          <li class="nav-item">
            <button class="nav-link active px-4 fw-semibold" id="ged-tab-camera" type="button">
              <i class="fas fa-camera me-2"></i>Caméra
              <span class="badge bg-secondary ms-2" style="font-size:10px">Webcam / Mobile</span>
            </button>
          </li>
          <li class="nav-item">
            <button class="nav-link px-4 fw-semibold" id="ged-tab-file" type="button">
              <i class="fas fa-file-import me-2"></i>Importer un fichier
              <span class="badge bg-secondary ms-2" style="font-size:10px">Scanner physique / PC</span>
            </button>
          </li>
        </ul>
      </div>

      <!-- Corps -->
      <div class="modal-body p-0">

        <!-- ===== ONGLET CAMÉRA ===== -->
        <div id="ged-pane-camera">
          <div class="row g-0" style="min-height:400px">
            <div class="col-lg-8 bg-black d-flex align-items-center justify-content-center" style="min-height:340px">
              <video id="ged-scan-video" autoplay playsinline muted
                     style="max-width:100%;max-height:440px;display:block"></video>
              <canvas id="ged-scan-canvas" style="display:none"></canvas>
              <div id="ged-scan-nocam" class="text-white text-center p-4 d-none">
                <i class="fas fa-camera-slash fa-3x mb-3 opacity-50"></i>
                <p class="mb-1 fw-semibold">Impossible d'accéder à la caméra</p>
                <small class="opacity-75">
                  Vérifiez l'autorisation dans votre navigateur.<br>
                  Sur PC, utilisez l'onglet <strong>Importer un fichier</strong>
                  si vous avez un scanner physique.
                </small>
              </div>
            </div>
            <div class="col-lg-4 d-flex flex-column border-start" style="background:#f8fafc">
              <div class="p-3 border-bottom">
                <div class="d-grid gap-2">
                  <button id="ged-btn-capture" class="btn btn-primary btn-lg">
                    <i class="fas fa-camera me-2"></i>Capturer la page
                  </button>
                  <button id="ged-btn-switch" class="btn btn-outline-secondary btn-sm">
                    <i class="fas fa-sync-alt me-1"></i>Changer de caméra
                  </button>
                </div>
                <p class="text-muted small mt-2 mb-0">
                  <i class="fas fa-info-circle me-1"></i>Capturez plusieurs pages puis créez le PDF.
                </p>
              </div>
              <div class="flex-grow-1 p-3 overflow-auto" style="max-height:260px">
                <div id="ged-scan-thumbs" class="d-flex flex-wrap gap-2">
                  <div id="ged-scan-empty" class="text-muted small fst-italic w-100 text-center pt-3">Aucune page capturée</div>
                </div>
              </div>
              <div class="p-3 border-top">
                <div id="ged-scan-status" class="small text-center text-muted"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- ===== ONGLET FICHIER ===== -->
        <div id="ged-pane-file" class="d-none p-3">
          <div class="alert alert-info mb-3 py-2 d-flex align-items-start gap-2">
            <i class="fas fa-print mt-1 flex-shrink-0"></i>
            <div>
              <strong>Comment utiliser votre scanner physique (imprimante multifonction) :</strong>
              <ol class="mb-0 mt-1 ps-3 small">
                <li>Scannez vos pages avec <strong>Windows Scan</strong>, <strong>NAPS2</strong> ou le logiciel de votre imprimante</li>
                <li>Enregistrez en <strong>PDF</strong>, JPEG ou PNG</li>
                <li>Glissez le fichier ci-dessous ou cliquez pour le sélectionner</li>
              </ol>
            </div>
          </div>
          <div id="ged-drop-zone" class="border border-2 border-dashed rounded-3 text-center p-5 mb-3"
               style="border-color:#667eea!important;cursor:pointer;transition:background .2s">
            <i class="fas fa-cloud-upload-alt fa-3x mb-3" style="color:#667eea;opacity:.7"></i>
            <p class="fw-semibold mb-1" style="color:#667eea">Glissez vos fichiers ici</p>
            <p class="text-muted small mb-3">ou cliquez pour sélectionner</p>
            <p class="text-muted" style="font-size:12px">
              Formats acceptés : <strong>PDF, JPEG, PNG, TIFF, BMP, WebP</strong><br>
              Vous pouvez sélectionner plusieurs images — elles seront assemblées en un seul PDF.
            </p>
            <input id="ged-file-input" type="file"
                   accept=".pdf,.jpg,.jpeg,.png,.tiff,.tif,.bmp,.webp,image/*,application/pdf"
                   multiple style="display:none">
          </div>
          <div id="ged-file-list" class="d-none">
            <h6 class="text-muted mb-2 small fw-semibold text-uppercase">
              <i class="fas fa-list me-1"></i>Fichiers sélectionnés
            </h6>
            <div id="ged-file-items" class="d-flex flex-wrap gap-2 mb-2"></div>
          </div>
          <div id="ged-file-status" class="small text-center text-muted mt-2"></div>
        </div>

      </div>

      <!-- Footer -->
      <div class="modal-footer border-0 bg-light">
        <span id="ged-scan-count" class="text-muted me-auto small fw-semibold"></span>
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Annuler</button>
        <button id="ged-btn-send" class="btn btn-success" disabled>
          <i class="fas fa-file-pdf me-1"></i>
          <span id="ged-btn-label">Créer PDF et envoyer</span>
          <span id="ged-btn-spinner" class="spinner-border spinner-border-sm ms-2 d-none" role="status"></span>
        </button>
      </div>

    </div>
  </div>
</div>`;

        document.body.insertAdjacentHTML('beforeend', html);
        document.getElementById('gedScannerModal')
            .addEventListener('hidden.bs.modal', stopCamera);

        // Drag & drop pour l'onglet fichier
        const dropZone  = document.getElementById('ged-drop-zone');
        const fileInput = document.getElementById('ged-file-input');
        dropZone.addEventListener('click', () => fileInput.click());
        dropZone.addEventListener('dragover', e => { e.preventDefault(); dropZone.style.background = 'rgba(102,126,234,0.08)'; });
        dropZone.addEventListener('dragleave', () => { dropZone.style.background = ''; });
        dropZone.addEventListener('drop', e => { e.preventDefault(); dropZone.style.background = ''; handleFiles(e.dataTransfer.files); });
        fileInput.addEventListener('change', () => handleFiles(fileInput.files));
    }

    // ── Switch onglets ────────────────────────────────────────────────────────
    function switchTab(tab) {
        activeTab = tab;
        const camTab   = document.getElementById('ged-tab-camera');
        const fileTab  = document.getElementById('ged-tab-file');
        const camPane  = document.getElementById('ged-pane-camera');
        const filePane = document.getElementById('ged-pane-file');
        const sendBtn  = document.getElementById('ged-btn-send');
        const btnLabel = document.getElementById('ged-btn-label');

        if (tab === 'camera') {
            camTab.classList.add('active');    fileTab.classList.remove('active');
            camPane.classList.remove('d-none'); filePane.classList.add('d-none');
            btnLabel.textContent = 'Créer PDF et envoyer';
            sendBtn.disabled = capturedImages.length === 0;
            startCamera();
        } else {
            fileTab.classList.add('active');   camTab.classList.remove('active');
            filePane.classList.remove('d-none'); camPane.classList.add('d-none');
            stopCamera();
            btnLabel.textContent = 'Envoyer le document';
            sendBtn.disabled = importedFiles.length === 0;
            refreshFileCount();
        }
    }

    // ── Caméra ────────────────────────────────────────────────────────────────
    function startCamera() {
        const video     = document.getElementById('ged-scan-video');
        const noCam     = document.getElementById('ged-scan-nocam');
        const btnSwitch = document.getElementById('ged-btn-switch');

        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            video.classList.add('d-none');
            noCam.classList.remove('d-none');
            return;
        }
        video.classList.remove('d-none');
        noCam.classList.add('d-none');

        navigator.mediaDevices.getUserMedia({
            video: { facingMode: { ideal: facingMode }, width: { ideal: 1920 }, height: { ideal: 1080 } },
            audio: false
        })
        .then(stream => {
            mediaStream = stream; video.srcObject = stream;
            const label = document.getElementById('ged-scan-label');
            if (label) label.textContent = facingMode === 'environment' ? '📷 Caméra arrière' : '🤳 Caméra frontale';
        })
        .catch(() => {
            navigator.mediaDevices.getUserMedia({ video: true, audio: false })
                .then(stream => { mediaStream = stream; video.srcObject = stream; if (btnSwitch) btnSwitch.disabled = true; })
                .catch(() => { video.classList.add('d-none'); noCam.classList.remove('d-none'); });
        });
    }

    function stopCamera() {
        if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null; }
        const label = document.getElementById('ged-scan-label');
        if (label) label.textContent = '';
    }

    function switchCamera() { stopCamera(); facingMode = facingMode === 'environment' ? 'user' : 'environment'; startCamera(); }

    // ── Capture caméra ────────────────────────────────────────────────────────
    function capture() {
        const video = document.getElementById('ged-scan-video');
        const canvas = document.getElementById('ged-scan-canvas');
        const w = video.videoWidth || 1280, h = video.videoHeight || 720;
        canvas.width = w; canvas.height = h;
        const ctx = canvas.getContext('2d');
        if (facingMode === 'user') { ctx.translate(w, 0); ctx.scale(-1, 1); }
        ctx.drawImage(video, 0, 0, w, h);
        capturedImages.push(canvas.toDataURL('image/jpeg', 0.88));
        flashEffect();
        renderCameraThumbs();
    }

    function flashEffect() {
        const video = document.getElementById('ged-scan-video');
        video.style.transition = 'opacity 0.08s'; video.style.opacity = '0.2';
        setTimeout(() => { video.style.opacity = '1'; }, 160);
    }

    function renderCameraThumbs() {
        const container = document.getElementById('ged-scan-thumbs');
        const empty     = document.getElementById('ged-scan-empty');
        const count     = document.getElementById('ged-scan-count');
        const sendBtn   = document.getElementById('ged-btn-send');
        container.querySelectorAll('.ged-thumb').forEach(el => el.remove());

        if (capturedImages.length === 0) {
            empty.classList.remove('d-none'); count.textContent = ''; sendBtn.disabled = true; return;
        }
        empty.classList.add('d-none');
        count.textContent = capturedImages.length + ' page' + (capturedImages.length > 1 ? 's' : '') +
            ' capturée' + (capturedImages.length > 1 ? 's' : '');
        sendBtn.disabled = false;

        capturedImages.forEach((imgData, i) => {
            const wrap = document.createElement('div');
            wrap.className = 'ged-thumb position-relative';
            wrap.style.cssText = 'display:inline-block';
            const img = document.createElement('img');
            img.src = imgData; img.alt = 'Page ' + (i+1);
            img.style.cssText = 'height:72px;width:auto;border-radius:6px;border:2px solid #667eea;display:block';
            const badge = document.createElement('span');
            badge.textContent = i + 1;
            badge.style.cssText = 'position:absolute;top:3px;left:5px;background:#667eea;color:white;font-size:10px;font-weight:700;border-radius:4px;padding:1px 5px';
            const del = document.createElement('button');
            del.innerHTML = '&times;'; del.type = 'button';
            del.style.cssText = 'position:absolute;top:-5px;right:-5px;width:18px;height:18px;border:none;border-radius:50%;background:#ef4444;color:white;font-size:11px;cursor:pointer;padding:0;display:flex;align-items:center;justify-content:center';
            del.addEventListener('click', () => { capturedImages.splice(i, 1); renderCameraThumbs(); });
            wrap.appendChild(img); wrap.appendChild(badge); wrap.appendChild(del);
            container.appendChild(wrap);
        });
    }

    // ── Import fichiers ───────────────────────────────────────────────────────
    function handleFiles(fileList) {
        if (!fileList || fileList.length === 0) return;
        const ALLOWED = ['application/pdf','image/jpeg','image/png','image/tiff','image/bmp','image/webp','image/gif'];
        importedFiles = [];
        document.getElementById('ged-file-items').innerHTML = '';

        Array.from(fileList).forEach(file => {
            const type = file.type || detectMime(file.name);
            if (ALLOWED.some(t => type === t || type.startsWith(t.split('/')[0] + '/'))) {
                importedFiles.push(file);
            }
        });
        if (importedFiles.length === 0) { setFileStatus('<span class="text-warning">Aucun format supporté parmi les fichiers sélectionnés.</span>'); return; }

        document.getElementById('ged-file-list').classList.remove('d-none');
        importedFiles.forEach((file, i) => {
            const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
            const item  = document.createElement('div');
            item.className = 'border rounded-2 px-3 py-2 d-flex align-items-center gap-2 bg-white';
            item.style.cssText = 'font-size:13px;max-width:260px';
            item.innerHTML = `
                <i class="fas ${isPdf ? 'fa-file-pdf text-danger' : 'fa-file-image text-primary'} fa-lg flex-shrink-0"></i>
                <div class="overflow-hidden">
                  <div class="text-truncate fw-semibold" title="${file.name}">${file.name}</div>
                  <div class="text-muted">${fmtSize(file.size)}</div>
                </div>
                <button type="button" class="btn btn-sm btn-outline-danger ms-auto px-1 py-0" style="font-size:11px" data-idx="${i}">
                  <i class="fas fa-times"></i></button>`;
            item.querySelector('button').addEventListener('click', () => {
                importedFiles.splice(i, 1);
                const dt = new DataTransfer();
                importedFiles.forEach(f => dt.items.add(f));
                handleFiles(dt.files);
            });
            document.getElementById('ged-file-items').appendChild(item);
        });
        refreshFileCount();
        setFileStatus('');
    }

    function detectMime(name) {
        const m = {'jpg':'image/jpeg','jpeg':'image/jpeg','png':'image/png','tiff':'image/tiff',
            'tif':'image/tiff','bmp':'image/bmp','webp':'image/webp','pdf':'application/pdf'};
        return m[(name.split('.').pop() || '').toLowerCase()] || '';
    }

    function fmtSize(b) {
        return b < 1024 ? b + ' o' : b < 1048576 ? (b/1024).toFixed(1) + ' Ko' : (b/1048576).toFixed(1) + ' Mo';
    }

    function refreshFileCount() {
        const count   = document.getElementById('ged-scan-count');
        const sendBtn = document.getElementById('ged-btn-send');
        if (!count) return;
        if (importedFiles.length === 0) { count.textContent = ''; sendBtn.disabled = true; return; }
        const pdfCnt = importedFiles.filter(f => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')).length;
        count.textContent = importedFiles.length + ' fichier' + (importedFiles.length > 1 ? 's' : '') +
            (pdfCnt > 0 ? ' dont ' + pdfCnt + ' PDF' : '');
        sendBtn.disabled = false;
    }

    function setFileStatus(html) { const el = document.getElementById('ged-file-status'); if (el) el.innerHTML = html; }
    function setStatus(html)     { const el = document.getElementById('ged-scan-status'); if (el) el.innerHTML = html; }
    function today() { return new Date().toISOString().slice(0,10) + '-' + Math.floor(Math.random()*9999); }

    // ── jsPDF ─────────────────────────────────────────────────────────────────
    function loadJsPDF(cb) {
        if (window.jspdf && window.jspdf.jsPDF) { cb(); return; }
        const s = document.createElement('script');
        s.src = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';
        s.onload = cb;
        s.onerror = () => setStatus('<span class="text-danger">Impossible de charger jsPDF (connexion internet requise)</span>');
        document.head.appendChild(s);
    }

    // ── Envoi caméra ──────────────────────────────────────────────────────────
    function buildAndSendCamera() {
        const sendBtn = document.getElementById('ged-btn-send');
        const spinner = document.getElementById('ged-btn-spinner');
        sendBtn.disabled = true; spinner.classList.remove('d-none');
        setStatus('Génération du PDF…');
        loadJsPDF(() => {
            try {
                const { jsPDF } = window.jspdf;
                const pdf = new jsPDF({ orientation: 'p', unit: 'mm', format: 'a4' });
                const pW = pdf.internal.pageSize.getWidth(), pH = pdf.internal.pageSize.getHeight(), m = 5;
                capturedImages.forEach((dataUrl, idx) => {
                    if (idx > 0) pdf.addPage();
                    const img = new Image(); img.src = dataUrl;
                    const nw = img.naturalWidth || 1280, nh = img.naturalHeight || 720;
                    const r = Math.min((pW-2*m)/nw, (pH-2*m)/nh);
                    pdf.addImage(dataUrl, 'JPEG', (pW-nw*r)/2, (pH-nh*r)/2, nw*r, nh*r);
                });
                doUpload(new File([pdf.output('blob')], 'scan-'+today()+'.pdf', {type:'application/pdf'}),
                    setStatus, sendBtn, spinner);
            } catch (e) {
                setStatus('<span class="text-danger">Erreur PDF : ' + e.message + '</span>');
                sendBtn.disabled = false; spinner.classList.add('d-none');
            }
        });
    }

    // ── Envoi fichier ─────────────────────────────────────────────────────────
    function buildAndSendFile() {
        if (importedFiles.length === 0) return;
        const sendBtn = document.getElementById('ged-btn-send');
        const spinner = document.getElementById('ged-btn-spinner');
        sendBtn.disabled = true; spinner.classList.remove('d-none');
        setFileStatus('Préparation du fichier…');

        const singlePdf = importedFiles.length === 1 &&
            (importedFiles[0].type === 'application/pdf' || importedFiles[0].name.toLowerCase().endsWith('.pdf'));
        const allImages = importedFiles.every(f => f.type.startsWith('image/') || /\.(jpe?g|png|tiff?|bmp|webp)$/i.test(f.name));

        if (singlePdf) {
            doUpload(new File([importedFiles[0]], 'import-'+today()+'.pdf', {type:'application/pdf'}),
                setFileStatus, sendBtn, spinner);
        } else if (allImages) {
            setFileStatus('Compilation des images en PDF…');
            loadJsPDF(() => imagesToPdf(importedFiles, result => {
                if (typeof result === 'string') {
                    setFileStatus('<span class="text-danger">' + result + '</span>');
                    sendBtn.disabled = false; spinner.classList.add('d-none');
                } else {
                    doUpload(result, setFileStatus, sendBtn, spinner);
                }
            }));
        } else {
            const pdfs = importedFiles.filter(f => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf'));
            if (pdfs.length > 0) {
                setFileStatus('Plusieurs types détectés — envoi du PDF.');
                doUpload(new File([pdfs[0]], 'import-'+today()+'.pdf', {type:'application/pdf'}), setFileStatus, sendBtn, spinner);
            } else {
                setFileStatus('<span class="text-warning">Formats mixtes non supportés ensemble. Sélectionnez uniquement des images ou un PDF.</span>');
                sendBtn.disabled = false; spinner.classList.add('d-none');
            }
        }
    }

    function imagesToPdf(files, cb) {
        try {
            const { jsPDF } = window.jspdf;
            const pdf = new jsPDF({ orientation:'p', unit:'mm', format:'a4' });
            const pW = pdf.internal.pageSize.getWidth(), pH = pdf.internal.pageSize.getHeight(), m = 5;
            const loaded = new Array(files.length).fill(null); let done = 0;
            files.forEach((file, idx) => {
                const reader = new FileReader();
                reader.onload = e => {
                    loaded[idx] = e.target.result; done++;
                    if (done === files.length) {
                        loaded.forEach((dataUrl, i) => {
                            if (i > 0) pdf.addPage();
                            const img = new Image(); img.src = dataUrl;
                            const nw = img.naturalWidth || 1280, nh = img.naturalHeight || 720;
                            const r = Math.min((pW-2*m)/nw, (pH-2*m)/nh);
                            pdf.addImage(dataUrl, 'JPEG', (pW-nw*r)/2, (pH-nh*r)/2, nw*r, nh*r);
                        });
                        cb(new File([pdf.output('blob')], 'scan-'+today()+'.pdf', {type:'application/pdf'}));
                    }
                };
                reader.onerror = () => cb('Erreur lecture : ' + file.name);
                reader.readAsDataURL(file);
            });
        } catch (e) { cb('Erreur PDF : ' + e.message); }
    }

    // ── Upload commun ─────────────────────────────────────────────────────────
    function doUpload(file, statusFn, sendBtn, spinner) {
        statusFn('Envoi du document…');
        const fd = new FormData(); fd.append('file', file);
        fetch(uploadUrl, { method:'POST', body:fd, credentials:'same-origin', headers:getCsrfHeaders() })
            .then(r => { if (!r.ok) return r.text().then(t => { throw new Error(t || 'Erreur serveur ' + r.status); }); return r.json(); })
            .then(data => {
                if (data.success === false) throw new Error(data.message || 'Erreur lors de l\'envoi');
                statusFn('<span class="text-success fw-semibold"><i class="fas fa-check-circle me-1"></i>Document enregistré !</span>');
                capturedImages = []; importedFiles = [];
                setTimeout(() => {
                    bootstrap.Modal.getInstance(document.getElementById('gedScannerModal')).hide();
                    if (redirectUrl) window.location.href = redirectUrl; else window.location.reload();
                }, 1400);
            })
            .catch(err => {
                statusFn('<span class="text-danger"><i class="fas fa-exclamation-circle me-1"></i>' + err.message + '</span>');
                sendBtn.disabled = false; spinner.classList.add('d-none');
            });
    }

    // ── API publique ──────────────────────────────────────────────────────────
    /**
     * Ouvrir le scanner / importeur.
     * @param {string} ajaxUploadUrl  Endpoint AJAX POST multipart → JSON {success, message}
     * @param {string} [afterUrl]     Redirection après succès
     */
    window.openScanner = function (ajaxUploadUrl, afterUrl) {
        uploadUrl = ajaxUploadUrl; redirectUrl = afterUrl || null;
        capturedImages = []; importedFiles = [];

        buildModal();

        const modalEl = document.getElementById('gedScannerModal');
        const modal   = bootstrap.Modal.getOrCreateInstance(modalEl);

        // Réinitialiser
        renderCameraThumbs(); setStatus(''); setFileStatus('');
        document.getElementById('ged-file-items').innerHTML = '';
        document.getElementById('ged-file-list').classList.add('d-none');
        document.getElementById('ged-scan-count').textContent = '';
        document.getElementById('ged-scan-label').textContent = '';

        // Onglet caméra actif par défaut
        activeTab = 'camera';
        document.getElementById('ged-tab-camera').classList.add('active');
        document.getElementById('ged-tab-file').classList.remove('active');
        document.getElementById('ged-pane-camera').classList.remove('d-none');
        document.getElementById('ged-pane-file').classList.add('d-none');
        document.getElementById('ged-btn-label').textContent = 'Créer PDF et envoyer';

        // Brancher les contrôles
        rebind('ged-tab-camera',  'click', () => switchTab('camera'));
        rebind('ged-tab-file',    'click', () => switchTab('file'));
        rebind('ged-btn-capture', 'click', capture);
        rebind('ged-btn-switch',  'click', switchCamera);
        rebind('ged-btn-send',    'click', () => activeTab === 'camera' ? buildAndSendCamera() : buildAndSendFile());

        modal.show();
        startCamera();
    };

    function rebind(id, event, handler) {
        const el = document.getElementById(id);
        if (!el) return;
        const neo = el.cloneNode(true);
        el.parentNode.replaceChild(neo, el);
        document.getElementById(id).addEventListener(event, handler);
    }

})();
