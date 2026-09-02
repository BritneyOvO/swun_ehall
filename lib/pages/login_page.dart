import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _user = TextEditingController();
  final _pass = TextEditingController();
  bool _hide = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final s = context.read<Session>();
      if (_user.text.isEmpty && s.studentId.isNotEmpty) {
        _user.text = s.studentId;
      }
    });
  }

  @override
  void dispose() {
    _user.dispose();
    _pass.dispose();
    super.dispose();
  }

  Future<void> _pickSaved(Session session, String id) async {
    if (session.busy) return;
    final ok = await session.switchTo(id);
    if (ok || !mounted) return;
    _user.text = id;
    final pwd = await session.accounts.passwordOf(id);
    if (!mounted) return;
    if (pwd != null && pwd.isNotEmpty) _pass.text = pwd;
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<Session>();
    final saved = session.accounts.items;
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
          children: [
            FadeSlideIn(
              child: Row(
                children: [
                  Container(width: 3, height: 18, color: kCrimson),
                  const SizedBox(width: 8),
                  Text('西南民族大学', style: TextStyle(color: context.muted, fontSize: 13)),
                ],
              ),
            ),
            const SizedBox(height: 18),
            FadeSlideIn(
              delay: const Duration(milliseconds: 60),
              child: Text('登录', style: TextStyle(fontSize: 32, fontWeight: FontWeight.w600, height: 1.1, color: context.ink)),
            ),
            const SizedBox(height: 8),
            FadeSlideIn(
              delay: const Duration(milliseconds: 100),
              child: Text('使用统一身份认证账号', style: TextStyle(color: context.muted, fontSize: 14)),
            ),
            const SizedBox(height: 28),
            FadeSlideIn(
              delay: const Duration(milliseconds: 140),
              child: TextField(
                controller: _user,
                keyboardType: TextInputType.number,
                enabled: !session.busy,
                decoration: InputDecoration(
                  labelText: '学号',
                  suffixIcon: saved.isEmpty
                      ? null
                      : PopupMenuButton<String>(
                          tooltip: '已保存的账号',
                          enabled: !session.busy,
                          padding: EdgeInsets.zero,
                          icon: Icon(Icons.arrow_drop_down_rounded, color: context.muted, size: 28),
                          onSelected: (id) => _pickSaved(session, id),
                          itemBuilder: (context) => [
                            for (final a in saved)
                              PopupMenuItem(
                                value: a.id,
                                child: a.name.isEmpty
                                    ? Text(a.id)
                                    : Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Text(a.label, style: const TextStyle(fontSize: 15)),
                                          Text(a.id, style: TextStyle(fontSize: 12, color: context.muted)),
                                        ],
                                      ),
                              ),
                          ],
                        ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            FadeSlideIn(
              delay: const Duration(milliseconds: 180),
              child: TextField(
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
            ),
            if (session.error != null) ...[
              const SizedBox(height: 12),
              Text(session.error!, style: const TextStyle(color: kCrimson, fontSize: 13)),
            ],
            const SizedBox(height: 24),
            FadeSlideIn(
              delay: const Duration(milliseconds: 220),
              child: FilledButton(
                onPressed: session.busy ? null : () => session.login(_user.text, _pass.text),
                child: session.busy
                    ? const SwunBusyDots(color: Colors.white, size: 5)
                    : const Text('登录'),
              ),
            ),
            const SizedBox(height: 8),
            FadeSlideIn(
              delay: const Duration(milliseconds: 260),
              child: TextButton(
                onPressed: session.busy ? null : session.enterDemo,
                child: const Text('先看看界面'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
