from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math

OUT = Path(__file__).resolve().parent
W, H = 1080, 2400
BG = '#F5F6FA'
SURFACE = '#FFFFFF'
TEXT = '#17191F'
MUTED = '#747985'
LINE = '#E6E9F0'
PRIMARY = '#315FC8'
PRIMARY_DARK = '#24498F'
PRIMARY_LIGHT = '#E8EEFC'
SUCCESS = '#2B8A6E'
WARN_BG = '#FFF4DA'
WARN = '#946200'
ERROR = '#C53A43'
FONT_REG = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
FONT_BOLD = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
_fonts = {}

def font(size, bold=False):
    key=(size,bold)
    if key not in _fonts:
        _fonts[key]=ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)
    return _fonts[key]

def image():
    return Image.new('RGB', (W,H), BG)

def rr(d, box, radius, fill, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

def txt(d, xy, value, size, color=TEXT, bold=False, anchor='la'):
    d.text(xy, value, font=font(size,bold), fill=color, anchor=anchor)

def centered(d, box, value, size, color=TEXT, bold=False):
    x1,y1,x2,y2=box
    d.text(((x1+x2)/2,(y1+y2)/2), value, font=font(size,bold), fill=color, anchor='mm')

def text_width(value,size,bold=False):
    b=ImageDraw.Draw(Image.new('RGB',(1,1))).textbbox((0,0),value,font=font(size,bold))
    return b[2]-b[0]

def wrap_cjk(value, size, max_width, max_lines, bold=False):
    f=font(size,bold)
    probe=ImageDraw.Draw(Image.new('RGB',(1,1)))
    lines=[]; cur=''; i=0
    for i,ch in enumerate(value):
        candidate=cur+ch
        if probe.textlength(candidate,font=f) <= max_width or not cur:
            cur=candidate
        else:
            lines.append(cur); cur=ch
            if len(lines) == max_lines:
                break
    else:
        i=len(value)-1
    if len(lines)<max_lines and cur:
        lines.append(cur)
    consumed=''.join(lines)
    if len(consumed)<len(value):
        last=lines[-1]
        while last and probe.textlength(last+'…',font=f)>max_width:
            last=last[:-1]
        lines[-1]=last+'…'
    return lines[:max_lines]

def status_bar(d, time='10:55', dark=True):
    c=TEXT if dark else '#FFFFFF'
    txt(d,(54,52),time,38,c,bold=False,anchor='lm')
    # quiet icon cluster
    d.arc((760,35,806,81),200,340,fill=c,width=5)
    d.arc((771,45,795,69),200,340,fill=c,width=5)
    d.ellipse((781,61,788,68),fill=c)
    # signal
    for i,h in enumerate([10,18,28,38]):
        d.rounded_rectangle((833+i*10,72-h,839+i*10,72),radius=2,fill=c)
    # battery
    d.rounded_rectangle((920,38,1000,72),radius=8,outline=c,width=4)
    d.rectangle((1000,48,1008,62),fill=c)
    d.rounded_rectangle((927,45,985,65),radius=4,fill='#79B83A')
    txt(d,(956,55),'100',20,'#17320B',bold=True,anchor='mm')

def gesture_bar(d):
    d.rounded_rectangle((388,2364,692,2378),radius=7,fill='#55585D')

def icon(d, name, cx, cy, size, color, stroke=5, fill=False):
    s=size; x=cx-s/2; y=cy-s/2
    if name=='home':
        pts=[(cx,y+4),(x+3,cy-2),(x+10,cy-2),(x+10,y+s-5),(cx-7,y+s-5),(cx-7,cy+9),(cx+7,cy+9),(cx+7,y+s-5),(x+s-10,y+s-5),(x+s-10,cy-2),(x+s-3,cy-2)]
        d.polygon(pts,fill=color if fill else None)
        if not fill: d.line(pts+[pts[0]],fill=color,width=stroke,joint='curve')
    elif name=='calendar':
        rr(d,(x+4,y+8,x+s-4,y+s-3),10,None if not fill else color,outline=color,width=stroke)
        d.line((x+5,y+22,x+s-5,y+22),fill=color,width=stroke)
        d.line((x+16,y+2,x+16,y+15),fill=color,width=stroke)
        d.line((x+s-16,y+2,x+s-16,y+15),fill=color,width=stroke)
        if not fill:
            for dx in [17,31,45]:
                for dy in [33,46]: d.ellipse((x+dx-2,y+dy-2,x+dx+2,y+dy+2),fill=color)
    elif name=='doc':
        rr(d,(x+9,y+3,x+s-8,y+s-3),8,None if not fill else color,outline=color,width=stroke)
        if not fill:
            for k,wid in enumerate([30,25,34]): d.line((x+18,y+20+k*13,x+18+wid,y+20+k*13),fill=color,width=stroke-1)
    elif name=='person':
        d.ellipse((cx-s*.17,y+4,cx+s*.17,y+s*.38),fill=color if fill else None,outline=color,width=stroke)
        d.arc((x+8,y+s*.43,x+s-8,y+s+10),190,350,fill=color,width=stroke)
    elif name=='wallet':
        rr(d,(x+4,y+12,x+s-4,y+s-8),10,color if fill else None,outline=color,width=stroke)
        d.line((x+5,y+27,x+s-5,y+27),fill=color if not fill else '#FFFFFF',width=stroke)
        d.ellipse((x+s-19,y+36,x+s-12,y+43),fill=color if not fill else '#FFFFFF')
    elif name=='check':
        rr(d,(x+7,y+7,x+s-7,y+s-7),10,color if fill else None,outline=color,width=stroke)
        cc='#FFFFFF' if fill else color
        d.line((x+17,cy,x+27,cy+11,x+s-15,y+21),fill=cc,width=stroke+1,joint='curve')
    elif name=='exam':
        rr(d,(x+10,y+4,x+s-8,y+s-4),7,None,outline=color,width=stroke)
        d.line((x+19,y+19,x+s-18,y+19),fill=color,width=stroke-1)
        d.line((x+19,y+32,x+s-24,y+32),fill=color,width=stroke-1)
        d.line((x+19,y+45,x+s-15,y+45),fill=color,width=stroke-1)
    elif name=='leave':
        rr(d,(x+9,y+7,x+s-13,y+s-7),5,None,outline=color,width=stroke)
        d.line((cx-2,cy,cx+s*.32,cy),fill=color,width=stroke)
        d.line((cx+s*.18,cy-10,cx+s*.32,cy,cx+s*.18,cy+10),fill=color,width=stroke,joint='curve')
    elif name=='bolt':
        pts=[(cx+4,y+1),(x+15,cy+2),(cx-2,cy+2),(cx-8,y+s-2),(x+s-13,cy-8),(cx+2,cy-8)]
        d.polygon(pts,fill=color)
    elif name=='bus':
        rr(d,(x+5,y+7,x+s-5,y+s-10),10,None,outline=color,width=stroke)
        d.line((x+10,y+27,x+s-10,y+27),fill=color,width=stroke)
        d.ellipse((x+13,y+s-14,x+25,y+s-2),fill=color)
        d.ellipse((x+s-25,y+s-14,x+s-13,y+s-2),fill=color)
    elif name=='edit':
        rr(d,(x+6,y+9,x+s-12,y+s-5),8,None,outline=color,width=stroke)
        d.line((x+21,y+s-18,x+s-4,y+10),fill=color,width=stroke+2)
        d.line((x+s-15,y+6,x+s-4,y+17),fill=color,width=stroke+2)
    elif name=='pin':
        d.ellipse((cx-s*.2,y+6,cx+s*.2,y+s*.44),outline=color,width=stroke)
        d.polygon([(cx-s*.16,y+s*.35),(cx+s*.16,y+s*.35),(cx,y+s-3)],fill=color)
        d.ellipse((cx-4,y+s*.2,cx+4,y+s*.2+8),fill='#FFFFFF')
    elif name=='clock':
        d.ellipse((x+5,y+5,x+s-5,y+s-5),outline=color,width=stroke)
        d.line((cx,cy,cx,cy-s*.22),fill=color,width=stroke)
        d.line((cx,cy,cx+s*.18,cy+s*.09),fill=color,width=stroke)
    elif name=='refresh':
        d.arc((x+5,y+5,x+s-5,y+s-5),35,315,fill=color,width=stroke)
        d.polygon([(x+s-5,y+12),(x+s-22,y+8),(x+s-14,y+24)],fill=color)
    elif name=='arrow_left':
        d.line((cx+s*.18,y+8,cx-s*.18,cy,cx+s*.18,y+s-8),fill=color,width=stroke+2,joint='curve')
    elif name=='chev_right':
        d.line((cx-s*.10,y+8,cx+s*.14,cy,cx-s*.10,y+s-8),fill=color,width=stroke,joint='curve')

def bottom_nav(d, selected):
    top=2165; bottom=2335
    d.rectangle((0,top,W,bottom),fill=SURFACE)
    d.line((0,top,W,top),fill=LINE,width=2)
    items=[('home','首页'),('calendar','课表'),('doc','成绩'),('person','我的')]
    centers=[135,405,675,945]
    for i,(ic,label) in enumerate(items):
        on=i==selected
        if on:
            rr(d,(centers[i]-82,top+18,centers[i]+82,top+96),39,PRIMARY_LIGHT)
        icon(d,ic,centers[i],top+58,46,PRIMARY if on else '#777D88',stroke=5,fill=on and ic=='home')
        txt(d,(centers[i],top+128),label,27,PRIMARY if on else '#656B76',bold=on,anchor='mm')
    d.rectangle((0,bottom,W,H),fill=SURFACE)
    gesture_bar(d)

def bottom_nav_floating(im, d, selected):
    from PIL import ImageFilter
    # The pill is visually floating, while the page still reserves this entire region.
    shadow=Image.new('RGBA',(W,H),(0,0,0,0))
    sd=ImageDraw.Draw(shadow)
    sd.rounded_rectangle((88,2150,992,2308),radius=79,fill=(30,40,60,48))
    shadow=shadow.filter(ImageFilter.GaussianBlur(24))
    im.paste(shadow,(0,0),shadow)
    rr(d,(88,2136,992,2294),79,'#FFFFFF',outline='#E3E6ED',width=2)
    items=[('home','首页'),('calendar','课表'),('doc','成绩'),('person','我的')]
    centers=[205,425,655,875]
    for i,(ic,label) in enumerate(items):
        on=i==selected
        if on: rr(d,(centers[i]-91,2154,centers[i]+91,2276),61,PRIMARY_LIGHT)
        icon(d,ic,centers[i],2192,42,PRIMARY if on else '#777D88',stroke=5,fill=on and ic=='home')
        txt(d,(centers[i],2248),label,23,PRIMARY if on else '#656B76',bold=on,anchor='mm')
    d.rectangle((0,2318,W,H),fill=SURFACE)
    gesture_bar(d)

def app_header(d, title, back=False, refresh=False):
    if back:
        icon(d,'arrow_left',72,154,54,TEXT,stroke=5)
        txt(d,(130,155),title,46,TEXT,bold=True,anchor='lm')
    else:
        txt(d,(48,155),title,48,TEXT,bold=True,anchor='lm')
    if refresh:
        icon(d,'refresh',996,154,48,TEXT,stroke=5)

def service_tile(d, x, y, label, ic, tint, bg):
    rr(d,(x-41,y-41,x+41,y+41),23,bg)
    icon(d,ic,x,y,43,tint,stroke=4)
    txt(d,(x,y+78),label,27,TEXT,bold=False,anchor='mm')

def draw_home(nav_style='docked', filename='mockup-home.png'):
    im=image(); d=ImageDraw.Draw(im)
    status_bar(d)
    txt(d,(50,154),'民大助手',44,TEXT,bold=True,anchor='lm')
    rr(d,(935,112,1015,192),40,PRIMARY_LIGHT)
    txt(d,(975,152),'李',32,PRIMARY,bold=True,anchor='mm')
    # hero
    hero=(44,220,1036,600)
    rr(d,hero,42,PRIMARY_DARK)
    # gradient-like overlays
    d.ellipse((650,220,1036,600),fill='#315CA9')
    rr(d,hero,42,None,outline='#3B68BA',width=2)
    txt(d,(88,275),'下午好，李同学',42,'#FFFFFF',bold=True,anchor='lm')
    rr(d,(755,248,980,310),31,'#5075B9')
    centered(d,(755,248,980,310),'第 13 周 · 周四',27,'#FFFFFF',False)
    txt(d,(88,352),'下一节 · 19:00—20:35',29,'#C9D8F5',anchor='lm')
    txt(d,(88,424),'无线网络与移动计算',53,'#FFFFFF',bold=True,anchor='lm')
    icon(d,'pin',107,505,40,'#D8E5FF',stroke=4)
    txt(d,(140,506),'H-206  ·  陈曦',30,'#E7EEFC',anchor='lm')
    rr(d,(780,492,980,562),35,'#FFFFFF')
    centered(d,(780,492,980,562),'查看课表',29,PRIMARY_DARK,True)
    # today
    txt(d,(48,675),'今日安排',39,TEXT,bold=True,anchor='lm')
    txt(d,(1018,675),'2 节课',27,MUTED,anchor='rm')
    rr(d,(44,720,1036,1055),34,SURFACE,outline=LINE,width=2)
    d.line((170,772,170,1000),fill='#D5DBE8',width=6)
    # row 1
    txt(d,(82,790),'13:00',29,MUTED,bold=True,anchor='lm')
    txt(d,(82,828),'14:35',23,'#A0A5AE',anchor='lm')
    d.ellipse((156,798,184,826),fill='#93A0B5',outline=SURFACE,width=6)
    txt(d,(215,796),'数字通信原理及协议',34,TEXT,bold=True,anchor='lm')
    txt(d,(215,850),'BW-106  ·  李成杰',27,MUTED,anchor='lm')
    d.line((215,895,982,895),fill=LINE,width=2)
    # row 2
    txt(d,(82,938),'19:00',29,PRIMARY,bold=True,anchor='lm')
    txt(d,(82,976),'20:35',23,MUTED,anchor='lm')
    d.ellipse((151,932,189,970),fill=PRIMARY_LIGHT,outline='#BED0F5',width=2)
    d.ellipse((160,941,180,961),fill=PRIMARY)
    txt(d,(215,936),'无线网络与移动计算',34,TEXT,bold=True,anchor='lm')
    txt(d,(215,990),'H-206  ·  陈曦',27,MUTED,anchor='lm')
    rr(d,(852,925,982,980),27,PRIMARY_LIGHT)
    centered(d,(852,925,982,980),'下一节',24,PRIMARY,True)
    # services
    txt(d,(48,1130),'常用服务',39,TEXT,bold=True,anchor='lm')
    txt(d,(964,1130),'全部服务',27,PRIMARY,anchor='rm')
    icon(d,'chev_right',996,1130,28,PRIMARY,stroke=4)
    rr(d,(44,1175,1036,1700),34,SURFACE,outline=LINE,width=2)
    centers_x=[170,415,660,905]
    rows=[(1280,[('一卡通','wallet','#315FC8','#E8EEFC'),('课堂考勤','check','#2B8A6E','#E4F5EF'),('考试安排','exam','#A05C22','#F9EBDD'),('请假销假','leave','#7B55B6','#F0E8FA')]),
          (1515,[('宿舍电费','bolt','#D17A00','#FFF0D5'),('校车','bus','#287E91','#E2F2F5'),('选课','edit','#315FC8','#E8EEFC'),('校历','calendar','#8D4D6F','#F6E6EF')])]
    for y,arr in rows:
        for x,(lab,ic,tint,bg) in zip(centers_x,arr): service_tile(d,x,y,lab,ic,tint,bg)
    # todo card
    txt(d,(48,1780),'待办提醒',39,TEXT,bold=True,anchor='lm')
    rr(d,(44,1825,1036,2078),34,SURFACE,outline=LINE,width=2)
    rr(d,(84,1872,180,1968),28,'#FBE9EA')
    icon(d,'exam',132,1920,48,ERROR,stroke=4)
    txt(d,(215,1888),'网络安全基础考试',34,TEXT,bold=True,anchor='lm')
    txt(d,(215,1944),'11月30日 09:00  ·  BS-243',27,MUTED,anchor='lm')
    txt(d,(215,1995),'请提前 15 分钟到场',25,'#9A6A70',anchor='lm')
    rr(d,(858,1888,982,1952),32,'#FBE9EA')
    centered(d,(858,1888,982,1952),'4 天后',25,ERROR,True)
    if nav_style == 'floating': bottom_nav_floating(im,d,0)
    else: bottom_nav(d,0)
    im.save(OUT/filename)

def course_block(d, x1,y1,x2,y2, title, room, fill, accent, title_size=25, max_lines=3):
    rr(d,(x1,y1,x2,y2),18,fill)
    d.rounded_rectangle((x1,y1,x1+8,y2),radius=4,fill=accent)
    lines=wrap_cjk(title,title_size,x2-x1-30,max_lines,True)
    yy=y1+18
    for line in lines:
        txt(d,(x1+18,yy),line,title_size,TEXT,bold=True,anchor='la'); yy+=title_size+8
    if room and yy+27<y2-10:
        txt(d,(x1+18,min(yy+4,y2-34)),room,21,'#515866',anchor='la')

def schedule_header(d, mode='周'):
    app_header(d,'课表',False,True)
    icon(d,'arrow_left',72,280,40,PRIMARY,stroke=5)
    txt(d,(126,275),'第 13 周',38,TEXT,bold=True,anchor='lm')
    icon(d,'chev_right',342,280,40,PRIMARY,stroke=5)
    txt(d,(392,276),'11/23—11/29',25,MUTED,anchor='lm')
    rr(d,(665,236,1030,320),42,'#E9EBF0')
    labels=['周','日','列表']
    for i,lab in enumerate(labels):
        x1=665+i*121.7; x2=x1+121.7
        if lab==mode: rr(d,(x1+4,240,x2-4,316),38,SURFACE)
        centered(d,(x1,236,x2,320),lab,27,PRIMARY if lab==mode else MUTED,lab==mode)

def draw_week():
    im=image(); d=ImageDraw.Draw(im)
    status_bar(d); schedule_header(d,'周')
    grid_top=455; grid_bottom=2165; gutter=82; col=(W-gutter)/7
    # weekday header
    days=[('周一','11/23'),('周二','11/24'),('周三','11/25'),('周四','11/26'),('周五','11/27'),('周六','11/28'),('周日','11/29')]
    for i,(day,date) in enumerate(days):
        cx=gutter+col*(i+.5)
        if i==3: rr(d,(cx-58,344,cx+58,438),28,PRIMARY_LIGHT)
        txt(d,(cx,372),day,25,PRIMARY if i==3 else MUTED,bold=i==3,anchor='mm')
        txt(d,(cx,414),date,22,PRIMARY if i==3 else '#9499A3',bold=i==3,anchor='mm')
    d.line((0,grid_top,W,grid_top),fill=LINE,width=2)
    rows=10; rh=(grid_bottom-grid_top)/rows
    times=[('1','8:30','9:15'),('2','9:20','10:05'),('3','10:25','11:10'),('4','11:15','12:00'),('5','14:00','14:45'),('6','14:50','15:35'),('7','15:55','16:40'),('8','16:45','17:30'),('9','19:00','19:45'),('10','19:50','20:35')]
    for r,(n,a,b) in enumerate(times):
        y=grid_top+r*rh
        d.line((gutter,y,W,y),fill=LINE,width=2)
        txt(d,(36,y+36),n,28,TEXT,bold=True,anchor='mm')
        txt(d,(37,y+76),a,19,MUTED,anchor='mm')
        txt(d,(37,y+102),b,19,MUTED,anchor='mm')
    for i in range(8):
        x=gutter+i*col; d.line((x,grid_top,x,grid_bottom),fill=LINE,width=2)
    def box(day,start,span,title,room,fill,accent):
        x1=gutter+(day-1)*col+6; x2=gutter+day*col-6
        y1=grid_top+(start-1)*rh+7; y2=grid_top+(start-1+span)*rh-7
        course_block(d,x1,y1,x2,y2,title,room,fill,accent,24,3)
    box(4,1,2,'计算机组成原理','BW-107','#F4DCE9','#D35D97')
    box(6,1,4,'网络安全基础及法律法规','BS-243','#F7E0EB','#D35D97')
    box(3,3,2,'计算机组成原理实验','BS-241','#DFF3E8','#39A66C')
    box(5,3,2,'网络攻防','BS-223','#DFE9FD','#4D7DDF')
    box(1,5,2,'网络工程学期实训','BS-222','#DDF2E7','#38A56A')
    box(3,5,2,'数字通信原理及协议','BW-106','#FBE9D6','#E58B2B')
    box(1,7,4,'网络工程学期实训（网络开发）','BS-217','#D9F0EC','#2CA796')
    box(4,9,2,'无线网络与移动计算','H-206','#F4DCE9','#D35D97')
    box(5,9,2,'网络攻防','BS-223','#DFE9FD','#4D7DDF')
    # now marker at period 6 Thu
    y=grid_top+5.55*rh
    d.line((gutter+3*col,y,gutter+4*col,y),fill=PRIMARY,width=4)
    d.ellipse((gutter+3*col-7,y-7,gutter+3*col+7,y+7),fill=PRIMARY)
    bottom_nav(d,1)
    im.save(OUT/'mockup-schedule-week.png')

def draw_day():
    im=image(); d=ImageDraw.Draw(im)
    status_bar(d); schedule_header(d,'日')
    # horizontal date selector
    days=[('一','23'),('二','24'),('三','25'),('四','26'),('五','27'),('六','28'),('日','29')]
    for i,(day,date) in enumerate(days):
        cx=82+i*153
        if i==3:
            rr(d,(cx-52,352,cx+52,470),30,PRIMARY)
            txt(d,(cx,384),day,24,'#FFFFFF',bold=True,anchor='mm')
            txt(d,(cx,433),date,31,'#FFFFFF',bold=True,anchor='mm')
        else:
            txt(d,(cx,384),day,24,MUTED,anchor='mm')
            txt(d,(cx,433),date,30,TEXT,bold=True,anchor='mm')
    txt(d,(48,540),'今天 · 周四',42,TEXT,bold=True,anchor='lm')
    rr(d,(44,590,1036,830),36,PRIMARY_DARK)
    txt(d,(82,635),'下一节 · 8 小时后',27,'#C9D8F5',anchor='lm')
    txt(d,(82,701),'无线网络与移动计算',45,'#FFFFFF',bold=True,anchor='lm')
    icon(d,'pin',100,766,36,'#D8E5FF',stroke=4)
    txt(d,(130,766),'19:00—20:35  ·  H-206  ·  陈曦',28,'#E7EEFC',anchor='lm')
    rr(d,(808,736,988,796),30,'#FFFFFF')
    centered(d,(808,736,988,796),'课程详情',25,PRIMARY_DARK,True)
    txt(d,(48,910),'全天安排',37,TEXT,bold=True,anchor='lm')
    # timeline
    d.line((174,970,174,1930),fill='#D6DCE8',width=6)
    events=[
        (1010,'08:30','10:05','计算机组成原理','BW-107 · 姜玥','done'),
        (1250,'10:25','12:00','计算机组成原理实验','BS-241 · 郑俊辉','done'),
        (1490,'14:00','15:35','数字通信原理及协议','BW-106 · 李成杰','normal'),
        (1730,'19:00','20:35','无线网络与移动计算','H-206 · 陈曦','next'),
    ]
    for y,start,end,title,sub,state in events:
        col='#A2A8B3' if state=='done' else PRIMARY if state=='next' else '#6D7789'
        txt(d,(50,y),start,29,col,bold=state=='next',anchor='lm')
        txt(d,(50,y+40),end,22,'#A0A5AE',anchor='lm')
        d.ellipse((157,y-13,191,y+21),fill=SURFACE,outline=col,width=6)
        if state=='next': d.ellipse((165,y-5,183,y+13),fill=PRIMARY)
        rr(d,(218,y-38,1028,y+154),28,SURFACE,outline='#CCD8F0' if state=='next' else LINE,width=3 if state=='next' else 2)
        txt(d,(252,y+12),title,34,TEXT,bold=True,anchor='lm')
        txt(d,(252,y+68),sub,27,MUTED,anchor='lm')
        if state=='done': txt(d,(938,y+12),'已结束',23,'#949AA5',anchor='rm')
        elif state=='next':
            rr(d,(842,y-2,984,y+54),28,PRIMARY_LIGHT)
            centered(d,(842,y-2,984,y+54),'下一节',23,PRIMARY,True)
    bottom_nav(d,1)
    im.save(OUT/'mockup-schedule-day.png')

def draw_leave():
    im=image(); d=ImageDraw.Draw(im)
    status_bar(d); app_header(d,'请假与销假',True,True)
    # inline stale banner
    rr(d,(44,230,1036,365),30,WARN_BG)
    txt(d,(82,273),'更新失败，正在显示上次成功数据',30,WARN,bold=True,anchor='lm')
    txt(d,(82,324),'上次更新：今天 10:42',24,'#8A7443',anchor='lm')
    rr(d,(856,258,992,338),40,'#FFFDF6',outline='#E7C873',width=2)
    centered(d,(856,258,992,338),'重试',26,WARN,True)
    # metrics
    rr(d,(44,400,1036,610),34,SURFACE,outline=LINE,width=2)
    metrics=[('2','请假次数'),('4 天','累计天数'),('1','待销假')]
    for i,(v,l) in enumerate(metrics):
        cx=208+i*332
        txt(d,(cx,468),v,44,TEXT,bold=True,anchor='mm')
        txt(d,(cx,535),l,25,MUTED,anchor='mm')
        if i<2: d.line((374+i*332,440,374+i*332,570),fill=LINE,width=2)
    # compact profile
    rr(d,(44,645,1036,875),34,SURFACE,outline=LINE,width=2)
    rr(d,(82,690,174,782),46,PRIMARY_LIGHT)
    txt(d,(128,736),'李',38,PRIMARY,bold=True,anchor='mm')
    txt(d,(204,700),'李同学',36,TEXT,bold=True,anchor='lm')
    txt(d,(204,758),'202430000000  ·  计算机科学与工程学院',26,MUTED,anchor='lm')
    txt(d,(204,810),'宿舍信息未同步',24,'#9A9FA9',anchor='lm')
    # primary action
    rr(d,(44,910,1036,1035),34,PRIMARY)
    centered(d,(44,910,1036,1035),'申请请假',34,'#FFFFFF',True)
    # section
    txt(d,(48,1110),'申请记录',39,TEXT,bold=True,anchor='lm')
    txt(d,(1018,1110),'查看全部',26,PRIMARY,anchor='rm')
    # record 1
    rr(d,(44,1160,1036,1455),34,SURFACE,outline=LINE,width=2)
    txt(d,(84,1212),'事假 · 个人事务',34,TEXT,bold=True,anchor='lm')
    rr(d,(846,1193,988,1252),30,'#E5F4EE')
    centered(d,(846,1193,988,1252),'待销假',23,SUCCESS,True)
    txt(d,(84,1280),'11月24日 08:00  至  11月25日 20:00',27,MUTED,anchor='lm')
    txt(d,(84,1340),'2 天',28,TEXT,bold=True,anchor='lm')
    txt(d,(84,1394),'回家处理个人事务',25,MUTED,anchor='lm')
    txt(d,(966,1394),'申请销假',26,PRIMARY,bold=True,anchor='rm')
    # record 2
    rr(d,(44,1485,1036,1768),34,SURFACE,outline=LINE,width=2)
    txt(d,(84,1537),'病假',34,TEXT,bold=True,anchor='lm')
    rr(d,(866,1518,988,1577),30,'#EEF0F4')
    centered(d,(866,1518,988,1577),'已完成',23,MUTED,True)
    txt(d,(84,1605),'10月18日 14:00  至  10月19日 14:00',27,MUTED,anchor='lm')
    txt(d,(84,1665),'1 天',28,TEXT,bold=True,anchor='lm')
    txt(d,(84,1719),'身体不适，已提交材料',25,MUTED,anchor='lm')
    # explanation
    rr(d,(44,1815,1036,2070),34,'#F0F3F9')
    txt(d,(84,1864),'当前可查看历史记录',30,TEXT,bold=True,anchor='lm')
    txt(d,(84,1920),'重新连接学校服务后，才能提交新的申请或销假。',26,MUTED,anchor='lm')
    txt(d,(84,1974),'页面不会弹出英文错误，也不会清空已有内容。',26,MUTED,anchor='lm')
    d.rectangle((0,2328,W,H),fill=SURFACE)
    gesture_bar(d)
    im.save(OUT/'mockup-leave-stale.png')

def contact_sheet():
    paths=[('首页',OUT/'mockup-home.png'),('周课表',OUT/'mockup-schedule-week.png'),('日课表',OUT/'mockup-schedule-day.png'),('请销假异常状态',OUT/'mockup-leave-stale.png')]
    thumb_w,thumb_h=405,900; gap=22; top=110
    canvas=Image.new('RGB',(gap+4*(thumb_w+gap),top+thumb_h+50),'#E9ECF2')
    dd=ImageDraw.Draw(canvas)
    txt(dd,(26,45),'民大助手 · 界面改版效果图 V1',34,TEXT,bold=True,anchor='lm')
    for i,(label,p) in enumerate(paths):
        x=gap+i*(thumb_w+gap)
        rr(dd,(x,top-50,x+thumb_w,top+thumb_h+8),24,SURFACE)
        centered(dd,(x,top-48,x+thumb_w,top-4),label,23,MUTED,True)
        im=Image.open(p).resize((thumb_w,thumb_h),Image.Resampling.LANCZOS)
        canvas.paste(im,(x,top))
    canvas.save(OUT/'mockup-board-v1.png')

def nav_options_sheet():
    items=[('固定底栏（建议默认）',OUT/'mockup-home.png'),('浮动底栏（仍预留空间）',OUT/'mockup-home-floating.png')]
    tw,th=540,1200; gap=28; top=120
    canvas=Image.new('RGB',(gap+2*(tw+gap),top+th+54),'#E9ECF2')
    dd=ImageDraw.Draw(canvas)
    txt(dd,(28,46),'底部导航样式选择',34,TEXT,bold=True,anchor='lm')
    for i,(label,p) in enumerate(items):
        x=gap+i*(tw+gap)
        rr(dd,(x,top-52,x+tw,top+th+10),28,SURFACE)
        centered(dd,(x,top-50,x+tw,top-4),label,25,MUTED,True)
        shot=Image.open(p).resize((tw,th),Image.Resampling.LANCZOS)
        canvas.paste(shot,(x,top))
    canvas.save(OUT/'mockup-bottom-nav-options.png')

if __name__=='__main__':
    draw_home(); draw_home('floating','mockup-home-floating.png')
    draw_week(); draw_day(); draw_leave(); contact_sheet(); nav_options_sheet()
    for p in sorted(OUT.glob('mockup-*.png')):
        print(p.name, Image.open(p).size)
