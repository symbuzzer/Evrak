const urlParams = new URLSearchParams(window.location.search);
const pdfUrl = urlParams.get('file');
const loadingElement = document.getElementById('loading');

console.log('viewer.js started, pdfUrl:', pdfUrl);

if (pdfUrl) {
    if (typeof window['pdfjs-dist/build/pdf'] === 'undefined') {
        console.error('PDF.js library is undefined in viewer.js');
        loadingElement.style.display = 'none';
        const errDiv = document.getElementById('error-message');
        const details = document.getElementById('error-details');
        if (errDiv) errDiv.style.display = 'block';
        if (details) details.innerText = 'PDF.js kütüphanesi yüklenemedi. Dosyalar eksik olabilir.';
        throw new Error('PDF.js Dist missing');
    }

    const pdfjsLib = window['pdfjs-dist/build/pdf'];
    console.log('PDF.js library found:', pdfjsLib);

    // Set worker source based on what is available
    pdfjsLib.GlobalWorkerOptions.workerSrc = window.isMjs ? 'pdf.worker.min.mjs' : 'pdf.worker.min.js';
    console.log('Worker source set to:', pdfjsLib.GlobalWorkerOptions.workerSrc);

    const container = document.getElementById('viewer-container');

    pdfjsLib.getDocument(pdfUrl).promise.then(pdf => {
        console.log('PDF loaded, pages:', pdf.numPages);
        loadingElement.style.display = 'none';

        for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
            const canvas = document.createElement('canvas');
            canvas.className = 'page';
            container.appendChild(canvas);

            renderPage(pdf, pageNum, canvas);
        }
    }).catch(error => {
        console.error('Error loading PDF document:', error);
        loadingElement.innerText = 'PDF yüklenirken hata oluştu: ' + error.message;
    });
} else {
    loadingElement.innerText = 'PDF dosyası belirtilmedi.';
}

function renderPage(pdf, pageNum, canvas) {
    pdf.getPage(pageNum).then(page => {
        // Higher initial render scale to maintain quality when zooming via WebView
        const viewport = page.getViewport({ scale: 2.0 });
        const context = canvas.getContext('2d');

        canvas.height = viewport.height;
        canvas.width = viewport.width;

        // Ensure canvases don't scale themselves down, let the parent layout and browser zoom handle it
        canvas.style.width = "auto";
        canvas.style.maxWidth = "100%";
        canvas.style.height = "auto";

        const renderContext = {
            canvasContext: context,
            viewport: viewport
        };
        page.render(renderContext);
    });
}

// JS-based Natural Zoom Engine
let currentScale = 1.0;
let lastTapTime = 0;

// Pinch tracking
let initialPinchDistance = 0;
let initialPinchScale = 1.0;

// Double Tap to Zoom
document.addEventListener('touchend', function(e) {
    if (e.touches.length > 0) return;

    const currentTime = new Date().getTime();
    const tapLength = currentTime - lastTapTime;

    if (tapLength < 350 && tapLength > 0) {
        const touch = e.changedTouches[0];
        const tapX = touch.clientX;
        const tapY = touch.clientY;

        if (currentScale > 1.2) {
            zoomTo(1.0, tapX, tapY, true);
        } else {
            zoomTo(2.5, tapX, tapY, true);
        }
        e.preventDefault();
    }
    lastTapTime = currentTime;
}, { passive: false });

// Pinch to Zoom
document.addEventListener('touchstart', function(e) {
    if (e.touches.length === 2) {
        initialPinchDistance = Math.hypot(
            e.touches[0].pageX - e.touches[1].pageX,
            e.touches[0].pageY - e.touches[1].pageY
        );
        initialPinchScale = currentScale;
    }
}, { passive: false });

document.addEventListener('touchmove', function(e) {
    if (e.touches.length === 2) {
        const currentDistance = Math.hypot(
            e.touches[0].pageX - e.touches[1].pageX,
            e.touches[0].pageY - e.touches[1].pageY
        );

        const delta = currentDistance / initialPinchDistance;
        const newScale = Math.min(Math.max(initialPinchScale * delta, 1.0), 5.0);

        if (newScale !== currentScale) {
            const centerX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
            const centerY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
            zoomTo(newScale, centerX, centerY);
        }
        e.preventDefault();
    }
}, { passive: false });

let isAnimating = false;

function zoomTo(newScale, viewportX, viewportY, animate = false) {
    const container = document.getElementById('viewer-container');
    if (!container || isAnimating) return;

    const startScale = currentScale;
    const endScale = newScale;

    // Get current scroll positions (robustly)
    const startScrollX = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft;
    const startScrollY = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;

    // Calculate the target scroll position at the END scale
    const contentX = (startScrollX + viewportX) / startScale;
    const contentY = (startScrollY + viewportY) / startScale;
    const targetScrollX = contentX * endScale - viewportX;
    const targetScrollY = contentY * endScale - viewportY;

    // 1. Update body dimensions to the MAX size immediately to allow scrolling during animation
    const containerWidth = container.offsetWidth;
    const containerHeight = container.offsetHeight;
    document.body.style.width = (containerWidth * Math.max(startScale, endScale)) + 'px';
    document.body.style.height = (containerHeight * Math.max(startScale, endScale)) + 'px';

    if (!animate) {
        currentScale = endScale;
        container.style.transformOrigin = '0 0';
        container.style.transform = `scale(${currentScale})`;

        document.body.style.width = (containerWidth * currentScale) + 'px';
        document.body.style.height = (containerHeight * currentScale) + 'px';

        void document.body.offsetHeight;
        window.scrollTo(targetScrollX, targetScrollY);
        return;
    }

    // Animation loop
    isAnimating = true;
    const duration = 250;
    const startTime = performance.now();

    function step(now) {
        const elapsed = now - startTime;
        const progress = Math.min(elapsed / duration, 1.0);

        // Easing: easeOutCubic
        const ease = 1 - Math.pow(1 - progress, 3);

        const tempScale = startScale + (endScale - startScale) * ease;
        const tempScrollX = startScrollX + (targetScrollX - startScrollX) * ease;
        const tempScrollY = startScrollY + (targetScrollY - startScrollY) * ease;

        container.style.transformOrigin = '0 0';
        container.style.transform = `scale(${tempScale})`;
        window.scrollTo(tempScrollX, tempScrollY);

        if (progress < 1.0) {
            requestAnimationFrame(step);
        } else {
            currentScale = endScale;
            document.body.style.width = (containerWidth * currentScale) + 'px';
            document.body.style.height = (containerHeight * currentScale) + 'px';
            isAnimating = false;
        }
    }

    requestAnimationFrame(step);
}

console.log('Advanced Zoom Engine initialized');
