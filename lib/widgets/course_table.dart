import 'package:flutter/material.dart';

import '../models/lesson.dart';
import '../theme.dart';

class CourseTable extends StatelessWidget {
  const CourseTable({
    super.key,
    required this.lessons,
    required this.times,
    this.week = 1,
    this.weekStart,
    this.periods,
    this.onLessonTap,
  });

  /// Already filtered to [week] when provided by the pager.
  final List<Lesson> lessons;
  final List<PeriodTime> times;
  final int week;
  /// Monday of the displayed school week.
  final DateTime? weekStart;
  final int? periods;
  final ValueChanged<Lesson>? onLessonTap;

  static const _days = 7;
  static const _periodH = 64.0;
  static const _timeW = 36.0;
  static const _headH = 46.0;

  DateTime _dateOf(int weekday) {
    final start = weekStart ?? _mondayOfCurrentWeek();
    return DateTime(start.year, start.month, start.day).add(Duration(days: weekday - 1));
  }

  static DateTime _mondayOfCurrentWeek() {
    final n = DateTime.now();
    return DateTime(n.year, n.month, n.day).subtract(Duration(days: n.weekday - 1));
  }

  static bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  @override
  Widget build(BuildContext context) {
    final n = periods ?? maxPeriodOf(lessons, times);
    final today = DateTime.now();
    final byDay = <int, List<_Placed>>{
      for (var d = 1; d <= _days; d++) d: _place([for (final l in lessons) if (l.weekday == d) l]),
    };

    return ClipRect(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
        SizedBox(
          height: _headH,
          child: Row(
            children: [
              const SizedBox(width: _timeW),
              for (var d = 1; d <= _days; d++)
                Expanded(
                  child: _DayHead(day: d, date: _dateOf(d), active: _isSameDay(_dateOf(d), today)),
                ),
            ],
          ),
        ),
        Expanded(
          child: SingleChildScrollView(
            child: SizedBox(
              height: n * _periodH,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    width: _timeW,
                    child: _TimeColumn(periods: n, times: times, periodH: _periodH),
                  ),
                  Expanded(
                    child: _Grid(
                      byDay: byDay,
                      periods: n,
                      periodH: _periodH,
                      onTap: onLessonTap,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        ],
      ),
    );
  }
}

class _TimeColumn extends StatelessWidget {
  const _TimeColumn({required this.periods, required this.times, required this.periodH});

  final int periods;
  final List<PeriodTime> times;
  final double periodH;

  @override
  Widget build(BuildContext context) {
    final byIndex = <int, PeriodTime>{for (final t in times) t.index: t};
    return Column(
      children: [
        for (var p = 1; p <= periods; p++)
          SizedBox(
            height: periodH,
            child: _TimeCell(index: p, time: byIndex[p]),
          ),
      ],
    );
  }
}

class _Grid extends StatelessWidget {
  const _Grid({
    required this.byDay,
    required this.periods,
    required this.periodH,
    this.onTap,
  });

  final Map<int, List<_Placed>> byDay;
  final int periods;
  final double periodH;
  final ValueChanged<Lesson>? onTap;

  static const _days = 7;

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: CustomPaint(
        painter: _GridPainter(days: _days, periods: periods, periodH: periodH),
        child: LayoutBuilder(
          builder: (context, box) {
            final colW = box.maxWidth / _days;
            return Stack(
              fit: StackFit.expand,
              children: [
                for (var d = 1; d <= _days; d++)
                  for (final p in byDay[d]!)
                    Positioned(
                      top: (p.lesson.start - 1) * periodH + 2,
                      height: p.lesson.duration * periodH - 4,
                      left: (d - 1) * colW + 2 + p.col * ((colW - 4) / p.cols),
                      width: (colW - 4) / p.cols - 2,
                      child: _LessonCard(lesson: p.lesson, onTap: onTap),
                    ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _GridPainter extends CustomPainter {
  const _GridPainter({required this.days, required this.periods, required this.periodH});

  final int days;
  final int periods;
  final double periodH;

  static const _h = Color(0x0F000000);
  static const _h0 = Color(0x0A000000);
  static const _v = Color(0x0D000000);

  @override
  void paint(Canvas canvas, Size size) {
    final hp = Paint()..strokeWidth = 1;
    final vp = Paint()
      ..color = _v
      ..strokeWidth = 1;
    final colW = size.width / days;
    for (var d = 1; d <= days; d++) {
      final x = d * colW;
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), vp);
    }
    for (var p = 0; p <= periods; p++) {
      hp.color = p == 0 ? _h0 : _h;
      final y = p * periodH;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), hp);
    }
  }

  @override
  bool shouldRepaint(covariant _GridPainter old) =>
      old.days != days || old.periods != periods || old.periodH != periodH;
}

class _DayHead extends StatelessWidget {
  const _DayHead({required this.day, required this.date, required this.active});
  final int day;
  final DateTime date;
  final bool active;

  @override
  Widget build(BuildContext context) {
    final color = active ? kCrimson : kMuted;
    final weight = active ? FontWeight.w600 : FontWeight.w400;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          kWeekdayLabels[day],
          style: TextStyle(fontSize: 12, fontWeight: weight, color: color),
        ),
        const SizedBox(height: 1),
        Text(
          '${date.month}/${date.day}',
          style: TextStyle(fontSize: 10, height: 1.1, fontWeight: weight, color: color),
        ),
      ],
    );
  }
}

class _TimeCell extends StatelessWidget {
  const _TimeCell({required this.index, this.time});
  final int index;
  final PeriodTime? time;

  @override
  Widget build(BuildContext context) {
    final t = time;
    return Padding(
      padding: const EdgeInsets.only(right: 4),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text('$index', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12, color: kInk)),
          if (t != null && t.start.isNotEmpty)
            Text(t.start, style: const TextStyle(fontSize: 9, color: Colors.black45, height: 1.05)),
          if (t != null && t.end.isNotEmpty)
            Text(t.end, style: const TextStyle(fontSize: 9, color: Colors.black45, height: 1.05)),
        ],
      ),
    );
  }
}

