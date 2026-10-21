const urlParams = new URLSearchParams(window.location.search);
const pdfUrl = urlParams.get('file');
const loadingElement = document.getElementById('loading');

window.pageStates = new Map();
window.pageElements = new Map();
window.pdfDoc = null;
window.currentScale = 1.0;
window.searchMatches = [];
window.currentMatchIndex = -1;
window.activeSearchQuery = "";

function normalizeText(str) {
    if (!str) return "";
    return str
        .replace(/İ/g, 'i')
        .replace(/I/g, 'ı')
        .toLowerCase()
        .replace(/\u0307/g, '');
}

let measureCanvas = null;
let measureCtx = null;
function getMeasureContext() {
    if (!measureCtx) {
        measureCanvas = document.createElement('canvas');
        measureCtx = measureCanvas.getContext('2d');
    }
    return measureCtx;
}

if (pdfUrl) {
    const pdfjsLib = window['pdfjs-dist/build/pdf'];
    pdfjsLib.GlobalWorkerOptions.workerSrc = window.isMjs ? 'pdf.worker.min.mjs' : 'pdf.worker.min.js';

    const container = document.getElementById('viewer-container');

    pdfjsLib.getDocument(pdfUrl).promise.then(async pdf => {
        window.pdfDoc = pdf;
        loadingElement.style.display = 'none';

        if (window.AndroidBridge && window.AndroidBridge.notifyPageChanged) {
            window.AndroidBridge.notifyPageChanged(1, pdf.numPages);
        }

        const checkCurrentPage = () => {
            let closestPage = 1;
            let minDistance = Infinity;
            for (let [pageNum, el] of window.pageElements) {
                const rect = el.getBoundingClientRect();
                const distance = Math.abs(rect.top);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPage = pageNum;
                }
            }
            if (window.AndroidBridge && window.AndroidBridge.notifyPageChanged) {
                window.AndroidBridge.notifyPageChanged(closestPage, pdf.numPages);
            }
        };

        window.addEventListener('scroll', checkCurrentPage, { passive: true });
        window.addEventListener('resize', checkCurrentPage, { passive: true });

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
                    containerDiv.className = 'page-wrapper';
                    state.rendered = true;
                    state.rendering = false;
                    containerDiv.style.height = 'auto';

                    if (window.activeSearchQuery) {
                        window.renderSearchHighlights(window.currentMatchIndex);
                    }
                });
            });
        }
        window.renderPageFunc = renderPage;

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

