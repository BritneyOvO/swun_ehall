from PIL import Image, ImageDraw
from pathlib import Path
import generate_mockups as b

OUT=Path(__file__).resolve().parent
W,H=b.W,b.H
BG='#F7F7F8'; SURFACE='#FFFFFF'; TEXT='#171719'; MUTED='#77777F'; LINE='#E5E5E8'
RED='#9B1B30'; RED_DARK='#781326'; RED_LIGHT='#F4E9EB'; WARN='#8A5A00'; WARN_BG='#FBF3DF'

def canvas(): return Image.new('RGB',(W,H),BG)
def rr(d,box,r,fill,outline=None,width=1): d.rounded_rectangle(box,radius=r,fill=fill,outline=outline,width=width)
def txt(d,xy,v,size,color=TEXT,bold=False,anchor='la'): b.txt(d,xy,v,size,color,bold,anchor)
def centered(d,box,v,size,color=TEXT,bold=False): b.centered(d,box,v,size,color,bold)

def bottom_nav(d,selected):
    top=2168; bottom=2334
    d.rectangle((0,top,W,bottom),fill=SURFACE)
    d.line((0,top,W,top),fill=LINE,width=2)
    items=[('home','首页'),('calendar','课表'),('doc','成绩'),('person','我的')]
    centers=[135,405,675,945]
    for i,(ic,label) in enumerate(items):
        on=i==selected
        if on: d.rounded_rectangle((centers[i]-28,top+4,centers[i]+28,top+10),radius=3,fill=RED)
        b.icon(d,ic,centers[i],top+58,43,RED if on else '#777982',stroke=5,fill=on and ic=='home')
        txt(d,(centers[i],top+126),label,26,RED if on else '#666872',on,'mm')
    d.rectangle((0,bottom,W,H),fill=SURFACE); b.gesture_bar(d)

def app_header(d,title,back=False,refresh=False,list_action=False):
    if back:
        b.icon(d,'arrow_left',72,155,52,TEXT,stroke=5)
        txt(d,(130,155),title,44,TEXT,True,'lm')
    else: txt(d,(48,155),title,46,TEXT,True,'lm')
    if list_action: txt(d,(900,155),'列表',28,RED,False,'rm')
    if refresh: b.icon(d,'refresh',997,155,43,TEXT,stroke=5)

def draw_home():
    im=canvas(); d=ImageDraw.Draw(im); b.status_bar(d)
    txt(d,(48,145),'民大助手',46,TEXT,True,'lm')
    txt(d,(50,204),'周四 · 第 13 周',26,MUTED,False,'lm')
    rr(d,(938,112,1018,192),40,RED_LIGHT); txt(d,(978,152),'李',32,RED,True,'mm')
    # next class: restrained, content-first
    rr(d,(44,250,1036,512),24,SURFACE,outline=LINE,width=2)
    d.rounded_rectangle((44,250,54,512),radius=5,fill=RED)
    txt(d,(84,294),'下一节课',25,RED,True,'lm')
    txt(d,(926,294),'19:00',29,RED,True,'rm')
    txt(d,(84,361),'无线网络与移动计算',42,TEXT,True,'lm')
    txt(d,(84,424),'19:00—20:35   H-206   陈曦',27,MUTED,False,'lm')
    txt(d,(950,420),'查看课表',25,RED,False,'rm'); b.icon(d,'chev_right',988,420,25,RED,stroke=4)
    # today list
    txt(d,(48,585),'今日课程',36,TEXT,True,'lm'); txt(d,(1018,585),'2 节',25,MUTED,False,'rm')
    rr(d,(44,630,1036,930),22,SURFACE,outline=LINE,width=2)
    for y,time,end,title,sub,next_ in [
        (702,'13:00','14:35','数字通信原理及协议','BW-106 · 李成杰',False),
        (838,'19:00','20:35','无线网络与移动计算','H-206 · 陈曦',True)]:
        txt(d,(82,y),time,29,RED if next_ else TEXT,next_,'lm'); txt(d,(82,y+38),end,21,MUTED,False,'lm')
        txt(d,(225,y),title,32,TEXT,True,'lm'); txt(d,(225,y+50),sub,25,MUTED,False,'lm')
        if next_: txt(d,(962,y+8),'下一节',23,RED,True,'rm')
    d.line((225,780,990,780),fill=LINE,width=2)
    # quick actions, monochrome
    txt(d,(48,1010),'常用功能',36,TEXT,True,'lm'); txt(d,(965,1010),'全部',25,RED,False,'rm'); b.icon(d,'chev_right',997,1010,24,RED,stroke=4)
    rr(d,(44,1055,1036,1288),22,SURFACE,outline=LINE,width=2)
    actions=[('wallet','一卡通'),('check','课堂考勤'),('exam','考试'),('leave','请假销假')]
    for i,(ic,label) in enumerate(actions):
        cx=168+i*248
        b.icon(d,ic,cx,1135,49,RED,stroke=4)
        txt(d,(cx,1212),label,27,TEXT,False,'mm')
        if i<3: d.line((292+i*248,1095,292+i*248,1248),fill=LINE,width=2)
    # grouped services as rows
    txt(d,(48,1370),'校园服务',36,TEXT,True,'lm')
    rr(d,(44,1415,1036,1792),22,SURFACE,outline=LINE,width=2)
    rows=[('教务服务','学分、选课、校历','doc'),('生活缴费','宿舍电费、热水、一卡通充值','wallet'),('校园出行','校车时刻与站点','bus')]
    for i,(title,sub,ic) in enumerate(rows):
        y=1480+i*118
        b.icon(d,ic,92,y,39,RED,stroke=4)
        txt(d,(140,y-12),title,29,TEXT,True,'lm'); txt(d,(140,y+30),sub,23,MUTED,False,'lm')
        b.icon(d,'chev_right',980,y,24,'#A1A1A8',stroke=4)
        if i<2: d.line((140,y+63,992,y+63),fill=LINE,width=2)
    # one understated reminder
    txt(d,(48,1870),'待办',36,TEXT,True,'lm')
    rr(d,(44,1915,1036,2080),22,SURFACE,outline=LINE,width=2)
    txt(d,(82,1964),'网络安全基础考试',30,TEXT,True,'lm')
    txt(d,(82,2016),'11月30日 09:00 · BS-243',24,MUTED,False,'lm')
    txt(d,(984,1976),'4 天后',25,RED,True,'rm')
    bottom_nav(d,0); im.save(OUT/'mockup-v2-home.png')

