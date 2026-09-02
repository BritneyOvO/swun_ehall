import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../state/rooms.dart';
import '../state/session.dart';
import '../theme.dart';

class RoomLocationsPage extends StatelessWidget {
  const RoomLocationsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final s = context.watch<Session>();
    final rooms = s.rooms.items;
    return Scaffold(
      appBar: AppBar(title: const Text('教室位置')),
      body: rooms.isEmpty
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Text(
                  '课堂签到成功后，会把教室名称和当时的经纬度保存在本机私有目录。',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: context.muted, fontSize: 14, height: 1.5),
                ),
              ),
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              itemCount: rooms.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, i) => _card(context, s, rooms[i]),
            ),
    );
  }

  Widget _card(BuildContext context, Session s, RoomFix room) {
    final when = room.at;
    final time = when == null
        ? ''
        : '${when.year.toString().padLeft(4, '0')}-${when.month.toString().padLeft(2, '0')}-${when.day.toString().padLeft(2, '0')} '
            '${when.hour.toString().padLeft(2, '0')}:${when.minute.toString().padLeft(2, '0')}';
    return DecoratedBox(
      decoration: BoxDecoration(
        color: context.panel,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: context.line),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 8, 14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: InkWell(
                onTap: () async {
                  await Clipboard.setData(ClipboardData(text: '${room.room}  ${room.coordText}'));
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已复制教室和坐标')));
                },
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(room.room, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    Text(room.coordText, style: const TextStyle(fontSize: 14)),
                    if (room.course.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(room.course, style: TextStyle(color: context.muted, fontSize: 13)),
                    ],
                    if (time.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        [
                          if (room.accuracy > 0) '精度 ${room.accuracy.toStringAsFixed(0)} 米',
                          '更新于 $time',
                        ].join(' · '),
                        style: TextStyle(color: context.muted, fontSize: 12),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            IconButton(
              tooltip: '删除',
              onPressed: () => s.forgetRoom(room.room),
              icon: Icon(Icons.delete_rounded, color: context.muted, size: 20),
            ),
          ],
        ),
      ),
    );
  }
}