window.searchPdf = async function(query) {
    if (!window.pdfDoc || !query || !query.trim()) {
        window.clearPdfSearch();
        return;
    }
    const cleanQuery = normalizeText(query.trim());
    if (cleanQuery.length === 0) {
        window.clearPdfSearch();
        return;
    }

    window.activeSearchQuery = cleanQuery;
    window.searchMatches = [];
    const ctx = getMeasureContext();

    for (let pageNum = 1; pageNum <= window.pdfDoc.numPages; pageNum++) {
        try {
            const page = await window.pdfDoc.getPage(pageNum);
            const textContent = await page.getTextContent();
            const viewport = page.getViewport({ scale: 2.0 });

            let fullText = "";
            const itemSpans = [];
            let pos = 0;

            for (let i = 0; i < textContent.items.length; i++) {
                const item = textContent.items[i];
                const str = item.str || "";
                if (str.length === 0) continue;

                itemSpans.push({
                    item: item,
                    start: pos,
                    end: pos + str.length
                });
                fullText += str + " ";
                pos += str.length + 1;
            }

            const lowerFullText = normalizeText(fullText);
            let idx = lowerFullText.indexOf(cleanQuery);

            while (idx !== -1) {
                const matchEnd = idx + cleanQuery.length;
                const matchBoxes = [];

                itemSpans.forEach(span => {
                    if (span.end > idx && span.start < matchEnd) {
                        const overlapStart = Math.max(idx, span.start);
                        const overlapEnd = Math.min(matchEnd, span.end);

                        const localStart = overlapStart - span.start;
                        const localEnd = overlapEnd - span.start;

                        const item = span.item;
                        const str = item.str || "";

                        if (item.transform && item.transform.length >= 6) {
                            const tx = item.transform[4];
                            const ty = item.transform[5];
                            const fontHeight = Math.abs(item.transform[3]) || item.height || 12;

                            ctx.font = fontHeight + 'px sans-serif';
                            const fullStrW = ctx.measureText(str).width || 1;
                            const beforeStrW = ctx.measureText(str.substring(0, localStart)).width;
                            const matchStrW = ctx.measureText(str.substring(localStart, localEnd)).width;

                            const itemWidth = item.width || fullStrW;
                            const ratioStart = beforeStrW / fullStrW;
                            const ratioEnd = (beforeStrW + matchStrW) / fullStrW;

                            const subX1 = tx + (itemWidth * ratioStart);
                            const subX2 = tx + (itemWidth * ratioEnd);
                            const subY1 = ty - fontHeight * 0.15;
                            const subY2 = ty + fontHeight * 0.85;

                            matchBoxes.push({
                                pdfRect: [subX1, subY1, subX2, subY2]
                            });
                        }
                    }
                });

                if (matchBoxes.length > 0) {
                    window.searchMatches.push({
                        pageNum: pageNum,
                        boxes: matchBoxes,
                        viewport: viewport
                    });
                }

                idx = lowerFullText.indexOf(cleanQuery, idx + cleanQuery.length);
            }
        } catch (e) {
            console.error("Error searching page " + pageNum, e);
        }
    }

    if (window.searchMatches.length > 0) {
        window.currentMatchIndex = 0;
        window.showPdfMatch(0);
    } else {
        window.currentMatchIndex = -1;
        window.clearSearchHighlights();
        if (window.AndroidBridge && window.AndroidBridge.notifySearchResult) {
            window.AndroidBridge.notifySearchResult(0, 0);
        }
    }
};

window.showPdfMatch = function(index) {
    if (index < 0 || index >= window.searchMatches.length) return;
    window.currentMatchIndex = index;
    const match = window.searchMatches[index];

    const pageEl = window.pageElements.get(match.pageNum);
    if (!pageEl) return;

    const state = window.pageStates.get(match.pageNum);
    if (state && !state.rendered && !state.rendering) {
        if (window.renderPageFunc) window.renderPageFunc(window.pdfDoc, match.pageNum, pageEl);
    }

    window.renderSearchHighlights(index);

    const scrollToMatch = (attempts = 0) => {
        const canvas = pageEl.querySelector('canvas');
        const activeMarker = pageEl.querySelector('.search-marker-active');

        if (!activeMarker && attempts < 10) {
            setTimeout(() => scrollToMatch(attempts + 1), 50);
            return;
        }

        const canvasLeft = canvas ? canvas.offsetLeft : 0;
        const canvasTop = canvas ? canvas.offsetTop : 0;

        const markerLeft = activeMarker ? activeMarker.offsetLeft : 0;
        const markerTop = activeMarker ? activeMarker.offsetTop : 0;
        const markerWidth = activeMarker ? activeMarker.offsetWidth : 20;
        const markerHeight = activeMarker ? activeMarker.offsetHeight : 20;

        const unscaledX = pageEl.offsetLeft + canvasLeft + markerLeft;
        const unscaledY = pageEl.offsetTop + canvasTop + markerTop;

        const scale = window.currentScale || 1.0;

        const scaledX = unscaledX * scale;
        const scaledY = unscaledY * scale;

        const targetX = scaledX + (markerWidth * scale / 2) - (window.innerWidth / 2);
        const targetY = scaledY + (markerHeight * scale / 2) - (window.innerHeight / 3);

        window.scrollTo({
            left: Math.max(0, targetX),
            top: Math.max(0, targetY),
            behavior: 'smooth'
        });
    };

    setTimeout(() => scrollToMatch(0), 40);

    if (window.AndroidBridge && window.AndroidBridge.notifySearchResult) {
        window.AndroidBridge.notifySearchResult(index + 1, window.searchMatches.length);
    }
};

window.nextPdfMatch = function() {
    if (window.searchMatches.length === 0) return;
    const nextIdx = (window.currentMatchIndex + 1) % window.searchMatches.length;
    window.showPdfMatch(nextIdx);
};

