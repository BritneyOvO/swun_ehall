import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/lesson.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/async_body.dart';
import '../widgets/loader.dart';
import '../widgets/course_table.dart';
import '../widgets/motion.dart';
import 'ktkq_sign_page.dart';

class SchedulePage extends StatefulWidget {
  const SchedulePage({super.key});

  @override
  State<SchedulePage> createState() => _SchedulePageState();
}

class _SchedulePageState extends State<SchedulePage> {
  Future<Map<String, dynamic>>? _future;
  var _started = false;
  var _epoch = 0;
  var _busy = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    _future = context.read<Session>().loadSchedule();
  }

  void _reload() {
    if (_busy) return;
    final fut = context.read<Session>().loadSchedule(force: true);
    setState(() {
      _epoch++;
      _busy = true;
      _future = fut;
    });
    fut.whenComplete(() {
      if (mounted) setState(() => _busy = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('课表'),
        automaticallyImplyLeading: false,
        actions: [
          RefreshBusyButton(busy: _busy, onPressed: _reload),
        ],
      ),
      body: _future == null
          ? const Center(child: SwunLoader())
          : AsyncBody(
              key: ValueKey(_epoch),
              future: _future!,
              onRetry: _busy ? null : _reload,
              builder: (context, data) => _ScheduleBoard(
                key: ObjectKey(data),
                data: data,
              ),
            ),
    );
  }
}

class _ScheduleBoard extends StatefulWidget {
  const _ScheduleBoard({super.key, required this.data});

  final Map<String, dynamic> data;

  @override
  State<_ScheduleBoard> createState() => _ScheduleBoardState();
}

class _ScheduleBoardState extends State<_ScheduleBoard> {
  late final List<Lesson> _lessons;
  late final List<List<Lesson>> _byWeek;
  late final List<PeriodTime> _times;
  late final int _cur;
  late final int _maxW;
  late final int _periods;
  late final PageController _pager;
  late final ValueNotifier<int> _week;

  @override
  void initState() {
    super.initState();
    final data = widget.data;
    _lessons = lessonsFromKb((data['kbList'] as List?) ?? const []);
    _cur = int.tryParse('${data['curWeek'] ?? 1}') ?? 1;
    final total = int.tryParse('${data['totalWeek'] ?? 16}') ?? 16;
    var maxW = total < 16 ? 16 : total;
    for (final l in _lessons) {
      for (final w in l.weeks) {
        if (w > maxW) maxW = w;
      }
    }
    _maxW = maxW;
    final week = _cur.clamp(1, _maxW);
    _week = ValueNotifier(week);
    _pager = PageController(initialPage: week - 1);
    _times = data['times'] is List<PeriodTime>
        ? data['times'] as List<PeriodTime>
        : parsePeriodTimes(data['times']);
    _periods = maxPeriodOf(_lessons, _times);
    _byWeek = List<List<Lesson>>.generate(_maxW, (i) => lessonsInWeek(_lessons, i + 1));
  }

  @override
  void dispose() {
    _pager.dispose();
    _week.dispose();
    super.dispose();
  }

  void _go(int week) {
    week = week.clamp(1, _maxW);
    _week.value = week;
    if (!_pager.hasClients) return;
    _pager.animateToPage(
      week - 1,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_lessons.isEmpty) return const Center(child: Text('本学期暂无课表'));
    return Column(
      children: [
        ValueListenableBuilder<int>(
          valueListenable: _week,
          builder: (context, week, _) => _WeekBar(
            week: week,
            maxWeek: _maxW,
            isCurrent: week == _cur,
            onPrev: week > 1 ? () => _go(week - 1) : null,
            onNext: week < _maxW ? () => _go(week + 1) : null,
            onCurrent: week == _cur ? null : () => _go(_cur),
          ),
        ),
        Expanded(
          child: PageView.custom(
            controller: _pager,
            clipBehavior: Clip.hardEdge,
            onPageChanged: (i) => _week.value = i + 1,
            childrenDelegate: SliverChildBuilderDelegate(
              (context, i) {
                final w = i + 1;
                return SizedBox.expand(
                  child: CourseTable(
                    lessons: _byWeek[i],
                    times: _times,
                    week: w,
                    weekStart: mondayOfSchoolWeek(w, _cur),
                    periods: _periods,
                    onLessonTap: (l) => pushPage(context, KtkqSignPage(lesson: l, week: w)),
                  ),
                );
              },
              childCount: _maxW,
              addAutomaticKeepAlives: false,
              addRepaintBoundaries: true,
            ),
          ),
        ),
      ],
    );
  }
}

class _WeekBar extends StatelessWidget {
  const _WeekBar({
    required this.week,
    required this.maxWeek,
    required this.isCurrent,
    this.onPrev,
    this.onNext,
    this.onCurrent,
  });

  final int week;
  final int maxWeek;
  final bool isCurrent;
  final VoidCallback? onPrev;
  final VoidCallback? onNext;
  final VoidCallback? onCurrent;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 6, 8, 2),
      child: Row(
        children: [
          IconButton(
            onPressed: onPrev,
            icon: const Icon(Icons.chevron_left_rounded),
            color: kCrimson,
          ),
          Text('第$week周', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
          IconButton(
            onPressed: onNext,
            icon: const Icon(Icons.chevron_right_rounded),
            color: kCrimson,
          ),
          Text('/ $maxWeek', style: const TextStyle(color: Colors.black45, fontSize: 12)),
          const Spacer(),
          if (!isCurrent)
            TextButton(onPressed: onCurrent, child: const Text('回本周'))
          else
            const Text('本周', style: TextStyle(color: Colors.black45, fontSize: 13)),
        ],
      ),
    );
  }
}
