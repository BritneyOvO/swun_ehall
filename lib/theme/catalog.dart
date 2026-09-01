import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'pack.dart';

class ThemeCatalog {
  ThemeCatalog._();

  static const builtin = ['light', 'dark'];

  static Future<Directory> userDir() async {
    final dir = await getApplicationSupportDirectory();
    final root = Directory('${dir.path}/themes');
    if (!await root.exists()) await root.create(recursive: true);
    return root;
  }

  static Future<List<ThemePack>> load() async {
    final out = <ThemePack>[];
    for (final id in builtin) {
      try {
        final xml = await rootBundle.loadString('assets/themes/$id/pack.xml');
        out.add(ThemePack.parse(xml, dir: 'assets/themes/$id'));
      } catch (_) {
        if (id == 'light') out.add(ThemePack.fallbackLight);
      }
    }
    try {
      final root = await userDir();
      final kids = root.list();
      await for (final entity in kids) {
        if (entity is! Directory) continue;
        final file = File('${entity.path}/pack.xml');
        if (!await file.exists()) continue;
        try {
          out.add(ThemePack.parse(await file.readAsString(), dir: entity.path, builtin: false));
        } catch (_) {}
      }
    } catch (_) {}
    if (out.isEmpty) out.add(ThemePack.fallbackLight);
    return out;
  }

  static String _safeId(String raw) {
    final s = raw.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
    if (s.isEmpty) return 'pack_${DateTime.now().millisecondsSinceEpoch}';
    if (builtin.contains(s)) return 'user-$s';
    return s;
  }

  /// 导入 zip 或 pack.xml，返回新包 id.
  static Future<String> importFile(String path) async {
    final lower = path.toLowerCase();
    if (lower.endsWith('.zip')) return _importZip(File(path));
    if (lower.endsWith('.xml')) return _importXml(File(path));
    throw Exception('请选择 .zip 主题包或 pack.xml');
  }

  static Future<String> _importXml(File xmlFile) async {
    if (!await xmlFile.exists()) throw Exception('找不到文件');
    final parsed = ThemePack.parse(await xmlFile.readAsString(), builtin: false);
    final id = _safeId(parsed.id);
    final dest = Directory(p.join((await userDir()).path, id));
    if (await dest.exists()) await dest.delete(recursive: true);
    await dest.create(recursive: true);
    await xmlFile.copy(p.join(dest.path, 'pack.xml'));
    final parent = xmlFile.parent;
    final icons = Directory(p.join(parent.path, 'icons'));
    if (await icons.exists()) {
      await _copyDir(icons, Directory(p.join(dest.path, 'icons')));
    }
    for (final def in parsed.icons.values) {
      for (final rel in [def.src, def.outlined, def.filled]) {
        if (rel == null || !PackIconDef.looksLikeFile(rel)) continue;
        final src = File(p.normalize(p.join(parent.path, rel)));
        if (!await src.exists()) continue;
        final out = File(p.join(dest.path, rel));
        await out.parent.create(recursive: true);
        await src.copy(out.path);
      }
    }
    return id;
  }

  static Future<String> _importZip(File zipFile) async {
    final bytes = await zipFile.readAsBytes();
    Archive archive;
    try {
      archive = ZipDecoder().decodeBytes(bytes);
    } catch (_) {
      throw Exception('无法解开 zip');
    }
    String? xmlPath;
    for (final f in archive) {
      final n = f.name.replaceAll('\\', '/');
      if (n.endsWith('/pack.xml') || n == 'pack.xml') {
        xmlPath = n;
        break;
      }
    }
    if (xmlPath == null) throw Exception('zip 里没有 pack.xml');
    final prefix = xmlPath == 'pack.xml' ? '' : xmlPath.substring(0, xmlPath.length - 'pack.xml'.length);
    final xmlFile = archive.files.firstWhere((f) => f.name.replaceAll('\\', '/') == xmlPath);
    final xml = utf8.decode(xmlFile.content);
    final parsed = ThemePack.parse(xml, builtin: false);
    final id = _safeId(parsed.id);
    final dest = Directory(p.join((await userDir()).path, id));
    if (await dest.exists()) await dest.delete(recursive: true);
    await dest.create(recursive: true);
    for (final f in archive) {
      if (!f.isFile) continue;
      var name = f.name.replaceAll('\\', '/');
      if (name.contains('..')) continue;
      if (prefix.isNotEmpty) {
        if (!name.startsWith(prefix)) continue;
        name = name.substring(prefix.length);
      }
      if (name.isEmpty) continue;
      final outPath = p.normalize(p.join(dest.path, name));
      if (!p.isWithin(dest.path, outPath)) continue;
      await File(outPath).parent.create(recursive: true);
      await File(outPath).writeAsBytes(f.content);
    }
    return id;
  }

  static Future<void> deleteUserPack(String id) async {
    if (builtin.contains(id)) throw Exception('不能删除内置主题包');
    final dir = Directory(p.join((await userDir()).path, id));
    if (await dir.exists()) await dir.delete(recursive: true);
  }

  static Future<void> _copyDir(Directory from, Directory to) async {
    await to.create(recursive: true);
    await for (final e in from.list(recursive: true, followLinks: false)) {
      final rel = p.relative(e.path, from: from.path);
      final dest = p.join(to.path, rel);
      if (e is Directory) {
        await Directory(dest).create(recursive: true);
      } else if (e is File) {
        await File(dest).parent.create(recursive: true);
        await e.copy(dest);
      }
    }
  }
}
