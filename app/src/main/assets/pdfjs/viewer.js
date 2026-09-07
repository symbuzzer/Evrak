const urlParams = new URLSearchParams(window.location.search);
const pdfUrl = urlParams.get('file');
const loadingElement = document.getElementById('loading');

window.pageStates = new Map();
window.pageElements = new Map();
window.pdfDoc = null;
window.currentScale = 1.0;

if (pdfUrl) {
    const pdfjsLib = window['pdfjs-dist/build/pdf'];
    pdfjsLib.GlobalWorkerOptions.workerSrc = window.isMjs ? 'pdf.worker.min.mjs' : 'pdf.worker.min.js';

    const container = document.getElementById('viewer-container');

    pdfjsLib.getDocument(pdfUrl).promise.then(async pdf => {
        window.pdfDoc = pdf;
        loadingElement.style.display = 'none';

        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                const pageNum = parseInt(entry.target.dataset.pageNumber);
                if (entry.isIntersecting) {
                    const state = window.pageStates.get(pageNum);
                    if (state && !state.rendered && !state.rendering) {
                        renderPage(pdf, pageNum, entry.target);
                    }
                }
            });
        }, {
            rootMargin: '1200px 0px',
            threshold: 0.01
        });

        function renderPage(pdf, pageNum, containerDiv) {
            const state = window.pageStates.get(pageNum);
            if (!state || state.rendering) return;
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

                const renderContext = { canvasContext: context, viewport: viewport };
                page.render(renderContext).promise.then(() => {
                    containerDiv.innerHTML = '';
                    containerDiv.appendChild(canvas);
                    containerDiv.className = '';
                    state.rendered = true;
                    state.rendering = false;
                    containerDiv.style.height = 'auto';
                });
            });
        }

        const updateHeights = async () => {
            const containerWidth = container.offsetWidth || window.innerWidth || 360;
            for (let [pageNum, placeholder] of window.pageElements) {
                const state = window.pageStates.get(pageNum);
                if (state && state.rendered) continue;
                const page = await pdf.getPage(pageNum);
                const viewport = page.getViewport({ scale: 1.0 });
                placeholder.style.height = Math.max(300, (containerWidth * 0.95 * (viewport.height / viewport.width))) + 'px';
            }
        };

        for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
            const placeholder = document.createElement('div');
            placeholder.className = 'page-placeholder';
            placeholder.dataset.pageNumber = pageNum;
            placeholder.innerText = `Sayfa ${pageNum} yükleniyor...`;
            container.appendChild(placeholder);

            window.pageStates.set(pageNum, { rendered: false, rendering: false, canvas: null });
            window.pageElements.set(pageNum, placeholder);
            observer.observe(placeholder);
        }

        await updateHeights();
        window.addEventListener('resize', updateHeights);

    }).catch(error => {
        loadingElement.innerText = 'Hata: ' + error.message;
    });
}

let lastTapTime = 0;
let initialPinchDistance = 0;
let initialPinchScale = 1.0;

document.addEventListener('touchend', function(e) {
    if (e.touches.length > 0) return;
    const currentTime = new Date().getTime();
    if (currentTime - lastTapTime < 350) {
        const touch = e.changedTouches[0];
        if (window.currentScale > 1.2) zoomTo(1.0, touch.clientX, touch.clientY, true);
        else zoomTo(2.5, touch.clientX, touch.clientY, true);
        e.preventDefault();
    }
    lastTapTime = currentTime;
}, { passive: false });

document.addEventListener('touchstart', function(e) {
    if (e.touches.length === 2) {
        initialPinchDistance = Math.hypot(e.touches[0].pageX - e.touches[1].pageX, e.touches[0].pageY - e.touches[1].pageY);
        initialPinchScale = window.currentScale;
    }
}, { passive: false });

document.addEventListener('touchmove', function(e) {
    if (e.touches.length === 2) {
        const currentDistance = Math.hypot(e.touches[0].pageX - e.touches[1].pageX, e.touches[0].pageY - e.touches[1].pageY);
        const delta = currentDistance / initialPinchDistance;
        const newScale = Math.min(Math.max(initialPinchScale * delta, 1.0), 5.0);
        if (newScale !== window.currentScale) {
            zoomTo(newScale, (e.touches[0].clientX + e.touches[1].clientX) / 2, (e.touches[0].clientY + e.touches[1].clientY) / 2);
        }
        e.preventDefault();
    }
}, { passive: false });

let isAnimating = false;
function zoomTo(newScale, viewportX, viewportY, animate = false) {
    const container = document.getElementById('viewer-container');
    if (!container || isAnimating) return;

    const startScale = window.currentScale;
    const endScale = newScale;
    const startScrollX = window.scrollX;
    const startScrollY = window.scrollY;

    const contentX = (startScrollX + viewportX) / startScale;
    const contentY = (startScrollY + viewportY) / startScale;
    const targetScrollX = contentX * endScale - viewportX;
    const targetScrollY = contentY * endScale - viewportY;

    const updateBodySize = (scale) => {
        document.body.style.width = (container.offsetWidth * scale) + 'px';
        document.body.style.height = (container.offsetHeight * scale) + 'px';
    };

    if (!animate) {
        window.currentScale = endScale;
        container.style.transform = `scale(${window.currentScale})`;
        updateBodySize(window.currentScale);
        window.scrollTo(targetScrollX, targetScrollY);
        return;
    }

    isAnimating = true;
    const duration = 200;
    const startTime = performance.now();

    function step(now) {
        const elapsed = now - startTime;
        const progress = Math.min(elapsed / duration, 1.0);
        const ease = 1 - Math.pow(1 - progress, 3);
        const tempScale = startScale + (endScale - startScale) * ease;
        container.style.transform = `scale(${tempScale})`;
        updateBodySize(tempScale);
        window.scrollTo(startScrollX + (targetScrollX - startScrollX) * ease, startScrollY + (targetScrollY - startScrollY) * ease);
        if (progress < 1.0) requestAnimationFrame(step);
        else {
            window.currentScale = endScale;
            isAnimating = false;
        }
    }
    requestAnimationFrame(step);
}
