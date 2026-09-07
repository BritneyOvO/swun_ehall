import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../api/httpx.dart';
import '../api/ykt.dart';
import '../demo/demo_data.dart';
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
  String? _qrError;
  double? _balanceYuan;
  var _qrLoading = true;
  var _billsLoading = true;
  String? _billsError;
  List<YktBill> _bills = const [];
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload(first: true));
    _timer = Timer.periodic(const Duration(seconds: 55), (_) => _loadQr());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _reload({bool first = false}) async {
    await _loadQr(showSpinner: first && _payload == null);
    unawaited(_loadBills());
  }

  Future<void> _loadQr({bool showSpinner = false}) async {
    final s = context.read<Session>();
    if (showSpinner) {
      setState(() {
        _qrLoading = true;
        _qrError = null;
      });
    }
    try {
      if (s.demoMode) {
        await Future<void>.delayed(const Duration(milliseconds: 280));
        if (!mounted) return;
        setState(() {
          _payload =
              'SWUN-DEMO-YKT-${s.studentId.isEmpty ? '202430000000' : s.studentId}';
          _qrError = null;
          _qrLoading = false;
        });
        return;
      }
      final got = await s.ykt!
          .fetchQr(
            studentId: s.studentId,
            schoolId: '${s.lantu?.schoolId ?? 187}',
          )
          .timeout(
            const Duration(seconds: 55),
            onTimeout: () => throw Exception('一卡通请求超时'),
          );
      if (!mounted) return;
      setState(() {
        _payload = got.payload;
        _qrError = null;
        _qrLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _qrError = publicError(e);
        _qrLoading = false;
      });
    }
  }

  Future<void> _loadBills() async {
    final s = context.read<Session>();
    setState(() {
      _billsLoading = true;
      _billsError = null;
    });
    try {
      if (s.demoMode) {
        await Future<void>.delayed(const Duration(milliseconds: 700));
        if (!mounted) return;
        setState(() {
          _balanceYuan = 18.7;
          _bills = [
            for (final m in demoYktBills)
              YktBill(
                title: '${m['title']}',
                time: '${m['time']}',
                amountYuan: (m['amountYuan'] as num).toDouble(),
                balanceYuan: (m['balanceYuan'] as num?)?.toDouble(),
              ),
          ];
          _billsLoading = false;
        });
        return;
      }
      final led = await s.ykt!.fetchLedger(
        studentId: s.studentId,
        schoolId: '${s.lantu?.schoolId ?? 187}',
      );
      if (!mounted) return;
      setState(() {
        _bills = led.items;
        _balanceYuan = led.balanceYuan ?? _balanceYuan;
        _billsError = null;
        _billsLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _billsError = publicError(e);
        _billsLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('一卡通'),
        actions: [
          RefreshBusyButton(
            busy: _qrLoading,
            onPressed: () => _reload(first: true),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              const Padding(
                padding: EdgeInsets.only(bottom: 6),
                child: Text(
                  '余额',
                  style: TextStyle(color: kMuted, fontSize: 13),
                ),
              ),
              const Spacer(),
              TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: _balanceYuan ?? 0),
                duration: const Duration(milliseconds: 500),
                curve: Curves.easeOutCubic,
                builder: (context, v, _) => Text(
                  _balanceYuan == null ? '--' : '¥ ${v.toStringAsFixed(2)}',
                  style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _qrCard(),
          const SizedBox(height: 12),
          _billsCard(),
          const SizedBox(height: 12),
          const Text(
            '温馨提示：二维码显示异常时请切换校园网后点右上角刷新。付款码走一卡通瑞数网关，请勿截图长时间外传。',
            style: TextStyle(
              color: Colors.black45,
              fontSize: 12,
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }

  Widget _qrCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 22, 20, 18),
        child: Column(
          children: [
            const Text(
              '付款码',
              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
            ),
            const SizedBox(height: 16),
            if (_qrLoading && _payload == null)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 48),
                child: SwunLoader(),
              )
            else if (_payload == null)
              Padding(
                padding: const EdgeInsets.all(32),
                child: Text(
                  _qrError ?? '暂无二维码',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: _qrError == null ? Colors.black54 : kCrimson,
                  ),
                ),
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
                  eyeStyle: const QrEyeStyle(
                    eyeShape: QrEyeShape.square,
                    color: kInk,
                  ),
                  dataModuleStyle: const QrDataModuleStyle(
                    dataModuleShape: QrDataModuleShape.square,
                    color: kInk,
                  ),
                ),
              ),
            if (_payload != null) ...[
              const SizedBox(height: 12),
              Text(
                _payload!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.black45, fontSize: 12),
              ),
            ],
            const SizedBox(height: 8),
            const Text(
              '每分钟自动刷新',
              style: TextStyle(color: Colors.black54, fontSize: 13),
            ),
            if (_qrError != null && _payload != null) ...[
              const SizedBox(height: 8),
              Text(_qrError!, style: const TextStyle(color: kCrimson, fontSize: 12)),
            ],
          ],
        ),
      ),
    );
  }

  Widget _billsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '余额使用明细',
              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
            ),
            const SizedBox(height: 8),
            if (_billsLoading && _bills.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 28),
                child: Center(child: SwunLoader(compact: true)),
              )
            else if (_billsError != null && _bills.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 20),
                child: Center(
                  child: Text(
                    _billsError!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: kMuted, fontSize: 13),
                  ),
                ),
              )
            else if (_bills.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 20),
                child: Center(
                  child: Text(
                    '暂无明细',
                    style: TextStyle(color: kMuted, fontSize: 13),
                  ),
                ),
              )
            else
              for (var i = 0; i < _bills.length; i++) ...[
                if (i > 0) const Divider(height: 1),
                _billTile(_bills[i]),
              ],
          ],
        ),
      ),
    );
  }

  Widget _billTile(YktBill b) {
    final spend = b.amountYuan < 0;
    final color = spend ? kCrimson : const Color(0xFF2E9E5B);
    final sign = b.amountYuan > 0 ? '+' : (spend ? '-' : '');
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(
        b.title,
        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        [
          if (b.type.isNotEmpty) b.type,
          if (b.time.isNotEmpty) b.time,
          if (b.balanceYuan != null)
            '余额 ¥ ${b.balanceYuan!.toStringAsFixed(2)}',
        ].join('  '),
        style: const TextStyle(color: Colors.black54, fontSize: 12),
      ),
      trailing: Text(
        '$sign¥ ${b.amountYuan.abs().toStringAsFixed(2)}',
        style: TextStyle(
          color: color,
          fontWeight: FontWeight.w600,
          fontSize: 15,
        ),
      ),
    );
  }
}
