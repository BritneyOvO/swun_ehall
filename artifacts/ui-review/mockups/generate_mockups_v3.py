from pathlib import Path
from PIL import Image, ImageDraw
import math
import shutil

import generate_mockups as b
import generate_mockups_v2 as v2


OUT = Path(__file__).resolve().parent
W, H = b.W, b.H
BG = "#F7F7F8"
SURFACE = "#FFFFFF"
TEXT = "#171719"
MUTED = "#77777F"
FAINT = "#A1A1A8"
LINE = "#E5E5E8"
RED = "#9B1B30"
RED_DARK = "#781326"
RED_LIGHT = "#F4E9EB"
GREEN = "#347A59"
GREEN_LIGHT = "#E8F1EC"
AMBER = "#8A5A00"
AMBER_LIGHT = "#FBF3DF"
BLUE = "#486A8E"
BLUE_LIGHT = "#E8EEF4"


def blank(color=BG):
    return Image.new("RGB", (W, H), color)


def rr(d, box, radius=20, fill=SURFACE, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def txt(d, xy, value, size, color=TEXT, bold=False, anchor="la"):
    b.txt(d, xy, value, size, color, bold, anchor)


def centered(d, box, value, size, color=TEXT, bold=False):
    b.centered(d, box, value, size, color, bold)


def wrap_lines(d, value, x, y, width, size=27, color=TEXT, bold=False, max_lines=2, gap=8):
    lines = b.wrap_cjk(value, size, width, max_lines, bold)
    for line in lines:
        txt(d, (x, y), line, size, color, bold, "la")
        y += size + gap
    return y


def header(d, title, back=True, refresh=False, action=None, dark=False):
    color = "#FFFFFF" if dark else TEXT
    if back:
        b.icon(d, "arrow_left", 72, 155, 52, color, stroke=5)
        txt(d, (130, 155), title, 44, color, True, "lm")
    else:
        txt(d, (48, 155), title, 46, color, True, "lm")
    right = 1016
    if refresh:
        b.icon(d, "refresh", right - 12, 155, 42, color, stroke=5)
        right -= 72
    if action:
        txt(d, (right, 155), action, 27, RED if not dark else "#FFFFFF", False, "rm")


def standard_screen(title, back=True, refresh=False, action=None, color=BG):
    im = blank(color)
    d = ImageDraw.Draw(im)
    b.status_bar(d, dark=color != "#000000")
    header(d, title, back=back, refresh=refresh, action=action, dark=color == "#000000")
    return im, d


def system_bottom(d, color=SURFACE):
    d.rectangle((0, 2328, W, H), fill=color)
    b.gesture_bar(d)


def finish(im, d, nav=None):
    if nav is None:
        system_bottom(d)
    else:
        v2.bottom_nav(d, nav)
    return im


def section(d, y, title, right=None):
    txt(d, (48, y), title, 34, TEXT, True, "lm")
    if right:
        txt(d, (1018, y), right, 24, RED, False, "rm")
    return y + 52


def badge(d, cx, cy, value, tone=RED, bg=RED_LIGHT, size=72):
    rr(d, (cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), size / 2, bg)
    txt(d, (cx, cy), value[:1], int(size * 0.38), tone, True, "mm")


def list_group(d, y, rows, row_h=120, x1=44, x2=1036, icon=True):
    total = row_h * len(rows)
    rr(d, (x1, y, x2, y + total), 20, SURFACE, outline=LINE, width=2)
    for i, row in enumerate(rows):
        top = y + i * row_h
        cy = top + row_h / 2
        tx = x1 + 38
        if icon:
            badge(d, x1 + 56, cy, row.get("badge", row["title"][:1]), row.get("tone", RED), row.get("badge_bg", RED_LIGHT), 56)
            tx = x1 + 104
        title_y = cy - (17 if row.get("sub") else 0)
        txt(d, (tx, title_y), row["title"], row.get("title_size", 28), row.get("title_color", TEXT), row.get("bold", True), "lm")
        if row.get("sub"):
            txt(d, (tx, cy + 27), row["sub"], row.get("sub_size", 22), MUTED, False, "lm")
        if row.get("right"):
            txt(d, (x2 - 50, cy), row["right"], row.get("right_size", 24), row.get("right_color", MUTED), row.get("right_bold", False), "rm")
        if row.get("chevron", True):
            b.icon(d, "chev_right", x2 - 24, cy, 22, FAINT, stroke=4)
        if i < len(rows) - 1:
            d.line((tx, top + row_h, x2 - 24, top + row_h), fill=LINE, width=2)
    return y + total


def plain_rows(d, y, rows, row_h=118, x1=44, x2=1036):
    return list_group(d, y, rows, row_h, x1, x2, icon=False)


def field(d, y, label, value="", right=None, height=108, enabled=True, multiline=False):
    fill = SURFACE if enabled else "#F1F1F3"
    rr(d, (44, y, 1036, y + height), 18, fill, outline=LINE, width=2)
    txt(d, (76, y + 32), label, 21, MUTED, False, "lm")
    if value:
        if multiline:
            wrap_lines(d, value, 76, y + 58, 880, 26, TEXT, False, 2)
        else:
            txt(d, (76, y + 73), value, 27, TEXT if enabled else FAINT, False, "lm")
    if right:
        txt(d, (992, y + height / 2), right, 24, RED if enabled else FAINT, False, "rm")
    return y + height


def primary_button(d, y, label, disabled=False, x1=44, x2=1036, height=110):
    color = "#C9C9CD" if disabled else RED
    rr(d, (x1, y, x2, y + height), 18, color)
    centered(d, (x1, y, x2, y + height), label, 30, "#FFFFFF", True)
    return y + height


def secondary_button(d, y, label, x1=44, x2=1036, height=100):
    rr(d, (x1, y, x2, y + height), 18, SURFACE, outline=LINE, width=2)
    centered(d, (x1, y, x2, y + height), label, 28, RED, True)
    return y + height


def chip(d, box, label, selected=False, danger=False):
    fill = RED if selected else SURFACE
    outline = RED if selected or danger else LINE
    text_color = "#FFFFFF" if selected else RED if danger else TEXT
    rr(d, box, 18, fill, outline=outline, width=2)
    centered(d, box, label, 24, text_color, selected)


def switch(d, cx, cy, on=True):
    fill = RED if on else "#D7D7DA"
    rr(d, (cx - 54, cy - 28, cx + 54, cy + 28), 28, fill)
    knob_x = cx + 27 if on else cx - 27
    d.ellipse((knob_x - 22, cy - 22, knob_x + 22, cy + 22), fill=SURFACE)


def notice(d, y, title, sub="", tone=AMBER, fill=AMBER_LIGHT, action=None):
    h = 108 if sub else 82
    d.rectangle((0, y, W, y + h), fill=fill)
    txt(d, (52, y + 33), title, 25, tone, True, "lm")
    if sub:
        txt(d, (52, y + 72), sub, 20, tone, False, "lm")
    if action:
        txt(d, (1018, y + h / 2), action, 24, tone, True, "rm")
    return y + h


def progress(d, x1, y, x2, value, color=RED):
    rr(d, (x1, y, x2, y + 12), 6, "#ECECEF")
    rr(d, (x1, y, x1 + (x2 - x1) * max(0, min(1, value)), y + 12), 6, color)


def draw_login():
    im = blank("#FAFAFB")
    d = ImageDraw.Draw(im)
    b.status_bar(d)
    rr(d, (444, 250, 636, 442), 44, RED)
    txt(d, (540, 346), "民", 82, "#FFFFFF", True, "mm")
    txt(d, (540, 520), "民大助手", 52, TEXT, True, "mm")
    txt(d, (540, 578), "西南民族大学", 26, MUTED, False, "mm")
    txt(d, (74, 730), "登录", 44, TEXT, True, "lm")
    txt(d, (74, 790), "使用统一身份认证账号", 26, MUTED, False, "lm")
    y = 860
    y = field(d, y, "学号", "202430809152") + 18
    y = field(d, y, "密码", "••••••••••", right="显示") + 30
    y = primary_button(d, y, "登录") + 24
    centered(d, (44, y, 1036, y + 78), "先看看界面", 27, RED, True)
    y += 142
    d.line((74, y, 1006, y), fill=LINE, width=2)
    txt(d, (74, y + 72), "账号密码仅用于登录学校统一身份认证", 23, MUTED, False, "lm")
    txt(d, (74, y + 116), "已保存账号可在登录页快速切换", 23, MUTED, False, "lm")
    system_bottom(d, "#FAFAFB")
    im.save(OUT / "mockup-v3-login.png")


def draw_main_copies():
    v2.draw_home()
    v2.draw_schedule()
    shutil.copyfile(OUT / "mockup-v2-home.png", OUT / "mockup-v3-home.png")
    shutil.copyfile(OUT / "mockup-v2-schedule.png", OUT / "mockup-v3-schedule-week.png")


def draw_schedule_list():
    im, d = standard_screen("课表", back=False, refresh=True, action="网格")
    b.icon(d, "arrow_left", 70, 270, 38, RED, stroke=5)
    txt(d, (126, 267), "第 13 周", 37, TEXT, True, "lm")
    b.icon(d, "chev_right", 340, 270, 38, RED, stroke=5)
    txt(d, (1015, 267), "本周", 25, RED, False, "rm")
    y = 340
    y = section(d, y, "周一 · 11月23日", "2 节")
    rows = [
        {"title": "网络工程学期实训", "sub": "14:00—15:35 · BS-222 · 徐小琼", "right": "5—6节", "right_color": MUTED, "chevron": False},
        {"title": "网络工程学期实训（网络开发）", "sub": "15:55—19:45 · BS-217 · 梅林", "right": "7—9节", "right_color": MUTED, "chevron": False},
    ]
    y = plain_rows(d, y, rows, 132) + 64
    y = section(d, y, "周三 · 11月25日", "2 节")
    rows = [
        {"title": "计算机组成原理实验", "sub": "10:25—12:00 · BS-241 · 姜玥", "right": "3—4节", "right_color": MUTED, "chevron": False},
        {"title": "数字通信原理及协议", "sub": "14:00—15:35 · BW-106 · 李成杰", "right": "5—6节", "right_color": MUTED, "chevron": False},
    ]
    y = plain_rows(d, y, rows, 132) + 64
    y = section(d, y, "今天 · 周四 · 11月26日", "2 节")
    rows = [
        {"title": "计算机组成原理", "sub": "08:30—10:05 · BW-107 · 姜玥", "right": "已结束", "right_color": FAINT, "chevron": False},
        {"title": "无线网络与移动计算", "sub": "19:00—20:35 · H-206 · 陈建英", "right": "下一节", "right_color": RED, "right_bold": True, "chevron": False},
    ]
    plain_rows(d, y, rows, 132)
    finish(im, d, 1).save(OUT / "mockup-v3-schedule-list.png")


def draw_grades():
    im, d = standard_screen("成绩", back=False, refresh=True)
    y = 235
    y = field(d, y, "学期", "2026—2027 学年第一学期", right="切换") + 22
    rr(d, (44, y, 1036, y + 210), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 50), "本学期", 23, MUTED, False, "lm")
    txt(d, (80, y + 112), "GPA  3.724", 48, TEXT, True, "lm")
    txt(d, (80, y + 166), "6 门 · 通过 6 门 · 18.0 学分", 25, MUTED, False, "lm")
    y += 268
    y = section(d, y, "课程成绩")
    rows = [
        {"title": "计算机组成原理", "sub": "专业必修课 · 3.5 学分 · 绩点 3.70", "right": "88.0", "right_color": GREEN, "right_size": 34, "right_bold": True, "chevron": False},
        {"title": "数字通信原理及协议", "sub": "专业必修课 · 3.0 学分 · 绩点 4.00", "right": "92.0", "right_color": GREEN, "right_size": 34, "right_bold": True, "chevron": False},
        {"title": "网络攻防", "sub": "专业选修课 · 2.0 学分 · 绩点 3.30", "right": "84.0", "right_color": GREEN, "right_size": 34, "right_bold": True, "chevron": False},
        {"title": "大学英语Ⅰ（一）", "sub": "公共基础课 · 2.0 学分 · 绩点 3.00", "right": "79.5", "right_color": GREEN, "right_size": 34, "right_bold": True, "chevron": False},
        {"title": "形势与政策（五）", "sub": "公共基础课 · 0.5 学分 · 绩点 2.70", "right": "76.0", "right_color": GREEN, "right_size": 34, "right_bold": True, "chevron": False},
    ]
    plain_rows(d, y, rows, 150)
    finish(im, d, 2).save(OUT / "mockup-v3-grades.png")


