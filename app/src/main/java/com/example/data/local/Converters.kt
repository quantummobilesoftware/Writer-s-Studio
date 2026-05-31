package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.models.BlockStyle
import com.example.data.models.EditorBlock
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromBlocksList(blocks: List<EditorBlock>?): String {
        if (blocks == null) return "[]"
        val array = JSONArray()
        for (block in blocks) {
            val obj = JSONObject().apply {
                put("id", block.id)
                put("type", block.type)
                put("text", block.text)
                put("alignment", block.alignment)
                put("imageUrl", block.imageUrl)
                put("imageSize", block.imageSize.toDouble())
                put("imageCaption", block.imageCaption)
                
                // Style nested object
                val styleObj = JSONObject().apply {
                    put("isBold", block.style.isBold)
                    put("isItalic", block.style.isItalic)
                    put("isUnderline", block.style.isUnderline)
                    put("isStrikethrough", block.style.isStrikethrough)
                    put("textColorHex", block.style.textColorHex)
                    put("bgColorHex", block.style.bgColorHex)
                    put("lineSpacing", block.style.lineSpacing.toDouble())
                    put("textIndentDp", block.style.textIndentDp)
                }
                put("style", styleObj)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toBlocksList(value: String?): List<EditorBlock> {
        if (value.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<EditorBlock>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                val type = obj.optString("type", "paragraph")
                val text = obj.optString("text", "")
                val alignment = obj.optString("alignment", "LEFT")
                val imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl")
                val imageSize = obj.optDouble("imageSize", 0.8).toFloat()
                val imageCaption = if (obj.isNull("imageCaption")) null else obj.optString("imageCaption")
                
                var blockStyle = BlockStyle()
                if (obj.has("style")) {
                    val styleObj = obj.getJSONObject("style")
                    blockStyle = BlockStyle(
                        isBold = styleObj.optBoolean("isBold", false),
                        isItalic = styleObj.optBoolean("isItalic", false),
                        isUnderline = styleObj.optBoolean("isUnderline", false),
                        isStrikethrough = styleObj.optBoolean("isStrikethrough", false),
                        textColorHex = styleObj.optString("textColorHex", "#000000"),
                        bgColorHex = styleObj.optString("bgColorHex", "#00000000"),
                        lineSpacing = styleObj.optDouble("lineSpacing", 1.25).toFloat(),
                        textIndentDp = styleObj.optInt("textIndentDp", 0)
                    )
                }
                
                list.add(
                    EditorBlock(
                        id = id.ifEmpty { java.util.UUID.randomUUID().toString() },
                        type = type,
                        text = text,
                        alignment = alignment,
                        style = blockStyle,
                        imageUrl = imageUrl,
                        imageSize = imageSize,
                        imageCaption = imageCaption
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
