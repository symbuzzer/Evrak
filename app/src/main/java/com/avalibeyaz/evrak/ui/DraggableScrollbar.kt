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
import androidx.compose.ui.platform.LocalContext
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

/**
 * ObservableWebView is a WebView subclass that exposes vertical scroll offset, range, and extent.
 */
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

/**
 * State holder for WebView vertical scroll position and bounds.
 */
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

/**
 * Adapter encapsulating scroll state and drag callback for any scrollable container.
 */
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

fun isScrollbarEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("evrak_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("scrollbar_enabled", false)
}

/**
 * DraggableScrollbar UI Component.
 * Placed on the right edge of a container.
 */
@Composable
fun BoxScope.DraggableScrollbar(
    adapter: ScrollbarStateAdapter,
    modifier: Modifier = Modifier,
    enabled: Boolean = isScrollbarEnabled(LocalContext.current),
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 8.dp,
    rightPadding: Dp = 2.dp,
    touchTargetWidth: Dp = 40.dp,
    unpressedWidth: Dp = 6.dp,
    pressedWidth: Dp = 12.dp,
    minThumbHeight: Dp = 48.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
) {
    if (!enabled || !adapter.isScrollable) return

    var isDragging by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }
    val currentAdapter by rememberUpdatedState(adapter)

    // Reset interaction fade-out timer when scroll ratio changes or user drags
    LaunchedEffect(adapter.scrollRatio, isDragging) {
        isInteracting = true
        if (!isDragging) {
            delay(1500)
            isInteracting = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isInteracting || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrollbarAlpha"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) pressedWidth else unpressedWidth,
        animationSpec = tween(durationMillis = 150),
        label = "thumbWidth"
    )

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
        if (alpha > 0f || isDragging) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalHeight = size.height
                val currentThumbWidth = thumbWidth.toPx()
                val xOffset = size.width - currentThumbWidth

                // Draw track if dragging
                if (isDragging) {
                    drawRoundRect(
                        color = trackColor.copy(alpha = trackColor.alpha * alpha),
                        topLeft = Offset(x = xOffset, y = 0f),
                        size = Size(width = currentThumbWidth, height = totalHeight),
                        cornerRadius = CornerRadius(x = currentThumbWidth / 2f, y = currentThumbWidth / 2f)
                    )
                }

                // Draw thumb
                val thumbHeight = (totalHeight * currentAdapter.thumbSizeRatio).coerceIn(minThumbHeightPx, totalHeight)
                val trackLength = totalHeight - thumbHeight
                val thumbTop = trackLength * currentAdapter.scrollRatio

                drawRoundRect(
                    color = thumbColor.copy(alpha = thumbColor.alpha * alpha),
                    topLeft = Offset(x = xOffset, y = thumbTop),
                    size = Size(width = currentThumbWidth, height = thumbHeight),
                    cornerRadius = CornerRadius(x = currentThumbWidth / 2f, y = currentThumbWidth / 2f)
                )
            }
        }
    }
}

fun isPageNumberIndicatorEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("evrak_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("page_number_indicator_enabled", false)
}

@Composable
fun BoxScope.PageNumberIndicator(
    currentPage: Int,
    totalPages: Int,
    enabled: Boolean = isPageNumberIndicatorEnabled(LocalContext.current)
) {
    if (!enabled || totalPages <= 1 || currentPage <= 0) return

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
