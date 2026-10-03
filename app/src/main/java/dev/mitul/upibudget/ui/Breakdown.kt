package dev.mitul.upibudget.ui

import dev.mitul.upibudget.core.Direction
import dev.mitul.upibudget.core.Paise
import dev.mitul.upibudget.data.CategoryEntity
import dev.mitul.upibudget.data.TxnEntity

data class SubTotal(val subId: Long, val name: String, val total: Paise)
data class GroupTotal(val groupId: Long, val name: String, val color: Int, val total: Paise, val subs: List<SubTotal>)

object Breakdown {
    fun byGroup(txns: List<TxnEntity>, cats: List<CategoryEntity>): List<GroupTotal> {
        val byId = cats.associateBy { it.id }
        val spending = txns.filter { it.direction == Direction.DEBIT && byId[it.subId]?.isSavings != true }
        return spending.groupBy { byId[it.subId]?.parentId ?: it.subId }.mapNotNull { (gid, list) ->
            val g = byId[gid] ?: return@mapNotNull null
            val subs = list.groupBy { it.subId }.map { (sid, l) -> SubTotal(sid, byId[sid]?.name ?: "?", l.sumOf { it.amount }) }
                .filter { it.total != 0L }.sortedByDescending { it.total }
            GroupTotal(g.id, g.name, g.color, subs.sumOf { it.total }, subs)
        }.filter { it.total != 0L }.sortedByDescending { it.total }
    }
}
