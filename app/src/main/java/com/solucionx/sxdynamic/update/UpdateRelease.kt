package com.solucionx.sxdynamic.update

data class UpdateRelease(
    val version: String,
    val tag: String,
    val name: String,
    val notes: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val publishedAt: String,
)
