import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../demo/demo_data.dart';
import '../state/session.dart';
import '../widgets/async_body.dart';

class ExamsPage extends StatelessWidget {
  const ExamsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final session = context.watch<Session>();
    final future = session.demoMode
        ? Future.value({'items': demoExams})
        : session.jwxt!.exams();
    return Scaffold(
      appBar: AppBar(title: const Text('考试')),
      body: AsyncBody(
        future: future,
        builder: (context, data) {
          final items = (data['items'] as List?) ?? [];
          if (items.isEmpty) return const Center(child: Text('本学期暂无考试安排'));
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, i) {
              final e = Map<String, dynamic>.from(items[i] as Map);
              return Card(
                child: ListTile(
                  title: Text('${e['kcmc'] ?? ''}'),
                  subtitle: Text(
                    '${e['kssj'] ?? e['ksqssj'] ?? ''}  ${e['cdmc'] ?? e['ksdd'] ?? ''}\n'
                    '${e['zwh'] != null ? '座位 ${e['zwh']}' : ''}',
                  ),
                  isThreeLine: true,
                ),
              );
            },
          );
        },
      ),
    );
  }
}
