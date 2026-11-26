package cn.edu.swun.swun_ehall.ui.leave

import org.junit.Assert.assertEquals
import org.junit.Test

class ChinaAreaTest {
    @Test fun groupsCitiesAndCountiesByAdministrativeCode() {
        val catalog = ChinaAreaCatalog.fromJson(
            """{
              "province_list":{"110000":"北京市","510000":"四川省"},
              "city_list":{"110100":"北京市","510100":"成都市","510300":"自贡市"},
              "county_list":{"110101":"东城区","510116":"双流区","510302":"自流井区"}
            }""",
        )
        assertEquals(listOf("北京市", "四川省"), catalog.provinces.map { it.name })
        assertEquals(listOf("成都市", "自贡市"), catalog.cities("510000").map { it.name })
        assertEquals(listOf("双流区"), catalog.counties("510100").map { it.name })
        assertEquals(listOf("东城区"), catalog.counties("110100").map { it.name })
    }
}