window.prevPdfMatch = function() {
    if (window.searchMatches.length === 0) return;
    const prevIdx = (window.currentMatchIndex - 1 + window.searchMatches.length) % window.searchMatches.length;
    window.showPdfMatch(prevIdx);
};

window.clearPdfSearch = function() {
    window.activeSearchQuery = "";
    window.searchMatches = [];
    window.currentMatchIndex = -1;
    window.clearSearchHighlights();
    if (window.AndroidBridge && window.AndroidBridge.notifySearchResult) {
        window.AndroidBridge.notifySearchResult(0, 0);
    }
};

window.clearSearchHighlights = function() {
    document.querySelectorAll('.search-highlight-container').forEach(el => el.remove());
};

window.renderSearchHighlights = function(activeIdx) {
    window.clearSearchHighlights();
    if (!window.activeSearchQuery || window.searchMatches.length === 0) return;

    window.searchMatches.forEach((match, idx) => {
        const pageEl = window.pageElements.get(match.pageNum);
        if (!pageEl) return;

        const canvas = pageEl.querySelector('canvas');
        if (!canvas) return;

        let highlightContainer = pageEl.querySelector('.search-highlight-container');
        if (!highlightContainer) {
            highlightContainer = document.createElement('div');
            highlightContainer.className = 'search-highlight-container';
            highlightContainer.style.position = 'absolute';
            highlightContainer.style.pointerEvents = 'none';
            highlightContainer.style.zIndex = '10';
            if (pageEl.style.position !== 'relative' && pageEl.style.position !== 'absolute') {
                pageEl.style.position = 'relative';
            }
            pageEl.appendChild(highlightContainer);
        }

        highlightContainer.style.left = canvas.offsetLeft + 'px';
        highlightContainer.style.top = canvas.offsetTop + 'px';
        highlightContainer.style.width = canvas.clientWidth + 'px';
        highlightContainer.style.height = canvas.clientHeight + 'px';

        const isActive = (idx === activeIdx);
        const viewport = match.viewport;
        const scaleX = viewport ? (canvas.clientWidth / viewport.width) : 1.0;
        const scaleY = viewport ? (canvas.clientHeight / viewport.height) : 1.0;

        match.boxes.forEach(box => {
            if (!box.pdfRect || box.pdfRect.length < 4) return;

            let displayLeft = 0;
            let displayTop = 0;
            let displayWidth = 0;
            let displayHeight = 0;

            if (viewport && viewport.convertToViewportRectangle) {
                const vRect = viewport.convertToViewportRectangle(box.pdfRect);
                displayLeft = Math.min(vRect[0], vRect[2]) * scaleX;
                displayTop = Math.min(vRect[1], vRect[3]) * scaleY;
                displayWidth = Math.abs(vRect[2] - vRect[0]) * scaleX;
                displayHeight = Math.abs(vRect[3] - vRect[1]) * scaleY;
            } else {
                displayLeft = box.pdfRect[0] * scaleX;
                displayTop = box.pdfRect[1] * scaleY;
                displayWidth = (box.pdfRect[2] - box.pdfRect[0]) * scaleX;
                displayHeight = (box.pdfRect[3] - box.pdfRect[1]) * scaleY;
            }

            const marker = document.createElement('div');
            if (isActive) {
                marker.className = 'search-marker-active';
            } else {
                marker.className = 'search-marker';
            }
            marker.style.position = 'absolute';
            marker.style.background = isActive ? 'rgba(255, 152, 0, 0.55)' : 'rgba(255, 235, 59, 0.45)';
            marker.style.border = isActive ? '2px solid #e65100' : '1px solid #fbc02d';
            marker.style.borderRadius = '2px';
            marker.style.left = Math.max(0, displayLeft) + 'px';
            marker.style.top = Math.max(0, displayTop) + 'px';
            marker.style.width = Math.max(4, displayWidth) + 'px';
            marker.style.height = Math.max(4, displayHeight) + 'px';
            highlightContainer.appendChild(marker);
        });
    });
};

