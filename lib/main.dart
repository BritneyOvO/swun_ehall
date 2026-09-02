import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'pages/login_page.dart';
import 'pages/shell.dart';
import 'state/session.dart';
import 'state/settings.dart';
import 'theme.dart';
import 'widgets/update_prompt.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SwunApp());
}

class SwunApp extends StatefulWidget {
  const SwunApp({super.key});

  @override
  State<SwunApp> createState() => _SwunAppState();
}

class _SwunAppState extends State<SwunApp> {
  final session = Session();
  final settings = AppSettings();
  final _nav = GlobalKey<NavigatorState>();
  var _splashDone = false;
  var _updatePrompted = false;
  Timer? _splashTimer;
  Timer? _updateTimer;
  VoidCallback? _settingsWait;

  @override
  void initState() {
    super.initState();
    session.init();
    settings.load();
    _splashTimer = Timer(const Duration(milliseconds: 900), () {
      if (mounted) setState(() => _splashDone = true);
    });
    _updateTimer = Timer(const Duration(milliseconds: 1400), () {
      unawaited(_bootUpdateCheck());
    });
  }

  @override
  void dispose() {
    _splashTimer?.cancel();
    _updateTimer?.cancel();
    final wait = _settingsWait;
    if (wait != null) settings.removeListener(wait);
    super.dispose();
  }

  Future<void> _bootUpdateCheck() async {
    if (!mounted || _updatePrompted) return;
    if (!settings.ready) {
      void once() {
        settings.removeListener(once);
        _settingsWait = null;
        unawaited(_bootUpdateCheck());
      }
      _settingsWait = once;
      settings.addListener(once);
      return;
    }
    if (!settings.checkUpdateOnLaunch) return;
    final ctx = _nav.currentContext;
    if (ctx == null || !ctx.mounted) return;
    _updatePrompted = true;
    await checkForUpdate(ctx, silent: true);
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: session),
        ChangeNotifierProvider.value(value: settings),
      ],
      child: Consumer<AppSettings>(
        builder: (context, cfg, _) {
          final light = cfg.lightPack();
          final dark = cfg.darkPack();
          final explicit = cfg.selected;
          final ThemeData lightTheme;
          final ThemeData darkTheme;
          final ThemeMode mode;
          if (cfg.followSystem) {
            lightTheme = buildThemeFromPack(light);
            darkTheme = buildThemeFromPack(dark);
            mode = ThemeMode.system;
          } else if (explicit.isDark) {
            lightTheme = buildThemeFromPack(light);
            darkTheme = buildThemeFromPack(explicit);
            mode = ThemeMode.dark;
          } else {
            lightTheme = buildThemeFromPack(explicit);
            darkTheme = buildThemeFromPack(dark);
            mode = ThemeMode.light;
          }
          return MaterialApp(
            navigatorKey: _nav,
            title: '民大助手',
            debugShowCheckedModeBanner: false,
            theme: lightTheme,
            darkTheme: darkTheme,
            themeMode: mode,
            builder: (context, child) {
              final brightness = Theme.of(context).brightness;
              final pack = cfg.resolve(brightness);
              return ThemePackScope(
                pack: pack,
                child: Theme(
                  data: buildThemeFromPack(pack),
                  child: child ?? const SizedBox.shrink(),
                ),
              );
            },
            home: Consumer<Session>(
              builder: (context, s, _) {
                Widget child;
                if (!s.ready || !_splashDone) {
                  child = const _Boot(key: ValueKey('boot'));
                } else if (s.loggedIn) {
                  child = const MainShell(key: ValueKey('shell'));
                } else {
                  child = const LoginPage(key: ValueKey('login'));
                }
                return AnimatedSwitcher(
                  duration: const Duration(milliseconds: 380),
                  switchInCurve: Curves.easeOutCubic,
                  switchOutCurve: Curves.easeInCubic,
                  transitionBuilder: (child, anim) => FadeTransition(opacity: anim, child: child),
                  child: child,
                );
              },
            ),
          );
        },
      ),
    );
  }
}

class _Boot extends StatelessWidget {
  const _Boot({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Image.asset('assets/icon/splash.png', width: 112, height: 112, filterQuality: FilterQuality.medium),
            const SizedBox(height: 16),
            Text(
              '民大助手',
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w600,
                letterSpacing: 4,
                color: context.ink,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
