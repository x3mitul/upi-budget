package dev.mitul.upibudget.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

object Backup {
    val TABLES = listOf("category", "txn", "name_rule", "keyword_rule", "credit_rule", "recurring", "budget_month", "seen_email", "setting")

    suspend fun export(db: AppDatabase): String = db.withTransaction {
        val tables = JSONObject()
        val sql = db.openHelper.readableDatabase
        for (t in TABLES) {
            val rows = JSONArray()
            sql.query("SELECT * FROM `$t`").use { c ->
                while (c.moveToNext()) rows.put(row(c))
            }
            tables.put(t, rows)
        }
        JSONObject().put("app", "upibudget").put("version", 1).put("tables", tables).toString()
    }

    private fun row(c: Cursor): JSONObject {
        val o = JSONObject()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                else -> o.put(name, c.getString(i))
            }
        }
        return o
    }

    suspend fun import(db: AppDatabase, json: String) {
        val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException("not a backup file") }
        require(root.optString("app") == "upibudget" && root.has("tables")) { "not a UPI Budget backup" }
        val tables = root.getJSONObject("tables")
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            for (t in TABLES) sql.execSQL("DELETE FROM `$t`")
            for (t in TABLES) {
                val rows = tables.optJSONArray(t) ?: continue
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONObject(i)
                    if (t == "setting" && r.getString("key") == "seed_hash") continue
                    sql.insert(t, SQLiteDatabase.CONFLICT_REPLACE, values(r))
                }
            }
        }
        db.invalidationTracker.refreshVersionsAsync()
    }

    private fun values(r: JSONObject): ContentValues {
        val cv = ContentValues()
        for (k in r.keys()) {
            when (val v = r.get(k)) {
                JSONObject.NULL -> cv.putNull(k)
                is Int -> cv.put(k, v.toLong())
                is Long -> cv.put(k, v)
                is Double -> cv.put(k, v)
                is Boolean -> cv.put(k, if (v) 1 else 0)
                else -> cv.put(k, v.toString())
            }
        }
        return cv
    }
}
