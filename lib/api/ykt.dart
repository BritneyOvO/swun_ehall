import 'dart:convert';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import 'httpx.dart';
import 'rs_gateway.dart';

const kYktH5 = 'https://ykth5.swun.edu.cn';
const kYktMenu = '$kYktH5/menu/menu.do?menu=qrcode';
const kYktQrApi = '$kYktH5/qrcode/queryCardInfo.do';
const kYktBillApi = '$kYktH5/bill/fun_bill.do';

class YktQr {
  const YktQr({required this.payload, this.expire});

  final String payload;
  final String? expire;
}

class YktBill {
  const YktBill({
    required this.title,
    required this.time,
    required this.amountYuan,
    this.balanceYuan,
    this.type = '',
  });

  final String title;
  final String time;
  final double amountYuan;
  final double? balanceYuan;
  final String type;
}

class YktLedger {
  const YktLedger({this.balanceYuan, this.items = const []});

  final double? balanceYuan;
  final List<YktBill> items;
}

class YktClient {
  YktClient(this.jar, {RsGateway? gateway}) : dio = buildDio(jar) {
    rs = gateway ?? RsGateway(jar: jar);
  }

  final CookieJar jar;
  final Dio dio;
  late final RsGateway rs;
  bool nightClosed = false;

  Future<RsHit> _postQr(String referer) {
    return rs.request(
      method: 'POST',
      url: kYktQrApi,
      headers: {
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'Referer': referer,
      },
    );
  }

  Future<YktQr> fetchQr({required String studentId, String schoolId = '187'}) {
    return _fetchQr(studentId: studentId, schoolId: schoolId).timeout(
      const Duration(seconds: 50),
      onTimeout: () => throw Exception('一卡通请求超时'),
    );
  }

  Future<YktLedger> fetchLedger({
    required String studentId,
    String schoolId = '187',
  }) {
    return _fetchLedger(studentId: studentId, schoolId: schoolId).timeout(
      const Duration(seconds: 50),
      onTimeout: () => throw Exception('一卡通明细超时'),
    );
  }

  Future<String> _openFunction({
    required String studentId,
    required String schoolId,
    required String menu,
  }) async {
    final host = Uri.parse(kYktH5).host;
    await rs.navigate(kYktMenu);
    final ts = await _menuTimestamp(host);
    if (ts.isEmpty) throw Exception('一卡通页面未就绪');
    final fn =
        '$kYktH5/menu/function.do?expire=$ts&stu_code=$studentId&acco_id=$studentId&school_id=$schoolId&menu=$menu';
    await rs.navigate(fn);
    return fn;
  }

  Future<double?> _readBalanceYuan() async {
    final host = Uri.parse(kYktH5).host;
    final raw = (await rs.evalJs(host, r'''
      try {
        var ps = document.getElementsByTagName('p');
        for (var i = 0; i < ps.length; i++) {
          var t = ps[i].innerText || '';
          if (t.indexOf('账户余额') >= 0) {
            var inp = ps[i].querySelector('input');
            if (inp && inp.value) return String(inp.value);
            return t;
          }
        }
        return '';
      } catch (e) { return ''; }
    ''')).trim();
    return parseYktYuan(raw);
  }

  Future<String> _menuTimestamp(String host) async {
    for (var i = 0; i < 8; i++) {
      final ts = (await rs.evalJs(host, r'''
        try {
          if (typeof timestamp === 'string' && timestamp.length) return timestamp;
          if (window.timestamp) return String(window.timestamp);
          var h = document.documentElement ? document.documentElement.innerHTML : '';
          var m = h.match(/var\s+timestamp\s*=\s*'([^']+)'/);
          return m ? m[1] : '';
        } catch (e) { return ''; }
      ''')).trim();
      if (ts.isNotEmpty) return ts;
      await Future<void>.delayed(const Duration(milliseconds: 200));
    }
    return '';
  }

  Future<YktQr> _fetchQr({
    required String studentId,
    String schoolId = '187',
  }) async {
    if (studentId.trim().isEmpty) throw Exception('没有学号，无法打开一卡通');
    final host = Uri.parse(kYktH5).host;
    rs.remember(host);
    final fn = await _openFunction(
      studentId: studentId,
      schoolId: schoolId,
      menu: 'qrcode',
    );
    await Future<void>.delayed(const Duration(milliseconds: 280));
    final hit = await _postQr(fn);
    if (looksNightClosed(status: hit.status, data: hit.body)) {
      nightClosed = true;
      throw Exception(nightClosedMessage('一卡通'));
    }
    if (looksLikeRuishu(status: hit.status, body: hit.body)) {
      throw Exception('一卡通二维码仍被网关拦截');
    }
    final payload = _payloadOf(hit.body);
    if (payload == null || payload.isEmpty) {
      throw Exception('用户无卡片，无法生成二维码');
    }
    nightClosed = false;
    return YktQr(payload: payload);
  }

