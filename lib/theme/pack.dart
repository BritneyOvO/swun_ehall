import 'package:flutter/material.dart';

class PackIconDef {
  const PackIconDef({this.outlined, this.filled, this.src, this.tint = true});

  final String? outlined;
  final String? filled;
  final String? src;
  final bool tint;

  String? fileFor({required bool filled}) {
    final ordered = filled ? [this.filled, src, outlined] : [outlined, src, this.filled];
    for (final s in ordered) {
      if (s != null && s.isNotEmpty && looksLikeFile(s)) return s;
    }
    return null;
  }

  static bool looksLikeFile(String s) {
    final l = s.toLowerCase();
    return l.contains('/') ||
        l.endsWith('.png') ||
        l.endsWith('.webp') ||
        l.endsWith('.jpg') ||
        l.endsWith('.jpeg') ||
        l.endsWith('.gif') ||
        l.endsWith('.svg') ||
        l.endsWith('.bmp');
  }
}

class ThemePack {
  ThemePack({
    required this.id,
    required this.name,
    required this.brightness,
    required this.author,
    required this.colors,
    required this.layout,
    required this.icons,
    this.dir = '',
    this.builtin = true,
  });

  final String id;
  final String name;
  final Brightness brightness;
  final String author;
  final Map<String, Color> colors;
  final Map<String, String> layout;
  final Map<String, PackIconDef> icons;
  final String dir;
  final bool builtin;

  bool get isDark => brightness == Brightness.dark;

  Color color(String name, Color fallback) => colors[name] ?? fallback;

  Color get primary => color('primary', const Color(0xFF9B1B30));
  Color get onPrimary => color('onPrimary', Colors.white);
  Color get background => color('background', const Color(0xFFF4F2EE));
  Color get onBackground => color('onBackground', const Color(0xFF1C1917));
  Color get muted => color('muted', const Color(0xFF6F6A64));
  Color get line => color('line', const Color(0xFFE6E1DA));
  Color get card => color('card', Colors.white);
  Color get onCard => color('onCard', const Color(0xFF1C1917));

  double get cardRadius => double.tryParse(layout['cardRadius'] ?? '') ?? 12;
  double get buttonRadius => double.tryParse(layout['buttonRadius'] ?? '') ?? 10;
  String get navStyle => layout['navStyle'] ?? 'underline';
  String get homeHeader => layout['homeHeader'] ?? 'plain';
  int get homeGrid => int.tryParse(layout['homeGrid'] ?? '') ?? 3;
  bool get appBarCenter => layout['appBarCenter'] == 'true';

  PackIconDef? icon(String name) => icons[name];

  static ThemePack get fallbackLight => parse(_lightXml, dir: 'assets/themes/light');

  static ThemePack parse(String xml, {String dir = '', bool builtin = true}) {
    final cleaned = xml.replaceAll(RegExp(r'<!--.*?-->', dotAll: true), '');
    final root = RegExp(r'<theme\b([^>]*)>').firstMatch(cleaned);
    final attrs = _attrs(root?.group(1) ?? '');
    final colors = <String, Color>{};
    for (final m in RegExp(r'<color\s+name="([^"]+)">\s*([^<]+)\s*</color>').allMatches(cleaned)) {
      final c = _parseColor(m.group(2)!.trim());
      if (c != null) colors[m.group(1)!] = c;
    }
    final layout = <String, String>{};
    for (final m in RegExp(r'<item\s+name="([^"]+)">\s*([^<]+)\s*</item>').allMatches(cleaned)) {
      layout[m.group(1)!] = m.group(2)!.trim();
    }
    final icons = <String, PackIconDef>{};
    for (final m in RegExp(r'<icon\b([^>]*)/?>').allMatches(cleaned)) {
      final a = _attrs(m.group(1) ?? '');
      final name = a['name'];
      if (name == null || name.isEmpty) continue;
      icons[name] = PackIconDef(
        outlined: a['outlined'] ?? a['srcOutlined'],
        filled: a['filled'] ?? a['srcFilled'],
        src: a['src'],
        tint: a['tint'] != 'false',
      );
    }
    return ThemePack(
      id: attrs['id'] ?? 'custom',
      name: attrs['name'] ?? '未命名',
      brightness: attrs['brightness'] == 'dark' ? Brightness.dark : Brightness.light,
      author: attrs['author'] ?? '',
      colors: colors,
      layout: layout,
      icons: icons,
      dir: dir,
      builtin: builtin,
    );
  }

  static Map<String, String> _attrs(String raw) {
    final out = <String, String>{};
    for (final m in RegExp(r'(\w+)="([^"]*)"').allMatches(raw)) {
      out[m.group(1)!] = m.group(2)!;
    }
    return out;
  }

  static Color? _parseColor(String raw) {
    var s = raw.trim();
    if (s.startsWith('#')) s = s.substring(1);
    if (s.length == 6) s = 'FF$s';
    if (s.length != 8) return null;
    final v = int.tryParse(s, radix: 16);
    if (v == null) return null;
    return Color(v);
  }
}

const _lightXml = '''
<theme id="light" name="浅色" brightness="light" author="内置">
  <colors>
    <color name="primary">#9B1B30</color>
    <color name="onPrimary">#FFFFFF</color>
    <color name="background">#F4F2EE</color>
    <color name="onBackground">#1C1917</color>
    <color name="muted">#6F6A64</color>
    <color name="line">#E6E1DA</color>
    <color name="card">#FFFFFF</color>
    <color name="onCard">#1C1917</color>
  </colors>
  <layout>
    <item name="cardRadius">12</item>
    <item name="buttonRadius">10</item>
    <item name="navStyle">underline</item>
    <item name="homeHeader">plain</item>
    <item name="homeGrid">3</item>
    <item name="appBarCenter">false</item>
  </layout>
</theme>
''';
