import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../demo/demo_data.dart';
import '../state/session.dart';
import '../widgets/async_body.dart';

class RoomsPage extends StatelessWidget {
  const RoomsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final session = context.watch<Session>();
    final future = session.demoMode
        ? Future.value({'items': demoRooms})
        : session.jwxt!.freeRooms();
    return Scaffold(
      appBar: AppBar(title: const Text('空闲教室')),
      body: AsyncBody(
        future: future,
        builder: (context, data) {
          final items = (data['items'] as List?) ?? [];
          if (items.isEmpty) {
            return const Center(child: Text('当前条件没有空闲教室\n可稍后在教务网页里按楼栋筛选'));
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, i) {
              final e = Map<String, dynamic>.from(items[i] as Map);
              return Card(
                child: ListTile(
                  leading: const Icon(Icons.meeting_room_outlined),
                  title: Text('${e['cdmc'] ?? e['jsmc'] ?? ''}'),
                  subtitle: Text('${e['xqmc'] ?? ''}  ${e['zws'] ?? e['kszws'] ?? ''} 座'),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
