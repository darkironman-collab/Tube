package com.extremetube.app.model

data class ResolvedVideo(
    val title: String,
    val durationSeconds: Long,
    val variants: List<StreamVariant>
)