def wrap(value,size,maxw,maxlines,bold=True): return b.wrap_cjk(value,size,maxw,maxlines,bold)
def course(d,x1,y1,x2,y2,title,room,fill,accent,maxlines=2):
    rr(d,(x1,y1,x2,y2),10,fill)
    d.rectangle((x1,y1,x1+6,y2),fill=accent)
    yy=y1+14
    for line in wrap(title,22,x2-x1-24,maxlines,True):
        txt(d,(x1+14,yy),line,22,TEXT,True,'la'); yy+=31
    if room and yy+22<y2-8: txt(d,(x1+14,yy+3),room,19,'#5F6067',False,'la')

def draw_schedule():
    im=canvas(); d=ImageDraw.Draw(im); b.status_bar(d); app_header(d,'课表',False,True,True)
    # minimal week selector
    b.icon(d,'arrow_left',70,275,38,RED,stroke=5)
    txt(d,(126,272),'第 13 周',37,TEXT,True,'lm')
    b.icon(d,'chev_right',340,275,38,RED,stroke=5)
    txt(d,(1015,272),'本周',25,RED,False,'rm')
    days=[('一','23'),('二','24'),('三','25'),('四','26'),('五','27'),('六','28'),('日','29')]
    gutter=82; col=(W-gutter)/7; head_top=330; grid_top=445; grid_bottom=2168
    for i,(day,date) in enumerate(days):
        cx=gutter+col*(i+.5); active=i==3
        txt(d,(cx,365),day,24,RED if active else MUTED,active,'mm')
        txt(d,(cx,405),date,24,RED if active else MUTED,active,'mm')
        if active: d.rounded_rectangle((cx-28,432,cx+28,437),radius=3,fill=RED)
    d.line((0,grid_top,W,grid_top),fill=LINE,width=2)
    rows=10; rh=(grid_bottom-grid_top)/rows
    times=[('1','8:30','9:15'),('2','9:20','10:05'),('3','10:25','11:10'),('4','11:15','12:00'),('5','14:00','14:45'),('6','14:50','15:35'),('7','15:55','16:40'),('8','16:45','17:30'),('9','19:00','19:45'),('10','19:50','20:35')]
    for r,(n,a,c) in enumerate(times):
        y=grid_top+r*rh; d.line((gutter,y,W,y),fill=LINE,width=2)
        txt(d,(37,y+35),n,27,TEXT,True,'mm'); txt(d,(37,y+75),a,18,MUTED,False,'mm'); txt(d,(37,y+101),c,18,MUTED,False,'mm')
    for i in range(8): d.line((gutter+i*col,grid_top,gutter+i*col,grid_bottom),fill=LINE,width=2)
    def add(day,start,span,title,room,fill,accent,ml=2):
        x1=gutter+(day-1)*col+5; x2=gutter+day*col-5; y1=grid_top+(start-1)*rh+6; y2=grid_top+(start-1+span)*rh-6
        course(d,x1,y1,x2,y2,title,room,fill,accent,ml)
    add(4,1,2,'计算机组成原理','BW-107','#F2E5E8',RED)
    add(6,1,4,'网络安全基础及法律法规','BS-243','#F2E5E8',RED,3)
    add(3,3,2,'计算机组成原理实验','BS-241','#E6F0EB','#4B8A6D',3)
    add(5,3,2,'网络攻防','BS-223','#E7ECF3','#607A9E')
    add(1,5,2,'网络工程学期实训','BS-222','#E6F0EB','#4B8A6D',3)
    add(3,5,2,'数字通信原理及协议','BW-106','#F2ECE1','#A77B3D',3)
    add(1,7,4,'网络工程学期实训（网络开发）','BS-217','#E5EFEF','#4D8785',3)
    add(4,9,2,'无线网络与移动计算','H-206','#F2E5E8',RED,3)
    add(5,9,2,'网络攻防','BS-223','#E7ECF3','#607A9E')
    y=grid_top+5.55*rh; d.line((gutter+3*col,y,gutter+4*col,y),fill=RED,width=3); d.ellipse((gutter+3*col-6,y-6,gutter+3*col+6,y+6),fill=RED)
    bottom_nav(d,1); im.save(OUT/'mockup-v2-schedule.png')

