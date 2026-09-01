import 'package:flutter/material.dart';

import '../api/locate.dart';
import '../theme.dart';
import 'grades_page.dart';
import 'home_page.dart';
import 'mine_page.dart';
import 'schedule_page.dart';

class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _index = 0;

  static const _keys = ['nav.home', 'nav.schedule', 'nav.grades', 'nav.mine'];
  static const _labels = ['首页', '课表', '成绩', '我的'];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => AppLocator.warmup());
  }

  @override
  Widget build(BuildContext context) {
    final pack = ThemePackScope.of(context);
    final pill = pack.navStyle == 'pill';
    final pages = const [
      HomePage(),
      SchedulePage(),
      GradesPage(),
      MinePage(),
    ];
    return Scaffold(
      body: IndexedStack(index: _index, children: pages),
      bottomNavigationBar: Material(
        color: context.panel,
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: 56,
            child: LayoutBuilder(
              builder: (context, box) {
                final w = box.maxWidth / _keys.length;
                return Stack(
                  children: [
                    Positioned(left: 0, right: 0, top: 0, child: Divider(height: 1, color: context.line)),
                    AnimatedPositioned(
                      duration: const Duration(milliseconds: 240),
                      curve: Curves.easeOutCubic,
                      left: pill ? w * _index + 8 : w * _index + w * 0.28,
                      bottom: pill ? 8 : 7,
                      width: pill ? w - 16 : w * 0.44,
                      height: pill ? 40 : 2,
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: pill ? context.primary.withValues(alpha: 0.12) : context.primary,
                          borderRadius: BorderRadius.circular(pill ? 20 : 2),
                        ),
                      ),
                    ),
                    Row(
                      children: [
                        for (var i = 0; i < _keys.length; i++)
                          Expanded(
                            child: InkWell(
                              onTap: () => setState(() => _index = i),
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  PackIcon(
                                    _keys[i],
                                    filled: i == _index,
                                    size: 22,
                                    color: i == _index ? context.primary : context.muted,
                                  ),
                                  const SizedBox(height: 3),
                                  AnimatedDefaultTextStyle(
                                    duration: const Duration(milliseconds: 180),
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: i == _index ? FontWeight.w600 : FontWeight.w400,
                                      color: i == _index ? context.primary : context.muted,
                                    ),
                                    child: Text(_labels[i]),
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}