def draw_mine():
    im, d = standard_screen("我的", back=False)
    y = 235
    rr(d, (44, y, 1036, y + 180), 20, SURFACE, outline=LINE, width=2)
    badge(d, 128, y + 90, "李", RED, RED_LIGHT, 104)
    txt(d, (210, y + 62), "李阳", 38, TEXT, True, "lm")
    txt(d, (210, y + 112), "202430809152 · 网络工程", 24, MUTED, False, "lm")
    b.icon(d, "chev_right", 990, y + 90, 24, FAINT, stroke=4)
    y += 238
    y = section(d, y, "账号")
    y = list_group(d, y, [
        {"title": "个人信息", "sub": "姓名、学号、学院", "badge": "人"},
        {"title": "账号管理", "sub": "多账号切换、添加和删除", "badge": "账"},
    ], 126) + 58
    y = section(d, y, "设置")
    y = list_group(d, y, [
        {"title": "主题外观", "sub": "深浅色、主题色与界面缩放", "badge": "色"},
        {"title": "关于", "sub": "版本、更新与开源仓库", "badge": "关"},
    ], 126) + 34
    rr(d, (44, y, 1036, y + 104), 18, SURFACE, outline=LINE, width=2)
    centered(d, (44, y, 1036, y + 104), "退出登录", 28, RED, True)
    finish(im, d, 3).save(OUT / "mockup-v3-mine.png")


def draw_credits():
    im, d = standard_screen("共修学分", refresh=False)
    y = 235
    rr(d, (44, y, 1036, y + 250), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "已修学分", 23, MUTED, False, "lm")
    txt(d, (80, y + 112), "74.5 / 160.5", 46, TEXT, True, "lm")
    txt(d, (80, y + 166), "GPA 3.63 · 已修 32 门 · 未过 0 门", 24, MUTED, False, "lm")
    progress(d, 80, y + 205, 998, 74.5 / 160.5)
    y += 310
    y = section(d, y, "学分详情")
    rows = [
        {"title": "公共基础课", "sub": "已修 24.0 / 要求 48.0 · 12 门", "right": "50%", "right_color": TEXT, "chevron": True},
        {"title": "学科基础课", "sub": "已修 20.0 / 要求 36.0 · 8 门", "right": "56%", "right_color": TEXT, "chevron": True},
        {"title": "专业必修课", "sub": "已修 21.0 / 要求 52.5 · 7 门", "right": "40%", "right_color": TEXT, "chevron": True},
        {"title": "专业选修课", "sub": "已修 9.5 / 要求 24.0 · 5 门", "right": "40%", "right_color": TEXT, "chevron": True},
    ]
    y = plain_rows(d, y, rows, 144) + 52
    y = section(d, y, "已展开 · 专业必修课")
    plain_rows(d, y, [
        {"title": "计算机组成原理", "sub": "3.5 学分 · 已修", "right": "88.0", "right_color": GREEN, "chevron": False},
        {"title": "数据结构", "sub": "4.0 学分 · 已修", "right": "86.0", "right_color": GREEN, "chevron": False},
    ], 126)
    finish(im, d).save(OUT / "mockup-v3-credits.png")