def draw_leave():
    im=canvas(); d=ImageDraw.Draw(im); b.status_bar(d); app_header(d,'请假与销假',True,True)
    # slim inline notice
    d.rectangle((0,225,W,325),fill=WARN_BG)
    txt(d,(52,263),'更新失败，正在显示上次数据',27,WARN,True,'lm')
    txt(d,(52,301),'上次更新：今天 10:42',21,'#8D7440',False,'lm')
    txt(d,(1018,276),'重试',25,WARN,True,'rm')
    # compact summary
    rr(d,(44,365,1036,555),22,SURFACE,outline=LINE,width=2)
    for i,(v,l) in enumerate([('2','请假次数'),('4 天','累计天数'),('1','待销假')]):
        cx=208+i*332; txt(d,(cx,428),v,40,TEXT,True,'mm'); txt(d,(cx,492),l,24,MUTED,False,'mm')
        if i<2: d.line((374+i*332,400,374+i*332,520),fill=LINE,width=2)
    # student information as one row
    rr(d,(44,590,1036,750),22,SURFACE,outline=LINE,width=2)
    rr(d,(80,624,160,704),40,RED_LIGHT); txt(d,(120,664),'李',31,RED,True,'mm')
    txt(d,(194,632),'李阳',32,TEXT,True,'lm'); txt(d,(194,684),'202430809152 · 计算机科学与工程学院',24,MUTED,False,'lm')
    b.icon(d,'chev_right',988,670,24,'#A1A1A8',stroke=4)
    rr(d,(44,790,1036,905),20,RED)
    centered(d,(44,790,1036,905),'申请请假',32,'#FFFFFF',True)
    txt(d,(48,990),'申请记录',36,TEXT,True,'lm')
    # one list container, not card-per-item
    rr(d,(44,1035,1036,1595),22,SURFACE,outline=LINE,width=2)
    txt(d,(82,1090),'事假 · 个人事务',31,TEXT,True,'lm'); txt(d,(970,1090),'待销假',24,RED,True,'rm')
    txt(d,(82,1148),'11月24日 08:00 至 11月25日 20:00',25,MUTED,False,'lm')
    txt(d,(82,1205),'2 天 · 回家处理个人事务',25,TEXT,False,'lm'); txt(d,(970,1205),'申请销假',24,RED,True,'rm')
    d.line((82,1280,998,1280),fill=LINE,width=2)
    txt(d,(82,1340),'病假',31,TEXT,True,'lm'); txt(d,(970,1340),'已完成',24,MUTED,True,'rm')
    txt(d,(82,1398),'10月18日 14:00 至 10月19日 14:00',25,MUTED,False,'lm')
    txt(d,(82,1455),'1 天 · 身体不适，已提交材料',25,TEXT,False,'lm')
    txt(d,(48,1680),'当前可以查看历史记录；提交操作将在重新连接后恢复。',24,MUTED,False,'lm')
    d.rectangle((0,2328,W,H),fill=SURFACE); b.gesture_bar(d); im.save(OUT/'mockup-v2-leave.png')

def board():
    paths=[('首页',OUT/'mockup-v2-home.png'),('周课表',OUT/'mockup-v2-schedule.png'),('请销假异常状态',OUT/'mockup-v2-leave.png')]
    tw,th=405,900; gap=24; top=112
    out=Image.new('RGB',(gap+3*(tw+gap),top+th+48),'#ECEDEF'); d=ImageDraw.Draw(out)
    txt(d,(26,42),'V2 · 原生校园工具风格',34,TEXT,True,'lm')
    txt(d,(26,80),'少装饰、少卡片、单一品牌色、信息优先',22,MUTED,False,'lm')
    for i,(label,p) in enumerate(paths):
        x=gap+i*(tw+gap); rr(d,(x,top-44,x+tw,top+th+8),20,SURFACE); centered(d,(x,top-42,x+tw,top-4),label,23,MUTED,True)
        shot=Image.open(p).resize((tw,th),Image.Resampling.LANCZOS); out.paste(shot,(x,top))
    out.save(OUT/'mockup-board-v2.png')

if __name__=='__main__':
    draw_home(); draw_schedule(); draw_leave(); board()
    for p in sorted(OUT.glob('mockup-v2-*.png')): print(p.name,Image.open(p).size)
