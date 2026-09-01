class CreditBucket {
  const CreditBucket({required this.name, required this.credits, this.courses = 0});

  final String name;
  final double credits;
  final int courses;
}

class CreditProgress {
  const CreditProgress({
    this.taken = 0,
    this.required = 0,
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

  /// 最低毕业学分.
  final double required;
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

  String get takenText => _xf(taken);
  String get requiredText => _xf(required);
  String get remainingText => _xf(remaining);
}

String _xf(double v) {
  if (v == v.roundToDouble()) return '${v.toInt()}';
  final s = v.toStringAsFixed(1);
  return s.endsWith('.0') ? s.substring(0, s.length - 2) : s;
}

bool gradePassed(Map<dynamic, dynamic> row) {
  final mark = '${row['cj'] ?? ''}'.trim();
  if (const {'及格', '合格', '优', '良', '中', '通过', 'A', 'B', 'C'}.contains(mark)) return true;
  if (const {'不及格', '不合格', '不通过', 'F'}.contains(mark)) return false;
  final n = double.tryParse('${row['bfzcj'] ?? row['cj'] ?? ''}');
  if (n != null) return n >= 60;
  final jd = double.tryParse('${row['jd'] ?? ''}');
  if (jd != null) return jd > 0;
  return false;
}

List<CreditBucket> creditBucketsOf(Iterable items) {
  final map = <String, CreditBucket>{};
  for (final raw in items) {
    if (raw is! Map) continue;
    if (!gradePassed(raw)) continue;
    final name = '${raw['kclbmc'] ?? raw['kcxzmc'] ?? '其他'}'.trim();
    if (name.isEmpty) continue;
    final xf = double.tryParse('${raw['xf'] ?? ''}') ?? 0;
    final prev = map[name];
    map[name] = CreditBucket(
      name: name,
      credits: (prev?.credits ?? 0) + xf,
      courses: (prev?.courses ?? 0) + 1,
    );
  }
  final list = map.values.toList()
    ..sort((a, b) => b.credits.compareTo(a.credits));
  return list;
}

double? _d(String? s) => double.tryParse((s ?? '').trim());

int? _i(String? s) => int.tryParse((s ?? '').trim());

String? _cap(String html, String pattern) =>
    RegExp(pattern, dotAll: true).firstMatch(html)?.group(1);

CreditProgress parseCreditProgress(String html, {List<dynamic> grades = const []}) {
  final taken = _d(_cap(html, r'修读总学分[：:][\s\S]{0,160}?>\s*([0-9]+(?:\.[0-9]+)?)\s*<')) ??
      creditBucketsOf(grades).fold<double>(0, (a, b) => a + b.credits);
  final required = _d(_cap(html, r"yqzdxf='([0-9]+(?:\.[0-9]+)?)'")) ??
      _d(_cap(html, r'最低毕业学分[:：]\s*([0-9]+(?:\.[0-9]+)?)')) ??
      0;
  final gpa = _d(_cap(html, r'平均学分绩点[\s\S]{0,280}?([0-9]+\.[0-9]+)'));
  final planTotal = _i(_cap(html, r'计划总课程[\s\S]{0,80}?(?:&nbsp;|\s)(\d+)')) ?? 0;
  final planPassed = _i(_cap(html, r'计划总课程[\s\S]{0,220}?通过[\s\S]{0,40}?(?:&nbsp;|\s)(\d+)')) ?? 0;
  final planFailed = _i(_cap(html, r'未通过[\s\S]{0,40}?(?:&nbsp;|\s)(\d+)')) ?? 0;
  final planUnstudied = _i(_cap(html, r'未修[\s\S]{0,40}?(?:&nbsp;|\s)(\d+)')) ?? 0;
  final planStudying = _i(_cap(html, r'在读[\s\S]{0,30}?(?:&nbsp;|\s)(\d+)')) ?? 0;
  final extraPassed = _i(_cap(html, r'计划外[\s\S]{0,80}?通过[\s\S]{0,40}?(?:&nbsp;|\s)(\d+)')) ?? 0;
  final extraFailed = _i(_cap(html, r'计划外[\s\S]{0,200}?未通过[\s\S]{0,40}?(?:&nbsp;|\s)(\d+)')) ?? 0;
  var buckets = creditBucketsOf(grades);
  if (buckets.isEmpty && taken > 0) {
    buckets = [CreditBucket(name: '共修学分', credits: taken)];
  }
  return CreditProgress(
    taken: taken,
    required: required,
    gpa: gpa,
    planTotal: planTotal,
    planPassed: planPassed,
    planFailed: planFailed,
    planUnstudied: planUnstudied,
    planStudying: planStudying,
    extraPassed: extraPassed,
    extraFailed: extraFailed,
    buckets: buckets,
  );
}

const demoCreditProgress = CreditProgress(
  taken: 15.0,
  required: 160.5,
  gpa: 3.63,
  planTotal: 29,
  planPassed: 4,
  planUnstudied: 20,
  planStudying: 5,
  extraPassed: 2,
  buckets: [
    CreditBucket(name: '学科基础课', credits: 5.0, courses: 2),
    CreditBucket(name: '公共基础课', credits: 5.0, courses: 2),
    CreditBucket(name: '专业必修课', credits: 3.5, courses: 1),
    CreditBucket(name: '专业选修课', credits: 1.5, courses: 1),
  ],
);