def draw_exams():
    im, d = standard_screen("考试", refresh=True)
    y = 235
    notice(d, y, "最近考试 · 4 天后", "请提前 15 分钟到场，携带学生证", RED, RED_LIGHT)
    y += 144
    y = section(d, y, "本学期考试", "2 场")
    rr(d, (44, y, 1036, y + 540), 20, SURFACE, outline=LINE, width=2)
    d.rectangle((44, y, 52, y + 250), fill=RED)
    txt(d, (84, y + 48), "数字通信原理及协议", 33, TEXT, True, "lm")
    txt(d, (84, y + 106), "第16周 · 周三 14:00—16:00", 25, MUTED, False, "lm")
    txt(d, (84, y + 154), "BW-106", 26, TEXT, False, "lm")
    txt(d, (984, y + 154), "座位 12", 24, RED, True, "rm")
    d.line((84, y + 250, 1000, y + 250), fill=LINE, width=2)
    txt(d, (84, y + 310), "计算机组成原理", 33, TEXT, True, "lm")
    txt(d, (84, y + 368), "第17周 · 周一 09:00—11:00", 25, MUTED, False, "lm")
    txt(d, (84, y + 416), "BW-107", 26, TEXT, False, "lm")
    txt(d, (984, y + 416), "座位 08", 24, MUTED, True, "rm")
    y += 600
    txt(d, (48, y), "考试时间和地点以教务系统最新安排为准。", 23, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-exams.png")


def draw_selection():
    im, d = standard_screen("选课")
    y = 230
    rr(d, (44, y, 1036, y + 82), 18, "#ECECEF")
    rr(d, (48, y + 4, 540, y + 78), 16, SURFACE)
    centered(d, (48, y + 4, 540, y + 78), "主修课程", 26, RED, True)
    centered(d, (540, y + 4, 1032, y + 78), "通识选修课", 26, MUTED, False)
    y += 112
    rr(d, (44, y, 820, y + 104), 18, SURFACE, outline=LINE, width=2)
    txt(d, (76, y + 54), "搜索课程名", 25, MUTED, False, "lm")
    rr(d, (840, y, 1036, y + 104), 18, RED)
    centered(d, (840, y, 1036, y + 104), "搜索", 27, "#FFFFFF", True)
    y += 156
    y = section(d, y, "课程", "共 3 门")
    rows = [
        {"title": "网络舆情智能分析", "sub": "2.0 学分 · 王老师 · 周三 5—6 节", "right": "余 4", "right_color": GREEN, "right_bold": True},
        {"title": "网络工程学期实训（网络开发）", "sub": "2.0 学分 · 李老师 · 周四 3—4 节", "right": "已满", "right_color": RED, "right_bold": True},
        {"title": "专业英语（网络工程）", "sub": "2.0 学分 · 张老师 · 周五 3—4 节", "right": "余 10", "right_color": GREEN, "right_bold": True},
    ]
    y = plain_rows(d, y, rows, 144) + 56
    y = section(d, y, "教学班 · 网络舆情智能分析")
    rr(d, (44, y, 1036, y + 250), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "（2026—2027—1）56050556—01", 28, TEXT, True, "lm")
    txt(d, (80, y + 100), "王老师 · 周三第 5—6 节 · BS-223", 24, MUTED, False, "lm")
    txt(d, (80, y + 148), "容量 50 · 已选 46 · 余 4", 24, GREEN, True, "lm")
    primary_button(d, y + 176, "提交选课", x1=720, x2=998, height=58)
    finish(im, d).save(OUT / "mockup-v3-selection.png")


def draw_ktkq():
    im, d = standard_screen("课堂考勤", refresh=True)
    y = 235
    txt(d, (48, y + 18), "2026—2027 学年秋季学期 · 第 13 周", 28, TEXT, True, "lm")
    y += 72
    rows = [
        {"title": "网络工程学期实训（网络仿真）", "sub": "周一 14:00—15:35 · BS-222 · 5—6节", "right": "签到", "right_color": RED, "right_bold": True},
        {"title": "数字通信原理及协议", "sub": "周三 14:00—15:35 · BW-106 · 5—6节", "right": "已签到", "right_color": GREEN, "right_bold": True},
        {"title": "无线网络与移动计算", "sub": "周四 19:00—20:35 · H-206 · 9—10节", "right": "未开始", "right_color": MUTED},
        {"title": "计算机组成原理", "sub": "周四 08:30—10:05 · BW-107 · 1—2节", "right": "无活动", "right_color": MUTED},
    ]
    plain_rows(d, y, rows, 168)
    y += 4 * 168 + 46
    rr(d, (44, y, 1036, y + 140), 20, "#F1F1F3")
    txt(d, (80, y + 46), "说明", 24, TEXT, True, "lm")
    txt(d, (80, y + 92), "进入课程后查看签到方式、定位范围和历史记录。", 23, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-ktkq.png")


def draw_map(d, box, label="西南民族大学"):
    x1, y1, x2, y2 = box
    rr(d, box, 18, "#EEF0EC", outline=LINE, width=2)
    for off in [0.18, 0.48, 0.76]:
        y = y1 + (y2 - y1) * off
        d.line((x1 + 20, y, x2 - 20, y + 26), fill="#FFFFFF", width=18)
        d.line((x1 + 20, y, x2 - 20, y + 26), fill="#D8DDD5", width=2)
    for off in [0.24, 0.64]:
        x = x1 + (x2 - x1) * off
        d.line((x, y1 + 12, x + 30, y2 - 12), fill="#FFFFFF", width=16)
    pts = [(x1 + 270, y1 + 70), (x1 + 720, y1 + 52), (x1 + 790, y1 + 245), (x1 + 340, y1 + 280)]
    d.line(pts + [pts[0]], fill=RED, width=6, joint="curve")
    d.ellipse((x1 + 500, y1 + 142, x1 + 530, y1 + 172), fill=BLUE, outline="#FFFFFF", width=5)
    txt(d, ((x1 + x2) / 2, y2 - 36), label, 22, MUTED, True, "mm")


def draw_ktkq_sign():
    im, d = standard_screen("课堂签到", refresh=True)
    y = 230
    rr(d, (44, y, 1036, y + 190), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 46), "网络工程学期实训（网络仿真）", 31, TEXT, True, "lm")
    txt(d, (80, y + 100), "周一 14:00—15:35 · BS-222 · 张老师", 24, MUTED, False, "lm")
    txt(d, (80, y + 148), "第 13 周", 22, MUTED, False, "lm")
    txt(d, (980, y + 46), "待签到", 23, RED, True, "rm")
    y += 214
    rr(d, (44, y, 1036, y + 142), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 42), "当前位置", 27, TEXT, True, "lm")
    txt(d, (80, y + 88), "西南民族大学（航空港校区） · 精度 12 米", 23, MUTED, False, "lm")
    txt(d, (990, y + 42), "重新定位", 23, RED, False, "rm")
    y += 164
    draw_map(d, (44, y, 1036, y + 365), "BS 教学楼定位围栏")
    y += 414
    y = section(d, y, "签到活动")
    rr(d, (44, y, 1036, y + 390), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "第13周课堂签到 · 数字码", 28, TEXT, True, "lm")
    txt(d, (980, y + 48), "进行中", 22, RED, True, "rm")
    txt(d, (80, y + 92), "14:00—15:35", 22, MUTED, False, "lm")
    field(d, y + 122, "教师口令 / 数字码", "请输入 4—6 位数字", height=104)
    primary_button(d, y + 248, "立即签到", x1=80, x2=1000, height=96)
    y += 440
    y = section(d, y, "本课记录")
    plain_rows(d, y, [
        {"title": "11月19日 14:18", "sub": "网络工程学期实训（网络仿真）", "right": "已签到", "right_color": GREEN, "chevron": False},
        {"title": "11月12日 14:20", "sub": "网络工程学期实训（网络仿真）", "right": "正常", "right_color": GREEN, "chevron": False},
    ], 124)
    finish(im, d).save(OUT / "mockup-v3-ktkq-sign.png")


