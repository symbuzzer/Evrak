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
    const pageStates = new Map(); // pageNum -> { rendered: bool, rendering: bool, canvas: element }

    pdfjsLib.getDocument(pdfUrl).promise.then(async pdf => {
        console.log('PDF loaded, pages:', pdf.numPages);
        loadingElement.style.display = 'none';

        if (window.Android) {
            window.Android.updatePageCount(pdf.numPages);
        }

        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                const pageNum = parseInt(entry.target.dataset.pageNumber);
                if (entry.isIntersecting) {
                    if (window.Android) {
                        // Find the minimum page number currently intersecting
                        const visiblePages = Array.from(document.querySelectorAll('.page-placeholder, .page'))
                            .filter(el => {
                                const rect = el.getBoundingClientRect();
                                return rect.bottom > 0 && rect.top < window.innerHeight;
                            })
                            .map(el => parseInt(el.dataset.pageNumber || el.parentElement?.dataset.pageNumber));

                        if (visiblePages.length > 0) {
                            const minPage = Math.min(...visiblePages.filter(p => !isNaN(p)));
                            window.Android.updateCurrentPage(minPage - 1);
                        }
                    }

                    if (!pageStates.get(pageNum).rendered && !pageStates.get(pageNum).rendering) {
                        renderPage(pdf, pageNum, entry.target);
                    }
                } else {
                    cleanupPage(pageNum, entry.target);
                }
            });
        }, {
            rootMargin: '800px 0px',
            threshold: 0
        });

        function cleanupPage(pageNum, containerDiv) {
            const state = pageStates.get(pageNum);
            if (state && state.rendered && !state.rendering) {
                containerDiv.innerHTML = `Sayfa ${pageNum} yükleniyor...`;
                containerDiv.className = 'page-placeholder';
                state.rendered = false;
                state.canvas = null;
            }
        }

        function renderPage(pdf, pageNum, containerDiv) {
            const state = pageStates.get(pageNum);
            if (!state) return;
            state.rendering = true;

            pdf.getPage(pageNum).then(page => {
                const viewport = page.getViewport({ scale: 2.0 });

                let canvas = state.canvas;
                if (!canvas) {
                    canvas = document.createElement('canvas');
                    canvas.className = 'page';
                    state.canvas = canvas;
                }

                const context = canvas.getContext('2d');
                canvas.height = viewport.height;
                canvas.width = viewport.width;

                canvas.style.width = "100%";
                canvas.style.height = "auto";

                const renderContext = {
                    canvasContext: context,
                    viewport: viewport
                };

                page.render(renderContext).promise.then(() => {
                    containerDiv.innerHTML = '';
                    containerDiv.appendChild(canvas);
                    containerDiv.className = '';
                    state.rendered = true;
                    state.rendering = false;
                });
            });
        }

        // First pass: Create placeholders for all pages
        for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
            const placeholder = document.createElement('div');
            placeholder.className = 'page-placeholder';
            placeholder.dataset.pageNumber = pageNum;
            placeholder.innerText = `Sayfa ${pageNum} yükleniyor...`;
            container.appendChild(placeholder);

            pageStates.set(pageNum, { rendered: false, rendering: false, canvas: null });

            const page = await pdf.getPage(pageNum);
            const viewport = page.getViewport({ scale: 1.0 });

            placeholder.style.height = (container.offsetWidth * 0.95 * (viewport.height / viewport.width)) + 'px';

            observer.observe(placeholder);
        }
    }).catch(error => {
        console.error('Error loading PDF document:', error);
        loadingElement.innerText = 'PDF yüklenirken hata oluştu: ' + error.message;
    });
} else {
    loadingElement.innerText = 'PDF dosyası belirtilmedi.';
}

// JS-based Natural Zoom Engine
let currentScale = 1.0;
let lastTapTime = 0;
let initialPinchDistance = 0;
let initialPinchScale = 1.0;

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
    const startScrollX = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft;
    const startScrollY = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;
    const contentX = (startScrollX + viewportX) / startScale;
    const contentY = (startScrollY + viewportY) / startScale;
    const targetScrollX = contentX * endScale - viewportX;
    const targetScrollY = contentY * endScale - viewportY;

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

    isAnimating = true;
    const duration = 250;
    const startTime = performance.now();

    function step(now) {
        const elapsed = now - startTime;
        const progress = Math.min(elapsed / duration, 1.0);
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

window.scrollToPage = function(index) {
    console.log('scrollToPage called:', index);
    const pageNum = index + 1;
    const placeholders = document.querySelectorAll('.page-placeholder, [data-page-number]');
    let target = null;
    for (let el of placeholders) {
        if (parseInt(el.dataset.pageNumber) === pageNum) {
            target = el;
            break;
        }
    }

    if (target) {
        // If we are zoomed, we might need a custom scroll logic, but let's try scrollIntoView first
        target.scrollIntoView({ behavior: 'auto', block: 'start' });
    }
};
