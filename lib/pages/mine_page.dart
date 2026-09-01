import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/profile.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/motion.dart';
import 'clock_page.dart';
import 'credits_page.dart';
import 'exams_page.dart';
import 'ktkq_page.dart';
import 'rooms_page.dart';
import 'settings_page.dart';
import 'venue_page.dart';
import 'ykt_page.dart';

class MinePage extends StatefulWidget {
  const MinePage({super.key});

  @override
  State<MinePage> createState() => _MinePageState();
}

class _MinePageState extends State<MinePage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<Session>().refreshProfile();
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<Session>();
    final p = s.profile;
    final title = p.hasName ? p.name : (s.displayName.isEmpty ? '同学' : s.displayName);
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的'),
        automaticallyImplyLeading: false,
        actions: [
          IconButton(
            tooltip: '设置',
            onPressed: () => pushPage(context, const SettingsPage()),
            icon: PackIcon('action.settings', color: context.ink),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          FadeSlideIn(
            child: Pressable(
              onTap: () => pushPage(context, const SettingsPage()),
              child: _header(s, p, title),
            ),
          ),
          const SizedBox(height: 16),
          FadeSlideIn(
            delay: const Duration(milliseconds: 80),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: context.panel,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: context.line),
              ),
              child: Column(
                children: [
                  _item(context, Icons.qr_code_2, '一卡通付款码', const YktPage()),
                  Divider(height: 1, indent: 52, color: context.line),
                  _item(context, Icons.pie_chart_outline, '共修学分', const CreditsPage()),
                  Divider(height: 1, indent: 52, color: context.line),
                  _item(context, Icons.edit_calendar_outlined, '考试安排', const ExamsPage()),
                  Divider(height: 1, indent: 52, color: context.line),
                  _item(context, Icons.meeting_room_outlined, '空闲教室', const RoomsPage()),
                  Divider(height: 1, indent: 52, color: context.line),
                  _item(context, Icons.sports_outlined, '预约场馆', const VenuePage()),
                  Divider(height: 1, indent: 52, color: context.line),
                  _item(context, Icons.fingerprint, '课堂考勤', const KtkqPage()),
                  Divider(height: 1, indent: 52, color: context.line),
                  _item(context, Icons.location_on_outlined, '公寓打卡', const ClockPage()),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),
          TextButton(onPressed: () => s.logout(), child: const Text('退出登录')),
        ],
      ),
    );
  }

  Widget _header(Session s, StudentProfile p, String title) {
    final subtitle = s.demoMode
        ? '预览模式'
        : (s.profileLoading && !p.hasName ? '正在读取…' : (p.role.isEmpty ? '已登录' : p.role));
    ImageProvider? avatar;
    if (p.avatar.startsWith('http')) avatar = NetworkImage(p.avatar);
    return Row(
      children: [
        CircleAvatar(
          radius: 26,
          backgroundColor: context.line,
          backgroundImage: avatar,
          child: avatar == null
              ? Text(
                  title.isEmpty ? '同' : title.substring(0, 1),
                  style: TextStyle(color: context.ink, fontSize: 18, fontWeight: FontWeight.w600),
                )
              : null,
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text(subtitle, style: TextStyle(color: context.muted, fontSize: 13)),
            ],
          ),
        ),
        Icon(Icons.chevron_right, color: context.muted, size: 18),
        if (s.profileLoading)
          const Padding(
            padding: EdgeInsets.only(left: 8),
            child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
          ),
      ],
    );
  }

  Widget _item(BuildContext context, IconData icon, String title, Widget page) {
    return Pressable(
      radius: 0,
      onTap: () => pushPage(context, page),
      child: ListTile(
        leading: Icon(icon, color: context.ink, size: 22),
        title: Text(title, style: const TextStyle(fontSize: 15)),
        trailing: Icon(Icons.chevron_right, color: context.muted, size: 18),
      ),
    );
  }
}
