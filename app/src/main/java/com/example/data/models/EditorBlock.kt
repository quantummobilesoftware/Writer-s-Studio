package com.example.data.models

import java.util.UUID

data class BlockStyle(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val textColorHex: String = "#000000",
    val bgColorHex: String = "#00000000",
    val lineSpacing: Float = 1.25f,
    val textIndentDp: Int = 0
)

data class EditorBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "paragraph", // paragraph, h1, h2, h3, quote, bullet_list, ordered_list, image, scene, action, character, parenthetical, dialogue, transition
    val text: String = "",
    val alignment: String = "LEFT", // LEFT, CENTER, RIGHT, JUSTIFY
    val style: BlockStyle = BlockStyle(),
    val imageUrl: String? = null,
    val imageSize: Float = 0.8f, // 0.2 to 1.0 (multiplier of screen width)
    val imageCaption: String? = null
)
