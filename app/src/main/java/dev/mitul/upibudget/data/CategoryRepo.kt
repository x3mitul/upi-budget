package dev.mitul.upibudget.data

class CategoryRepo(private val db: AppDatabase) {
    /** Moves payments and rules of [id] (and its subs, if it is a group) to Other › Uncategorized, then deletes it. */
    suspend fun deleteCategory(id: Long) {
        val unc = db.categories().subIdByName("Uncategorized") ?: return
        if (id == unc) return
        val all = db.categories().all()
        val target = all.firstOrNull { it.id == id } ?: return
        val uncGroup = all.first { it.id == unc }.parentId
        if (target.id == uncGroup) return
        val doomed = listOf(id) + all.filter { it.parentId == id }.map { it.id }
        db.rules().deleteBuiltinKeywordsOf(doomed)   // before repointing, so built-ins don't land on Uncategorized
        for (d in doomed) {
            db.txns().repoint(d, unc)
            db.rules().repointNameRules(d, unc)
            db.rules().repointKeywords(d, unc)
        }
        doomed.reversed().forEach { db.categories().delete(it) }
    }

    suspend fun addGroup(name: String, color: Int): Long {
        require(name.isNotBlank()) { "name required" }
        val order = (db.categories().all().maxOfOrNull { it.sortOrder } ?: 0) + 1
        return db.categories().insert(CategoryEntity(name = name.trim(), parentId = null, color = color, isSavings = false, builtin = false, sortOrder = order))
    }

    suspend fun addSub(name: String, parentId: Long): Long {
        require(name.isNotBlank()) { "name required" }
        val g = db.categories().all().first { it.id == parentId && it.parentId == null }
        val order = (db.categories().all().maxOfOrNull { it.sortOrder } ?: 0) + 1
        return db.categories().insert(CategoryEntity(name = name.trim(), parentId = g.id, color = g.color, isSavings = g.isSavings, builtin = false, sortOrder = order))
    }

    suspend fun rename(id: Long, name: String) {
        require(name.isNotBlank()) { "name required" }
        db.categories().all().firstOrNull { it.id == id }?.let { db.categories().update(it.copy(name = name.trim())) }
    }

    suspend fun recolor(id: Long, color: Int) {
        db.categories().all().filter { it.id == id || it.parentId == id }.forEach { db.categories().update(it.copy(color = color)) }
    }
}
