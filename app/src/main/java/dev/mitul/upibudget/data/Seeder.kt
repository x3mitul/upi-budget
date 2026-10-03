package dev.mitul.upibudget.data

import org.json.JSONObject

class Seeder(private val db: AppDatabase) {
    private val palette = intArrayOf(0xFFE57373.toInt(), 0xFF81C784.toInt(), 0xFF64B5F6.toInt(), 0xFFFFB74D.toInt(),
        0xFFBA68C8.toInt(), 0xFF4DB6AC.toInt(), 0xFFA1887F.toInt(), 0xFF90A4AE.toInt(), 0xFFF06292.toInt(), 0xFFAED581.toInt())

    suspend fun seed(json: String) {
        val hash = json.hashCode().toString()
        if (db.misc().setting("seed_hash") == hash) return
        val root = JSONObject(json).getJSONArray("groups")
        if (db.categories().count() == 0) {
            var order = 0
            for (g in 0 until root.length()) {
                val group = root.getJSONObject(g)
                val color = palette[g % palette.size]
                val savings = group.getBoolean("savings")
                val gid = db.categories().insert(CategoryEntity(name = group.getString("name"), parentId = null, color = color,
                    isSavings = savings, builtin = true, sortOrder = order++))
                val subs = group.getJSONArray("subs")
                for (s in 0 until subs.length()) {
                    db.categories().insert(CategoryEntity(name = subs.getJSONObject(s).getString("name"), parentId = gid, color = color,
                        isSavings = savings, builtin = true, sortOrder = order++))
                }
            }
        }
        db.rules().deleteBuiltinKeywords()
        val rows = ArrayList<KeywordRuleEntity>()
        var ord = 0
        for (g in 0 until root.length()) {
            val subs = root.getJSONObject(g).getJSONArray("subs")
            for (s in 0 until subs.length()) {
                val sub = subs.getJSONObject(s)
                val subId = db.categories().subIdByName(sub.getString("name")) ?: continue
                val kws = sub.getJSONArray("keywords")
                for (k in 0 until kws.length()) {
                    val kw = kws.getJSONObject(k)
                    rows += KeywordRuleEntity(word = kw.getString("k"), subId = subId, weak = kw.getBoolean("weak"), builtin = true, ord = ord++)
                }
            }
        }
        db.rules().insertKeywords(rows)
        db.misc().putSetting(SettingEntity("seed_hash", hash))
    }

    suspend fun uncategorizedId(): Long = db.categories().subIdByName("Uncategorized")!!
}
