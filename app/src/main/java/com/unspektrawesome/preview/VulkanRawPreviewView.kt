package com.unspektrawesome.preview

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.lifecycle.LifecycleOwner

/** SurfaceView host suitable for Compose AndroidView and traditional view hierarchies. */
class VulkanRawPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private var controller: RawVulkanPreviewController? = null
    private var lifecycleOwner: LifecycleOwner? = null

    init {
        holder.addCallback(this)
    }

    fun bind(controller: RawVulkanPreviewController, lifecycleOwner: LifecycleOwner) {
        if (this.controller === controller && this.lifecycleOwner === lifecycleOwner) return
        unbind()
        this.controller = controller
        this.lifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(controller)
        if (holder.surface.isValid) {
            controller.updateViewport(width, height)
            controller.attachSurface(holder.surface)
        }
    }

    fun unbind() {
        controller?.detachSurface(holder.surface)
        lifecycleOwner?.lifecycle?.removeObserver(controller ?: return)
        controller = null
        lifecycleOwner = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        controller?.updateViewport(width, height)
        controller?.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            controller?.updateViewport(width, height)
            controller?.attachSurface(holder.surface)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        controller?.detachSurface(holder.surface)
    }

    override fun onDetachedFromWindow() {
        controller?.detachSurface(holder.surface)
        super.onDetachedFromWindow()
    }
}
