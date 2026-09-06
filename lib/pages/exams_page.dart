import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../state/session.dart';
import '../widgets/async_body.dart';
import '../widgets/loader.dart';

class ExamsPage extends StatefulWidget {
  const ExamsPage({super.key});

  @override
  State<ExamsPage> createState() => _ExamsPageState();
}

class _ExamsPageState extends State<ExamsPage> {
  Future<Map<String, dynamic>>? _future;
  var _busy = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _future ??= context.read<Session>().loadExams();
  }

  void _reload() {
    if (_busy) return;
    final fut = context.read<Session>().loadExams(force: true);
    setState(() {
      _busy = true;
      _future = fut;
    });
    fut.whenComplete(() {
      if (mounted) setState(() => _busy = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    final future = _future;
    return Scaffold(
      appBar: AppBar(
        title: const Text('考试'),
        actions: [
          RefreshBusyButton(busy: _busy, onPressed: _reload),
        ],
      ),
      body: future == null
          ? const Center(child: SwunLoader())
          : AsyncBody(
              future: future,
              onRetry: _busy ? null : _reload,
              builder: (context, data) {
                final items = (data['items'] as List?) ?? [];
                if (items.isEmpty) {
                  return const Center(child: Text('本学期暂无考试安排'));
                }
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
