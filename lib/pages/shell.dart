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
  final _tabs = List<Widget?>.filled(4, null);

  static const _keys = ['nav.home', 'nav.schedule', 'nav.grades', 'nav.mine'];
  static const _labels = ['首页', '课表', '成绩', '我的'];

  Widget _tab(int i) {
    final hit = _tabs[i];
    if (hit != null) return hit;
    final page = switch (i) {
      0 => const HomePage(),
      1 => const SchedulePage(),
      2 => const GradesPage(),
      _ => const MinePage(),
    };
    _tabs[i] = page;
    return page;
  }

  @override
  void initState() {
    super.initState();
    _tab(0);
    WidgetsBinding.instance.addPostFrameCallback((_) => AppLocator.warmup());
  }

  @override
  Widget build(BuildContext context) {
    final pack = ThemePackScope.of(context);
    final pill = pack.navStyle == 'pill';
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: [
          for (var i = 0; i < 4; i++) _tabs[i] ?? const SizedBox.shrink(),
        ],
      ),
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
                    Positioned(
                      left: 0,
                      right: 0,
                      top: 0,
                      child: Divider(height: 1, color: context.line),
                    ),
                    if (pill)
                      AnimatedPositioned(
                        duration: const Duration(milliseconds: 240),
                        curve: Curves.easeOutCubic,
                        left: w * _index + 8,
                        bottom: 8,
                        width: w - 16,
                        height: 40,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: context.primary.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(20),
                          ),
                        ),
                      ),
                    Row(
                      children: [
                        for (var i = 0; i < _keys.length; i++)
                          Expanded(
                            child: InkWell(
                              onTap: () => setState(() {
                                _tab(i);
                                _index = i;
                              }),
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  PackIcon(
                                    _keys[i],
                                    filled: i == _index,
                                    size: 22,
                                    color: i == _index
                                        ? context.primary
                                        : context.muted,
                                  ),
                                  const SizedBox(height: 3),
                                  AnimatedDefaultTextStyle(
                                    duration: const Duration(milliseconds: 180),
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: i == _index
                                          ? FontWeight.w600
                                          : FontWeight.w400,
                                      color: i == _index
                                          ? context.primary
                                          : context.muted,
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