def draw_clock():
    im, d = standard_screen("公寓打卡", refresh=True)
    y = 235
    rr(d, (44, y, 1036, y + 270), 20, SURFACE, outline=LINE, width=2)
    badge(d, 128, y + 92, "今", RED, RED_LIGHT, 88)
    txt(d, (200, y + 72), "今日尚未打卡", 34, TEXT, True, "lm")
    txt(d, (200, y + 122), "允许时间 20:00—23:30", 23, MUTED, False, "lm")
    primary_button(d, y + 166, "立即打卡", x1=80, x2=1000, height=76)
    y += 330
    y = section(d, y, "打卡记录")
    rows = [
        {"title": "昨天 22:01", "sub": "武侯校区 · 位置校验通过", "right": "正常", "right_color": GREEN, "chevron": False},
        {"title": "11月24日 21:43", "sub": "武侯校区 · 位置校验通过", "right": "正常", "right_color": GREEN, "chevron": False},
        {"title": "11月23日 23:06", "sub": "武侯校区 · 超过建议时间", "right": "迟到", "right_color": AMBER, "chevron": False},
    ]
    y = plain_rows(d, y, rows, 140) + 58
    rr(d, (44, y, 1036, y + 150), 20, "#F1F1F3")
    txt(d, (80, y + 48), "定位说明", 25, TEXT, True, "lm")
    txt(d, (80, y + 94), "打卡前会请求当前位置，仅用于本次校内范围校验。", 23, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-clock.png")


def draw_bus():
    im, d = standard_screen("校车时刻")
    y = 230
    rr(d, (44, y, 1036, y + 88), 18, "#ECECEF")
    rr(d, (48, y + 4, 375, y + 84), 16, SURFACE)
    centered(d, (48, y + 4, 375, y + 84), "航空港 ↔ 武侯", 24, RED, True)
    centered(d, (375, y + 4, 702, y + 84), "航空港 ↔ 太平园", 24, MUTED, False)
    centered(d, (702, y + 4, 1032, y + 84), "校内摆渡", 24, MUTED, False)
    y += 122
    chip(d, (44, y, 526, y + 82), "航空港 → 武侯", True)
    chip(d, (554, y, 1036, y + 82), "武侯 → 航空港", False)
    y += 112
    chip(d, (44, y, 350, y + 76), "工作日", True)
    chip(d, (387, y, 693, y + 76), "周末", False)
    chip(d, (730, y, 1036, y + 76), "节假日", False)
    y += 122
    rr(d, (44, y, 1036, y + 220), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "下一班", 23, MUTED, False, "lm")
    txt(d, (80, y + 112), "12:30", 52, TEXT, True, "lm")
    txt(d, (80, y + 166), "航空港校区 → 武侯校区", 25, MUTED, False, "lm")
    txt(d, (980, y + 112), "约 18 分钟", 25, RED, True, "rm")
    y += 278
    y = section(d, y, "发车时刻")
    times = ["07:10", "08:20", "09:40", "11:00", "12:30", "14:00", "15:30", "17:10", "18:40", "20:10", "21:30", "22:30"]
    for i, value in enumerate(times):
        col = i % 4
        row = i // 4
        x1 = 44 + col * 254
        yy = y + row * 94
        chip(d, (x1, yy, x1 + 224, yy + 70), value, value == "12:30")
    y += 322
    y = section(d, y, "乘车提示")
    rr(d, (44, y, 1036, y + 170), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "请提前 10 分钟候车，刷校园卡乘车。", 25, TEXT, False, "lm")
    txt(d, (80, y + 98), "临时调整以学校后勤处通知为准。", 23, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-bus.png")


def draw_calendar():
    im, d = standard_screen("校历", refresh=True)
    y = 235
    rr(d, (44, y, 1036, y + 155), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 44), "2026—2027 学年第一学期", 30, TEXT, True, "lm")
    txt(d, (80, y + 94), "8月31日—1月17日", 23, MUTED, False, "lm")
    txt(d, (984, y + 94), "今天第 13 教学周", 23, RED, True, "rm")
    y += 206
    y = section(d, y, "2026年11月")
    rr(d, (44, y, 1036, y + 780), 20, SURFACE, outline=LINE, width=2)
    days = ["一", "二", "三", "四", "五", "六", "日"]
    colw = 944 / 7
    for i, day in enumerate(days):
        txt(d, (80 + colw * (i + .5), y + 55), day, 22, MUTED, True, "mm")
    start = 6
    for n in range(1, 31):
        idx = start + n - 1
        row = idx // 7
        col = idx % 7
        cx = 80 + colw * (col + .5)
        cy = y + 125 + row * 112
        if n == 26:
            d.ellipse((cx - 36, cy - 36, cx + 36, cy + 36), fill=RED)
            txt(d, (cx, cy), str(n), 25, "#FFFFFF", True, "mm")
        else:
            txt(d, (cx, cy), str(n), 25, TEXT if col < 5 else MUTED, False, "mm")
        if n in [1, 2, 7, 8, 14, 15, 21, 22, 28, 29]:
            txt(d, (cx, cy + 34), "休", 17, MUTED, False, "mm")
    y += 832
    y = section(d, y, "校历事件")
    plain_rows(d, y, [
        {"title": "期末考试周", "sub": "2027年1月11日—1月17日", "right": "第19—20周", "right_color": MUTED, "chevron": False},
        {"title": "寒假开始", "sub": "2027年1月18日", "right": "", "chevron": False},
    ], 124)
    finish(im, d).save(OUT / "mockup-v3-calendar.png")


def draw_locate_test():
    im, d = standard_screen("测试定位")
    y = 225
    y = notice(d, y, "开发者工具", "仅验证定位和教学楼围栏，不会提交签到。", BLUE, BLUE_LIGHT)
    y += 40
    y = section(d, y, "选择测试地点")
    rows = [
        {"title": "BS 教学楼", "sub": "BS 教学楼中心 · 多边形围栏", "right": "测试", "right_color": RED},
        {"title": "BW 教学楼", "sub": "BW 教学楼中心 · 多边形围栏", "right": "测试", "right_color": RED},
        {"title": "H 教学楼", "sub": "H 教学楼中心 · 半径围栏", "right": "测试", "right_color": RED},
    ]
    y = plain_rows(d, y, rows, 140) + 54
    y = section(d, y, "最近结果")
    rr(d, (44, y, 1036, y + 230), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "BS 教学楼", 29, TEXT, True, "lm")
    txt(d, (980, y + 48), "范围内", 23, GREEN, True, "rm")
    txt(d, (80, y + 100), "定位精度 12 米 · 距围栏中心 48 米", 23, MUTED, False, "lm")
    txt(d, (80, y + 148), "经纬度 30.58120, 103.97048", 22, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-locate-test.png")


def draw_qr(d, x, y, size=430):
    d.rectangle((x, y, x + size, y + size), fill="#FFFFFF")
    n = 29
    cell = size / n
    for r in range(n):
        for c in range(n):
            on = ((r * 17 + c * 11 + r * c) % 7 in [0, 1]) or ((r + c * 3) % 11 == 0)
            if on:
                d.rectangle((x + c * cell, y + r * cell, x + (c + 1) * cell + 1, y + (r + 1) * cell + 1), fill="#171719")
    for fx, fy in [(1, 1), (21, 1), (1, 21)]:
        d.rectangle((x + fx * cell, y + fy * cell, x + (fx + 7) * cell, y + (fy + 7) * cell), fill="#FFFFFF")
        d.rectangle((x + (fx + .5) * cell, y + (fy + .5) * cell, x + (fx + 6.5) * cell, y + (fy + 6.5) * cell), outline="#171719", width=12)
        d.rectangle((x + (fx + 2) * cell, y + (fy + 2) * cell, x + (fx + 5) * cell, y + (fy + 5) * cell), fill="#171719")


def draw_ykt():
    im, d = standard_screen("一卡通", refresh=True)
    y = 230
    txt(d, (48, y + 34), "余额", 23, MUTED, False, "lm")
    txt(d, (1018, y + 34), "¥ 18.70", 44, TEXT, True, "rm")
    y += 88
    y = list_group(d, y, [
        {"title": "一卡通充值", "sub": "创建学校官方充值订单", "badge": "充"},
        {"title": "宿舍电费充值", "sub": "按校区、楼栋和房间充值", "badge": "电"},
        {"title": "热水充值", "sub": "从一卡通余额直接扣款", "badge": "水"},
    ], 112) + 24
    rr(d, (44, y, 1036, y + 690), 20, SURFACE, outline=LINE, width=2)
    txt(d, (540, y + 46), "付款码", 28, TEXT, True, "mm")
    txt(d, (540, y + 88), "自动刷新 · 42 秒后更新", 20, MUTED, False, "mm")
    draw_qr(d, 325, y + 120, 430)
    txt(d, (540, y + 590), "请向收款设备出示", 22, MUTED, False, "mm")
    txt(d, (540, y + 632), "202430809152", 20, FAINT, False, "mm")
    y += 740
    y = section(d, y, "今日明细", "近 7 天")
    rows = [
        {"title": "学生食堂", "sub": "今天 12:31 · 余额 ¥ 10.70", "right": "−¥ 8.00", "right_color": RED, "right_bold": True, "chevron": False},
        {"title": "校园超市", "sub": "今天 18:04 · 余额 ¥ 18.70", "right": "−¥ 2.50", "right_color": RED, "right_bold": True, "chevron": False},
        {"title": "圈存转入", "sub": "昨天 09:12 · 余额 ¥ 21.20", "right": "+¥ 50.00", "right_color": GREEN, "right_bold": True, "chevron": False},
    ]
    plain_rows(d, y, rows, 122)
    finish(im, d).save(OUT / "mockup-v3-ykt.png")


def quick_amounts(d, y, values, selected=None):
    count = len(values)
    gap = 16
    width = (992 - gap * (count - 1)) / count
    for i, value in enumerate(values):
        x1 = 44 + i * (width + gap)
        chip(d, (x1, y, x1 + width, y + 74), f"¥ {value}", str(value) == str(selected))
    return y + 74


def payment_method(d, y, selected="建行"):
    y = section(d, y, "支付方式")
    chip(d, (44, y, 526, y + 84), "建行收银台", selected == "建行")
    chip(d, (554, y, 1036, y + 84), "微信支付", selected == "微信")
    return y + 84


def draw_recharge():
    im, d = standard_screen("一卡通充值")
    y = 235
    rr(d, (44, y, 1036, y + 200), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "一卡通账户", 25, TEXT, True, "lm")
    txt(d, (80, y + 96), "李阳 · 202430809152", 23, MUTED, False, "lm")
    txt(d, (80, y + 154), "余额 ¥ 18.70", 34, TEXT, True, "lm")
    y += 258
    y = section(d, y, "充值金额")
    y = quick_amounts(d, y, [5, 10, 20, 50, 100, 200], 50) + 20
    y = field(d, y, "自定义金额（最高 200 元）", "50.00") + 52
    y = payment_method(d, y, "建行") + 54
    y = primary_button(d, y, "创建官方充值订单") + 30
    rr(d, (44, y, 1036, y + 170), 20, "#F1F1F3")
    txt(d, (80, y + 46), "充值说明", 25, TEXT, True, "lm")
    txt(d, (80, y + 92), "每天 00:30—23:50 可充值。", 23, MUTED, False, "lm")
    txt(d, (80, y + 132), "付款结果以一卡通余额和账单为准。", 23, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-ykt-recharge.png")


def draw_electric():
    im, d = standard_screen("宿舍电费充值")
    y = 220
    y = notice(d, y, "请确认宿舍信息", "选择结果将写入学校电费充值会话。", BLUE, BLUE_LIGHT)
    y += 30
    labels = [
        ("校区 / 电表类型", "航空港校区 7—30 栋照明"),
        ("楼宇", "10号楼1单元"),
        ("楼层", "1层"),
        ("房间", "101"),
    ]
    for label, value in labels:
        y = field(d, y, label, value, right="选择", height=98) + 12
    y += 22
    y = section(d, y, "充值金额")
    y = quick_amounts(d, y, [10, 20, 50, 100, 200], 50) + 18
    y = field(d, y, "自定义金额（最高 200 元）", "50.00") + 38
    y = payment_method(d, y, "建行") + 40
    y = primary_button(d, y, "创建官方电费订单") + 24
    txt(d, (48, y + 20), "学校每天 23:50—00:30 对账，期间不能充值。", 22, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-electric.png")


def draw_water():
    im, d = standard_screen("热水充值")
    y = 235
    rr(d, (44, y, 1036, y + 220), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "热水账户", 25, TEXT, True, "lm")
    txt(d, (80, y + 96), "李阳 · 202430809152", 23, MUTED, False, "lm")
    txt(d, (80, y + 156), "一卡通余额 ¥ 18.70", 34, TEXT, True, "lm")
    y += 280
    y = section(d, y, "充值金额")
    y = quick_amounts(d, y, [5, 10, 20], 10) + 20
    y = field(d, y, "自定义金额（最高 20 元）", "10.00") + 42
    rr(d, (44, y, 1036, y + 150), 20, "#F1F1F3")
    txt(d, (80, y + 48), "扣款方式", 24, TEXT, True, "lm")
    txt(d, (80, y + 96), "从一卡通余额立即扣除", 23, MUTED, False, "lm")
    y += 198
    y = primary_button(d, y, "确认充值") + 34
    txt(d, (48, y), "确认后无法在本应用撤销，请核对充值金额。", 22, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-water.png")


def draw_leave():
    im, d = standard_screen("请假与销假", refresh=True)
    y = 235
    rr(d, (44, y, 1036, y + 190), 20, SURFACE, outline=LINE, width=2)
    for i, (value, label) in enumerate([("2", "请假次数"), ("5 天", "累计天数"), ("1", "待销假")]):
        cx = 208 + i * 332
        txt(d, (cx, y + 70), value, 40, TEXT, True, "mm")
        txt(d, (cx, y + 132), label, 23, MUTED, False, "mm")
        if i < 2:
            d.line((374 + i * 332, y + 34, 374 + i * 332, y + 156), fill=LINE, width=2)
    y += 225
    rr(d, (44, y, 1036, y + 150), 20, SURFACE, outline=LINE, width=2)
    badge(d, 120, y + 75, "李", RED, RED_LIGHT, 72)
    txt(d, (180, y + 54), "李阳", 30, TEXT, True, "lm")
    txt(d, (180, y + 100), "202430809152 · 网络工程2024—1班", 22, MUTED, False, "lm")
    b.icon(d, "chev_right", 988, y + 75, 22, FAINT, stroke=4)
    y += 184
    y = primary_button(d, y, "申请请假") + 54
    y = section(d, y, "申请记录")
    rr(d, (44, y, 1036, y + 550), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 50), "事假 · 请假离蓉回家", 30, TEXT, True, "lm")
    txt(d, (980, y + 50), "待销假", 23, RED, True, "rm")
    txt(d, (80, y + 104), "12月5日 08:00 至 12月7日 20:00", 23, MUTED, False, "lm")
    txt(d, (80, y + 154), "3 天 · 参加家庭活动", 24, TEXT, False, "lm")
    txt(d, (980, y + 154), "申请销假", 23, RED, True, "rm")
    d.line((80, y + 225, 1000, y + 225), fill=LINE, width=2)
    txt(d, (80, y + 285), "事假 · 请假离校在蓉", 30, TEXT, True, "lm")
    txt(d, (980, y + 285), "已完成", 23, MUTED, True, "rm")
    txt(d, (80, y + 339), "10月10日 08:00 至 10月11日 18:00", 23, MUTED, False, "lm")
    txt(d, (80, y + 389), "2 天 · 已按时返校", 24, TEXT, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-leave.png")


def draw_leave_apply():
    im, d = standard_screen("申请请假")
    y = 235
    y = section(d, y, "请假信息")
    fields = [
        ("请假类型", "事假"),
        ("事假类型", "请假离蓉回家"),
        ("开始时间", "2026—12—05 08:00"),
        ("结束时间", "2026—12—07 20:00"),
    ]
    for label, value in fields:
        y = field(d, y, label, value, right="选择", height=94) + 10
    rr(d, (44, y, 1036, y + 90), 18, SURFACE, outline=LINE, width=2)
    txt(d, (76, y + 45), "请假时长", 23, MUTED, False, "lm")
    txt(d, (996, y + 45), "3 天", 27, TEXT, True, "rm")
    y += 110
    y = field(d, y, "请假事由", "参加家庭活动", height=116, multiline=True) + 12
    y = field(d, y, "家长联系电话", "138 0000 0000", height=94) + 38
    y = section(d, y, "外出去向")
    y = field(d, y, "省 / 市 / 区县", "四川省 · 成都市 · 双流区", right="选择", height=98) + 10
    y = field(d, y, "详细地址", "家中", height=98) + 38
    y = section(d, y, "证明材料", "0 / 6 张")
    rr(d, (44, y, 1036, y + 116), 18, SURFACE, outline=LINE, width=2)
    centered(d, (44, y, 1036, y + 116), "+ 选择 JPG / PNG 图片", 25, RED, True)
    y += 150
    y = primary_button(d, y, "检查并提交") + 26
    txt(d, (48, y), "确认后申请会立即进入学校审批流程。", 21, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-leave-apply.png")


def draw_leave_detail():
    im, d = standard_screen("请假详情")
    y = 235
    rr(d, (44, y, 1036, y + 190), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 48), "事假 · 请假离蓉回家", 31, TEXT, True, "lm")
    txt(d, (980, y + 48), "已通过", 23, GREEN, True, "rm")
    txt(d, (80, y + 104), "2026—12—05 08:00 至 12—07 20:00", 23, MUTED, False, "lm")
    txt(d, (80, y + 150), "3 天 · 参加家庭活动", 24, TEXT, False, "lm")
    y += 242
    y = section(d, y, "申请信息")
    rows = [
        {"title": "申请编号", "right": "demo-no-1", "chevron": False, "bold": False},
        {"title": "外出去向", "right": "四川省成都市双流区", "chevron": False, "bold": False},
        {"title": "联系电话", "right": "138 0000 0000", "chevron": False, "bold": False},
    ]
    y = plain_rows(d, y, rows, 104) + 56
    y = section(d, y, "审批进度")
    rr(d, (44, y, 1036, y + 500), 20, SURFACE, outline=LINE, width=2)
    d.line((106, y + 70, 106, y + 392), fill=LINE, width=5)
    steps = [
        (y + 76, "提交申请", "12月1日 09:42", GREEN, "已完成"),
        (y + 210, "辅导员审批", "示例老师 · 同意", GREEN, "已通过"),
        (y + 344, "学生返校", "请返校后申请销假", RED, "待销假"),
    ]
    for cy, title, sub, tone, state in steps:
        d.ellipse((92, cy - 14, 120, cy + 14), fill=tone, outline=SURFACE, width=4)
        txt(d, (150, cy - 12), title, 28, TEXT, True, "lm")
        txt(d, (150, cy + 34), sub, 22, MUTED, False, "lm")
        txt(d, (980, cy - 12), state, 22, tone, True, "rm")
    y += 552
    primary_button(d, y, "申请销假")
    finish(im, d).save(OUT / "mockup-v3-leave-detail.png")


def draw_leave_cancel():
    im, d = standard_screen("申请销假")
    y = 235
    rr(d, (44, y, 1036, y + 250), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 46), "销假范围", 29, TEXT, True, "lm")
    txt(d, (80, y + 98), "12月5日 08:00 至 12月7日 20:00", 23, MUTED, False, "lm")
    txt(d, (80, y + 146), "销假时长", 23, MUTED, False, "lm")
    txt(d, (980, y + 146), "3 天", 28, TEXT, True, "rm")
    txt(d, (80, y + 198), "申请编号  demo-no-1", 21, FAINT, False, "lm")
    y += 305
    y = section(d, y, "销假说明")
    y = field(d, y, "请说明返校和销假情况", "已按时返校，现申请销假。", height=150, multiline=True) + 54
    y = section(d, y, "位置校验")
    rr(d, (44, y, 1036, y + 200), 20, SURFACE, outline=LINE, width=2)
    txt(d, (80, y + 46), "当前位置", 25, TEXT, True, "lm")
    txt(d, (80, y + 92), "航空港校区 · 精度 12 米", 23, MUTED, False, "lm")
    txt(d, (80, y + 136), "位置校验已通过", 23, GREEN, True, "lm")
    txt(d, (980, y + 92), "重新定位", 23, RED, False, "rm")
    y += 252
    y = section(d, y, "现场自拍", "1 张")
    rr(d, (44, y, 1036, y + 180), 20, SURFACE, outline=LINE, width=2)
    rr(d, (76, y + 26, 204, y + 154), 16, "#D9DBDE")
    d.ellipse((112, y + 48, 168, y + 104), fill="#A6A8AC")
    d.arc((92, y + 82, 188, y + 152), 190, 350, fill="#A6A8AC", width=24)
    txt(d, (240, y + 70), "销假自拍已上传", 26, TEXT, True, "lm")
    txt(d, (240, y + 114), "可重新拍摄", 22, RED, False, "lm")
    y += 230
    y = primary_button(d, y, "检查并提交销假") + 24
    txt(d, (48, y), "确认后销假申请会立即提交到学校学生管理平台。", 21, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-leave-cancel.png")


def draw_profile():
    im, d = standard_screen("个人信息", refresh=True)
    y = 235
    rows = [
        {"title": "姓名", "right": "李阳", "chevron": False, "bold": False},
        {"title": "学号", "right": "202430809152", "chevron": False, "bold": False},
        {"title": "性别", "right": "男", "chevron": False, "bold": False},
        {"title": "身份", "right": "本科生", "chevron": False, "bold": False},
        {"title": "学院", "right": "计算机科学与工程学院", "chevron": False, "bold": False},
        {"title": "专业", "right": "网络工程", "chevron": False, "bold": False},
        {"title": "班级", "right": "网络工程2024—1班", "chevron": False, "bold": False},
        {"title": "年级", "right": "2024", "chevron": False, "bold": False},
        {"title": "校区", "right": "航空港校区", "chevron": False, "bold": False},
        {"title": "手机", "right": "138****0000", "chevron": False, "bold": False},
    ]
    plain_rows(d, y, rows, 120)
    finish(im, d).save(OUT / "mockup-v3-profile.png")


def draw_accounts():
    im, d = standard_screen("账号管理")
    y = 235
    y = section(d, y, "已保存账号", "2 个")
    rr(d, (44, y, 1036, y + 340), 20, SURFACE, outline=LINE, width=2)
    accounts = [
        (y + 84, "李阳", "202430809152 · 当前账号", True),
        (y + 246, "预览同学", "202430000000", False),
    ]
    for i, (cy, name, sub, current) in enumerate(accounts):
        badge(d, 110, cy, name[:1], RED if current else MUTED, RED_LIGHT if current else "#EFEFF1", 64)
        txt(d, (165, cy - 18), name, 29, TEXT, True, "lm")
        txt(d, (165, cy + 28), sub, 22, RED if current else MUTED, False, "lm")
        if not current:
            txt(d, (870, cy), "切换", 23, RED, True, "rm")
        txt(d, (980, cy), "删除", 23, MUTED, False, "rm")
        if i == 0:
            d.line((165, y + 170, 1000, y + 170), fill=LINE, width=2)
    y += 392
    y = primary_button(d, y, "添加账号") + 32
    txt(d, (48, y), "账号凭据仅保存在本机应用数据中。", 22, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-accounts.png")


def draw_add_account():
    im, d = standard_screen("添加账号")
    y = 290
    txt(d, (48, y), "登录并切换到另一个学生账号", 32, TEXT, True, "lm")
    txt(d, (48, y + 56), "成功登录后会自动保存到账号管理。", 23, MUTED, False, "lm")
    y += 130
    y = field(d, y, "学号", "") + 18
    y = field(d, y, "密码", "", right="显示") + 32
    y = primary_button(d, y, "登录并切换") + 46
    rr(d, (44, y, 1036, y + 180), 20, "#F1F1F3")
    txt(d, (80, y + 48), "安全提示", 25, TEXT, True, "lm")
    txt(d, (80, y + 94), "请只登录本人的统一身份认证账号。", 23, MUTED, False, "lm")
    txt(d, (80, y + 136), "密码不会上传到项目仓库或第三方服务。", 23, MUTED, False, "lm")
    finish(im, d).save(OUT / "mockup-v3-add-account.png")


def preference_rows(d, y, rows, row_h=112):
    rr(d, (44, y, 1036, y + row_h * len(rows)), 20, SURFACE, outline=LINE, width=2)
    for i, row in enumerate(rows):
        cy = y + i * row_h + row_h / 2
        txt(d, (80, cy - (15 if row.get("sub") else 0)), row["title"], 27, TEXT, True, "lm")
        if row.get("sub"):
            txt(d, (80, cy + 28), row["sub"], 21, MUTED, False, "lm")
        if row.get("switch") is not None:
            switch(d, 946, cy, row["switch"])
        elif row.get("right"):
            txt(d, (984, cy), row["right"], 23, MUTED, False, "rm")
        if i < len(rows) - 1:
            d.line((80, y + (i + 1) * row_h, 1000, y + (i + 1) * row_h), fill=LINE, width=2)
    return y + row_h * len(rows)


def draw_theme():
    im, d = standard_screen("主题外观")
    y = 225
    rr(d, (365, y, 715, y + 510), 34, "#F7F7F8", outline=LINE, width=3)
    txt(d, (402, y + 62), "民大助手", 24, TEXT, True, "lm")
    rr(d, (395, y + 104, 685, y + 210), 16, SURFACE, outline=LINE, width=2)
    d.rectangle((395, y + 104, 402, y + 210), fill=RED)
    rr(d, (395, y + 230, 685, y + 390), 16, SURFACE, outline=LINE, width=2)
    d.rectangle((365, y + 420, 715, y + 510), fill=SURFACE)
    for i in range(4):
        badge(d, 415 + i * 83, y + 458, "", RED if i == 0 else MUTED, RED_LIGHT if i == 0 else "#EFEFF1", 28)
    y += 556
    rr(d, (44, y, 1036, y + 82), 18, "#ECECEF")
    for i, label in enumerate(["跟随系统", "浅色", "深色"]):
        x1 = 48 + i * 328
        if i == 0:
            rr(d, (x1, y + 4, x1 + 324, y + 78), 16, SURFACE)
        centered(d, (x1, y + 4, x1 + 324, y + 78), label, 24, RED if i == 0 else MUTED, i == 0)
    y += 112
    y = preference_rows(d, y, [
        {"title": "主题色", "sub": "默认使用民大红", "right": "民大红"},
        {"title": "跟随壁纸取色", "sub": "使用系统动态配色", "switch": False},
    ], 112) + 20
    y = preference_rows(d, y, [
        {"title": "固定底栏", "sub": "四个一级页面始终可切换", "switch": True},
        {"title": "背景模糊", "sub": "顶栏和底栏半透明", "switch": False},
        {"title": "预测性返回", "sub": "侧滑时预览上一页", "switch": True},
        {"title": "界面缩放", "sub": "当前 100%", "right": "调整"},
    ], 108)
    finish(im, d).save(OUT / "mockup-v3-theme.png")


def draw_about():
    im, d = standard_screen("关于")
    y = 255
    rr(d, (444, y, 636, y + 192), 44, RED)
    txt(d, (540, y + 96), "民", 82, "#FFFFFF", True, "mm")
    txt(d, (540, y + 258), "民大助手", 38, TEXT, True, "mm")
    txt(d, (540, y + 310), "版本 1.0.0", 23, MUTED, False, "mm")
    y += 370
    y = plain_rows(d, y, [
        {"title": "制作者", "right": "Britney", "chevron": False, "bold": False},
        {"title": "开源仓库", "right": "BritneyOvO/swun_ehall", "chevron": True, "bold": False},
        {"title": "进入首页时检查更新", "right": "已开启", "right_color": RED, "chevron": False, "bold": False},
    ], 110) + 28
    y = secondary_button(d, y, "检查更新") + 58
    y = section(d, y, "说明")
    rr(d, (44, y, 1036, y + 205), 20, "#F1F1F3")
    wrap_lines(d, "本应用为非官方校园客户端，仅服务学生账号。学校名称与标识归西南民族大学所有。", 80, y + 50, 900, 23, MUTED, False, 3, 14)
    finish(im, d).save(OUT / "mockup-v3-about.png")


def draw_avatar_crop():
    im = blank("#000000")
    d = ImageDraw.Draw(im)
    b.status_bar(d, dark=False)
    header(d, "裁剪头像", back=True, dark=True)
    y1, y2 = 300, 1930
    d.rectangle((0, y1, W, y2), fill="#2E3338")
    d.ellipse((290, 560, 790, 1060), fill="#C9A27F")
    d.pieslice((250, 470, 830, 1060), 180, 360, fill="#26282B")
    d.ellipse((205, 980, 875, 1730), fill="#6C747C")
    d.ellipse((90, 575, 990, 1475), outline="#FFFFFF", width=6)
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rectangle((0, y1, W, y2), fill=(0, 0, 0, 110))
    od.ellipse((90, 575, 990, 1475), fill=(0, 0, 0, 0))
    im.paste(overlay, (0, 0), overlay)
    d = ImageDraw.Draw(im)
    txt(d, (540, 2020), "拖动和缩放照片，使头像位于圆形区域内", 24, "#C7C7CB", False, "mm")
    secondary_button(d, 2100, "取消", x1=44, x2=510, height=104)
    primary_button(d, 2100, "完成", x1=570, x2=1036, height=104)
    system_bottom(d, "#000000")
    im.save(OUT / "mockup-v3-avatar-crop.png")


def draw_states():
    # Loading
    im, d = standard_screen("成绩", back=False, refresh=True)
    y = 250
    for w in [620, 910, 760, 900, 680]:
        rr(d, (60, y, 60 + w, y + 68), 14, "#E9E9EC")
        y += 105
    finish(im, d, 2).save(OUT / "mockup-v3-state-loading.png")

    # Empty state
    im, d = standard_screen("考试", refresh=True)
    badge(d, 540, 850, "空", MUTED, "#EFEFF1", 120)
    txt(d, (540, 970), "本学期暂无考试安排", 31, TEXT, True, "mm")
    txt(d, (540, 1024), "有新安排时会在这里显示", 23, MUTED, False, "mm")
    secondary_button(d, 1090, "重新加载", x1=340, x2=740, height=90)
    finish(im, d).save(OUT / "mockup-v3-state-empty.png")

    # Cached/offline state
    v2.draw_leave()
    shutil.copyfile(OUT / "mockup-v2-leave.png", OUT / "mockup-v3-state-offline.png")

    # Confirm dialog
    base = Image.open(OUT / "mockup-v3-water.png").convert("RGBA")
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 100))
    base.alpha_composite(overlay)
    d = ImageDraw.Draw(base)
    rr(d, (110, 720, 970, 1335), 30, SURFACE)
    txt(d, (160, 790), "确认热水充值", 38, TEXT, True, "lm")
    wrap_lines(d, "将从一卡通余额立即扣除 ¥ 10.00。确认后无法在本应用撤销。", 160, 870, 740, 27, TEXT, False, 4, 14)
    d.line((160, 1110, 920, 1110), fill=LINE, width=2)
    centered(d, (160, 1140, 510, 1260), "取消", 28, MUTED, True)
    centered(d, (560, 1140, 920, 1260), "确认扣款", 28, RED, True)
    base.convert("RGB").save(OUT / "mockup-v3-state-dialog.png")


def draw_splash():
    im = blank("#FAFAFB")
    d = ImageDraw.Draw(im)
    rr(d, (414, 880, 666, 1132), 56, RED)
    txt(d, (540, 1006), "民", 104, "#FFFFFF", True, "mm")
    txt(d, (540, 1218), "民大助手", 42, TEXT, True, "mm")
    txt(d, (540, 1272), "西南民族大学", 24, MUTED, False, "mm")
    im.save(OUT / "mockup-v3-splash.png")


def draw_update_prompt():
    base = Image.open(OUT / "mockup-v3-home.png").convert("RGBA")
    base.alpha_composite(Image.new("RGBA", base.size, (0, 0, 0, 104)))
    d = ImageDraw.Draw(base)
    rr(d, (104, 585, 976, 1510), 30, SURFACE)
    txt(d, (154, 660), "发现新版本 1.1.0", 38, TEXT, True, "lm")
    txt(d, (154, 730), "本次更新", 25, MUTED, True, "lm")
    notes = [
        "• 重做首页、课表和成绩的信息层级",
        "• 修复底部导航遮挡正文的问题",
        "• 请销假失败时保留上次成功数据",
        "• 优化加载、空数据与错误提示",
    ]
    yy = 792
    for note in notes:
        txt(d, (154, yy), note, 26, TEXT, False, "lm")
        yy += 70
    rr(d, (154, 1110, 926, 1210), 18, "#F1F1F3")
    progress(d, 184, 1154, 896, .64)
    txt(d, (540, 1260), "下载中 64%", 23, MUTED, False, "mm")
    d.line((154, 1320, 926, 1320), fill=LINE, width=2)
    centered(d, (154, 1350, 500, 1460), "稍后", 27, MUTED, True)
    centered(d, (540, 1350, 926, 1460), "下载并安装", 27, RED, True)
    base.convert("RGB").save(OUT / "mockup-v3-update-prompt.png")


PAGES = {
    "核心页面": [
        ("登录", "mockup-v3-login.png"),
        ("首页", "mockup-v3-home.png"),
        ("周课表", "mockup-v3-schedule-week.png"),
        ("成绩", "mockup-v3-grades.png"),
        ("我的", "mockup-v3-mine.png"),
    ],
    "教务功能": [
        ("课表列表", "mockup-v3-schedule-list.png"),
        ("共修学分", "mockup-v3-credits.png"),
        ("考试", "mockup-v3-exams.png"),
        ("选课", "mockup-v3-selection.png"),
    ],
    "校园服务": [
        ("课堂考勤", "mockup-v3-ktkq.png"),
        ("课堂签到", "mockup-v3-ktkq-sign.png"),
        ("公寓打卡", "mockup-v3-clock.png"),
        ("校车时刻", "mockup-v3-bus.png"),
        ("校历", "mockup-v3-calendar.png"),
        ("测试定位", "mockup-v3-locate-test.png"),
    ],
    "一卡通与缴费": [
        ("一卡通", "mockup-v3-ykt.png"),
        ("一卡通充值", "mockup-v3-ykt-recharge.png"),
        ("宿舍电费", "mockup-v3-electric.png"),
        ("热水充值", "mockup-v3-water.png"),
    ],
    "请假与销假": [
        ("请假记录", "mockup-v3-leave.png"),
        ("申请请假", "mockup-v3-leave-apply.png"),
        ("请假详情", "mockup-v3-leave-detail.png"),
        ("申请销假", "mockup-v3-leave-cancel.png"),
    ],
    "账号与设置": [
        ("个人信息", "mockup-v3-profile.png"),
        ("账号管理", "mockup-v3-accounts.png"),
        ("添加账号", "mockup-v3-add-account.png"),
        ("主题外观", "mockup-v3-theme.png"),
        ("关于", "mockup-v3-about.png"),
        ("裁剪头像", "mockup-v3-avatar-crop.png"),
    ],
    "全局与通用状态": [
        ("启动页", "mockup-v3-splash.png"),
        ("版本更新", "mockup-v3-update-prompt.png"),
        ("加载中", "mockup-v3-state-loading.png"),
        ("空数据", "mockup-v3-state-empty.png"),
        ("离线缓存", "mockup-v3-state-offline.png"),
        ("确认弹窗", "mockup-v3-state-dialog.png"),
    ],
}


def board_for(title, pages, filename, cols=None):
    if cols is None:
        cols = min(5, len(pages))
    rows = math.ceil(len(pages) / cols)
    tw, th = 324, 720
    gap = 24
    top = 170
    label_h = 46
    width = gap + cols * (tw + gap)
    height = top + rows * (th + label_h + gap) + 30
    out = Image.new("RGB", (width, height), "#ECEDEF")
    d = ImageDraw.Draw(out)
    txt(d, (28, 42), f"V3 · {title}", 34, TEXT, True, "lm")
    txt(d, (28, 84), "原生校园工具风格 · 固定底栏 · 单一品牌色 · 信息优先", 21, MUTED, False, "lm")
    for i, (label, name) in enumerate(pages):
        row, col = divmod(i, cols)
        x = gap + col * (tw + gap)
        y = top + row * (th + label_h + gap)
        rr(d, (x, y - label_h, x + tw, y + th + 7), 18, SURFACE)
        centered(d, (x, y - label_h, x + tw, y - 4), label, 21, MUTED, True)
        shot = Image.open(OUT / name).resize((tw, th), Image.Resampling.LANCZOS)
        out.paste(shot, (x, y))
    out.save(OUT / filename)


def build_boards():
    for index, (title, pages) in enumerate(PAGES.items(), start=1):
        cols = 3 if len(pages) == 6 else min(5, len(pages))
        board_for(title, pages, f"mockup-board-v3-{index:02d}.png", cols)
    all_pages = []
    for pages in PAGES.values():
        all_pages.extend(pages)
    board_for("全界面总览", all_pages, "mockup-board-v3-all.png", 5)


def write_index():
    lines = [
        "# 民大助手 UI V3 全界面设计稿",
        "",
        "设计方向：原生校园工具风格，固定四栏底部导航，单一民大红品牌色，减少装饰和独立卡片。",
        "",
        "共覆盖 27 个实际页面、1 个课表列表形态、头像裁剪/启动/更新浮层，以及 4 类通用状态。",
        "",
    ]
    for index, (title, pages) in enumerate(PAGES.items(), start=1):
        lines += [f"## {title}", "", f"总览：`mockup-board-v3-{index:02d}.png`", ""]
        for label, name in pages:
            lines.append(f"- {label}：`{name}`")
        lines.append("")
    lines += ["## 总览", "", "- 全部界面：`mockup-board-v3-all.png`", ""]
    (OUT / "mockup-v3-index.md").write_text("\n".join(lines), encoding="utf-8")


def main():
    draw_login()
    draw_main_copies()
    draw_schedule_list()
    draw_grades()
    draw_mine()
    draw_credits()
    draw_exams()
    draw_selection()
    draw_ktkq()
    draw_ktkq_sign()
    draw_clock()
    draw_bus()
    draw_calendar()
    draw_locate_test()
    draw_ykt()
    draw_recharge()
    draw_electric()
    draw_water()
    draw_leave()
    draw_leave_apply()
    draw_leave_detail()
    draw_leave_cancel()
    draw_profile()
    draw_accounts()
    draw_add_account()
    draw_theme()
    draw_about()
    draw_avatar_crop()
    draw_states()
    draw_splash()
    draw_update_prompt()
    build_boards()
    write_index()
    for path in sorted(OUT.glob("mockup-board-v3-*.png")):
        print(path.name, Image.open(path).size)


if __name__ == "__main__":
    main()
