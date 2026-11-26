# 校园功能协议实现

更新日期：2026-11-26。

业务页面使用原生 Compose 表单、列表和确认对话框。隐藏 WebView 只负责校方瑞数挑战、CAS 会话、Cookie 和页面内请求签名，不把官方 H5 作为业务界面嵌入。

## 实现状态

| 功能 | 已实现内容 | 验证方式 |
| --- | --- | --- |
| 一卡通充值 | 账户读取、金额校验、官方订单创建、建行签名参数原样传递 | 响应解析、金额边界和建行 URL 单元测试；真实付款由用户在官方收银台完成 |
| 电费充值 | 校区/电表类型、楼栋、楼层、房间四级目录；下单前官方页面上下文重放 | 目录解析和请求流程检查；不执行真实支付 |
| 热水充值 | 热水账户读取、余额校验、从一卡通余额扣款、二次确认 | 金额边界和响应解析测试；不执行真实扣款 |
| 请假/销假 | 学工 CAS、汇总、记录、详情、审批轨迹、申请、行政区划、附件、定位、自拍和销假 | JSON 解析、请求字段和响应信封单元测试；仅做只读真机验证 |
| 校车 | 三校区线路、方向、工作日/周末/节假日、下一班倒计时 | 官方后勤时刻表解析与时间边界测试 |
| 校历 | 学期起止、月视图、教学日、校历事件 | 官方教务 HTML/脚本解析及显示验证 |
| 退出登录 | 独立确认入口、会话清理、保留已保存账号 | 真机退出、再次登录和账号隔离验证 |
| 历史账单 | 原生日期选择、流水查询 | `fun_bill.do` 日期参数和账单解析测试 |
| 动态付款码 | 页面可见时周期刷新、过期和失败状态 | 生命周期与二维码刷新请求验证 |
| 课表列表与缓存 | 网格/列表切换、按账号和学期持久化、离线恢复 | 缓存损坏、账号切换、学期切换和取消写入测试 |

## 一卡通协议

入口页为 `https://ykth5.swun.edu.cn/menu/menu.do?menu=qrcode`。客户端读取页面时间戳后打开：

```text
/menu/function.do?expire={timestamp}&stu_code={studentId}&acco_id={studentId}&school_id={schoolId}&menu={menu}
```

已使用的业务接口：

| 功能 | 请求 |
| --- | --- |
| 流水 | `POST /bill/fun_bill.do`，表单字段 `date=yyyy-MM-dd`，当天为 `date=null` |
| 一卡通充值 | `POST /app/poscardcircle.do`，字段 `payMethod`、`payMoney` |
| 热水充值 | `POST /water/watertransferpay.do`，字段 `payMethod=poscardpay`、`payMoney`、`money` |
| 电费校区 | `POST /app/electric_school.do` |
| 电费楼栋 | `POST /app/electric_building.do`，字段 `dormid` |
| 电费楼层 | `POST /app/electric_floor.do`，字段 `buildingid` |
| 电费房间 | `POST /app/electric_room.do`，字段 `floorid` |
| 电费下单 | `POST /app/poscardpay.do`，字段包含 `payMethod`、`money` 和支付方式同名字段 |

一卡通和电费金额必须大于 0、最多两位小数且不超过 200 元；热水金额上限为 20 元，并且不能超过当前一卡通余额。热水请求成功会立即扣减余额，因此页面在请求前进行二次确认。

旧电费系统把目录选择写入 HTTP 会话。下单前必须依次打开 `school.do`、`building.do`、`floor.do`、`room.do` 和最终 `electric.do` 页面，不能直接携带房间编号跳到下单接口。

建行订单响应中的 `orderInfo` 已包含 `TXCODE`、`MAC` 等签名字段。客户端按原字节顺序拼接到以下地址，不重新编码、解析或排序：

```text
https://ibsbjstar.ccb.com.cn/CCBIS/ccbMain?CCB_IBSVersion=V6&{orderInfo}&QRCODE=1&CHANNEL={channel}
```

创建订单只表示获得官方收银台参数，不表示支付成功。最终结果以一卡通余额、账单或宿舍电表为准。

## 请假与销假协议

CAS service：

```text
https://zxtjfx.swun.edu.cn/warnvisit-api/authority/noToken/casLogin/swun-stu-manage-platform-mobile
```

登录后打开 `/swun-stu-manage-platform-mobile/#/leave/apply` 建立学生平台页面会话。请求统一携带 `zbp-role-no: xs`。

只读接口：

| 数据 | 请求 |
| --- | --- |
| 当前用户 | `GET /warnvisit-api/authority/getUserByToken` |
| 请假汇总 | `GET /warnvisit-api/askLeave/getAskLeaveSummary` |
| 请假记录 | `GET /warnvisit-api/askLeave/getAskLeave?page=1&pageSize=100` |
| 学生信息 | `GET /warnvisit-api/askLeave/getStuInfo` |
| 请假类型 | `GET /warnvisit-api/common/getDim?dimClass=zy_leave_type` |
| 事假类型 | `GET /warnvisit-api/common/getDim?dimClass=swnu_aff_leave_type` |
| 规则配置 | `GET /warnvisit-api/leaveConfig/getRuleConfig` |
| 申请资格 | `GET /warnvisit-api/askLeave/checkTakeLeave` |
| 请假时长 | `GET /warnvisit-api/common/getAskLeaveDays` |
| 申请详情 | `GET /warnvisit-api/askLeave/getAskLeaveDetail?leaveNo=...` |
| 审批轨迹 | `GET /warnvisit-api/askLeave/getApprovalDetail?leaveNo=...` |
| 销假时间范围 | `GET /warnvisit-api/askLeave/getRenewStartEnd?leaveNo=...` |
| 销假时长 | `GET /warnvisit-api/common/getAskLeaveDays`，传入销假时间范围返回的开始与结束时间 |
| 定位校验 | `GET /warnvisit-api/common/getCorrectAddress?latitude=...&longitude=...` |

请假提交使用 `POST /warnvisit-api/askLeave/createTakeLeave`，JSON 字段固定为：

```text
addrDetail, internshipAddr, addrName, addrCode, affLeaveTypeCode,
leaveDays, leaveEndTime, leaveStartTime, leaveReason, prntMobile,
leaveTypeCode, provePics
```

销假提交使用 `POST /warnvisit-api/askLeave/createCancelLeave`，JSON 字段固定为：

```text
elimRemark, leaveNo, leaveStartTime, leaveEndTime, leaveDays,
locaInfo, selfiePics
```

图片通过 `POST /warnvisit-api/common/upload` 上传，multipart 字段名为 `multipartFile`。客户端只接受 JPG/PNG，单张原图上限 10 MB；较大图片在本地缩放和压缩后上传。

当前请假类型为 `1=事假`、`2=病假`。事假附加类型代码为 `1..5`，代码 `5` 需要填写实习地址。行政区划来自本地 `china_area.json`，提交县区六位代码和完整名称。销假页面按照后台 `locaFlag`、`selfieFlag` 动态要求定位校验和现场自拍。

所有提交按钮都有最终确认步骤。开发和验证期间不执行真实充值、支付、请假或销假。

## 数据留存

原始 HTML、JavaScript、带会话响应、网络抓包和真机截图只允许放入 git 忽略的 `captures/`，不得提交认证值、Cookie、票据、学号、手机号或银行订单签名。仓库文档只记录脱敏后的接口、字段和验证结论。
