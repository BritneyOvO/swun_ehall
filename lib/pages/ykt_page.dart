import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../api/httpx.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';

class YktPage extends StatefulWidget {
  const YktPage({super.key});

  @override
  State<YktPage> createState() => _YktPageState();
}

class _YktPageState extends State<YktPage> {
  String? _payload;
  String? _error;
  double? _balanceYuan;
  bool _loading = true;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload(first: true));
    _timer = Timer.periodic(const Duration(seconds: 55), (_) => _reload());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _reload({bool first = false}) async {
    final s = context.read<Session>();
    if (first) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      if (s.demoMode) {
        await Future<void>.delayed(const Duration(milliseconds: 300));
        if (!mounted) return;
        setState(() {
          _payload = 'SWUN-DEMO-YKT-${s.studentId.isEmpty ? '202430000000' : s.studentId}';
          _balanceYuan = 18.7;
          _error = null;
          _loading = false;
        });
        return;
      }
      final qr = s.ykt!.fetchQr(
        studentId: s.studentId,
        schoolId: '${s.lantu?.schoolId ?? 187}',
      ).timeout(const Duration(seconds: 22), onTimeout: () => throw Exception('一卡通请求超时'));
      final brief = () async {
        try {
          return await s.lantu?.getCardBrief();
        } catch (_) {
          return null;
        }
      }();
      final got = await qr;
      final info = await brief;
      if (!mounted) return;
      setState(() {
        _payload = got.payload;
        _balanceYuan = _yuanOf(info);
        _error = null;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = publicError(e);
        _loading = false;
      });
    }
  }

  double? _yuanOf(Map<String, dynamic>? info) {
    if (info == null) return null;
    final main = info['cardBreifInfo'];
    Object? v;
    if (main is Map) {
      final md = main['mainData'];
      if (md is Map) v = md['value'];
    }
    v ??= info['value'];
    final n = int.tryParse('$v');
    if (n == null) return null;
    return n / 100.0;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('一卡通'),
        actions: [
          IconButton(onPressed: () => _reload(first: true), icon: const Icon(Icons.refresh_rounded)),
        ],
      ),
      body: _loading && _payload == null
          ? const Center(child: SwunLoader())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (_error != null)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(_error!, style: const TextStyle(color: kCrimson)),
                    ),
                  ),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Padding(
                      padding: EdgeInsets.only(bottom: 6),
                      child: Text('余额', style: TextStyle(color: kMuted, fontSize: 13)),
                    ),
                    const Spacer(),
                    TweenAnimationBuilder<double>(
                      tween: Tween(begin: 0, end: _balanceYuan ?? 0),
                      duration: const Duration(milliseconds: 500),
                      curve: Curves.easeOutCubic,
                      builder: (context, v, _) => Text(
                        _balanceYuan == null ? '--' : '¥ ${v.toStringAsFixed(2)}',
                        style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 22, 20, 18),
                    child: Column(
                      children: [
                        const Text('付款码', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                        const SizedBox(height: 16),
                        if (_payload == null)
                          const Padding(
                            padding: EdgeInsets.all(32),
                            child: Text('暂无二维码'),
                          )
                        else
                          TweenAnimationBuilder<double>(
                            key: ValueKey(_payload),
                            tween: Tween(begin: 0.94, end: 1),
                            duration: const Duration(milliseconds: 360),
                            curve: Curves.easeOutCubic,
                            builder: (context, t, child) => Opacity(
                              opacity: t.clamp(0.0, 1.0),
                              child: Transform.scale(scale: t, child: child),
                            ),
                            child: QrImageView(
                              data: _payload!,
                              size: 240,
                              backgroundColor: Colors.white,
                              eyeStyle: const QrEyeStyle(eyeShape: QrEyeShape.square, color: kInk),
                              dataModuleStyle: const QrDataModuleStyle(
                                dataModuleShape: QrDataModuleShape.square,
                                color: kInk,
                              ),
                            ),
                          ),
                        const SizedBox(height: 12),
                        Text(
                          _payload == null ? '' : _payload!,
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: Colors.black45, fontSize: 12),
                        ),
                        const SizedBox(height: 8),
                        const Text('每分钟自动刷新', style: TextStyle(color: Colors.black54, fontSize: 13)),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  '温馨提示：二维码显示异常时请切换校园网后下拉刷新。付款码走一卡通瑞数网关，请勿截图长时间外传。',
                  style: TextStyle(color: Colors.black45, fontSize: 12, height: 1.4),
                ),
              ],
            ),
    );
  }
}
