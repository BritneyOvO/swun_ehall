import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../theme/catalog.dart';
import '../theme/pack.dart';

class AppSettings extends ChangeNotifier {
  bool ready = false;
  bool followSystem = true;
  bool checkUpdateOnLaunch = true;
  String packId = 'light';
  List<ThemePack> packs = [ThemePack.fallbackLight];
  String? _path;

  ThemePack get selected {
    for (final p in packs) {
      if (p.id == packId) return p;
    }
    return packs.first;
  }

  ThemePack resolve(Brightness platform) {
    if (followSystem) {
      final want = platform == Brightness.dark ? 'dark' : 'light';
      for (final p in packs) {
        if (p.id == want) return p;
      }
    }
    return selected;
  }

  ThemePack lightPack() {
    for (final p in packs) {
      if (p.id == 'light') return p;
    }
    return packs.firstWhere((p) => !p.isDark, orElse: () => packs.first);
  }

  ThemePack darkPack() {
    for (final p in packs) {
      if (p.id == 'dark') return p;
    }
    return packs.firstWhere((p) => p.isDark, orElse: () => packs.first);
  }

  Future<void> load() async {
    packs = await ThemeCatalog.load();
    try {
      final dir = await getApplicationSupportDirectory();
      _path = '${dir.path}/settings.json';
      final f = File(_path!);
      if (await f.exists()) {
        final m = jsonDecode(await f.readAsString());
        if (m is Map) {
          if (m['pack'] != null) packId = '${m['pack']}';
          if (m['followSystem'] is bool) {
            followSystem = m['followSystem'] as bool;
          } else if (m['theme'] == 'system') {
            followSystem = true;
          } else if (m['theme'] == 'light' || m['theme'] == 'dark') {
            followSystem = false;
            packId = '${m['theme']}';
          }
          if (m['checkUpdateOnLaunch'] is bool) {
            checkUpdateOnLaunch = m['checkUpdateOnLaunch'] as bool;
          }
        }
      }
    } catch (_) {}
    if (!packs.any((p) => p.id == packId)) packId = packs.first.id;
    ready = true;
    notifyListeners();
  }

  Future<void> setFollowSystem(bool v) async {
    followSystem = v;
    notifyListeners();
    await _save();
  }

  Future<void> setCheckUpdateOnLaunch(bool v) async {
    checkUpdateOnLaunch = v;
    notifyListeners();
    await _save();
  }

  Future<void> setPack(String id) async {
    if (!packs.any((p) => p.id == id)) return;
    packId = id;
    followSystem = false;
    notifyListeners();
    await _save();
  }

  Future<void> reloadPacks() async {
    final keep = packId;
    packs = await ThemeCatalog.load();
    if (!packs.any((p) => p.id == keep)) packId = packs.first.id;
    notifyListeners();
  }

  Future<String> importPack(String path) async {
    final id = await ThemeCatalog.importFile(path);
    await reloadPacks();
    await setPack(id);
    return id;
  }

  Future<void> deletePack(String id) async {
    await ThemeCatalog.deleteUserPack(id);
    if (packId == id) {
      packId = 'light';
      followSystem = true;
    }
    await reloadPacks();
    await _save();
  }

  Future<void> _save() async {
    final path = _path;
    if (path == null) return;
    try {
      await File(path).writeAsString(jsonEncode({
        'followSystem': followSystem,
        'pack': packId,
        'checkUpdateOnLaunch': checkUpdateOnLaunch,
      }));
    } catch (_) {}
  }
}
