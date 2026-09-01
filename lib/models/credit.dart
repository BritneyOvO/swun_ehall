class PlanCourse {
  const PlanCourse({
    required this.name,
    required this.credits,
    required this.status,
    this.code = '',
    this.score = '',
    this.term = '',
    this.nature = '',
  });

  final String name;
  final double credits;
  final String status;
  final String code;
  final String score;
  final String term;
  final String nature;

  bool get passed => status == '已修' || status == '校内被替代' || status == '校内替代';
  bool get studying => status == '在修';
  bool get unstudied => status == '未修';
  bool get failed => status == '未过';
}

class CreditBucket {
  const CreditBucket({
    required this.name,
    required this.credits,
    this.required = 0,
    this.failed = 0,
    this.unstudied = 0,
    this.studying = 0,
    this.courses = 0,
    this.failedCourses = 0,
    this.unstudiedCourses = 0,
    this.studyingCourses = 0,
    this.items = const [],
  });

  final String name;

  /// 该方向已修学分（XDZT 已修/替代）.
  final double credits;

  /// 节点 yqzdxf；培养方案未填时为 0.
  final double required;

  /// 未过学分.
  final double failed;

  /// 未修学分.
  final double unstudied;

  /// 在修学分.
  final double studying;
  final int courses;
  final int failedCourses;
  final int unstudiedCourses;
  final int studyingCourses;
  final List<PlanCourse> items;

  double get scope {
    final v = credits + unstudied + studying + failed;
    if (required > 0 && required > v) return required;
    return v;
  }
}

class CreditProgress {
  const CreditProgress({
    this.taken = 0,
    this.required = 0,
    this.earned = 0,
    this.gpa,
    this.planTotal = 0,
    this.planPassed = 0,
    this.planFailed = 0,
    this.planUnstudied = 0,
    this.planStudying = 0,
    this.extraPassed = 0,
    this.extraFailed = 0,
    this.buckets = const [],
  });

  /// 修读总学分 / 共修学分.
  final double taken;

  /// 要求最低学分 / 最低毕业学分.
  final double required;

  /// 获得总学分（方案内）.
  final double earned;
  final double? gpa;
  final int planTotal;
  final int planPassed;
  final int planFailed;
  final int planUnstudied;
  final int planStudying;
  final int extraPassed;
  final int extraFailed;
  final List<CreditBucket> buckets;

  double get remaining {
    if (required <= 0) return 0;
    final d = required - taken;
    return d < 0 ? 0 : d;
  }

  bool get met => required > 0 && taken + 0.0001 >= required;

  String get takenText => formatXf(taken);
  String get requiredText => formatXf(required);
  String get remainingText => formatXf(remaining);
  String get earnedText => formatXf(earned);
}

String formatXf(double v) {
  if (v == v.roundToDouble()) return '${v.toInt()}';
  final s = v.toStringAsFixed(1);
  return s.endsWith('.0') ? s.substring(0, s.length - 2) : s;
}

bool gradePassed(Map<dynamic, dynamic> row) {
  final mark = '${row['cj'] ?? ''}'.trim();
  if (const {'及格', '合格', '优', '良', '中', '通过', 'A', 'B', 'C'}.contains(mark)) return true;
  if (const {'不及格', '不合格', '不通过', 'F'}.contains(mark)) return false;
  final n = double.tryParse('${row['bfzcj'] ?? ''}');
  if (n != null) return n >= 60;
  final jd = double.tryParse('${row['jd'] ?? ''}');
  if (jd != null) return jd > 0;
  return false;
}

List<CreditBucket> creditBucketsOf(Iterable items) {
  final map = <String, CreditBucket>{};
  for (final raw in items) {
    if (raw is! Map) continue;
    final name = '${raw['kclbmc'] ?? '其他'}'.trim();
    if (name.isEmpty) continue;
    final xf = double.tryParse('${raw['xf'] ?? ''}') ?? 0;
    final prev = map[name] ?? CreditBucket(name: name, credits: 0);
    if (gradePassed(raw)) {
      map[name] = CreditBucket(
        name: name,
        credits: prev.credits + xf,
        failed: prev.failed,
        courses: prev.courses + 1,
        failedCourses: prev.failedCourses,
      );
    } else if (xf > 0) {
      map[name] = CreditBucket(
        name: name,
        credits: prev.credits,
        failed: prev.failed + xf,
        courses: prev.courses,
        failedCourses: prev.failedCourses + 1,
      );
    }
  }
  final list = map.values.toList()
    ..sort((a, b) => b.credits.compareTo(a.credits));
  return list;
}

CreditProgress creditProgressFromGrades(Iterable grades) {
  final buckets = creditBucketsOf(grades);
  final taken = buckets.fold<double>(0, (a, b) => a + b.credits);
  var xf = 0.0;
  var jdXf = 0.0;
  var passed = 0;
  var failed = 0;
  for (final raw in grades) {
    if (raw is! Map) continue;
    final credit = double.tryParse('${raw['xf'] ?? ''}') ?? 0;
    final jd = double.tryParse('${raw['jd'] ?? ''}');
    if (gradePassed(raw)) {
      passed++;
      if (jd != null && credit > 0) {
        xf += credit;
        jdXf += jd * credit;
      }
    } else if (credit > 0) {
      failed++;
    }
  }
  return CreditProgress(
    taken: taken,
    gpa: xf > 0 ? jdXf / xf : null,
    planPassed: passed,
    planFailed: failed,
    buckets: buckets.isEmpty && taken > 0 ? [CreditBucket(name: '共修学分', credits: taken)] : buckets,
  );
}

