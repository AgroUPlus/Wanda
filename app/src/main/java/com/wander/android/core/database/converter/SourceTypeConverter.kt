package com.wander.android.core.database.converter

import androidx.room.TypeConverter
import com.wander.android.data.model.SourceType

class SourceTypeConverter {
    @TypeConverter
    fun toSourceType(value: String?): SourceType = when (value) {
        "NAVIDROME" -> SourceType.NAVIDROME
        "YTMUSIC" -> SourceType.YTMUSIC
        "LOCAL" -> SourceType.LOCAL
        else -> SourceType.LOCAL
    }

    @TypeConverter
    fun fromSourceType(source: SourceType): String = source.name
}