let lastTapTime = 0;
let initialPinchDistance = 0;
let initialPinchScale = 1.0;
let initialScrollX = 0;
let initialScrollY = 0;
let anchorContentX = 0;
let anchorContentY = 0;

document.addEventListener('touchstart', function(e) {
    if (e.touches.length === 2) {
        initialPinchDistance = Math.hypot(
            e.touches[0].clientX - e.touches[1].clientX,
            e.touches[0].clientY - e.touches[1].clientY
        );
        initialPinchScale = window.currentScale || 1.0;
        initialScrollX = window.scrollX;
        initialScrollY = window.scrollY;

        const focalX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        const focalY = (e.touches[0].clientY + e.touches[1].clientY) / 2;

        anchorContentX = (initialScrollX + focalX) / initialPinchScale;
        anchorContentY = (initialScrollY + focalY) / initialPinchScale;
    }
}, { passive: false });

document.addEventListener('touchmove', function(e) {
    if (e.touches.length === 2 && initialPinchDistance > 0) {
        const currentDistance = Math.hypot(
            e.touches[0].clientX - e.touches[1].clientX,
            e.touches[0].clientY - e.touches[1].clientY
        );
        const delta = currentDistance / initialPinchDistance;
        const newScale = Math.min(Math.max(initialPinchScale * delta, 1.0), 5.0);

        const currentFocalX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        const currentFocalY = (e.touches[0].clientY + e.touches[1].clientY) / 2;

        zoomToPinch(newScale, currentFocalX, currentFocalY);
        e.preventDefault();
    }
}, { passive: false });

document.addEventListener('touchend', function(e) {
    if (e.touches.length < 2) {
        initialPinchDistance = 0;
    }
    if (e.touches.length > 0) return;

    const currentTime = new Date().getTime();
    if (currentTime - lastTapTime < 350) {
        const touch = e.changedTouches[0];
        if (window.currentScale > 1.2) {
            zoomToFocal(1.0, touch.clientX, touch.clientY, true);
        } else {
            zoomToFocal(2.5, touch.clientX, touch.clientY, true);
        }
        e.preventDefault();
    }
    lastTapTime = currentTime;
}, { passive: false });

function zoomToPinch(targetScale, currentFocalX, currentFocalY) {
    const container = document.getElementById('viewer-container');
    if (!container) return;

    window.currentScale = targetScale;
    container.style.transform = `scale(${targetScale})`;

    document.body.style.width = (container.offsetWidth * targetScale) + 'px';
    document.body.style.height = (container.offsetHeight * targetScale) + 'px';

    const targetScrollX = anchorContentX * targetScale - currentFocalX;
    const targetScrollY = anchorContentY * targetScale - currentFocalY;

    window.scrollTo(Math.max(0, targetScrollX), Math.max(0, targetScrollY));
}

let isAnimating = false;
function zoomToFocal(newScale, viewportX, viewportY, animate = true) {
    const container = document.getElementById('viewer-container');
    if (!container || isAnimating) return;

    const startScale = window.currentScale || 1.0;
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
        container.style.transform = `scale(${endScale})`;
        updateBodySize(endScale);
        window.scrollTo(Math.max(0, targetScrollX), Math.max(0, targetScrollY));
        return;
    }

    isAnimating = true;
    const duration = 220;
    const startTime = performance.now();

    const maxAnimScale = Math.max(startScale, endScale);
    updateBodySize(maxAnimScale);

    function step(now) {
        const elapsed = now - startTime;
        const progress = Math.min(elapsed / duration, 1.0);
        const ease = 1 - Math.pow(1 - progress, 3);

        const tempScale = startScale + (endScale - startScale) * ease;
        const tempScrollX = startScrollX + (targetScrollX - startScrollX) * ease;
        const tempScrollY = startScrollY + (targetScrollY - startScrollY) * ease;

        container.style.transform = `scale(${tempScale})`;
        window.scrollTo(Math.max(0, tempScrollX), Math.max(0, tempScrollY));

        if (progress < 1.0) {
            requestAnimationFrame(step);
        } else {
            window.currentScale = endScale;
            updateBodySize(endScale);
            isAnimating = false;
        }
    }
    requestAnimationFrame(step);
}