const demoCreditProgress = CreditProgress(
  taken: 15.0,
  required: 160.5,
  earned: 15.0,
  gpa: 3.63,
  planTotal: 29,
  planPassed: 4,
  planUnstudied: 20,
  planStudying: 5,
  extraPassed: 2,
  buckets: [
    CreditBucket(
      name: '学科基础课',
      credits: 5.0,
      unstudied: 4.0,
      courses: 2,
      unstudiedCourses: 1,
      items: [
        PlanCourse(name: '高等数学Ⅰ（上）', credits: 4.5, status: '已修', score: '89.6', term: '2024-2025 1'),
        PlanCourse(name: '大学物理实验Ⅳ', credits: 0.5, status: '已修', score: '87.4', term: '2024-2025 2'),
        PlanCourse(name: '线性代数', credits: 4.0, status: '未修'),
      ],
    ),
    CreditBucket(name: '公共基础课', credits: 5.0, studying: 0.3, courses: 2, studyingCourses: 1),
    CreditBucket(name: '专业必修课', credits: 3.5, studying: 7.5, courses: 1, studyingCourses: 2),
    CreditBucket(name: '专业选修课', credits: 1.5, unstudied: 9.5, courses: 1, unstudiedCourses: 4),
  ],
);

/// 学业情况 getXdzt：1 在修，2 未过，3 未修，4/21 已修，5–10 替代/认定.
String xdztLabel(String xdzt, String maxcj) {
  if (xdzt == '1' || maxcj == '未开放') return '在修';
  switch (xdzt) {
    case '2':
      return '未过';
    case '3':
      return '未修';
    case '4':
    case '21':
      return '已修';
    case '5':
      return '校内被替代';
    case '6':
      return '校内替代';
    case '7':
      return '校内课程替代节点';
    case '8':
      return '校外认定';
    case '9':
      return '学分认定';
    case '10':
      return '预警不审核';
    default:
      return '';
  }
}

bool xdztPassed(String xdzt) => xdzt == '4' || xdzt == '21' || xdzt == '5' || xdzt == '6';

PlanCourse planCourseFromXyqk(Map<dynamic, dynamic> row) {
  final xdzt = '${row['XDZT'] ?? ''}'.trim();
  final maxcj = '${row['MAXCJ'] ?? ''}'.trim();
  final xf = double.tryParse('${row['XF'] ?? ''}') ?? 0;
  final xn = '${row['XNMC'] ?? ''}'.trim();
  final xq = '${row['XQMMC'] ?? ''}'.trim();
  final jyxn = '${row['JYXDXNMC'] ?? ''}'.trim();
  final jyxq = '${row['JYXDXQMC'] ?? ''}'.trim();
  var term = [xn, xq].where((s) => s.isNotEmpty).join(' ');
  if (term.isEmpty) {
    final hint = [jyxn, jyxq].where((s) => s.isNotEmpty).join(' ');
    if (hint.isNotEmpty) term = '建议 $hint';
  }
  final cj = '${row['CJ'] ?? ''}'.trim();
  return PlanCourse(
    name: '${row['KCMC'] ?? ''}'.trim(),
    credits: xf,
    status: xdztLabel(xdzt, maxcj),
    code: '${row['KCH'] ?? ''}'.trim(),
    score: cj.isNotEmpty ? cj : (maxcj.isNotEmpty && maxcj != '未开放' ? maxcj : ''),
    term: term,
    nature: '${row['KCXZMC'] ?? row['KCLBMC'] ?? ''}'.trim(),
  );
}

CreditBucket bucketFromXyqk({
  required String name,
  required double yxxf,
  required double yqzdxf,
  required List<PlanCourse> items,
}) {
  var passedXf = 0.0;
  var failedXf = 0.0;
  var unstudiedXf = 0.0;
  var studyingXf = 0.0;
  var passedN = 0;
  var failedN = 0;
  var unstudiedN = 0;
  var studyingN = 0;
  for (final c in items) {
    if (c.passed) {
      passedXf += c.credits;
      passedN++;
    } else if (c.failed) {
      failedXf += c.credits;
      failedN++;
    } else if (c.unstudied) {
      unstudiedXf += c.credits;
      unstudiedN++;
    } else if (c.studying) {
      studyingXf += c.credits;
      studyingN++;
    }
  }
  return CreditBucket(
    name: name,
    credits: yxxf > 0 ? yxxf : passedXf,
    required: yqzdxf,
    failed: failedXf,
    unstudied: unstudiedXf,
    studying: studyingXf,
    courses: passedN,
    failedCourses: failedN,
    unstudiedCourses: unstudiedN,
    studyingCourses: studyingN,
    items: items,
  );
}
