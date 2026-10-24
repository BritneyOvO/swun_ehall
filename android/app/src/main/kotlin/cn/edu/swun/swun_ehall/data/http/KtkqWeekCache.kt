package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.KtkqWeek

internal class KtkqWeekCache {
    private data class Entry(val account: String, val term: String, val week: KtkqWeek)
    private var entry: Entry? = null

    @Synchronized
    fun get(account: String, term: String, week: Int?): KtkqWeek? {
        val cached = entry ?: return null
        return cached.week.takeIf {
            cached.account == account && cached.term == term &&
                (week == null || week == it.week) && it.courses.isNotEmpty()
        }
    }

    @Synchronized
    fun put(account: String, term: String, week: KtkqWeek) {
        entry = Entry(account, term, week)
    }

    @Synchronized
    fun clear() { entry = null }
}
