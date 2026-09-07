import 'package:flutter/material.dart';

import 'theme/pack.dart';

export 'theme/icons.dart';
export 'theme/pack.dart';

const kCrimson = Color(0xFF9B1B30);
const kGold = Color(0xFF8A7A5C);
const kCream = Color(0xFFF4F2EE);
const kInk = Color(0xFF1C1917);
const kMuted = Color(0xFF6F6A64);
const kLine = Color(0xFFE6E1DA);

extension SwunColors on BuildContext {
  Color get ink => Theme.of(this).colorScheme.onSurface;
  Color get muted => Theme.of(this).colorScheme.onSurfaceVariant;
  Color get line => Theme.of(this).dividerColor;
  Color get panel => Theme.of(this).cardTheme.color ?? Theme.of(this).colorScheme.surface;
  Color get primary => Theme.of(this).colorScheme.primary;
}

ThemeData buildLightTheme() => buildThemeFromPack(ThemePack.fallbackLight);
ThemeData buildDarkTheme() => buildThemeFromPack(
      ThemePack.parse(
        '<theme id="dark" name="深色" brightness="dark"><colors>'
        '<color name="primary">#C45C63</color><color name="onPrimary">#1C1917</color>'
        '<color name="background">#141210</color><color name="onBackground">#F3F0EB</color>'
        '<color name="muted">#A39E97</color><color name="line">#2C2926</color>'
        '<color name="card">#1C1A18</color><color name="onCard">#F3F0EB</color>'
        '</colors></theme>',
      ),
    );

ThemeData buildThemeFromPack(ThemePack pack) {
  final dark = pack.isDark;
  final bg = pack.background;
  final ink = pack.onBackground;
  final muted = pack.muted;
  final line = pack.line;
  final card = pack.card;
  final primary = pack.primary;
  final onPrimary = pack.onPrimary;
  final cardR = pack.cardRadius;
  final btnR = pack.buttonRadius;
  final scheme = ColorScheme(
    brightness: pack.brightness,
    primary: primary,
    onPrimary: onPrimary,
    secondary: dark ? const Color(0xFFD6D0C8) : const Color(0xFF3D3A37),
    onSecondary: dark ? const Color(0xFF1C1917) : Colors.white,
    surface: bg,
    onSurface: ink,
    onSurfaceVariant: muted,
    error: primary,
    onError: onPrimary,
  );
  return ThemeData(
    colorScheme: scheme,
    useMaterial3: true,
    brightness: pack.brightness,
    scaffoldBackgroundColor: bg,
    cardColor: card,
    splashColor: primary.withValues(alpha: 0.08),
    highlightColor: primary.withValues(alpha: 0.04),
    dividerColor: line,
    appBarTheme: AppBarTheme(
      backgroundColor: bg,
      foregroundColor: ink,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: pack.appBarCenter,
      titleTextStyle: TextStyle(color: ink, fontSize: 18, fontWeight: FontWeight.w600, height: 1.2),
      iconTheme: IconThemeData(color: ink, size: 22),
    ),
    cardTheme: CardThemeData(
      color: card,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(cardR),
        side: BorderSide(color: line),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: card,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(btnR), borderSide: BorderSide(color: line)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(btnR), borderSide: BorderSide(color: line)),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(btnR),
        borderSide: BorderSide(color: primary, width: 1.2),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: primary,
        foregroundColor: onPrimary,
        minimumSize: const Size.fromHeight(46),
        elevation: 0,
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(btnR)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: muted),
    ),
    listTileTheme: ListTileThemeData(iconColor: ink, textColor: ink),
    iconTheme: IconThemeData(color: ink, size: 22),
    textTheme: (dark
            ? Typography.material2021().white
            : Typography.material2021().black)
        .apply(bodyColor: ink, displayColor: ink),
    dialogTheme: DialogThemeData(
      backgroundColor: card,
      titleTextStyle: TextStyle(
        color: ink,
        fontSize: 18,
        fontWeight: FontWeight.w600,
      ),
      contentTextStyle: TextStyle(color: ink, fontSize: 14, height: 1.4),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: dark ? const Color(0xFF2A2724) : ink,
      contentTextStyle: TextStyle(color: dark ? ink : Colors.white),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(btnR)),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: primary),
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: {
        TargetPlatform.android: _FadeSlideTransitions(),
        TargetPlatform.iOS: _FadeSlideTransitions(),
        TargetPlatform.linux: _FadeSlideTransitions(),
      },
    ),
  );
}

class _FadeSlideTransitions extends PageTransitionsBuilder {
  const _FadeSlideTransitions();

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    final curved = CurvedAnimation(parent: animation, curve: Curves.easeOutCubic);
    return FadeTransition(
      opacity: curved,
      child: SlideTransition(
        position: Tween<Offset>(begin: const Offset(0, 0.035), end: Offset.zero).animate(curved),
        child: child,
      ),
    );
  }
}
