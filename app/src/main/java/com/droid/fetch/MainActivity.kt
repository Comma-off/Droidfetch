// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.droid.fetch.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot(application as DroidfetchApp) }
    }
}
