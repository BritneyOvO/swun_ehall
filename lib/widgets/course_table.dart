import 'package:flutter/material.dart';

import '../models/lesson.dart';
import '../theme.dart';

class CourseTable extends StatelessWidget {
  const CourseTable({
    super.key,
    required this.lessons,
    required this.times,
    this.week = 1,
    this.onLessonTap,
  });

  final List<Lesson> lessons;
  final List<PeriodTime> times;
  final int week;
  final ValueChanged<Lesson>? onLessonTap;

  static const _periodH = 64.0;
  static const _timeW = 38.0;
  static const _headH = 42.0;

  @override
  Widget build(BuildContext context) {
    final visible = lessonsInWeek(lessons, week);
    final lastDay = visible.fold<int>(5, (m, l) => l.weekday > m ? l.weekday : m);
    final days = lastDay <= 5 ? 5 : lastDay;
    final periods = maxPeriodOf(visible, times);
    final today = DateTime.now().weekday;
    final byDay = <int, List<Lesson>>{
      for (var d = 1; d <= days; d++) d: [for (final l in visible) if (l.weekday == d) l],
    };

    return Column(
      children: [
        SizedBox(
          height: _headH,
          child: Row(
            children: [
              const SizedBox(width: _timeW),
              for (var d = 1; d <= days; d++)
                Expanded(
                  child: _DayHead(day: d, active: d == today),
                ),
            ],
          ),
        ),
        Expanded(
          child: SingleChildScrollView(
            child: SizedBox(
              height: periods * _periodH,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    width: _timeW,
                    child: Column(
                      children: [
                        for (var p = 1; p <= periods; p++)
                          SizedBox(
                            height: _periodH,
                            child: _TimeCell(index: p, times: times),
                          ),
                      ],
                    ),
                  ),
                  for (var d = 1; d <= days; d++)
                    Expanded(
                      child: _DayColumn(
                        lessons: byDay[d]!,
                        periods: periods,
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
    );
  }
}

class _DayHead extends StatelessWidget {
  const _DayHead({required this.day, required this.active});
  final int day;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          kWeekdayLabels[day],
          style: TextStyle(
            fontSize: 13,
            fontWeight: active ? FontWeight.w600 : FontWeight.w400,
            color: active ? kCrimson : kMuted,
          ),
        ),
        const SizedBox(height: 4),
        AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: active ? 14 : 0,
          height: 2,
          decoration: BoxDecoration(color: kCrimson, borderRadius: BorderRadius.circular(1)),
        ),
      ],
    );
  }
}

class _TimeCell extends StatelessWidget {
  const _TimeCell({required this.index, required this.times});
  final int index;
  final List<PeriodTime> times;

  @override
  Widget build(BuildContext context) {
    PeriodTime? t;
    for (final e in times) {
      if (e.index == index) {
        t = e;
        break;
      }
    }
    return Padding(
      padding: const EdgeInsets.only(right: 4),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text('$index', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12, color: kInk)),
          if (t != null && t.start.isNotEmpty)
            Text(t.start, style: const TextStyle(fontSize: 9, color: Colors.black45, height: 1.1)),
        ],
      ),
    );
  }
}

class _DayColumn extends StatelessWidget {
  const _DayColumn({
    required this.lessons,
    required this.periods,
    required this.periodH,
    this.onTap,
  });

  final List<Lesson> lessons;
  final int periods;
  final double periodH;
  final ValueChanged<Lesson>? onTap;

  @override
  Widget build(BuildContext context) {
    final placed = _place(lessons);
    return Stack(
      children: [
        Column(
          children: [
            for (var p = 1; p <= periods; p++)
              Container(
                height: periodH,
                decoration: BoxDecoration(
                  border: Border(
                    top: BorderSide(color: Colors.black.withValues(alpha: p == 1 ? 0.04 : 0.06)),
                    right: BorderSide(color: Colors.black.withValues(alpha: 0.05)),
                  ),
                ),
              ),
          ],
        ),
        Positioned.fill(
          child: LayoutBuilder(
            builder: (context, box) {
              final w = box.maxWidth;
              return Stack(
                children: [
                  for (final p in placed)
                    Positioned(
                      top: (p.lesson.start - 1) * periodH + 2,
                      height: p.lesson.duration * periodH - 4,
                      left: 2 + p.col * ((w - 4) / p.cols),
                      width: (w - 4) / p.cols - 2,
                      child: _LessonCard(lesson: p.lesson, onTap: onTap),
                    ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  List<_Placed> _place(List<Lesson> list) {
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
}

class _Placed {
  _Placed(this.lesson, this.col, this.cols);
  final Lesson lesson;
  final int col;
  final int cols;
}

class _LessonCard extends StatelessWidget {
  const _LessonCard({required this.lesson, this.onTap});
  final Lesson lesson;
  final ValueChanged<Lesson>? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: lesson.color,
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap == null ? null : () => onTap!(lesson),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(5, 5, 4, 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  lesson.name,
                  maxLines: lesson.duration >= 3 ? 3 : 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    height: 1.15,
                  ),
                ),
              ),
              if (lesson.room.isNotEmpty)
                Text(
                  lesson.room,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Colors.white70, fontSize: 10, height: 1.1),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
