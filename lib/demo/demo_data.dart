import '../models/lesson.dart';

const demoSchedule = [
  {'xqj': 1, 'skjc': 5, 'cxjc': 2, 'jcs': '5-6', 'kcmc': '网络工程学期实训 (网络仿真)', 'cdmc': 'BS-222', 'xm': '徐小琼', 'skzc': '1111111111111111'},
  {'xqj': 1, 'skjc': 7, 'cxjc': 4, 'jcs': '7-10', 'kcmc': '网络工程学期实训(网络开发)(上)', 'cdmc': 'BS-243', 'xm': '梅林', 'skzc': '1111111111111111'},
  {'xqj': 2, 'skjc': 3, 'cxjc': 2, 'jcs': '3-4', 'kcmc': '形势与政策（五）', 'cdmc': 'BX-317', 'xm': '吴音萃', 'skzc': '0000111100000000'},
  {'xqj': 3, 'skjc': 3, 'cxjc': 2, 'jcs': '3-4', 'kcmc': '网络攻防', 'cdmc': 'BS-223', 'xm': '陈浩', 'skzc': '1111111111111111'},
  {'xqj': 3, 'skjc': 5, 'cxjc': 2, 'jcs': '5-6', 'kcmc': '数字通信原理及协议', 'cdmc': 'BW-106', 'xm': '李成杰', 'skzc': '1111111111111111'},
  {'xqj': 3, 'skjc': 3, 'cxjc': 2, 'jcs': '3-4', 'kcmc': '计算机组成原理实验', 'cdmc': 'BS-241', 'xm': '姜玥', 'skzc': '0000000011111111'},
  {'xqj': 4, 'skjc': 1, 'cxjc': 4, 'jcs': '1-4', 'kcmc': '计算机组成原理', 'cdmc': 'BW-107', 'xm': '姜玥', 'skzc': '1111111100000000'},
  {'xqj': 4, 'skjc': 1, 'cxjc': 2, 'jcs': '1-2', 'kcmc': '计算机组成原理', 'cdmc': 'BW-107', 'xm': '姜玥', 'skzc': '0000000011111111'},
  {'xqj': 4, 'skjc': 9, 'cxjc': 2, 'jcs': '9-10', 'kcmc': '无线网络与移动计算', 'cdmc': 'H-206', 'xm': '陈建英', 'skzc': '1111111111111111'},
  {'xqj': 5, 'skjc': 9, 'cxjc': 2, 'jcs': '9-10', 'kcmc': '网络攻防', 'cdmc': 'BS-223', 'xm': '陈浩', 'skzc': '1111111111111111'},
];

const demoGrades = [
  {'kcmc': '高等数学Ⅰ（上）', 'xf': '4.5', 'bfzcj': '89.6', 'jd': '3.70', 'kclbmc': '学科基础课', 'xnm': '2024', 'xqm': '3'},
  {'kcmc': '大学英语I(一)', 'xf': '2.0', 'bfzcj': '79.5', 'jd': '3.00', 'kclbmc': '公共基础课', 'xnm': '2024', 'xqm': '3'},
  {'kcmc': '大学物理实验Ⅳ', 'xf': '0.5', 'bfzcj': '87.4', 'jd': '3.70', 'kclbmc': '学科基础课', 'xnm': '2024', 'xqm': '12'},
  {'kcmc': '马克思主义基本原理', 'xf': '3.0', 'bfzcj': '87.4', 'jd': '3.70', 'kclbmc': '公共基础课', 'xnm': '2025', 'xqm': '3'},
  {'kcmc': '计算机科学与技术引论', 'xf': '1.5', 'bfzcj': '92.7', 'jd': '4.00', 'kclbmc': '专业选修课', 'xnm': '2025', 'xqm': '12'},
  {'kcmc': '计算机组成原理', 'xf': '3.5', 'bfzcj': '88.0', 'jd': '3.70', 'kclbmc': '专业必修课', 'xnm': '2026', 'xqm': '3'},
];

const demoExams = [
  {'kcmc': '数字通信原理及协议', 'kssj': '第16周 周三 14:00-16:00', 'cdmc': 'BW-106', 'zwh': '12'},
  {'kcmc': '计算机组成原理', 'kssj': '第17周 周一 09:00-11:00', 'cdmc': 'BW-107', 'zwh': '08'},
];

const demoXkRounds = [
  {'kklxdm': '01', 'kklxmc': '主修课程', 'xkkz_id': 'demo-01', 'njdm_id': '2024', 'zyh_id': '1106', 'xkkz_xh': 'demo'},
  {'kklxdm': '10', 'kklxmc': '通识选修课', 'xkkz_id': 'demo-10', 'njdm_id': '2024', 'zyh_id': '1106', 'xkkz_xh': 'demo'},
];

