/**
 * GedAvocat Document Scanner
 * ===========================
 * Utilise l'API getUserMedia du navigateur pour :
 *  - PC  : accéder à la webcam
 *  - Mobile : utiliser la caméra arrière (facingMode: environment)
 *
 * Flux :
 *  1. L'utilisateur clique sur "Scanner"
 *  2. Un modal s'affiche avec le flux vidéo en direct
 *  3. Il capture une ou plusieurs pages (= images JPEG)
 *  4. Un clic sur "Créer PDF et envoyer" compile les images en PDF via jsPDF
 *  5. Le PDF est envoyé via AJAX (multipart/form-data) à l'endpoint fourni
 *
 * Usage :
 *   openScanner('/documents/case/ABC/upload-ajax', '/cases/ABC');
 */
(function () {
    'use strict';

    // ── État global ───────────────────────────────────────────────────────────
    let mediaStream    = null;
    let capturedImages = [];
    let uploadUrl      = null;
    let redirectUrl    = null;
    let facingMode     = 'environment'; // caméra arrière par défaut (mobile)

    // ── Helpers CSRF ──────────────────────────────────────────────────────────
    function getCsrfHeaders() {
        const token  = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
        if (token && header) return { [header]: token };
        return {};
    }

    // ── Injection du modal dans le DOM ────────────────────────────────────────
    function buildModal() {
        if (document.getElementById('gedScannerModal')) return;

        const html = `
<div id="gedScannerModal" class="modal fade" tabindex="-1" aria-labelledby="gedScannerLabel" aria-hidden="true" style="z-index:1070">
  <div class="modal-dialog modal-xl modal-dialog-centered">
    <div class="modal-content border-0 shadow-lg">

      <!-- Header -->
      <div class="modal-header text-white border-0" style="background:linear-gradient(135deg,#667eea,#764ba2)">
        <h5 class="modal-title" id="gedScannerLabel">
          <i class="fas fa-camera me-2"></i>Scanner un document
        </h5>
        <div class="ms-auto d-flex align-items-center gap-2">
          <small id="ged-scan-label" class="opacity-75"></small>
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
        </div>
      </div>

      <!-- Body -->
      <div class="modal-body p-0">
        <div class="row g-0" style="min-height:400px">

          <!-- Flux caméra -->
          <div class="col-lg-8 bg-black d-flex align-items-center justify-content-center" style="min-height:340px">
            <video id="ged-scan-video" autoplay playsinline muted
                   style="max-width:100%;max-height:440px;display:block"></video>
            <canvas id="ged-scan-canvas" style="display:none"></canvas>
            <div id="ged-scan-nocam" class="text-white text-center p-4 d-none">
              <i class="fas fa-camera-slash fa-3x mb-3 opacity-50"></i>
              <p class="mb-1">Impossible d'accéder à la caméra</p>
              <small class="opacity-75">Vérifiez que vous avez accordé l'autorisation dans votre navigateur</small>
            </div>
          </div>

          <!-- Panneau droit : vignettes + contrôles -->
          <div class="col-lg-4 d-flex flex-column border-start" style="background:#f8fafc">

            <!-- Boutons caméra -->
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
                <i class="fas fa-info-circle me-1"></i>
                Capturez plusieurs pages puis créez le PDF.
              </p>
            </div>

            <!-- Vignettes des pages capturées -->
            <div class="flex-grow-1 p-3 overflow-auto" style="max-height:260px">
              <div id="ged-scan-thumbs" class="d-flex flex-wrap gap-2">
                <div id="ged-scan-empty" class="text-muted small fst-italic w-100 text-center pt-3">
                  Aucune page capturée
                </div>
              </div>
            </div>

            <!-- Statut -->
            <div class="p-3 border-top">
              <div id="ged-scan-status" class="small text-center text-muted"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="modal-footer border-0 bg-light">
        <span id="ged-scan-count" class="text-muted me-auto small fw-semibold"></span>
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">
          Annuler
        </button>
        <button id="ged-btn-send" class="btn btn-success" disabled>
          <i class="fas fa-file-pdf me-1"></i>Créer PDF et envoyer
          <span id="ged-btn-spinner" class="spinner-border spinner-border-sm ms-2 d-none" role="status"></span>
        </button>
      </div>

    </div>
  </div>
</div>`;

        document.body.insertAdjacentHTML('beforeend', html);

        // Fermeture → stopper la caméra
        document.getElementById('gedScannerModal')
            .addEventListener('hidden.bs.modal', stopCamera);
    }

    // ── Caméra ────────────────────────────────────────────────────────────────
    function startCamera() {
        const video   = document.getElementById('ged-scan-video');
        const noCam   = document.getElementById('ged-scan-nocam');
        const btnSwitch = document.getElementById('ged-btn-switch');

        video.classList.remove('d-none');
        noCam.classList.add('d-none');

        const constraints = {
            video: {
                facingMode: { ideal: facingMode },
                width:  { ideal: 1920 },
                height: { ideal: 1080 }
            },
            audio: false
        };

        navigator.mediaDevices.getUserMedia(constraints)
            .then(stream => {
                mediaStream  = stream;
                video.srcObject = stream;
                // Afficher quel type de caméra est actif
                const label = document.getElementById('ged-scan-label');
                if (label) {
                    label.textContent = facingMode === 'environment'
                        ? '📷 Caméra arrière'
                        : '🤳 Caméra frontale';
                }
            })
            .catch(() => {
                // Tentative sans contrainte facingMode
                navigator.mediaDevices.getUserMedia({ video: true, audio: false })
                    .then(stream => {
                        mediaStream = stream;
                        video.srcObject = stream;
                        btnSwitch.disabled = true; // pas de switch si unique caméra
                    })
                    .catch(() => {
                        video.classList.add('d-none');
                        noCam.classList.remove('d-none');
                    });
            });
    }

    function stopCamera() {
        if (mediaStream) {
            mediaStream.getTracks().forEach(t => t.stop());
            mediaStream = null;
        }
    }

    function switchCamera() {
        stopCamera();
        facingMode = facingMode === 'environment' ? 'user' : 'environment';
        startCamera();
    }

    // ── Capture ───────────────────────────────────────────────────────────────
    function capture() {
        const video  = document.getElementById('ged-scan-video');
        const canvas = document.getElementById('ged-scan-canvas');

        const w = video.videoWidth  || 1280;
        const h = video.videoHeight || 720;

        canvas.width  = w;
        canvas.height = h;

        const ctx = canvas.getContext('2d');

        // Flip horizontal si caméra frontale (image miroir → correction)
        if (facingMode === 'user') {
            ctx.translate(w, 0);
            ctx.scale(-1, 1);
        }
        ctx.drawImage(video, 0, 0, w, h);

        const dataUrl = canvas.toDataURL('image/jpeg', 0.88);
        capturedImages.push(dataUrl);

        // Flash visuel pour indiquer la capture
        flashEffect();
        renderThumbs();
    }

    function flashEffect() {
        const video = document.getElementById('ged-scan-video');
        video.style.transition = 'opacity 0.08s';
        video.style.opacity    = '0.2';
        setTimeout(() => { video.style.opacity = '1'; }, 160);
    }

    // ── Vignettes ─────────────────────────────────────────────────────────────
    function renderThumbs() {
        const container = document.getElementById('ged-scan-thumbs');
        const empty     = document.getElementById('ged-scan-empty');
        const count     = document.getElementById('ged-scan-count');
        const sendBtn   = document.getElementById('ged-btn-send');

        // Vider sauf le placeholder
        container.querySelectorAll('.ged-thumb').forEach(el => el.remove());

        if (capturedImages.length === 0) {
            empty.classList.remove('d-none');
            count.textContent = '';
            sendBtn.disabled  = true;
            return;
        }

        empty.classList.add('d-none');
        count.textContent = capturedImages.length + ' page' +
            (capturedImages.length > 1 ? 's' : '') + ' capturée' +
            (capturedImages.length > 1 ? 's' : '');
        sendBtn.disabled = false;

        capturedImages.forEach((imgData, i) => {
            const wrap = document.createElement('div');
            wrap.className = 'ged-thumb position-relative';
            wrap.style.cssText = 'display:inline-block;cursor:default';

            const img = document.createElement('img');
            img.src   = imgData;
            img.alt   = 'Page ' + (i + 1);
            img.style.cssText = 'height:72px;width:auto;border-radius:6px;border:2px solid #667eea;display:block';
            img.title = 'Page ' + (i + 1);

            const badge = document.createElement('span');
            badge.textContent = i + 1;
            badge.style.cssText = 'position:absolute;top:3px;left:5px;background:#667eea;color:white;font-size:10px;font-weight:700;border-radius:4px;padding:1px 5px';

            const del = document.createElement('button');
            del.innerHTML = '&times;';
            del.type  = 'button';
            del.title = 'Supprimer cette page';
            del.style.cssText = 'position:absolute;top:-5px;right:-5px;width:18px;height:18px;border:none;border-radius:50%;background:#ef4444;color:white;font-size:11px;line-height:1;cursor:pointer;padding:0;display:flex;align-items:center;justify-content:center';
            del.addEventListener('click', () => {
                capturedImages.splice(i, 1);
                renderThumbs();
            });

            wrap.appendChild(img);
            wrap.appendChild(badge);
            wrap.appendChild(del);
            container.appendChild(wrap);
        });
    }

    // ── jsPDF (chargement dynamique depuis CDN) ────────────────────────────────
    function loadJsPDF(cb) {
        if (window.jspdf && window.jspdf.jsPDF) { cb(); return; }
        const s = document.createElement('script');
        s.src   = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';
        s.onload  = cb;
        s.onerror = () => setStatus('<span class="text-danger"><i class="fas fa-exclamation-circle me-1"></i>Impossible de charger jsPDF (vérifiez votre connexion internet)</span>');
        document.head.appendChild(s);
    }

    // ── Compilation PDF + envoi ───────────────────────────────────────────────
    function buildAndSend() {
        const status  = document.getElementById('ged-scan-status');
        const sendBtn = document.getElementById('ged-btn-send');
        const spinner = document.getElementById('ged-btn-spinner');

        sendBtn.disabled = true;
        spinner.classList.remove('d-none');
        setStatus('Génération du PDF…');

        loadJsPDF(() => {
            try {
                const { jsPDF } = window.jspdf;
                const pdf = new jsPDF({ orientation: 'p', unit: 'mm', format: 'a4' });

                const pageW = pdf.internal.pageSize.getWidth();
                const pageH = pdf.internal.pageSize.getHeight();
                const margin = 5; // mm de marge

                capturedImages.forEach((dataUrl, idx) => {
                    if (idx > 0) pdf.addPage();

                    // Calculer les dimensions en respectant le ratio de l'image
                    const tmpImg = new Image();
                    tmpImg.src   = dataUrl;
                    const nw = tmpImg.naturalWidth  || 1280;
                    const nh = tmpImg.naturalHeight || 720;

                    const maxW = pageW - 2 * margin;
                    const maxH = pageH - 2 * margin;
                    const ratio = Math.min(maxW / nw, maxH / nh);

                    const w = nw * ratio;
                    const h = nh * ratio;
                    const x = (pageW - w) / 2;
                    const y = (pageH - h) / 2;

                    pdf.addImage(dataUrl, 'JPEG', x, y, w, h);
                });

                const blob  = pdf.output('blob');
                const fname = 'scan-' + new Date().toISOString().slice(0, 10) + '-' +
                    Math.floor(Math.random() * 10000) + '.pdf';
                const file  = new File([blob], fname, { type: 'application/pdf' });

                const fd = new FormData();
                fd.append('file', file);

                setStatus('Envoi du document…');

                fetch(uploadUrl, {
                    method:      'POST',
                    body:        fd,
                    credentials: 'same-origin',
                    headers:     getCsrfHeaders()
                })
                .then(r => {
                    if (!r.ok) return r.text().then(t => { throw new Error(t || 'Erreur serveur ' + r.status); });
                    return r.json();
                })
                .then(data => {
                    if (data.success === false) throw new Error(data.message || 'Erreur lors de l\'envoi');
                    setStatus('<span class="text-success fw-semibold"><i class="fas fa-check-circle me-1"></i>Document scanné et enregistré !</span>');
                    capturedImages = [];
                    renderThumbs();
                    setTimeout(() => {
                        bootstrap.Modal.getInstance(document.getElementById('gedScannerModal')).hide();
                        if (redirectUrl) window.location.href = redirectUrl;
                        else window.location.reload();
                    }, 1400);
                })
                .catch(err => {
                    setStatus('<span class="text-danger"><i class="fas fa-exclamation-circle me-1"></i>' + err.message + '</span>');
                    sendBtn.disabled = false;
                    spinner.classList.add('d-none');
                });

            } catch (e) {
                setStatus('<span class="text-danger"><i class="fas fa-exclamation-circle me-1"></i>Erreur PDF : ' + e.message + '</span>');
                sendBtn.disabled = false;
                spinner.classList.add('d-none');
            }
        });
    }

    function setStatus(html) {
        const el = document.getElementById('ged-scan-status');
        if (el) el.innerHTML = html;
    }

    // ── API publique ──────────────────────────────────────────────────────────
    /**
     * Ouvrir le scanner.
     *
     * @param {string} ajaxUploadUrl  - Endpoint AJAX POST multipart (retourne JSON {success, message})
     * @param {string} [afterUrl]     - URL de redirection après succès (défaut: rechargement)
     */
    window.openScanner = function (ajaxUploadUrl, afterUrl) {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            alert('Votre navigateur ne supporte pas l\'accès à la caméra (getUserMedia).\n' +
                  'Veuillez utiliser Chrome, Firefox ou Safari récent en HTTPS.');
            return;
        }

        uploadUrl   = ajaxUploadUrl;
        redirectUrl = afterUrl || null;
        capturedImages = [];

        buildModal();

        const modalEl = document.getElementById('gedScannerModal');
        const modal   = bootstrap.Modal.getOrCreateInstance(modalEl);

        // Réinitialiser l'état
        renderThumbs();
        setStatus('');
        document.getElementById('ged-scan-label').textContent = '';

        // Brancher les boutons (remplacement pour éviter doublon d'événement)
        rebind('ged-btn-capture', 'click', capture);
        rebind('ged-btn-switch',  'click', switchCamera);
        rebind('ged-btn-send',    'click', buildAndSend);

        modal.show();
        startCamera();
    };

    function rebind(id, event, handler) {
        const el  = document.getElementById(id);
        const neo = el.cloneNode(true);
        el.parentNode.replaceChild(neo, el);
        document.getElementById(id).addEventListener(event, handler);
    }

})();
