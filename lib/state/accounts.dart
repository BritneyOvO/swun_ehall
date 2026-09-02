import 'dart:convert';
import 'dart:io';

import 'vault.dart';

class SavedAccount {
  const SavedAccount({required this.id, this.name = ''});

  final String id;
  final String name;

  String get label => name.trim().isEmpty ? id : name;
}

class AccountStore {
  Directory? _root;
  String? currentId;
  List<SavedAccount> items = [];

  Directory get root {
    final d = _root;
    if (d == null) throw StateError('AccountStore not loaded');
    return d;
  }

  bool get isEmpty => items.isEmpty;

  SavedAccount? get current {
    final id = currentId;
    if (id == null) return null;
    for (final a in items) {
      if (a.id == id) return a;
    }
    return null;
  }

  Future<void> load(Directory support) async {
    _root = support;
    final f = File('${support.path}/accounts.json');
    if (!await f.exists()) {
      await migrateLegacy(support);
      return;
    }
    try {
      final raw = jsonDecode(await f.readAsString());
      if (raw is! Map) {
        await migrateLegacy(support);
        await _sealPlainVaults();
        return;
      }
      currentId = '${raw['current'] ?? ''}'.trim();
      if (currentId != null && currentId!.isEmpty) currentId = null;
      final list = raw['accounts'];
      if (list is List) {
        items = [
          for (final e in list)
            if (e is Map && '${e['id'] ?? ''}'.trim().isNotEmpty)
              SavedAccount(id: '${e['id']}'.trim(), name: '${e['name'] ?? ''}'),
        ];
      }
    } catch (_) {}
    if (currentId != null && !items.any((a) => a.id == currentId)) {
      currentId = items.isEmpty ? null : items.first.id;
    }
    await migrateLegacy(support);
    await _sealPlainVaults();
  }

  Future<void> migrateLegacy(Directory support) async {
    if (items.isNotEmpty) return;
    final lantu = File('${support.path}/lantu.json');
    final cookies = Directory('${support.path}/cookies');
    final teachers = File('${support.path}/kb_teachers.json');
    if (!await lantu.exists() && !await cookies.exists()) return;
    var id = '';
    var name = '';
    if (await lantu.exists()) {
      try {
        final raw = jsonDecode(await lantu.readAsString());
        if (raw is Map) {
          final login = raw['userLoginInfo'];
          final base = raw['userBaseInfo'];
          if (login is Map) id = '${login['userName'] ?? ''}'.trim();
          if (base is Map) name = '${base['realName'] ?? ''}'.trim();
        }
      } catch (_) {}
    }
    if (id.isEmpty) return;
    final dir = await ensureDir(id);
    Future<void> moveFile(File src, String destName) async {
      if (!await src.exists()) return;
      final dest = File('${dir.path}/$destName');
      if (await dest.exists()) return;
      try {
        await src.rename(dest.path);
      } catch (_) {
        await src.copy(dest.path);
        await src.delete();
      }
    }

    Future<void> moveDir(Directory src, String destName) async {
      if (!await src.exists()) return;
      final dest = Directory('${dir.path}/$destName');
      if (await dest.exists()) return;
      try {
        await src.rename(dest.path);
      } catch (_) {}
    }

    await moveFile(lantu, 'lantu.json');
    await moveFile(teachers, 'kb_teachers.json');
    await moveDir(cookies, 'cookies');
    items = [SavedAccount(id: id, name: name)];
    currentId = id;
    await _saveIndex();
    await _sealPlainVaults();
  }

  Directory dirFor(String id) => Directory('${root.path}/accounts/${_safe(id)}');

  Future<Directory> ensureDir(String id) async {
    final d = dirFor(id);
    if (!await d.exists()) await d.create(recursive: true);
    return d;
  }

  Future<void> upsert({required String id, String name = '', String? password}) async {
    final sid = id.trim();
    if (sid.isEmpty) return;
    await ensureDir(sid);
    final i = items.indexWhere((a) => a.id == sid);
    final next = SavedAccount(id: sid, name: name.trim());
    if (i >= 0) {
      items[i] = SavedAccount(id: sid, name: next.name.isEmpty ? items[i].name : next.name);
    } else {
      items = [...items, next];
    }
    currentId = sid;
    if (password != null && password.isNotEmpty) {
      await _writeSecret(sid, password);
    }
    await _saveIndex();
  }

  Future<void> setCurrent(String id) async {
    currentId = id.trim();
    await _saveIndex();
  }

  Future<String?> passwordOf(String id) async {
    try {
      final f = File('${dirFor(id).path}/vault.json');
      if (!await f.exists()) return null;
      final raw = jsonDecode(await f.readAsString());
      if (raw is! Map) return null;
      final iv = '${raw['iv'] ?? ''}'.trim();
      final ct = '${raw['ct'] ?? ''}'.trim();
      if (iv.isNotEmpty && ct.isNotEmpty) {
        return await AccountVault.decrypt(iv, ct);
      }
      final legacy = '${raw['password'] ?? ''}'.trim();
      if (legacy.isEmpty) return null;
      await _writeSecret(id, legacy);
      return legacy;
    } catch (_) {
      return null;
    }
  }

  Future<void> _writeSecret(String id, String password) async {
    final sealed = await AccountVault.encrypt(password);
    final f = File('${dirFor(id).path}/vault.json');
    await f.parent.create(recursive: true);
    await f.writeAsString(
      jsonEncode({
        'v': 1,
        'k': 'AndroidKeyStore',
        'iv': sealed.iv,
        'ct': sealed.ct,
      }),
    );
  }

  Future<void> _sealPlainVaults() async {
    for (final a in items) {
      await passwordOf(a.id);
    }
  }

  Future<bool> hasPassword(String id) async {
    final p = await passwordOf(id);
    return p != null && p.isNotEmpty;
  }

  Future<void> remove(String id) async {
    final sid = id.trim();
    items = [for (final a in items) if (a.id != sid) a];
    if (currentId == sid) currentId = items.isEmpty ? null : items.first.id;
    final dir = dirFor(sid);
    if (await dir.exists()) {
      try {
        await dir.delete(recursive: true);
      } catch (_) {}
    }
    await _saveIndex();
  }

  Future<void> _saveIndex() async {
    final f = File('${root.path}/accounts.json');
    await f.writeAsString(
      jsonEncode({
        'current': currentId,
        'accounts': [
          for (final a in items) {'id': a.id, 'name': a.name},
        ],
      }),
    );
  }

  static String _safe(String id) {
    final s = id.trim();
    if (s.isEmpty) return '_';
    return s.replaceAll(RegExp(r'[^0-9A-Za-z._-]'), '_');
  }
}
