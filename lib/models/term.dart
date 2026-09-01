class SchoolTerm {
  const SchoolTerm({required this.xnm, required this.xqm});

  const SchoolTerm.all()
      : xnm = '',
        xqm = '';

  /// 学年起始年, 如 2025 表示 2025-2026 学年. 空为全部.
  final String xnm;

  /// 正方学期码: 3=第1学期, 12=第2学期, 16=小学期. 空为全部.
  final String xqm;

  bool get isAll => xnm.isEmpty && xqm.isEmpty;

  String get key => '$xnm|$xqm';

  String get label {
    if (isAll) return '全部学期';
    final y = int.tryParse(xnm) ?? 0;
    final season = _seasonName(xqm);
    if (y <= 0) return season;
    return '$y-${y + 1}学年 $season';
  }

  static String _seasonName(String xqm) {
    switch (normalizeXqm(xqm)) {
      case '12':
        return '第2学期';
      case '16':
        return '小学期';
      default:
        return '第1学期';
    }
  }

  @override
  bool operator ==(Object other) => other is SchoolTerm && other.key == key;

  @override
  int get hashCode => key.hashCode;
}

(int, String) currentSchoolTerm() {
  final now = DateTime.now();
  if (now.month >= 8 || now.month <= 1) return (now.year, '3');
  return (now.year - 1, '12');
}

int? _enrollYear({String? studentId, String? grade}) {
  final fromGrade = RegExp(r'(19|20)\d{2}').firstMatch(grade ?? '')?.group(0);
  final fromId = RegExp(r'^(19|20)\d{2}').firstMatch(studentId ?? '')?.group(0);
  return int.tryParse(fromGrade ?? '') ?? int.tryParse(fromId ?? '');
}

/// 全部学期 + 从入学年到当前学期, 新学期在前.
List<SchoolTerm> buildGradeTerms({String? studentId, String? grade}) {
  final cur = currentSchoolTerm();
  var start = cur.$1 - 5;
  final enroll = _enrollYear(studentId: studentId, grade: grade);
  if (enroll != null && enroll >= 2000 && enroll <= cur.$1) start = enroll;

  final out = <SchoolTerm>[const SchoolTerm.all()];
  for (var xnm = cur.$1; xnm >= start; xnm--) {
    final isCurYear = xnm == cur.$1;
    if (!isCurYear || cur.$2 == '12' || cur.$2 == '16') {
      out.add(SchoolTerm(xnm: '$xnm', xqm: '12'));
    }
    out.add(SchoolTerm(xnm: '$xnm', xqm: '3'));
  }
  return out;
}

String normalizeXqm(String raw) {
  final s = raw.trim();
  if (s == '12' || s == '02' || s == '2') return '12';
  if (s == '16') return '16';
  if (s == '3' || s == '03' || s == '1' || s == '01') return '3';
  if (s.contains('三') || s.contains('小学期')) return '16';
  if (s.contains('二')) return '12';
  if (s.contains('一')) return '3';
  return s;
}

bool matchesTerm(Map<dynamic, dynamic> item, SchoolTerm term) {
  if (term.isAll) return true;
  final xnm = '${item['xnm'] ?? ''}';
  final xqm = '${item['xqm'] ?? ''}';
  final xnmmc = '${item['xnmmc'] ?? ''}';
  final xqmmc = '${item['xqmmc'] ?? ''}';
  final yearOk = xnm == term.xnm || xnmmc.startsWith(term.xnm);
  if (!yearOk) return false;
  final got = xqm.isNotEmpty ? xqm : xqmmc;
  return normalizeXqm(got) == normalizeXqm(term.xqm);
}

String termLabelOfItem(Map<dynamic, dynamic> item) {
  final xnm = '${item['xnm'] ?? ''}';
  final xqm = '${item['xqm'] ?? ''}';
  if (xnm.isNotEmpty) return SchoolTerm(xnm: xnm, xqm: xqm).label;
  final xnmmc = '${item['xnmmc'] ?? ''}'.trim();
  final xqmmc = '${item['xqmmc'] ?? ''}'.trim();
  final joined = '$xnmmc $xqmmc'.trim();
  return joined;
}
