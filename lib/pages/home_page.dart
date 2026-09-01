import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/lesson.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/motion.dart';
import 'clock_page.dart';
import 'credits_page.dart';
import 'exams_page.dart';
import 'ktkq_page.dart';
import 'ktkq_sign_page.dart';
import 'rooms_page.dart';
import 'venue_page.dart';
import 'ykt_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  Future<List<Lesson>>? _today;
  final weekday = DateTime.now().weekday;
  int _week = 1;

  List<Lesson> _todayFrom(Map<String, dynamic> data) {
    _week = int.tryParse('${data['curWeek'] ?? 1}') ?? 1;
    final all = lessonsFromKb((data['kbList'] as List?) ?? const []);
    final hit = lessonsInWeek(all, _week).where((l) => l.weekday == weekday).toList()
      ..sort((a, b) => a.start.compareTo(b.start));
    return hit;
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final s = context.read<Session>();
      setState(() {
        _today = s.loadSchedule().then(_todayFrom);
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<Session>();
    final pack = ThemePackScope.of(context);
    const services = [
      ('svc.ykt', '一卡通', YktPage()),
      ('svc.credits', '学分', CreditsPage()),
      ('svc.exams', '考试', ExamsPage()),
      ('svc.rooms', '空教室', RoomsPage()),
      ('svc.venue', '预约场馆', VenuePage()),
      ('svc.ktkq', '课堂考勤', KtkqPage()),
      ('svc.clock', '公寓打卡', ClockPage()),
    ];

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: SafeArea(
              bottom: false,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (pack.homeHeader == 'band')
                    Container(height: 3, color: context.primary),
                  Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
                child: FadeSlideIn(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text('民大', style: TextStyle(color: context.muted, fontSize: 13)),
                          const Spacer(),
                          if (session.demoMode)
                            Text('预览', style: TextStyle(color: context.muted, fontSize: 12)),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        '你好，${session.displayName}',
                        style: TextStyle(fontSize: 26, fontWeight: FontWeight.w600, height: 1.15, color: context.ink),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '星期${['', '一', '二', '三', '四', '五', '六', '日'][weekday]}',
                        style: TextStyle(color: context.muted, fontSize: 14),
                      ),
                      if (session.jwxt?.portalClosed == true) ...[
                        const SizedBox(height: 12),
                        const Text(
                          '教务夜间关闭，课表和成绩可能暂不可用。',
                          style: TextStyle(color: kCrimson, fontSize: 13),
                        ),
                      ],
                    ],
                  ),
                ),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            sliver: SliverToBoxAdapter(
              child: FadeSlideIn(
                delay: const Duration(milliseconds: 80),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: context.panel,
                    borderRadius: BorderRadius.circular(pack.cardRadius),
                    border: Border.all(color: context.line),
                  ),
                  child: GridView.count(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    crossAxisCount: pack.homeGrid.clamp(2, 4),
                    childAspectRatio: 1.15,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    children: [
                      for (var i = 0; i < services.length; i++)
                        Pressable(
                          onTap: () => pushPage(context, services[i].$3),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              PackIcon(services[i].$1, color: context.ink, size: 22),
                              const SizedBox(height: 8),
                              Text(services[i].$2, style: TextStyle(fontSize: 13, color: context.ink)),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
            sliver: SliverToBoxAdapter(
              child: Text('今天', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: context.ink)),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 28),
            sliver: SliverToBoxAdapter(
              child: FutureBuilder<List<Lesson>>(
                future: _today,
                builder: (context, snap) {
                  if (snap.connectionState != ConnectionState.done) {
                    return const Padding(
                      padding: EdgeInsets.all(24),
                      child: Center(child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))),
                    );
                  }
                  final today = snap.data ?? const <Lesson>[];
                  if (today.isEmpty) {
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 20),
                      child: Text('今天没有课', style: TextStyle(color: context.muted)),
                    );
                  }
                  return Column(
                    children: [
                      for (var i = 0; i < today.length; i++)
                        FadeSlideIn(
                          delay: Duration(milliseconds: 40 * i),
                          child: _todayRow(today[i], i == today.length - 1),
                        ),
                    ],
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _todayRow(Lesson e, bool last) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 52,
            child: Padding(
              padding: const EdgeInsets.only(top: 14),
              child: Text(
                e.periodLabel.replaceFirst('第', '').replaceFirst('节', ''),
                style: TextStyle(color: context.muted, fontSize: 12),
              ),
            ),
          ),
          Column(
            children: [
              Container(
                width: 7,
                height: 7,
                margin: const EdgeInsets.only(top: 18),
                decoration: BoxDecoration(color: e.color, shape: BoxShape.circle),
              ),
              Container(width: 1, height: last ? 0 : 52, color: context.line),
            ],
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Pressable(
              onTap: () => pushPage(context, KtkqSignPage(lesson: e, week: _week)),
              child: Container(
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
                decoration: BoxDecoration(
                  color: context.panel,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: context.line),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(e.name, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
                    const SizedBox(height: 4),
                    Text('${e.periodLabel}  ${e.room}', style: TextStyle(color: context.muted, fontSize: 13)),
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
