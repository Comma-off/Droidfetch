// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.data

import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES20

data class GpuInfo(
    /** GPU name with any translation layer peeled off ("PowerVR C-Series CXTP-48-1536 MC1"). */
    val renderer: String?,
    /** "ANGLE on Vulkan 1.4.317" when GLES is translated, else null. */
    val backend: String?,
    val vendor: String?,
    val glVersion: String?,
    val glslVersion: String?,
    val extensionCount: Int,
    val vulkanVersion: String?,
    val vulkanLevel: Int?,
)

object Gpu {
    /**
     * Renderer strings are only available from a live GL context, so this makes a throwaway
     * 1×1 pbuffer context. Must not run on the main thread's own GL state (it has none in Compose).
     */
    fun query(pm: PackageManager): GpuInfo {
        val gl = runCatching { queryGl() }.getOrNull()
        val features = pm.systemAvailableFeatures
        val vkVersion = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }?.version
        val vkLevel = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }?.version
        val (renderer, backend) = parseRenderer(gl?.get(0))
        return GpuInfo(
            renderer = renderer,
            backend = backend,
            vendor = gl?.get(1),
            glVersion = gl?.get(2),
            glslVersion = gl?.get(3),
            extensionCount = gl?.get(4)?.split(' ')?.count { it.isNotBlank() } ?: 0,
            vulkanVersion = vkVersion?.let { decodeVulkan(it) },
            vulkanLevel = vkLevel,
        )
    }

    /**
     * Devices that ship ANGLE report e.g. "ANGLE (Imagination Technologies, Vulkan 1.4.317
     * (PowerVR C-Series CXTP-48-1536 MC1 (0x70061042)), PowerVR ... Driver-1.662.3024)".
     */
    private fun parseRenderer(raw: String?): Pair<String?, String?> {
        if (raw == null || !raw.startsWith("ANGLE (")) return raw to null
        val m = Regex("""ANGLE \([^,]*, (Vulkan [\d.]+|OpenGL[^(]*) \((.+?)(?: \(0x[0-9a-fA-F]+\))?\)""").find(raw)
            ?: return raw to "ANGLE"
        return m.groupValues[2].trim() to "ANGLE on ${m.groupValues[1].trim()}"
    }

    private fun decodeVulkan(v: Int) = "${v ushr 22}.${(v ushr 12) and 0x3ff}.${v and 0xfff}"

    private fun queryGl(): List<String?> {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1))
        try {
            val (config, clientVersion) = chooseConfig(display, EGLExt.EGL_OPENGL_ES3_BIT_KHR)?.let { it to 3 }
                ?: chooseConfig(display, EGL14.EGL_OPENGL_ES2_BIT)?.let { it to 2 }
                ?: error("No EGL config")
            val context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE), 0,
            )
            val surface = EGL14.eglCreatePbufferSurface(
                display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            try {
                check(EGL14.eglMakeCurrent(display, surface, surface, context))
                return listOf(
                    GLES20.glGetString(GLES20.GL_RENDERER),
                    GLES20.glGetString(GLES20.GL_VENDOR),
                    GLES20.glGetString(GLES20.GL_VERSION),
                    GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION),
                    GLES20.glGetString(GLES20.GL_EXTENSIONS),
                )
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    private fun chooseConfig(display: android.opengl.EGLDisplay, renderableBit: Int): EGLConfig? {
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, renderableBit,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val ok = EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0)
        return if (ok && count[0] > 0) configs[0] else null
    }
}
