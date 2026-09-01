import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/profile.dart';
import '../state/session.dart';
import '../state/settings.dart';
import '../theme.dart';
import '../theme/catalog.dart';
import '../widgets/motion.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool _busy = false;
  String? _dirHint;

  @override
  void initState() {
    super.initState();
    ThemeCatalog.userDir().then((d) {
      if (mounted) setState(() => _dirHint = d.path);
    });
  }

  Future<void> _import() async {
    final file = await FilePicker.pickFile(
      type: FileType.custom,
      allowedExtensions: const ['zip', 'xml'],
    );
    final path = file?.path;
    if (path == null || !mounted) return;
    setState(() => _busy = true);
    try {
      final id = await context.read<AppSettings>().importPack(path);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('已导入主题包 $id')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reload() async {
    setState(() => _busy = true);
    try {
      await context.read<AppSettings>().reloadPacks();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<AppSettings>();
    final session = context.watch<Session>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('设置'),
        actions: [
          IconButton(
            tooltip: '重新扫描',
            onPressed: _busy ? null : _reload,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: RefreshIndicator(
        color: Theme.of(context).colorScheme.primary,
        onRefresh: () => session.refreshProfile(force: true),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            FadeSlideIn(child: Text('主题包', style: TextStyle(color: context.muted, fontSize: 13))),
            const SizedBox(height: 8),
            FadeSlideIn(
              delay: const Duration(milliseconds: 40),
              child: _panel(context, [
                SwitchListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16),
                  title: const Text('跟随系统', style: TextStyle(fontSize: 15)),
                  subtitle: Text('按系统浅色/深色自动切换内置主题包', style: TextStyle(color: context.muted, fontSize: 12)),
                  value: settings.followSystem,
                  activeThumbColor: Theme.of(context).colorScheme.primary,
                  onChanged: settings.setFollowSystem,
                ),
              ]),
            ),
            const SizedBox(height: 10),
            FadeSlideIn(
              delay: const Duration(milliseconds: 70),
              child: _panel(
                context,
                [
                  for (var i = 0; i < settings.packs.length; i++) ...[
                    if (i > 0) Divider(height: 1, indent: 16, color: context.line),
                    _packTile(context, settings, settings.packs[i]),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 10),
            FilledButton.tonal(
              onPressed: _busy ? null : _import,
              child: _busy
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('导入主题包'),
            ),
            const SizedBox(height: 8),
            Text(
              '支持 zip（内含 pack.xml 和 icons/）或单独的 pack.xml。图标用 src / outlined / filled 指向包内 png、webp、jpg。\n'
              '${_dirHint == null ? '' : '本地目录：$_dirHint'}',
              style: TextStyle(color: context.muted, fontSize: 12, height: 1.4),
            ),
            const SizedBox(height: 24),
            FadeSlideIn(
              delay: const Duration(milliseconds: 100),
              child: Text('个人信息', style: TextStyle(color: context.muted, fontSize: 13)),
            ),
            const SizedBox(height: 8),
            FadeSlideIn(delay: const Duration(milliseconds: 120), child: _profile(context, session)),
            if (session.profileError != null && !session.profile.hasDetails && !session.profile.hasName) ...[
              const SizedBox(height: 8),
              Text(
                '${session.profileError}，下拉可重试',
                textAlign: TextAlign.center,
                style: TextStyle(color: context.muted, fontSize: 12),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _panel(BuildContext context, List<Widget> children) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: context.panel,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: context.line),
      ),
      child: Column(children: children),
    );
  }

  Widget _packTile(BuildContext context, AppSettings settings, ThemePack pack) {
    final on = !settings.followSystem && settings.packId == pack.id;
    return ListTile(
      onTap: () => settings.setPack(pack.id),
      title: Text(pack.name, style: const TextStyle(fontSize: 15)),
      subtitle: Text(
        '${pack.builtin ? '内置' : '已导入'} · ${pack.isDark ? '深色' : '浅色'}'
        '${pack.author.isEmpty ? '' : ' · ${pack.author}'}',
        style: TextStyle(color: context.muted, fontSize: 12),
      ),
      leading: _preview(pack),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedOpacity(
            duration: const Duration(milliseconds: 180),
            opacity: on ? 1 : 0,
            child: Icon(Icons.check, color: Theme.of(context).colorScheme.primary, size: 20),
          ),
          if (!pack.builtin)
            IconButton(
              tooltip: '删除',
              onPressed: _busy
                  ? null
                  : () async {
                      await settings.deletePack(pack.id);
                    },
              icon: Icon(Icons.delete_outline, color: context.muted, size: 20),
            ),
        ],
      ),
    );
  }

  Widget _preview(ThemePack pack) {
    return Container(
      width: 36,
      height: 36,
      decoration: BoxDecoration(
        color: pack.background,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: pack.line),
      ),
      child: Center(
        child: Container(
          width: 14,
          height: 14,
          decoration: BoxDecoration(color: pack.primary, shape: BoxShape.circle),
        ),
      ),
    );
  }

  Widget _profile(BuildContext context, Session s) {
    final rows = _profileRows(s, s.profile);
    if (rows.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 4),
        child: Text(s.profileLoading ? '正在读取个人信息…' : '暂无个人信息', style: TextStyle(color: context.muted)),
      );
    }
    return _panel(context, [
      for (var i = 0; i < rows.length; i++) ...[
        if (i > 0) Divider(height: 1, indent: 16, endIndent: 16, color: context.line),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
          child: Row(
            children: [
              SizedBox(width: 56, child: Text(rows[i].$1, style: TextStyle(color: context.muted, fontSize: 13))),
              Expanded(child: Text(rows[i].$2, textAlign: TextAlign.right, style: const TextStyle(fontSize: 15))),
            ],
          ),
        ),
      ],
    ]);
  }

  List<(String, String)> _profileRows(Session s, StudentProfile p) {
    return [
      ('姓名', p.hasName ? p.name : ''),
      ('学号', p.studentId.isNotEmpty ? p.studentId : s.studentId),
      ('性别', p.gender),
      ('身份', p.role),
      ('学院', p.college),
      ('专业', p.major),
      ('班级', p.klass),
      ('年级', p.grade.isEmpty ? '' : (p.grade.endsWith('级') ? p.grade : '${p.grade}级')),
      ('校区', p.campus),
      ('手机', p.phone),
    ].where((e) => e.$2.trim().isNotEmpty).toList();
  }
}
