package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Question(
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String
)
