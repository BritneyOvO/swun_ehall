import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;

import 'pack.dart';

const kMaterialIcons = <String, IconData>{
  'home_outlined': Icons.home_outlined,
  'home_rounded': Icons.home_rounded,
  'calendar_view_week_outlined': Icons.calendar_view_week_outlined,
  'calendar_view_week': Icons.calendar_view_week,
  'menu_book_outlined': Icons.menu_book_outlined,
  'menu_book': Icons.menu_book,
  'person_outline': Icons.person_outline,
  'person': Icons.person,
  'qr_code_2': Icons.qr_code_2,
  'pie_chart_outline': Icons.pie_chart_outline,
  'edit_calendar_outlined': Icons.edit_calendar_outlined,
  'meeting_room_outlined': Icons.meeting_room_outlined,
  'fingerprint': Icons.fingerprint,
  'location_on_outlined': Icons.location_on_outlined,
  'sports_outlined': Icons.sports_outlined,
  'settings_outlined': Icons.settings_outlined,
  'brightness_auto_outlined': Icons.brightness_auto_outlined,
  'light_mode_outlined': Icons.light_mode_outlined,
  'dark_mode_outlined': Icons.dark_mode_outlined,
};

class PackIcon extends StatelessWidget {
  const PackIcon(this.name, {super.key, this.filled = false, this.size = 22, this.color});

  final String name;
  final bool filled;
  final double size;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final pack = ThemePackScope.of(context);
    final def = pack.icon(name);
    final file = def?.fileFor(filled: filled);
    if (file != null) {
      final abs = _abs(pack, file);
      final tint = def?.tint == true ? color : null;
      Widget img;
      if (pack.builtin || abs.startsWith('assets/')) {
        img = Image.asset(
          abs,
          width: size,
          height: size,
          color: tint,
          colorBlendMode: tint == null ? null : BlendMode.srcIn,
          filterQuality: FilterQuality.medium,
          errorBuilder: (_, _, _) => _material(def, filled, color),
        );
      } else {
        img = Image.file(
          File(abs),
          width: size,
          height: size,
          color: tint,
          colorBlendMode: tint == null ? null : BlendMode.srcIn,
          filterQuality: FilterQuality.medium,
          errorBuilder: (_, _, _) => _material(def, filled, color),
        );
      }
      return SizedBox(width: size, height: size, child: img);
    }
    return _material(def, filled, color);
  }

  String _abs(ThemePack pack, String rel) {
    if (rel.startsWith('assets/') || p.isAbsolute(rel)) return rel;
    if (pack.dir.isEmpty) return rel;
    return p.normalize(p.join(pack.dir, rel));
  }

  Widget _material(PackIconDef? def, bool filled, Color? color) {
    final key = filled ? (def?.filled ?? def?.outlined) : (def?.outlined ?? def?.filled);
    final data = (key != null && !PackIconDef.looksLikeFile(key) ? kMaterialIcons[key] : null) ?? Icons.circle_outlined;
    return Icon(data, size: size, color: color);
  }
}

class ThemePackScope extends InheritedWidget {
  const ThemePackScope({super.key, required this.pack, required super.child});

  final ThemePack pack;

  static ThemePack of(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<ThemePackScope>()?.pack ?? ThemePack.fallbackLight;
  }

  @override
  bool updateShouldNotify(ThemePackScope oldWidget) => oldWidget.pack.id != pack.id || oldWidget.pack != pack;
}