const demoXkCourses = [
  {
    'kch_id': '56050556', 'kcmc': '网络舆情智能分析', 'xf': '2.0', 'jxb_id': 'demo-jxb-1',
    'do_jxb_id': 'demo-do-1',
    'jxbmc': '(2026-2027-1)-56050556-01', 'yxzrs': '46', 'jxbrl': '50', 'jxbzls': '1',
    'jsmc': '王老师', 'sksj': '星期三第5-6节{1-16周}',
  },
  {
    'kch_id': '56050548', 'kcmc': '网络工程学期实训(网络开发)(上)', 'xf': '2.0', 'jxb_id': 'demo-jxb-2',
    'jxbmc': '(2026-2027-1)-56050548-01', 'yxzrs': '50', 'jxbrl': '50', 'jxbzls': '1',
    'jsmc': '李老师', 'sksj': '星期四第3-4节{1-16周}',
  },
  {
    'kch_id': '11180500', 'kcmc': '专业英语（网络工程）', 'xf': '2.0', 'jxb_id': 'demo-jxb-3',
    'jxbmc': '(2026-2027-1)-11180500-01', 'yxzrs': '44', 'jxbrl': '54', 'jxbzls': '1',
    'jsmc': '张老师', 'sksj': '星期五第3-4节{1-16周}',
  },
];

const demoXkProfile = <String, String>{
  'xkxnm': '2026', 'xkxqm': '3', 'njdm_id': '2024', 'zyh_id': '1106', 'xqh_id': '2',
};

const demoProfile = {
  'name': '预览同学',
  'studentId': '202430000000',
  'gender': '男',
  'college': '计算机科学与工程学院',
  'major': '网络工程',
  'klass': '网络工程2024-1班',
  'grade': '2024',
  'phone': '13800000000',
  'campus': '武侯校区',
  'role': '学生',
};

const demoYktBills = [
  {'title': '学生食堂', 'time': '2026-09-07 12:31', 'amountYuan': -8.0, 'balanceYuan': 10.7},
  {'title': '校园超市', 'time': '2026-09-07 18:04', 'amountYuan': -2.5, 'balanceYuan': 18.7},
  {'title': '圈存转入', 'time': '2026-09-06 09:12', 'amountYuan': 50.0, 'balanceYuan': 21.2},
];

const demoClock = {
  'status': {
    'code': 0,
    'msg': 'success',
    'backMap': {'showImgUpload': false, 'isNeedClock': true, 'isClock': false},
  },
  'schedule': {
    'code': 0,
    'list': [
      {
        'id': 'demo-task',
        'name': '在校打卡-平常',
        'startTime': '21:30:00',
        'endTime': '23:25:00',
        'checked': false,
        'isOpen': true,
      },
    ],
  },
  'positions': {
    'code': 0,
    'list': [
      {'indexCode': 1, 'lng': '103.97048', 'lat': '30.58120'},
    ],
  },
  'records': {
    'code': 0,
    'page': {
      'list': [
        {'clockTime': '昨天 22:01', 'address': '武侯校区', 'clockStatus': '正常'},
      ],
    },
  },
};

const demoKtkq = {
  'code': 200,
  '_meta': {
    'xnxqdm': '2026-2027-1',
    'skzc': 1,
    'schoolTime': {'xnxqmc': '2026-2027学年 秋季学期', 'todayWeekNum': 1, 'todayWeekDay': 1},
  },
  'todayWeekDay': 1,
  'data': [
    {
      'kcm': '网络工程学期实训 (网络仿真)',
      'kch': '56050553',
      'xf': '1.0',
      'xs': '32',
      'list': [
        {
          'jxbmc': '(2026-2027-1)-56050553-01',
          'sksj': '周一 14:00~15:35',
          'jasmc': 'BS-222',
          'ksjc': '5',
          'jsjc': '6',
          'skxq': 1,
          'jxbid': 'demo-jxb-1',
          'jxblx': 'THEORY',
          'kbid': 'demo-kb-1',
        },
      ],
    },
    {
      'kcm': '数字通信原理及协议',
      'kch': '56040248',
      'xf': '3.0',
      'xs': '48',
      'list': [
        {
          'jxbmc': '(2026-2027-1)-56040248-01',
          'sksj': '周二 14:00~15:35',
          'jasmc': 'BW-106',
          'ksjc': '5',
          'jsjc': '6',
          'skxq': 2,
          'jxbid': 'demo-jxb-2',
          'jxblx': 'THEORY',
          'kbid': 'demo-kb-2',
        },
      ],
    },
  ],
  'today': [
    {
      'courseName': '网络工程学期实训 (网络仿真)',
      'courseCode': '56050553',
      'teacher': '张老师',
      'classroom': 'BS-222',
      'timeText': '周一 14:00~15:35  第 5-6 节',
      'teachClassId': 'demo-jxb-1',
      'teachClassType': 'THEORY',
      'scheduleId': 'demo-kb-1',
      'week': 1,
      'weekDay': 1,
      'startNode': 5,
      'endNode': 6,
      'status': 'pending_signin',
      'message': '待签到',
      'activityId': 'demo-act-1',
      'signType': 'NUMBER',
      'signCode': '',
      'activities': [
        {
          'activityId': 'demo-act-1',
          'title': '第1周课堂签到',
          'signType': 'NUMBER',
          'status': 'pending_signin',
          'message': '待签到',
          'startTime': '14:00',
          'endTime': '15:35',
          'signCode': '',
        },
      ],
    },
    {
      'courseName': '数字通信原理及协议',
      'courseCode': '56040248',
      'teacher': '李老师',
      'classroom': 'BW-106',
      'timeText': '周二 14:00~15:35  第 5-6 节',
      'teachClassId': 'demo-jxb-2',
      'teachClassType': 'THEORY',
      'scheduleId': 'demo-kb-2',
      'week': 1,
      'weekDay': 2,
      'startNode': 5,
      'endNode': 6,
      'status': 'already_signed',
      'message': '已签到',
      'activities': [
        {
          'activityId': 'demo-act-2',
          'title': '第1周课堂签到',
          'signType': 'LOCATION',
          'status': 'already_signed',
          'message': '已签到',
          'startTime': '14:00',
          'endTime': '15:35',
          'signCode': '',
        },
      ],
    },
  ],
  'history': [
    {'time': '昨天 15:10', 'course': '网络攻防', 'status': '正常', 'place': 'BS-223'},
    {'time': '周一 14:18', 'course': '数字通信原理及协议', 'status': '已签到', 'place': 'BW-106'},
  ],
};

