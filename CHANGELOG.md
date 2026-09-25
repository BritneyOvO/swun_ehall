# 更新说明 | Changelog

## 1.0.7

### 更新内容

- 公寓打卡：卡片下方显示地图，标出打卡范围和当前位置。
- 课堂签到：按教室标出签到范围和中心点，地图对准中心。
- 课表：课程名尽量完整显示，颜色更亮，老师和教室紧挨课程名。
- 加载：功能页加载时只显示转圈；一卡通付款码出来就显示整页。
- 更新：发现新版本时弹窗显示更新说明，并可下载安装。
- 开屏：只显示图标，下面不再写「民大助手」。
- 深色模式底栏颜色更贴近页面。

### What's New

- Apartment punch shows a map with the punch area and your location.
- Classroom sign-in shows the room's sign area and its center, with the map zoomed on that point.
- Timetable course names wrap to fit, in brighter colors, with teacher and room under the name.
- Feature pages show only a spinner while loading; the campus card appears as soon as the payment QR is ready.
- A new release opens a dialog with the notes and a download.
- Splash shows only the icon, without the words 民大助手 underneath.
- The dark-mode bottom bar sits closer to the page color.

## 1.0.6

### 更新内容

- 课表：同一时间多门课选定后所有周都用这门；老师按上课周分开；教室名称尽量完整显示。
- 首页：已经下课的今日课程不再列出。
- 课堂考勤：按课程分组，点进签到再查活动；登录被踢回统一身份时改走页面登录。
- 我的：自动显示办事大厅头像，点头像可自定义或取消自定义。
- 更新：只查最新发行版，下载在通知栏进行，完成后可安装。
- 预测性返回跟手放慢，后面不再露出开屏图标。

### What's New

- Timetable: a chosen overlapping course applies to every week; teachers follow the week range; room names stay visible.
- Home: finished classes drop off today's list.
- Classroom attendance: group by course and probe the sign activity after opening it; if CAS bounces the ticket, fall back to the page login.
- Mine: show the campus-hall avatar, and tap it to set or clear a custom photo.
- Updates: check only the latest release and download in a notification, then install.
- Slow the predictive-back gesture so the splash icon no longer shows behind it.

## 1.0.5

### 更新内容

- 更新：关于页检查到新版本后应用内下载 APK 并安装，不再跳转浏览器。
- 课堂签到：连续采集高德定位、丢掉缓存点；活动时间按东八区显示。
- 一卡通：余额不再等明细；明细标题改为「今日余额使用明细」。
- 我的：头像可从相册选择并裁剪；不再使用蓝图 headImage。
- 崩溃 / ANR 上报到 Bugly，便于定位无响应问题。

### What's New

- In-app APK download and install from About when a new release is found.
- Classroom check-in: sample Amap continuously instead of the first cached fix; show activity times in UTC+8.
- Campus card: show balance without waiting for the ledger; title is “today’s transactions”.
- Mine: pick and crop a custom avatar; stop using Lantu headImage.
- Report crashes and ANRs to Bugly.

## 1.0.4

### 更新内容

- 课堂签到：GPS 转成校方围栏用的 GCJ-02，优先高德并在提交前刷新定位，减少人在教室却提示位置不对。
- 提示：签到、选课、复制等改为短 toast，不再挡住页面。
- 关于：启动时检查更新开关收到关于页。

### What's New

- Classroom check-in: convert GPS to GCJ-02, prefer Amap, and refresh the fix before punch so in-room signs fail less often.
- Prompts use short toasts instead of blocking the page.
- Move the launch update-check toggle into About.

## 1.0.3

### 更新内容

- 课堂签到：CAS 换票后同步课堂考勤会话，避免「认证失败」。
- 选课：已满按官网容量判断；已选课与官网已选列表对齐。
- 登录：已有账号只填充学号密码；蓝图已登录时自动补统一身份。
- 我的 / 首页：去掉重复功能入口和预约场馆。
- 教室位置：签到成功后本机保存。
- 开发者模式：关于页连点图标开启。

### What's New

- Classroom attendance: sync the ktkq session after a CAS ticket so sign-in no longer fails with auth errors.
- Course selection: treat full classes by official capacity; align selected courses with the registrar list.
- Login: saved accounts only fill student id and password; finish CAS when Lantu is already signed in.
- Mine / Home: drop duplicate entries and venue booking.
- Remember classroom coordinates after a successful sign-in.
- Developer mode: tap the About icon repeatedly to enable.

## 1.0.2

### 更新内容

- 自主选课：按官网分页拉课、选上课班级后提交；满员显示「已无余量」，不再弹出一串数字。
- 一卡通：付款码好了就显示；卡片下方单独加载余额使用明细；消费为支出、充值/圈存为收入。
- 课堂考勤：学期接口空返回时按日期落到当前学年学期，避免「未能确定当前学期」。
- 公寓打卡：识别 token 失效并自动重新登录后再请求。

### What's New

- Self-service selection: paginate like the official site, pick a teaching class, then submit; full classes show “no seats left”.
- Campus card: show the payment QR as soon as it is ready; load the ledger under the card; spend vs top-up signs.
- Attendance: if the term API is empty, fall back to the current school year/term.
- Dorm check-in: detect an expired token and log in again before retrying.

## 1.0.1

### 更新内容

- 修复登录 HTTPS 400。
- 个人信息班级显示教务班级名。
- 启动检查更新，关于页可手动检查。
- 登录已存账号改为学号后缀下拉。

### What's New

- Fix HTTPS 400 on login.
- Show the registrar class name on the profile.
- Check for updates on launch; About can check manually.
- Saved accounts use a student-id suffix dropdown.

## 1.0.0

### 更新内容

- 首个公开发布：课表、成绩、学分、考试、课堂考勤、一卡通、场馆预约、公寓打卡。

### What's New

- First public release: schedule, grades, credits, exams, classroom attendance, campus card, venues, and dorm check-in.
