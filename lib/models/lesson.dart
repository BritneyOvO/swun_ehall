import 'package:flutter/material.dart';

const kWeekdayNames = ['', '一', '二', '三', '四', '五', '六', '日'];
const kWeekdayLabels = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/// 民大默认作息 (course/getCourseTimeConfig.do).
const kDefaultPeriodTimes = <PeriodTime>[
  PeriodTime(1, '8:00', '9:15'),
  PeriodTime(2, '9:20', '10:05'),
  PeriodTime(3, '10:25', '11:10'),
  PeriodTime(4, '11:15', '12:00'),
  PeriodTime(5, '14:00', '14:45'),
  PeriodTime(6, '14:50', '15:35'),
  PeriodTime(7, '15:55', '16:40'),
  PeriodTime(8, '16:45', '17:30'),
  PeriodTime(9, '19:00', '19:45'),
  PeriodTime(10, '19:50', '20:35'),
  PeriodTime(11, '20:40', '21:25'),
];

const _palette = <Color>[
  Color(0xFF9B1B30),
  Color(0xFF3D6B5E),
  Color(0xFF4A6080),
  Color(0xFF6A4E6E),
  Color(0xFF8A5A3A),
  Color(0xFF4E6B3E),
  Color(0xFF5A6278),
  Color(0xFF6D4C41),
];

class PeriodTime {
  const PeriodTime(this.index, this.start, this.end);
  final int index;
  final String start;
  final String end;
}

class Lesson {
  const Lesson({
    required this.weekday,
    required this.start,
    required this.end,
    required this.name,
    this.room = '',
    this.teacher = '',
    this.weeks = const {},
    this.raw = const {},
  });

  /// 1=周一 ... 7=周日.
  final int weekday;
  final int start;
  final int end;
  final String name;
  final String room;
  final String teacher;
  final Set<int> weeks;
  final Map<String, dynamic> raw;

  int get duration => (end - start + 1).clamp(1, 12);

  String get periodLabel => start == end ? '第$start节' : '第$start-$end节';

  bool inWeek(int week) {
    if (weeks.isEmpty) return true;
    return weeks.contains(week);
  }

  String get weekLabel {
    if (weeks.isEmpty) return '全学期';
    return formatWeekSet(weeks);
  }

  Color get color {
    var h = 0;
    for (final c in name.codeUnits) {
      h = (h * 31 + c) & 0x7fffffff;
    }
    return _palette[h % _palette.length];
  }
}

int? parseWeekday(Object? v) {
  if (v == null) return null;
  if (v is num) {
    final n = v.round();
    if (n >= 1 && n <= 7) return n;
    if (n == 0) return 7;
    return null;
  }
  final s = '$v'.trim();
  if (s.isEmpty || s == 'null') return null;
  final n = int.tryParse(s);
  if (n != null) {
    if (n >= 1 && n <= 7) return n;
    if (n == 0) return 7;
  }
  const named = {
    '一': 1,
    '二': 2,
    '三': 3,
    '四': 4,
    '五': 5,
    '六': 6,
    '日': 7,
    '天': 7,
    'mon': 1,
    'tue': 2,
    'wed': 3,
    'thu': 4,
    'fri': 5,
    'sat': 6,
    'sun': 7,
  };
  for (final e in named.entries) {
    if (s.toLowerCase().contains(e.key)) return e.value;
  }
  return null;
}

List<int> parseWeekdays(Object? v) {
  if (v == null) return const [];
  if (v is num) {
    final d = parseWeekday(v);
    return d == null ? const [] : [d];
  }
  final s = '$v'.trim();
  if (s.isEmpty || s == 'null') return const [];
  const chars = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7};
  final fromChars = <int>[];
  for (final r in chars.entries) {
    if (s.contains(r.key) && !fromChars.contains(r.value)) fromChars.add(r.value);
  }
  if (fromChars.length >= 2) {
    fromChars.sort();
    return fromChars;
  }
  final fromParts = <int>[];
  for (final part in s.split(RegExp(r'[,，、;/\s]+'))) {
    final d = parseWeekday(part);
    if (d != null && !fromParts.contains(d)) fromParts.add(d);
  }
  if (fromParts.length >= 2) {
    fromParts.sort();
    return fromParts;
  }
  if (fromParts.isNotEmpty) return fromParts;
  if (fromChars.length == 1) return fromChars;
  final one = parseWeekday(s);
  return one == null ? const [] : [one];
}

(int, int) parsePeriods(Map<dynamic, dynamic> m) {
  final jcs = '${m['jcs'] ?? m['jcor'] ?? ''}'.trim();
  final jc = RegExp(r'(\d+)\s*[-~到至]\s*(\d+)').firstMatch(jcs);
  if (jc != null) {
    final a = int.parse(jc.group(1)!);
    final b = int.parse(jc.group(2)!);
    return (a, b >= a ? b : a);
  }
  final single = int.tryParse(jcs);
  final start = int.tryParse('${m['skjc'] ?? m['ksjc'] ?? m['startNode'] ?? ''}') ?? single ?? 1;
  final endRaw = int.tryParse('${m['jsjc'] ?? m['endNode'] ?? ''}');
  if (endRaw != null && endRaw >= start) return (start, endRaw);
  final dur = int.tryParse('${m['cxjc'] ?? 1}') ?? 1;
  return (start, start + (dur > 0 ? dur : 1) - 1);
}

