package cn.edu.swun.swun_ehall.ui.leave

import org.json.JSONObject

internal data class ChinaAreaItem(
    val code: String,
    val name: String,
)

internal class ChinaAreaCatalog private constructor(
    val provinces: List<ChinaAreaItem>,
    private val cities: List<ChinaAreaItem>,
    private val counties: List<ChinaAreaItem>,
) {
    fun cities(provinceCode: String): List<ChinaAreaItem> {
        val prefix = provinceCode.take(2)
        return cities.filter { it.code.startsWith(prefix) }
    }

    fun counties(cityCode: String): List<ChinaAreaItem> {
        val prefix = cityCode.take(4)
        return counties.filter { it.code.startsWith(prefix) }
    }

    companion object {
        fun fromJson(raw: String): ChinaAreaCatalog {
            val root = JSONObject(raw)
            return ChinaAreaCatalog(
                provinces = root.getJSONObject("province_list").areaItems(),
                cities = root.getJSONObject("city_list").areaItems(),
                counties = root.getJSONObject("county_list").areaItems(),
            )
        }
    }
}

private fun JSONObject.areaItems(): List<ChinaAreaItem> = keys().asSequence()
    .mapNotNull { code -> optString(code).trim().takeIf { it.isNotEmpty() }?.let { ChinaAreaItem(code, it) } }
    .sortedBy { it.code }
    .toList()