List<_Placed> _place(List<Lesson> list) {
  if (list.isEmpty) return const [];
  final sorted = [...list]..sort((a, b) {
      final c = a.start.compareTo(b.start);
      return c != 0 ? c : a.end.compareTo(b.end);
    });
  final out = <_Placed>[];
  for (final l in sorted) {
    final group = [for (final o in sorted) if (_overlap(l, o)) o];
    out.add(_Placed(l, group.indexOf(l), group.length.clamp(1, 4)));
  }
  return out;
}

bool _overlap(Lesson a, Lesson b) => a.start <= b.end && b.start <= a.end;

class _Placed {
  const _Placed(this.lesson, this.col, this.cols);
  final Lesson lesson;
  final int col;
  final int cols;
}

class _LessonCard extends StatelessWidget {
  const _LessonCard({required this.lesson, this.onTap});
  final Lesson lesson;
  final ValueChanged<Lesson>? onTap;

  static const _radius = BorderRadius.all(Radius.circular(8));
  static const _name = TextStyle(
    color: Colors.white,
    fontSize: 11,
    fontWeight: FontWeight.w700,
    height: 1.15,
  );
  static const _sub = TextStyle(color: Colors.white70, fontSize: 9, height: 1.15);
  static const _room = TextStyle(color: Colors.white70, fontSize: 10, height: 1.1);

  @override
  Widget build(BuildContext context) {
    final child = DecoratedBox(
      decoration: BoxDecoration(color: lesson.color, borderRadius: _radius),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(4, 4, 3, 3),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              lesson.name,
              maxLines: lesson.duration >= 3 ? 3 : 2,
              overflow: TextOverflow.ellipsis,
              style: _name,
            ),
            if (lesson.teacher.isNotEmpty)
              Text(lesson.teacher, maxLines: 1, overflow: TextOverflow.ellipsis, style: _sub),
            if (lesson.room.isNotEmpty)
              Text(lesson.room, maxLines: 1, overflow: TextOverflow.ellipsis, style: _room),
          ],
        ),
      ),
    );
    if (onTap == null) return child;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => onTap!(lesson),
      child: child,
    );
  }
}
