package com.avalibeyaz.evrak.ui

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Text


class ObservableWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onScrollStateChanged: ((offset: Int, range: Int, extent: Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        notifyScroll()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        notifyScroll()
    }

    fun notifyScroll() {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val offset = computeVerticalScrollOffset()
        onScrollStateChanged?.invoke(offset, range, extent)
    }

    fun getScrollOffset(): Int = computeVerticalScrollOffset()
    fun getScrollRange(): Int = computeVerticalScrollRange()
    fun getScrollExtent(): Int = computeVerticalScrollExtent()
}


class WebViewScrollState {
    var offset by mutableIntStateOf(0)
    var range by mutableIntStateOf(0)
    var extent by mutableIntStateOf(0)

    val scrollRatio: Float
        get() {
            val maxScroll = range - extent
            if (maxScroll <= 0) return 0f
            return (offset.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
        }

    val thumbSizeRatio: Float
        get() {
            if (range <= 0) return 1f
            return (extent.toFloat() / range.toFloat()).coerceIn(0.1f, 1f)
        }

    val isScrollable: Boolean
        get() = range > extent && extent > 0
}

@Composable
fun rememberWebViewScrollState(): WebViewScrollState {
    return remember { WebViewScrollState() }
}


data class ScrollbarStateAdapter(
    val scrollRatio: Float,
    val thumbSizeRatio: Float,
    val isScrollable: Boolean,
    val onDrag: (targetRatio: Float) -> Unit
)

fun webViewScrollbarAdapter(
    state: WebViewScrollState,
    webView: ObservableWebView?
): ScrollbarStateAdapter {
    return ScrollbarStateAdapter(
        scrollRatio = state.scrollRatio,
        thumbSizeRatio = state.thumbSizeRatio,
        isScrollable = state.isScrollable,
        onDrag = { targetRatio ->
            webView?.let { wv ->
                val maxScroll = (wv.getScrollRange() - wv.getScrollExtent()).coerceAtLeast(0)
                val targetY = (targetRatio * maxScroll).toInt()
                wv.scrollTo(wv.scrollX, targetY)
                state.offset = wv.getScrollOffset()
            }
        }
    )
}

@Composable
fun rememberLazyColumnScrollbarAdapter(
    listState: LazyListState,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): ScrollbarStateAdapter {
    val totalItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    val visibleItemsInfo by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo } }
    val visibleItemsCount by remember { derivedStateOf { visibleItemsInfo.size } }

    val isScrollable by remember {
        derivedStateOf {
            if (totalItems <= 0) false
            else if (totalItems > visibleItemsCount) true
            else {
                val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                val totalHeight = visibleItemsInfo.sumOf { it.size }
                totalHeight > viewportHeight
            }
        }
    }

    val scrollRatio by remember {
        derivedStateOf {
            if (totalItems <= 1) {
                val firstItemSize = visibleItemsInfo.firstOrNull()?.size ?: 1
                val maxScroll = (firstItemSize - (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)).coerceAtLeast(1)
                (listState.firstVisibleItemScrollOffset.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
            } else {
                val firstItem = visibleItemsInfo.firstOrNull()
                val firstItemSize = firstItem?.size ?: 1
                val firstItemOffset = listState.firstVisibleItemScrollOffset.toFloat() / firstItemSize.toFloat()
                val progress = listState.firstVisibleItemIndex.toFloat() + firstItemOffset
                (progress / (totalItems - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val thumbSizeRatio by remember {
        derivedStateOf {
            if (totalItems <= 0) 1f
            else if (totalItems <= visibleItemsCount) {
                val viewportHeight = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).toFloat()
                val totalHeight = visibleItemsInfo.sumOf { it.size }.toFloat()
                if (totalHeight <= 0f) 1f else (viewportHeight / totalHeight).coerceIn(0.1f, 1f)
            } else {
                (visibleItemsCount.toFloat() / totalItems.toFloat()).coerceIn(0.1f, 1f)
            }
        }
    }

    return ScrollbarStateAdapter(
        scrollRatio = scrollRatio,
        thumbSizeRatio = thumbSizeRatio,
        isScrollable = isScrollable,
        onDrag = { targetRatio ->
            coroutineScope.launch {
                if (totalItems <= 1) {
                    val firstItemSize = visibleItemsInfo.firstOrNull()?.size ?: 1
                    val maxScroll = (firstItemSize - (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)).coerceAtLeast(0)
                    val targetOffset = (targetRatio * maxScroll).toInt()
                    listState.scrollToItem(0, targetOffset)
                } else {
                    val targetProgress = targetRatio * (totalItems - 1)
                    val targetIndex = targetProgress.toInt().coerceIn(0, totalItems - 1)
                    val itemFraction = targetProgress - targetIndex
                    val itemSize = visibleItemsInfo.find { it.index == targetIndex }?.size ?: visibleItemsInfo.firstOrNull()?.size ?: 100
                    val targetOffset = (itemFraction * itemSize).toInt()
                    listState.scrollToItem(targetIndex, targetOffset)
                }
            }
        }
    )
}

@Composable
fun rememberScrollStateAdapter(
    scrollState: ScrollState,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): ScrollbarStateAdapter {
    val maxScroll by remember { derivedStateOf { scrollState.maxValue } }
    val isScrollable by remember { derivedStateOf { maxScroll > 0 } }
    val scrollRatio by remember {
        derivedStateOf {
            if (maxScroll > 0) (scrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f) else 0f
        }
    }
    val thumbSizeRatio by remember {
        derivedStateOf {
            if (maxScroll > 0) {
                val viewportHeight = scrollState.viewportSize.toFloat()
                if (viewportHeight > 0) (viewportHeight / (viewportHeight + maxScroll)).coerceIn(0.1f, 1f) else 0.2f
            } else 1f
        }
    }

    return ScrollbarStateAdapter(
        scrollRatio = scrollRatio,
        thumbSizeRatio = thumbSizeRatio,
        isScrollable = isScrollable,
        onDrag = { targetRatio ->
            coroutineScope.launch {
                val targetValue = (targetRatio * maxScroll).toInt()
                scrollState.scrollTo(targetValue)
            }
        }
    )
}

@Composable
fun BoxScope.DraggableScrollbar(
    adapter: ScrollbarStateAdapter,
    modifier: Modifier = Modifier,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 8.dp,
    rightPadding: Dp = 2.dp,
    touchTargetWidth: Dp = 40.dp,
    unpressedWidth: Dp = 6.dp,
    pressedWidth: Dp = 10.dp,
    minThumbHeight: Dp = 48.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
) {
    if (!adapter.isScrollable) return

    var isDragging by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }
    val currentAdapter by rememberUpdatedState(adapter)

    LaunchedEffect(adapter.scrollRatio, isDragging) {
        isInteracting = true
        if (!isDragging) {
            delay(1500)
            isInteracting = false
        }
    }

    // Alpha for dimming: dimmed (0.4f) when idle/waiting, full (1.0f) when pressed/dragged/scrolling
    val alpha by animateFloatAsState(
        targetValue = if (isDragging || isInteracting) 1f else 0.4f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollbarAlpha"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) pressedWidth else unpressedWidth,
        animationSpec = tween(durationMillis = 150),
        label = "thumbWidth"
    )

    val strokeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }

    Box(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(top = topPadding, bottom = bottomPadding, end = rightPadding)
            .width(touchTargetWidth)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        isInteracting = true
                        val height = size.height.toFloat()
                        val thumbHeight = (height * currentAdapter.thumbSizeRatio).coerceIn(minThumbHeightPx, height)
                        val trackLength = height - thumbHeight
                        if (trackLength > 0f) {
                            val targetRatio = ((offset.y - thumbHeight / 2f) / trackLength).coerceIn(0f, 1f)
                            currentAdapter.onDrag(targetRatio)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        isDragging = true
                        isInteracting = true
                        val height = size.height.toFloat()
                        val thumbHeight = (height * currentAdapter.thumbSizeRatio).coerceIn(minThumbHeightPx, height)
                        val trackLength = height - thumbHeight
                        if (trackLength > 0f) {
                            val targetRatio = ((change.position.y - thumbHeight / 2f) / trackLength).coerceIn(0f, 1f)
                            currentAdapter.onDrag(targetRatio)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalHeight = size.height
            val currentThumbWidth = thumbWidth.toPx()
            val xOffset = size.width - currentThumbWidth

            // Track channel
            drawRoundRect(
                color = trackColor.copy(alpha = if (isDragging) 0.4f else 0.15f),
                topLeft = Offset(x = xOffset, y = 0f),
                size = Size(width = currentThumbWidth, height = totalHeight),
                cornerRadius = CornerRadius(x = currentThumbWidth / 2f, y = currentThumbWidth / 2f)
            )

            // Thumb calculation
            val thumbHeight = (totalHeight * currentAdapter.thumbSizeRatio).coerceIn(minThumbHeightPx, totalHeight)
            val trackLength = totalHeight - thumbHeight
            val thumbTop = trackLength * currentAdapter.scrollRatio

            // Outline for contrast
            drawRoundRect(
                color = strokeColor.copy(alpha = alpha),
                topLeft = Offset(x = xOffset - 1f, y = thumbTop - 1f),
                size = Size(width = currentThumbWidth + 2f, height = thumbHeight + 2f),
                cornerRadius = CornerRadius(x = (currentThumbWidth + 2f) / 2f, y = (currentThumbWidth + 2f) / 2f)
            )

            // Primary App Logo Blue Thumb Fill (dimmed when idle, full when pressed/dragging)
            drawRoundRect(
                color = thumbColor.copy(alpha = alpha),
                topLeft = Offset(x = xOffset, y = thumbTop),
                size = Size(width = currentThumbWidth, height = thumbHeight),
                cornerRadius = CornerRadius(x = currentThumbWidth / 2f, y = currentThumbWidth / 2f)
            )
        }
    }
}

@Composable
fun BoxScope.PageNumberIndicator(
    currentPage: Int,
    totalPages: Int
) {
    if (totalPages <= 1 || currentPage <= 0) return

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        isVisible = true
        delay(2000)
        isVisible = false
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "pageIndicatorAlpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .graphicsLayer(alpha = alpha)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$currentPage / $totalPages",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
