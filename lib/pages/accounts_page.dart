import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../state/accounts.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';

class AccountsPage extends StatelessWidget {
  const AccountsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final s = context.watch<Session>();
    return Scaffold(
      appBar: AppBar(title: const Text('账号管理')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
        children: [
          if (s.accounts.items.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Text('还没有保存的账号', textAlign: TextAlign.center, style: TextStyle(color: context.muted)),
            )
          else
            DecoratedBox(
              decoration: BoxDecoration(
                color: context.panel,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: context.line),
              ),
              child: Column(
                children: [
                  for (var i = 0; i < s.accounts.items.length; i++) ...[
                    if (i > 0) Divider(height: 1, indent: 16, endIndent: 16, color: context.line),
                    _tile(context, s, s.accounts.items[i]),
                  ],
                ],
              ),
            ),
          const SizedBox(height: 12),
          FilledButton.tonal(
            onPressed: s.busy ? null : () => pushPage(context, const AddAccountPage()),
            child: const Text('添加账号'),
          ),
          const SizedBox(height: 8),
          Text(
            '每个账号的登录状态和教室位置保存在本机私有目录，互不影响。',
            style: TextStyle(color: context.muted, fontSize: 12, height: 1.4),
          ),
        ],
      ),
    );
  }

  Widget _tile(BuildContext context, Session s, SavedAccount a) {
    final on = s.loggedIn && !s.demoMode && (a.id == s.studentId || a.id == s.accounts.currentId);
    return ListTile(
      title: Text(a.label, style: const TextStyle(fontSize: 15)),
      subtitle: a.name.isEmpty ? null : Text(a.id, style: TextStyle(color: context.muted, fontSize: 12)),
      leading: Icon(on ? Icons.person_rounded : Icons.person_outline_rounded, color: context.ink),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (on)
            Icon(Icons.check_rounded, color: Theme.of(context).colorScheme.primary, size: 20)
          else
            IconButton(
              tooltip: '切换',
              onPressed: s.busy ? null : () => s.switchTo(a.id),
              icon: Icon(Icons.swap_horiz_rounded, color: context.ink, size: 22),
            ),
          IconButton(
            tooltip: '删除',
            onPressed: s.busy ? null : () => _confirmDelete(context, s, a),
            icon: Icon(Icons.delete_rounded, color: context.muted, size: 20),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmDelete(BuildContext context, Session s, SavedAccount a) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除账号'),
        content: Text('确定删除 ${a.label}？该账号的登录状态和教室位置会从本机删除。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('删除')),
        ],
      ),
    );
    if (ok == true) await s.removeAccount(a.id);
  }
}

class AddAccountPage extends StatefulWidget {
  const AddAccountPage({super.key});

  @override
  State<AddAccountPage> createState() => _AddAccountPageState();
}

class _AddAccountPageState extends State<AddAccountPage> {
  final _user = TextEditingController();
  final _pass = TextEditingController();
  bool _hide = true;

  @override
  void dispose() {
    _user.dispose();
    _pass.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final s = context.read<Session>();
    await s.login(_user.text, _pass.text);
    if (!mounted) return;
    if (s.loggedIn && !s.demoMode) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<Session>();
    return Scaffold(
      appBar: AppBar(title: const Text('添加账号')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
        children: [
          TextField(
            controller: _user,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: '学号'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _pass,
            obscureText: _hide,
            decoration: InputDecoration(
              labelText: '密码',
              suffixIcon: IconButton(
                icon: Icon(_hide ? Icons.visibility_rounded : Icons.visibility_off_rounded, size: 20, color: context.muted),
                onPressed: () => setState(() => _hide = !_hide),
              ),
            ),
          ),
          if (s.error != null) ...[
            const SizedBox(height: 12),
            Text(s.error!, style: const TextStyle(color: kCrimson, fontSize: 13)),
          ],
          const SizedBox(height: 24),
          FilledButton(
            onPressed: s.busy ? null : _submit,
            child: s.busy ? const SwunBusyDots(color: Colors.white, size: 5) : const Text('登录并切换'),
          ),
        ],
      ),
    );
  }
}
