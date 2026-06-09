package com.annodata.api.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "jsonb"

    override fun valueFromDB(value: Any): String {
        return when (value) {
            is PGobject -> value.value ?: "{}"
            else -> value.toString()
        }
    }

    override fun notNullValueToDB(value: String): Any {
        return PGobject().apply {
            type = "jsonb"
            this.value = value
        }
    }

    override fun nonNullValueToString(value: String): String {
        return "'${value.replace("'", "''")}'::jsonb"
    }

    override fun parameterMarker(value: String?): String = "?::jsonb"
}

fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonbColumnType())
