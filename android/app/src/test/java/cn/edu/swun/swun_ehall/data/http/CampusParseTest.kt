package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.SignActivity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusParseTest {
    @Test
    fun parseApkAssetNameReadsVersionCode() {
        assertEquals(
            cn.edu.swun.swun_ehall.data.update.ApkAssetMeta("1.0.5", 0),
            cn.edu.swun.swun_ehall.data.update.parseApkAssetName("swun_ehall-1.0.5-arm64-release.apk"),
        )
        assertEquals(
            cn.edu.swun.swun_ehall.data.update.ApkAssetMeta("1.0.6", 7),
            cn.edu.swun.swun_ehall.data.update.parseApkAssetName("swun_ehall-1.0.6-7-arm64-release.apk"),
        )
        assertEquals(
            cn.edu.swun.swun_ehall.data.update.ApkAssetMeta("1.0.5", 11872),
            cn.edu.swun.swun_ehall.data.update.parseApkAssetName("KernelSU_v1.0.5_11872-release.apk"),
        )
    }

    @Test
    fun parseGithubLatestReleasePrefersReleaseApk() {
        val raw = JSONObject(
            """
            {"tag_name":"v1.0.6","html_url":"https://github.com/BritneyOvO/swun_ehall/releases/tag/v1.0.6","prerelease":false,"draft":false,"body":"fix",
             "assets":[
               {"name":"swun_ehall-1.0.6-7-arm64-debug.apk","browser_download_url":"https://example/debug.apk"},
               {"name":"swun_ehall-1.0.6-7-arm64-release.apk","browser_download_url":"https://example/release.apk"}
             ]}
            """.trimIndent(),
        )
        val rel = cn.edu.swun.swun_ehall.data.update.UpdateClient.parse(raw)!!
        assertEquals("1.0.6", rel.version)
        assertEquals(7, rel.versionCode)
        assertEquals("https://example/release.apk", rel.apkUrl)
        assertTrue(cn.edu.swun.swun_ehall.data.update.Versions.isNewer(rel))
    }

    @Test
    fun ehallAvatarUsesHttpsGateway() {
        assertEquals(
            "https://gateway.swun.edu.cn/photo/1.jpg",
            normalizeEhallAvatarUrl("http://gateway.swun.edu.cn/photo/1.jpg"),
        )
        val p = cn.edu.swun.swun_ehall.data.model.Profile(name = "同学").merge(
            cn.edu.swun.swun_ehall.data.model.Profile(name = "李阳", avatar = "https://ehall.example/a.png"),
        )
        assertEquals("李阳", p.name)
        assertEquals("https://ehall.example/a.png", p.avatar)
    }

    @Test
    fun fenceRandomPointStaysInside() {
        val fence = cn.edu.swun.swun_ehall.data.fences.CampusFences.bs
        val random = kotlin.random.Random(7)
        repeat(20) {
            val (lat, lng) = fence.randomInside(random)
            assertTrue(fence.contains(lat, lng))
        }
        assertTrue(fence.contains(fence.centerLat, fence.centerLng))
        assertFalse(fence.contains(fence.northLat, fence.eastLng))
    }

    @Test
    fun finishedPeriodDropsFromHome() {
        val lesson = cn.edu.swun.swun_ehall.data.model.Lesson("网络攻防", "BS-223", "陈浩", 3, 3, 2)
        assertEquals(4, lesson.end)
        assertFalse(lesson.ended(11 * 60 + 14))
        assertTrue(lesson.ended(12 * 60))
        assertTrue(lesson.ended(12 * 60 + 1))
    }

    @Test
    fun casEncryptProducesBase64() {
        val out = CasCrypto.encryptPassword("secret", "1234567890123456")
        assertTrue(out.isNotBlank())
        java.util.Base64.getDecoder().decode(out)
    }

    @Test
    fun lantuSignIsStable() {
        val a = JSONObject().apply {
            put("appKey", "GiITvn")
            put("param", "{\"x\":1}")
            put("secure", 0)
            put("time", 1)
        }
        val b = JSONObject().apply {
            put("time", 1)
            put("secure", 0)
            put("param", "{\"x\":1}")
            put("appKey", "GiITvn")
        }
        assertEquals(lantuSign(a), lantuSign(b))
        assertEquals(32, lantuSign(a).length)
    }

    @Test
    fun gyAesRoundtripNotEmpty() {
        val c = gyAesEcb("""{"lat":30.1,"lng":103.9}""")
        assertTrue(c.isNotBlank())
        java.util.Base64.getDecoder().decode(c)
    }

    @Test
    fun ktkqXnxqdmNowMatchesJinZhi() {
        fun cal(y: Int, m: Int, d: Int) = java.util.Calendar.getInstance().apply {
            set(y, m - 1, d)
        }
        assertEquals("2026-2027-1", ktkqXnxqdmNow(cal(2026, 9, 7)))
        assertEquals("2025-2026-1", ktkqXnxqdmNow(cal(2026, 1, 10)))
        assertEquals("2025-2026-2", ktkqXnxqdmNow(cal(2026, 3, 1)))
    }

    @Test
    fun pickKtkqXnxqdmPrefersTermCodeNotXnxqmc() {
        val school = JSONObject().put("xnxqmc", "2026-2027学年 秋季学期").put("termCode", "2026-2027-1")
        assertEquals("2026-2027-1", pickKtkqXnxqdm(school, emptyList()))
        assertEquals(
            "2026-2027-1",
            pickKtkqXnxqdm(JSONObject(), listOf(JSONObject().put("currentFlag", "1").put("termCode", "2026-2027-1"))),
        )
        assertEquals(
            "2026-2027-1",
            pickKtkqXnxqdm(JSONObject(), listOf(JSONObject().put("sfdq", true).put("xnxqdm", "2026-2027-1"))),
        )
        assertEquals(
            "2025-2026-2",
            pickKtkqXnxqdm(
                JSONObject(),
                listOf(
                    JSONObject().put("termCode", "2025-2026-2"),
                    JSONObject().put("termCode", "2026-2027-1"),
                ),
            ),
        )
        assertEquals("", pickKtkqXnxqdm(JSONObject().put("xnxqmc", "2026-2027学年 秋季学期"), emptyList()))
    }

    @Test
    fun normalizeKtkqWeekGroupsByJxbid() {
        val raw = JSONObject().put(
            "data",
            JSONObject().put(
                "theorySchedule",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("kcm", "网络攻防")
                        .put("kch", "1")
                        .put("jxbid", "jxb-1")
                        .put("jxblx", "THEORY")
                        .put("kbid", "kb-1")
                        .put("skxq", 3)
                        .put("ksjc", 3)
                        .put("jsjc", 4)
                        .put("jasmc", "BS-223"),
                ).put(
                    JSONObject()
                        .put("kcm", "网络攻防")
                        .put("kch", "1")
                        .put("jxbid", "jxb-1")
                        .put("jxblx", "THEORY")
                        .put("kbid", "kb-2")
                        .put("skxq", 5)
                        .put("ksjc", 9)
                        .put("jsjc", 10)
                        .put("jasmc", "BS-223"),
                ),
            ),
        )
        val week = ktkqWeekOf(normalizeKtkqWeek(raw), "2026-2027-1", 1, JSONObject().put("xnxqmc", "秋季"))
        assertEquals(1, week.courses.size)
        assertEquals("网络攻防", week.courses[0].name)
        assertEquals(2, week.courses[0].slots.size)
        assertEquals("", week.courses[0].slots[0].status)
        assertEquals("周三", week.courses[0].slots[0].startTime)
        assertTrue(week.courses[0].slots[0].timeText.contains("第 3-4 节"))
        assertEquals("jxb-1", week.courses[0].slots[0].teachClassId)
        assertEquals("kb-1", week.courses[0].slots[0].scheduleId)
    }

    @Test
    fun ktkqActivityStatusUsesSignStatusAndWindow() {
        val act = JSONObject().put("status", "1")
        assertEquals(
            "already_signed",
            ktkqActivityStatus(act, JSONObject().put("signStatus", "1"), JSONObject()),
        )
        assertEquals(
            "expired",
            ktkqActivityStatus(JSONObject().put("isEnd", true), JSONObject(), JSONObject()),
        )
        assertEquals(
            "expired",
            ktkqActivityStatus(act, JSONObject(), JSONObject().put("leftSeconds", 0)),
        )
        assertEquals("pending_signin", ktkqActivityStatus(act, JSONObject(), JSONObject()))
        assertEquals("inactive", ktkqActivityStatus(JSONObject().put("status", "0"), JSONObject(), JSONObject()))
    }

    @Test
    fun formatKtkqCstKeepsNaiveChinaTime() {
        assertEquals("2026-09-07 14:00", formatKtkqCst("2026-09-07 14:00"))
    }

    @Test
    fun ktkqSlotScorePrefersNameAndPeriod() {
        val lesson = cn.edu.swun.swun_ehall.data.model.Lesson("网络攻防", "BS-223", "陈浩", 3, 3, 2)
        val hit = SignActivity(
            activityId = "",
            title = "网络攻防",
            status = "",
            signType = "",
            startTime = "周三",
            endTime = "",
            classroom = "BS-223",
            course = "网络攻防",
            teachClassId = "jxb",
            teachClassType = "THEORY",
            scheduleId = "kb",
            week = 1,
            weekDay = 3,
            startNode = 3,
            endNode = 4,
        )
        val other = hit.copy(course = "高等数学", classroom = "BW-106", weekDay = 1)
        val matched = matchKtkqSlot(lesson, listOf(other, hit))
        assertEquals("jxb", matched?.teachClassId)
        assertNull(matchKtkqSlot(lesson, listOf(other)))
    }

    @Test
    fun ktkqTokenFromUrlAndBearer() {
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaaa.bbbbccccddddeeee"
        assertEquals(jwt, parseKtkqToken(urls = listOf("https://ktkq.swun.edu.cn/jwmobile/index#/x?token=$jwt")))
        assertEquals(jwt, cleanKtkqToken("Bearer $jwt"))
        assertNull(cleanKtkqToken("short"))
        assertNull(cleanKtkqToken("null"))
    }

    @Test
    fun teacherCacheFillsEmptyXm() {
        val cache = cn.edu.swun.swun_ehall.data.session.TeacherCache(java.io.File.createTempFile("kbt", ".json"))
        val kb = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("kcmc", "网络攻防")
                .put("xm", "陈浩")
                .put("xqj", "3")
                .put("skjc", "3"),
        )
        cache.fillFromJwxt(kb)
        val lessons = cache.apply(
            listOf(
                cn.edu.swun.swun_ehall.data.model.Lesson("网络攻防", "BS-223", "", 3, 3, 2),
            ),
        )
        assertEquals("陈浩", lessons[0].teacher)
    }

    @Test
    fun lantuCourseXmIsStudentNotTeacher() {
        val course = JSONObject().put(
            "courseList",
            org.json.JSONArray().put(
                JSONObject()
                    .put("kcmc", "网络攻防")
                    .put("jash", "BS-223")
                    .put("skxq", 3)
                    .put("skjc", 3)
                    .put("cxjc", 2)
                    .put("skzc", "1111111111111111")
                    .put("xm", "李阳")
                    .put("jsxx", "计算机科学与工程学院"),
            ).put(
                JSONObject()
                    .put("kcmc", "数字通信原理及协议")
                    .put("jash", "BW-106")
                    .put("skxq", 3)
                    .put("skjc", 5)
                    .put("cxjc", 2)
                    .put("skzc", "1111111111111111")
                    .put("xm", "李阳"),
            ),
        )
        val lessons = LantuClient(null).mapLessons(course)
        assertEquals(2, lessons.size)
        assertEquals("", lessons[0].teacher)
        assertEquals("", lessons[1].teacher)
        val cache = cn.edu.swun.swun_ehall.data.session.TeacherCache(java.io.File.createTempFile("kbt", ".json"))
        cache.fillFromJwxt(
            org.json.JSONArray()
                .put(JSONObject().put("kcmc", "网络攻防").put("xm", "陈浩").put("xqj", "3").put("skjc", "3"))
                .put(JSONObject().put("kcmc", "数字通信原理及协议").put("xm", "李成杰").put("xqj", "3").put("skjc", "5")),
        )
        val filled = cache.apply(lessons)
        assertEquals("陈浩", filled[0].teacher)
        assertEquals("李成杰", filled[1].teacher)
    }

    @Test
    fun teacherCacheMatchesWeekRanges() {
        val cache = cn.edu.swun.swun_ehall.data.session.TeacherCache(java.io.File.createTempFile("kbt", ".json"))
        val kb = org.json.JSONArray()
            .put(JSONObject().put("kcmc", "无线网络与移动计算").put("xm", "文瑞涵").put("xqj", "4").put("jcs", "9-10").put("zcd", "1-2周"))
            .put(JSONObject().put("kcmc", "无线网络与移动计算").put("xm", "陈曦").put("xqj", "4").put("jcs", "9-10").put("zcd", "1-16周"))
            .put(JSONObject().put("kcmc", "无线网络与移动计算").put("xm", "唐东明").put("xqj", "4").put("jcs", "9-10").put("zcd", "3-4周"))
            .put(JSONObject().put("kcmc", "无线网络与移动计算").put("xm", "陈建英").put("xqj", "4").put("jcs", "9-10").put("zcd", "5-6周"))
        cache.fillFromJwxt(kb)
        fun row(mask: String) = cn.edu.swun.swun_ehall.data.model.Lesson("无线网络与移动计算", "H-206", "", 4, 9, 2, mask)
        val filled = cache.apply(
            listOf(
                row("1100000000000000"),
                row("0011000000000000"),
                row("0000110000000000"),
                row("1111111111111111"),
            ),
        )
        assertEquals("文瑞涵", filled[0].teacher)
        assertEquals("唐东明", filled[1].teacher)
        assertEquals("陈建英", filled[2].teacher)
        assertEquals("陈曦", filled[3].teacher)
        assertEquals(setOf(15), cn.edu.swun.swun_ehall.data.session.TeacherCache.parseWeekSet(zcd = "第15周"))
        assertEquals((1..16).toSet(), cn.edu.swun.swun_ehall.data.session.TeacherCache.parseWeekSet(zcd = "1-16周"))
    }

    @Test
    fun matchChosenKeepsReselectOfSameCourse() {
        val a = cn.edu.swun.swun_ehall.data.model.Lesson("无线网络与移动计算", "H-206", "陈曦", 4, 9, 2, "1111111111111111")
        val b = cn.edu.swun.swun_ehall.data.model.Lesson("无线网络与移动计算", "H-206", "唐东明", 4, 9, 2, "0011000000000000")
        val group = listOf(a, b)
        assertEquals(
            b,
            cn.edu.swun.swun_ehall.data.session.SlotChoiceStore.matchChosen(
                group,
                cn.edu.swun.swun_ehall.data.session.SlotChoiceStore.lessonIdOf(b),
            ),
        )
        assertEquals(
            a,
            cn.edu.swun.swun_ehall.data.session.SlotChoiceStore.matchChosen(
                group,
                cn.edu.swun.swun_ehall.data.session.SlotChoiceStore.lessonIdOf(a),
            ),
        )
    }

    @Test
    fun teacherOfIgnoresCollegeAndClassroom() {
        val college = JSONObject().put("kcmc", "网络攻防").put("jsxx", "计算机科学与工程学院").put("jsmc", "BS-223")
        assertEquals("", cn.edu.swun.swun_ehall.data.session.TeacherCache.teacherOf(college))
        val jsxx = JSONObject().put("jsxx", "陈浩/2001001")
        assertEquals("陈浩", cn.edu.swun.swun_ehall.data.session.TeacherCache.teacherOf(jsxx))
    }

    @Test
    fun looksLikeRuishuNeedsTsAndWafMarker() {
        assertTrue(looksLikeRuishu(412, "x"))
        assertTrue(looksLikeRuishu(200, "var \$_ts = 1; FSSBBIl1=a"))
        assertFalse(looksLikeRuishu(200, "hello \$_ts only"))
        assertFalse(looksLikeRuishu(200, "FSSBBIl1 without ts"))
    }

    @Test
    fun gyTokenFromRedirect() {
        val t = "abcDEFGHijklmnop1234"
        assertEquals(t, gyTokenFrom("https://gyglxt.swun.edu.cn/casredirectsvc?token=$t"))
        assertEquals(t, extractUrlToken("https://gyglxt.swun.edu.cn/#/home?token=$t"))
        assertNull(gyTokenFrom("https://gyglxt.swun.edu.cn/appcas/ssoLogin.jsp"))
    }

    @Test
    fun xkRoundsFromHtml() {
        val html = """<a onclick="queryCourse(this,'01','id1','2024','1106','rsa')">主修课程</a>"""
        val rounds = parseXkRounds(html)
        assertEquals(1, rounds.size)
        assertEquals("01", rounds[0].kklxdm)
        assertEquals("主修课程", rounds[0].name)
    }

    @Test
    fun yktYuanAndBills() {
        assertEquals(3.45, parseYktYuan("账户余额：3.45元")!!, 0.001)
        val bills = parseYktBills("""{"bill":[{"area":"武侯校区","tradeBranchName":"学生食堂","consumeAmount":"8.00","consumeTime":"2026-09-07 12:31:00","generalOperateTypeName":"消费"}]}""")
        assertEquals(1, bills.size)
        assertEquals("武侯校区-学生食堂", bills[0].title)
        assertEquals(-8.0, bills[0].amountYuan, 0.001)
        assertEquals("abc", payloadOf("\"abc\""))
        assertEquals("xyz", payloadOf("""{"data":{"qrcode":"xyz"}}"""))
    }
}
