import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/httpx.dart';
import '../models/lesson.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';
import 'clock_page.dart';
import 'credits_page.dart';
import 'exams_page.dart';
import 'ktkq_page.dart';
import 'ktkq_sign_page.dart';
import 'venue_page.dart';
import 'ykt_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  Future<List<Lesson>>? _today;
  final weekday = DateTime.now().weekday;
  int _week = 1;
  Timer? _tick;
  var _refreshing = false;

  List<Lesson> _todayFrom(Map<String, dynamic> data) {
    _week = int.tryParse('${data['curWeek'] ?? 1}') ?? 1;
    final all = lessonsFromKb((data['kbList'] as List?) ?? const []);
    final hit =
        lessonsInWeek(all, _week).where((l) => l.weekday == weekday).toList()
          ..sort((a, b) => a.start.compareTo(b.start));
    return hit;
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _tick = Timer.periodic(const Duration(seconds: 30), (_) {
      if (mounted) setState(() {});
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final s = context.read<Session>();
      setState(() {
        _today = s.loadSchedule().then((data) {
          final list = _todayFrom(data);
          if (mounted) setState(() {});
          return list;
        });
      });
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) setState(() {});
  }

  @override
  void dispose() {
    _tick?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<Session>();
    final pack = ThemePackScope.of(context);
    final accents = _accents(context);
    final weekdayName = ['', '一', '二', '三', '四', '五', '六', '日'][weekday];
    final cols = pack.homeGrid.clamp(2, 4);
    final services = [
      ('svc.ykt', '一卡通', const YktPage(), accents[0]),
      ('svc.credits', '学分', const CreditsPage(), accents[1]),
      ('svc.exams', '考试', const ExamsPage(), accents[2]),
      ('svc.venue', '预约场馆', const VenuePage(), accents[3]),
      ('svc.ktkq', '课堂考勤', const KtkqPage(), accents[4]),
      ('svc.clock', '公寓打卡', const ClockPage(), accents[5]),
    ];

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            if (pack.homeHeader == 'band')
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Container(height: 3, color: context.primary),
              ),
            FadeSlideIn(child: _hello(session, weekdayName)),
            const SizedBox(height: 12),
            FadeSlideIn(
              delay: const Duration(milliseconds: 80),
              child: GridView.count(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                crossAxisCount: cols,
                mainAxisSpacing: 10,
                crossAxisSpacing: 10,
                childAspectRatio: 1.08,
                children: [
                  for (final s in services)
                    _serviceTile(
                      icon: s.$1,
                      label: s.$2,
                      page: s.$3,
                      accent: s.$4,
                    ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            FadeSlideIn(
              delay: const Duration(milliseconds: 150),
              child: _todayCard(),
            ),
          ],
        ),
      ),
    );
  }

  void _reloadToday() {
    if (_refreshing) return;
    final fut = context.read<Session>().loadSchedule(force: true).then((data) {
      final list = _todayFrom(data);
      if (mounted) setState(() {});
      return list;
    });
    setState(() {
      _refreshing = true;
      _today = fut;
    });
    fut.whenComplete(() {
      if (mounted) setState(() => _refreshing = false);
    });
  }

  List<Color> _accents(BuildContext context) {
    final p = HSLColor.fromColor(context.primary);
    Color hue(double d) {
      return p
          .withHue((p.hue + d) % 360)
          .withSaturation((p.saturation * 0.85).clamp(0.28, 0.78))
          .withLightness((p.lightness).clamp(0.32, 0.52))
          .toColor();
    }

    return [context.primary, hue(42), hue(205), hue(155), hue(328), hue(268)];
  }

  Color _wash(Color accent) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Color.alphaBlend(
      accent.withValues(alpha: dark ? 0.22 : 0.12),
      context.panel,
    );
  }

  Widget _iconWell(String icon, Color accent) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: PackIcon(icon, color: accent, size: 20),
      ),
    );
  }

  Widget _serviceTile({
    required String icon,
    required String label,
    required Widget page,
    required Color accent,
  }) {
    return Pressable(
      radius: 16,
      onTap: () => pushPage(context, page),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: _wash(accent),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _iconWell(icon, accent),
              const SizedBox(height: 8),
              Text(
                label,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: context.ink,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _hello(Session session, String weekdayName) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: _wash(context.primary),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  '民大',
                  style: TextStyle(
                    color: context.primary,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const Spacer(),
                if (session.demoMode)
                  Padding(
                    padding: const EdgeInsets.only(right: 4),
                    child: Text(
                      '预览',
                      style: TextStyle(color: context.muted, fontSize: 12),
                    ),
                  ),
                RefreshBusyButton(busy: _refreshing, onPressed: _reloadToday),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              '你好，${session.displayName}',
              style: TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.w700,
                height: 1.15,
                color: context.ink,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '星期$weekdayName · 第$_week周',
              style: TextStyle(color: context.muted, fontSize: 13),
            ),
            if (session.jwxt?.portalClosed == true ||
                session.ktkq?.nightClosed == true ||
                session.ykt?.nightClosed == true) ...[
              const SizedBox(height: 10),
              const Text(
                '教务、课堂考勤、一卡通夜间关闭',
                style: TextStyle(color: kCrimson, fontSize: 13),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _todayCard() {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: context.panel,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: context.line.withValues(alpha: 0.7)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: context.primary,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  '今天',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: context.ink,
                  ),
                ),
              ],
            ),
          ),
          FutureBuilder<List<Lesson>>(
            future: _today,
            builder: (context, snap) {
              if (snap.connectionState != ConnectionState.done) {
                return const Padding(
                  padding: EdgeInsets.fromLTRB(16, 12, 16, 20),
                  child: Center(child: SwunLoader(compact: true)),
                );
              }
              if (snap.hasError) {
                return Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
                  child: Text(
                    '课表加载失败：${publicError(snap.error!)}',
                    style: TextStyle(color: context.muted, fontSize: 13),
                  ),
                );
              }
              final today = snap.data ?? const <Lesson>[];
              final left = remainingToday(today);
              if (today.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
                  child: Text(
                    '今天没有课',
                    style: TextStyle(color: context.muted, fontSize: 13),
                  ),
                );
              }
              if (left.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
                  child: Text(
                    '今天的课上完了',
                    style: TextStyle(color: context.muted, fontSize: 13),
                  ),
                );
              }
              return Column(
                children: [
                  for (var i = 0; i < left.length; i++) ...[
                    if (i > 0)
                      Divider(
                        height: 1,
                        indent: 16,
                        endIndent: 16,
                        color: context.line,
                      ),
                    _lessonTile(left[i]),
                  ],
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _lessonTile(Lesson e) {
    final loc = [e.periodLabel, if (e.room.isNotEmpty) e.room].join('  ');
    final sub = e.teacher.isNotEmpty ? '${e.teacher}\n$loc' : loc;
    return Pressable(
      radius: 0,
      onTap: () => pushPage(context, KtkqSignPage(lesson: e, week: _week)),
      child: ListTile(
        isThreeLine: e.teacher.isNotEmpty,
        leading: Container(
          width: 4,
          height: 36,
          decoration: BoxDecoration(
            color: e.color,
            borderRadius: BorderRadius.circular(4),
          ),
        ),
        title: Text(
          e.name,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
        ),
        subtitle: Text(
          sub,
          style: TextStyle(color: context.muted, fontSize: 13, height: 1.35),
        ),
        trailing: Icon(
          Icons.chevron_right_rounded,
          color: context.muted,
          size: 18,
        ),
      ),
    );
  }
}