  Future<YktLedger> _fetchLedger({
    required String studentId,
    String schoolId = '187',
  }) async {
    if (studentId.trim().isEmpty) throw Exception('没有学号，无法打开一卡通');
    final host = Uri.parse(kYktH5).host;
    rs.remember(host);
    final fn = await _openFunction(
      studentId: studentId,
      schoolId: schoolId,
      menu: 'bill',
    );
    await Future<void>.delayed(const Duration(milliseconds: 280));
    // 官网账单页 check()：POST /bill/fun_bill.do，data='date='+date，首屏 date='null'
    final hit = await rs.request(
      method: 'POST',
      url: kYktBillApi,
      data: 'date=null',
      headers: {
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'Referer': fn,
      },
    );
    _yktTrace(
      'fun_bill HTTP ${hit.status} ${hit.body.length}b ${hit.body.substring(0, hit.body.length < 600 ? hit.body.length : 600)}',
    );
    if (looksNightClosed(status: hit.status, data: hit.body)) {
      nightClosed = true;
      throw Exception(nightClosedMessage('一卡通'));
    }
    if (looksLikeRuishu(status: hit.status, body: hit.body)) {
      throw Exception('一卡通明细仍被网关拦截');
    }
    final items = parseYktBills(hit.body);
    _yktTrace('bills n=${items.length}');
    double? balance;
    try {
      await _openFunction(
        studentId: studentId,
        schoolId: schoolId,
        menu: 'data',
      );
      await Future<void>.delayed(const Duration(milliseconds: 200));
      balance = await _readBalanceYuan();
    } catch (_) {}
    return YktLedger(balanceYuan: balance, items: items);
  }

  String? _payloadOf(String body) {
    final t = body.trim();
    if (t.isEmpty || t == 'null') return null;
    try {
      final d = jsonDecode(t);
      if (d == null) return null;
      if (d is String) return d.trim().isEmpty ? null : d.trim();
      if (d is num) return '$d';
      if (d is Map) {
        for (final k in const [
          'qrcode',
          'qrCode',
          'code',
          'data',
          'barCode',
          'barcode',
          'msg',
        ]) {
          final v = '${d[k] ?? ''}'.trim();
          if (v.isNotEmpty && v != 'null') return v;
        }
      }
    } catch (_) {}
    if (t.startsWith('"') && t.endsWith('"') && t.length > 2) {
      return t.substring(1, t.length - 1);
    }
    if (t.startsWith('<')) return null;
    return t;
  }
}

/// 一卡通个人档案「账户余额：3.45元」.
double? parseYktYuan(String raw) {
  final m = RegExp(r'(-?\d+(?:\.\d+)?)').firstMatch(raw.replaceAll(',', ''));
  if (m == null) return null;
  return double.tryParse(m.group(1)!);
}

/// 官网账单查询 JSON / 表格 / 纯文本。
List<YktBill> parseYktBills(Object? raw) {
  if (raw == null) return const [];
  if (raw is String) {
    final s = raw.trim();
    if (s.isEmpty) return const [];
    if (s.startsWith('{') || s.startsWith('[')) {
      try {
        return parseYktBills(jsonDecode(s));
      } catch (_) {
        return parseYktBillsFromHtml(s);
      }
    }
    return parseYktBillsFromHtml(s);
  }
  if (raw is List) {
    final out = <YktBill>[];
    for (final e in raw) {
      if (e is Map) {
        final bill = _billFromMap(Map<String, dynamic>.from(e));
        if (bill != null) out.add(bill);
      }
    }
    return out;
  }
  if (raw is Map) {
    final m = Map<String, dynamic>.from(raw);
    for (final k in const [
      'bill',
      'list',
      'rows',
      'data',
      'items',
      'tmpList',
      'result',
    ]) {
      final v = m[k];
      if (v is List || v is Map) {
        final nested = parseYktBills(v);
        if (nested.isNotEmpty) return nested;
      }
    }
    final one = _billFromMap(m);
    return one == null ? const [] : [one];
  }
  return const [];
}

List<YktBill> parseYktBillsFromHtml(String text) {
  final lines = text
      .split(RegExp(r'[\r\n]+'))
      .map((e) => e.replaceAll(RegExp(r'\s+'), ' ').trim())
      .where((e) => e.isNotEmpty)
      .toList();
  final out = <YktBill>[];
  for (final line in lines) {
    final bill = _billFromLine(line);
    if (bill != null) out.add(bill);
  }
  if (out.isNotEmpty) return out;
  // 表格单元格 JSON：{cells:[时间, 商户, 金额, 余额]}
  return const [];
}