String _demoNorm(String s) =>
    s.toLowerCase().replaceAll(RegExp(r'\s+'), '').replaceAll('（', '(').replaceAll('）', ')');

Map<String, dynamic> demoKtkqSign(Lesson lesson) {
  final today = demoKtkq['today'];
  if (today is List) {
    for (final raw in today) {
      if (raw is! Map) continue;
      final name = '${raw['courseName'] ?? ''}';
      final a = _demoNorm(name);
      final b = _demoNorm(lesson.name);
      if (a.isEmpty || b.isEmpty) continue;
      if (a != b && !a.contains(b) && !b.contains(a)) continue;
      return {
        ...Map<String, dynamic>.from(raw),
        'activities': [
          for (final x in (raw['activities'] as List? ?? const []))
            if (x is Map) Map<String, dynamic>.from(x),
        ],
        'history': [
          for (final h in demoKtkq['history'] as List)
            if (h is Map && '${h['course']}' == name) Map<String, dynamic>.from(h),
        ],
      };
    }
  }
  return {
    'courseName': lesson.name,
    'classroom': lesson.room,
    'teacher': lesson.teacher,
    'timeText': '${kWeekdayLabels[lesson.weekday]}  ${lesson.periodLabel}',
    'week': 1,
    'weekDay': lesson.weekday,
    'startNode': lesson.start,
    'endNode': lesson.end,
    'status': 'no_activity',
    'message': '无签到活动',
    'activities': <Map<String, dynamic>>[],
    'history': <Map<String, dynamic>>[],
  };
}

const demoVenues = [
  {
    'id': 'demo-badminton-1',
    'placeName': '1号场',
    'placeType': '羽毛球',
    'campusName': '航空港校区',
    'openTime': '08:00',
    'closeTime': '21:00',
    'taken': ['10:00', '14:00', '19:00'],
  },
  {
    'id': 'demo-badminton-2',
    'placeName': '2号场',
    'placeType': '羽毛球',
    'campusName': '航空港校区',
    'openTime': '08:00',
    'closeTime': '21:00',
    'taken': ['09:00', '15:00'],
  },
  {
    'id': 'demo-badminton-3',
    'placeName': '3号场',
    'placeType': '羽毛球',
    'campusName': '航空港校区',
    'openTime': '08:00',
    'closeTime': '21:00',
    'taken': ['18:00', '20:00'],
  },
  {
    'id': 'demo-badminton-4',
    'placeName': '4号场',
    'placeType': '羽毛球',
    'campusName': '武侯校区',
    'openTime': '09:00',
    'closeTime': '21:00',
    'taken': ['11:00'],
  },
  {
    'id': 'demo-basket-1',
    'placeName': '室内1号场',
    'placeType': '篮球',
    'campusName': '武侯校区',
    'openTime': '08:00',
    'closeTime': '21:00',
    'taken': ['16:00', '17:00'],
  },
  {
    'id': 'demo-pingpong-1',
    'placeName': '1号台',
    'placeType': '乒乓球',
    'campusName': '航空港校区',
    'openTime': '08:00',
    'closeTime': '21:00',
    'taken': ['12:00'],
  },
];

const demoVenueBookings = [
  {
    'id': 'demo-b1',
    'placeName': '2号场',
    'placeType': '羽毛球',
    'campusName': '航空港校区',
    'bookDate': '今天',
    'startTime': '19:00',
    'endTime': '20:00',
    'status': '已预约',
  },
];
