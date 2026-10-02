package com.solucionx.sxdynamic.core

import android.content.Context
import com.solucionx.sxdynamic.SxDynamicApp

val Context.appContainer: AppContainer
    get() = (applicationContext as SxDynamicApp).container