Set<int> parseWeeks({Object? skzc, Object? zcd}) {
  final mask = '${skzc ?? ''}';
  if (mask.contains('1') && RegExp(r'^[01]+$').hasMatch(mask)) {
    final s = <int>{};
    for (var i = 0; i < mask.length; i++) {
      if (mask[i] == '1') s.add(i + 1);
    }
    if (s.isNotEmpty) return s;
  }
  final text = '${zcd ?? skzc ?? ''}'.trim();
  if (text.isEmpty || text == 'null') return {};
  final odd = text.contains('单');
  final even = text.contains('双') && !text.contains('单');
  final s = <int>{};
  for (final m in RegExp(r'(\d+)\s*[-~到至]\s*(\d+)').allMatches(text)) {
    final a = int.parse(m.group(1)!);
    final b = int.parse(m.group(2)!);
    for (var w = a; w <= b; w++) {
      if (odd && w.isEven) continue;
      if (even && w.isOdd) continue;
      s.add(w);
    }
  }
  if (s.isEmpty) {
    for (final m in RegExp(r'(\d+)').allMatches(text)) {
      s.add(int.parse(m.group(1)!));
    }
  }
  return s;
}

List<PeriodTime> parsePeriodTimes(Object? raw) {
  if (raw is Map && raw['coursetimeList'] is List) {
    return parsePeriodTimes(raw['coursetimeList']);
  }
  if (raw is! List) return kDefaultPeriodTimes;
  final out = <PeriodTime>[];
  for (final e in raw) {
    if (e is! Map) continue;
    final i = int.tryParse('${e['jcdm'] ?? e['skjc'] ?? e['index'] ?? ''}') ?? 0;
    if (i < 1) continue;
    out.add(PeriodTime(i, '${e['startTime'] ?? e['kssj'] ?? ''}', '${e['endTime'] ?? e['jssj'] ?? ''}'));
  }
  out.sort((a, b) => a.index.compareTo(b.index));
  return out.isEmpty ? kDefaultPeriodTimes : out;
}

/// 同一门课在不同星期/节次拆成多条, 不合并.
List<Lesson> lessonsFromKb(Iterable raw) {
  final out = <Lesson>[];
  for (final row in raw) {
    if (row is! Map) continue;
    final m = Map<String, dynamic>.from(row);
    final name = '${m['kcmc'] ?? m['kcm'] ?? ''}'.trim();
    if (name.isEmpty) continue;
    var days = parseWeekdays(m['xqj'] ?? m['skxq'] ?? m['xqjmc'] ?? m['dateDigit'] ?? m['weekDay']);
    if (days.isEmpty) continue;
    final (start, end) = parsePeriods(m);
    final weeks = parseWeeks(skzc: m['skzc'], zcd: m['zcd']);
    final room = '${m['cdmc'] ?? m['jash'] ?? m['jxlH'] ?? m['jasmc'] ?? ''}'.trim();
    final teacher = '${m['xm'] ?? m['jgmc'] ?? m['jsxm'] ?? m['teacherName'] ?? ''}'.trim();
    for (final d in days) {
      out.add(
        Lesson(
          weekday: d,
          start: start < 1 ? 1 : start,
          end: end < start ? start : end,
          name: name,
          room: room,
          teacher: teacher == '—' ? '' : teacher,
          weeks: weeks,
          raw: m,
        ),
      );
    }
  }
  return out;
}

List<Lesson> lessonsInWeek(List<Lesson> all, int week) {
  final hit = [for (final l in all) if (l.inWeek(week)) l];
  final seen = <String>{};
  final out = <Lesson>[];
  for (final l in hit) {
    final k = '${l.weekday}|${l.start}|${l.end}|${l.name}|${l.room}';
    if (seen.add(k)) out.add(l);
  }
  return out;
}

String formatWeekSet(Set<int> weeks) {
  if (weeks.isEmpty) return '';
  final list = weeks.toList()..sort();
  final parts = <String>[];
  var i = 0;
  while (i < list.length) {
    var j = i;
    while (j + 1 < list.length && list[j + 1] == list[j] + 1) {
      j++;
    }
    parts.add(i == j ? '${list[i]}' : '${list[i]}-${list[j]}');
    i = j + 1;
  }
  return '${parts.join(',')}周';
}

int maxPeriodOf(List<Lesson> lessons, List<PeriodTime> times) {
  var n = times.length;
  if (n < 10) n = 10;
  for (final l in lessons) {
    if (l.end > n) n = l.end;
  }
  return n.clamp(1, 16);
}