YktBill? _billFromMap(Map<String, dynamic> m) {
  if (m['cells'] is List) {
    return _billFromCells([
      for (final c in m['cells'] as List) '$c'.trim(),
    ]);
  }
  final text = '${m['text'] ?? ''}'.trim();
  if (text.isNotEmpty) return _billFromLine(text);

  final area = '${m['area'] ?? ''}'.trim();
  final branch = '${m['tradeBranchName'] ?? ''}'.trim();
  var title = [if (area.isNotEmpty) area, if (branch.isNotEmpty) branch].join('-');
  if (title.isEmpty) {
    title = _firstNonEmpty(m, const [
      'generalOperateTypeName',
      'dealName',
      'mercName',
      'shmc',
      'title',
      'remark',
      'recName',
      'typeName',
      'jyzy',
      'name',
    ]);
  }
  final amountRaw = _firstNonEmpty(m, const [
    'consumeAmount',
    'monDeal',
    'txamt',
    'amount',
    'jyje',
    'dealMoney',
    'money',
    'txAmt',
    'je',
  ]);
  var amount = _amountOf(m);
  final type = _firstNonEmpty(m, const [
    'generalOperateTypeName',
    'typeName',
    'dealType',
  ]);
  // 官网 consumeAmount 已带符号（消费 "-9.5"）；没符号时只把明确的消费记成支出。
  final signed = amountRaw.startsWith('+') || amountRaw.startsWith('-');
  if (!signed && amount != null && amount > 0 && _yktSpendType(type)) {
    amount = -amount;
  }
  if (amount == null) return null;
  final time = _firstNonEmpty(m, const [
    'consumeTime',
    'dealTime',
    'jysj',
    'occTime',
    'recDate',
    'time',
    'txdate',
    'date',
  ]);
  final bal = _amountOf(m, const [
    'accStatus',
    'ye',
    'balance',
    'accBal',
    'balanceYuan',
  ]);
  return YktBill(
    title: title.isEmpty ? (type.isEmpty ? '消费' : type) : title,
    time: time,
    amountYuan: amount,
    balanceYuan: bal,
    type: type,
  );
}

YktBill? _billFromCells(List<String> cells) {
  final joined = cells.join(' ');
  if (RegExp(r'时间|商户|金额|余额|摘要').hasMatch(joined) &&
      !RegExp(r'-?\d+\.\d{2}').hasMatch(joined)) {
    return null;
  }
  return _billFromLine(joined, cells: cells);
}

YktBill? _billFromLine(String line, {List<String>? cells}) {
  final amount = _yuanIn(line);
  if (amount == null) return null;
  if (line.contains('账户余额') &&
      !RegExp(r'[+-]\d+\.\d{2}').hasMatch(line) &&
      cells == null) {
    return null;
  }
  final time = RegExp(
    r'(\d{4}[-/]\d{1,2}[-/]\d{1,2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)',
  ).firstMatch(line)?.group(1) ?? '';
  var title = line;
  if (time.isNotEmpty) title = title.replaceFirst(time, '');
  title = title
      .replaceAll(RegExp(r'[+-]?\d+(?:\.\d+)?\s*元?'), ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
  if (title.isEmpty && cells != null) {
    for (final c in cells) {
      if (c == time) continue;
      if (_yuanIn(c) != null) continue;
      if (c.trim().isNotEmpty) {
        title = c.trim();
        break;
      }
    }
  }
  double? bal;
  if (cells != null && cells.length >= 4) {
    bal = _yuanIn(cells.last);
  }
  return YktBill(
    title: title.isEmpty ? '消费' : title,
    time: time,
    amountYuan: amount,
    balanceYuan: bal,
  );
}

double? _amountOf(Map<String, dynamic> m, [List<String>? keys]) {
  for (final k in keys ??
      const [
        'consumeAmount',
        'monDeal',
        'txamt',
        'amount',
        'jyje',
        'dealMoney',
        'money',
        'txAmt',
        'je',
      ]) {
    final v = m[k];
    if (v == null || '$v'.trim().isEmpty) continue;
    final raw = '$v'.replaceAll(',', '').replaceAll('元', '').trim();
    final n = double.tryParse(raw) ?? _yuanIn('$v');
    if (n != null) return n;
  }
  return null;
}

bool _yktSpendType(String type) {
  return type.contains('消费') ||
      type.contains('扣款') ||
      type.contains('扣费') ||
      type.contains('支出') ||
      type.contains('罚款');
}

void _yktTrace(String msg) {
  debugPrint('[ykt] $msg');
  for (final p in const [
    '/data/data/cn.edu.swun.swun_ehall/files/ykt.log',
    '/data/user/0/cn.edu.swun.swun_ehall/files/ykt.log',
  ]) {
    try {
      File(p).writeAsStringSync(
        '${DateTime.now().toIso8601String()} [ykt] $msg\n',
        mode: FileMode.append,
        flush: true,
      );
      return;
    } catch (_) {}
  }
}

String _firstNonEmpty(Map<String, dynamic> m, List<String> keys) {
  for (final k in keys) {
    final v = '${m[k] ?? ''}'.trim();
    if (v.isNotEmpty && v != 'null') return v;
  }
  return '';
}

double? _yuanIn(String raw) {
  final s = raw.replaceAll(',', '');
  final money = RegExp(r'(?<![\d.])([+-]?\d+\.\d{2})(?![\d])').firstMatch(s);
  if (money != null) return double.tryParse(money.group(1)!);
  final yuan = RegExp(r'([+-]?\d+(?:\.\d+)?)\s*元').firstMatch(s);
  if (yuan != null) return double.tryParse(yuan.group(1)!);
  return null;
}
