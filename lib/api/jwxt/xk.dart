/// 正方自主选课 HTML / JSON 纯函数。无网络、无 JwxtClient。
library;

String _xkAttr(String html, String name) {
  final a = RegExp('name="$name"[^>]*value="([^"]*)"').firstMatch(html);
  if (a != null) return a.group(1) ?? '';
  final b = RegExp('value="([^"]*)"[^>]*name="$name"').firstMatch(html);
  return b?.group(1) ?? '';
}

/// 从选课入口页 HTML 解析轮次列表。
/// 每个 tab: `queryCourse(this,'01','<xkkz_id>','<njdm>','<zyh>','<xkkz_xh RSA串>')`
List<Map<String, dynamic>> parseXkRounds(String html) {
  final out = <Map<String, dynamic>>[];
  final re = RegExp(
    r"queryCourse\(this,'([^']+)','([^']+)','([^']+)','([^']+)','([^']*)'\)",
  );
  for (final m in re.allMatches(html)) {
    out.add({
      'kklxdm': m.group(1) ?? '',
      'xkkz_id': m.group(2) ?? '',
      'njdm_id': m.group(3) ?? '',
      'zyh_id': m.group(4) ?? '',
      'xkkz_xh': m.group(5) ?? '',
      // tab 文本在同一个 <li> 里，向前找最近的 >xxx<
      'kklxmc': _xkTabName(html, m.start),
    });
  }
  return out;
}

String _xkTabName(String html, int at) {
  // tab 文本在 onclick 所在 <a> 标签闭合与 </a> 之间：
  // …queryCourse(…)"> 主修课程</a>
  final open = html.indexOf('">', at);
  final gt = open < 0 ? -1 : html.indexOf('>', open);
  final stop = gt < 0 ? -1 : html.indexOf('</a>', gt);
  if (gt < 0 || stop < 0) return '';
  final name = html.substring(gt + 1, stop).trim();
  if (name.isEmpty || name.contains('input')) return '';
  return name;
}

/// 入口页学生画像隐藏字段（PartDisplay 必须原样回传）。
Map<String, String> extractXkProfile(String html) {
  const keys = [
    'xh_id', 'xqh_id', 'jg_id', 'jg_id_1', 'zyh_id', 'zyfx_id', 'njdm_id',
    'bh_id', 'xbm', 'xslbdm', 'mzm', 'xz', 'ccdm', 'xsbj', 'njdm_id_1',
    'zyh_id_1', 'xkxnm', 'xkxqm', 'xkkz_xh', 'jxbzbkg', 'jxbzhkg', 'qzz',
    'xkxfqzfs', 'xkmcjzxskcs', 'xszxzt',
  ];
  final out = <String, String>{};
  for (final k in keys) {
    final v = _xkAttr(html, k);
    if (v.isNotEmpty) out[k] = v;
  }
  return out;
}

/// Display.html 面板 hidden：rwlx/xklc 等，点 tab 后官网靠它们查课。
Map<String, String> parseXkPanel(String html) {
  final out = <String, String>{};
  for (final m in RegExp(
    r'<input[^>]*type="hidden"[^>]*>',
    caseSensitive: false,
  ).allMatches(html)) {
    final tag = m.group(0)!;
    final name = RegExp(r'\bname="([^"]+)"').firstMatch(tag)?.group(1);
    if (name == null || name.isEmpty) continue;
    out[name] = RegExp(r'\bvalue="([^"]*)"').firstMatch(tag)?.group(1) ?? '';
  }
  return out;
}

/// PartDisplay 查询体：轮次 + 画像 + Display 面板 + 分页。
///
/// 校方口径（zzxkYzb.js `loadCoursesByPaged`）：隐藏域 `jspage` 是已拉到的结束行，
/// 请求时 `kspage = jspage + 1`、`jspage = jspage + step`。第一页隐藏域为 0，
/// 所以请求必须是 `kspage=1, jspage=step`。`jspage=0` 会被服务端当成空区间。
Map<String, dynamic> buildXkQuery({
  required Map<String, dynamic> round,
  required Map<String, String> profile,
  String kchId = '',
  String kcmc = '',
  int page = 1,
  int size = 10,
  Map<String, String> panel = const {},
}) {
  final q = <String, dynamic>{
    'rwlx': '1',
    'xklc': '',
    'xkly': '0',
    'bklx_id': '',
    'sfkkjyxdnxq': '',
    'sfkkjyxdxnxq': '',
    'kzkcgs': '0',
    'jg_id': '',
    'gnjkxdnj': '',
    'bjgkczxbbjwcx': '',
    'zyfx_id': 'wfx',
    'sfkknj': '',
    'sfkkzy': '',
    'kzybkxy': '',
    'sfznkx': '',
    'zdkxms': '',
    'sfkxq': '',
    'bhbcyxkjxb': '',
    'sfkcfx': '',
    'kkbk': '',
    'kkbkdj': '',
    'bklbkcj': '',
    'sfkgbcx': '',
    'sfrxtgkcxd': '',
    'tykczgxdcs': '',
    'bbhzxjxb': '',
    'zxgbxkkg': '',
    'xkzgbj': '0',
    'rlkz': '0',
    'jxbzcxskg': '',
    'zh': '',
    'jxbzb': '',
    'cxbj': '',
    'fxbj': '',
  };
  const skipPanel = {
    'kspage',
    'jspage',
    'globJsPage',
    'isEnd',
    'js_kcrow',
  };
  for (final e in panel.entries) {
    if (e.value.isNotEmpty && !skipPanel.contains(e.key)) q[e.key] = e.value;
  }
  final range = xkPageRange(page, size: size);
  q.addAll({
    'xkkz_id': round['xkkz_id'],
    'xkkz_xh': round['xkkz_xh'],
    'kklxdm': round['kklxdm'],
    'njdm_id_1': profile['njdm_id_1'] ?? round['njdm_id'] ?? '',
    'zyh_id_1': profile['zyh_id_1'] ?? round['zyh_id'] ?? '',
    'zyh_id': round['zyh_id'] ?? profile['zyh_id'] ?? '',
    'njdm_id': round['njdm_id'] ?? profile['njdm_id'] ?? '',
    'zyfx_id': profile['zyfx_id'] ?? q['zyfx_id'] ?? 'wfx',
    'xqh_id': profile['xqh_id'] ?? '',
    'bh_id': profile['bh_id'] ?? '',
    'xh_id': profile['xh_id'] ?? '',
    'jg_id': profile['jg_id'] ?? profile['jg_id_1'] ?? q['jg_id'] ?? '',
    'xbm': profile['xbm'] ?? '',
    'xslbdm': profile['xslbdm'] ?? '',
    'mzm': profile['mzm'] ?? '',
    'xz': profile['xz'] ?? '',
    'ccdm': profile['ccdm'] ?? '',
    'xsbj': profile['xsbj'] ?? '',
    'xkxnm': profile['xkxnm'] ?? '',
    'xkxqm': profile['xkxqm'] ?? '',
    'kch_id': kchId,
    'kcmc': kcmc,
    'kspage': range.$1,
    'jspage': range.$2,
  });
  return q;
}

/// 金智 PartDisplay 闭区间：[kspage, jspage]，按课程行号 `kcrow`。
(int, int) xkPageRange(int page, {int size = 10}) {
  final s = size < 1 ? 10 : size;
  final p = page < 1 ? 1 : page;
  return ((p - 1) * s + 1, p * s);
}

/// 这一页是否已经到末尾。分页按 `kcrow`（课程），`tmpList` 一行可能是一个教学班。
bool xkPartDisplayDone(List<dynamic> rows, int size) {
  if (rows.isEmpty) return true;
  final kcs = [
    for (final r in rows)
      if (r is Map) int.tryParse('${r['kcrow'] ?? ''}'),
  ].whereType<int>();
  if (kcs.isEmpty) return rows.length < size;
  return (kcs.last - kcs.first + 1) < size;
}

/// PartDisplay 按教学班展开；列表页按课程去重，记下全部 jxb_id，余量取各班最大。
List<dynamic> xkCollapseByCourse(List<dynamic> rows) {
  final seen = <String, Map<String, dynamic>>{};
  final order = <String>[];
  final extra = <Map<String, dynamic>>[];
  for (final r in rows) {
    if (r is! Map) {
      continue;
    }
    final m = Map<String, dynamic>.from(r);
    final id = '${m['kch_id'] ?? ''}';
    if (id.isEmpty) {
      extra.add(m);
      continue;
    }
    final jxb = '${m['jxb_id'] ?? ''}';
    if (!seen.containsKey(id)) {
      m['jxb_ids'] = <String>[if (jxb.isNotEmpty) jxb];
      seen[id] = m;
      order.add(id);
      continue;
    }
    final g = seen[id]!;
    final ids = g['jxb_ids'];
    if (jxb.isNotEmpty && ids is List && !ids.contains(jxb)) ids.add(jxb);
    if (_xkRemainBetter(m, g)) {
      m['jxb_ids'] = ids;
      seen[id] = m;
    }
  }
  return [...[for (final id in order) seen[id]!], ...extra];
}

bool _xkRemainBetter(Map<String, dynamic> next, Map<String, dynamic> cur) {
  final a = xkRemain(next);
  final b = xkRemain(cur);
  if (b < 0 && a >= 0) return true;
  return a > b;
}

/// 官网 `setRlxxAddZzxk`：已满是 `yxzrs >= jxbrl`。PartDisplay 的 blzyl 常年是 0，
/// 不能当成真实余量；容量未知时返回 -1，列表不要标已满。
int xkRemain(Map<String, dynamic> row) {
  final cap = _xkInt(row['jxbrl']) ?? _xkInt(row['jxbrs']);
  final used = _xkInt(row['yxzrs']);
  if (cap != null && cap > 0 && used != null) {
    final n = cap - used;
    return n < 0 ? 0 : n;
  }
  final bl = _xkInt(row['blzyl']);
  if (bl != null && bl > 0) return bl;
  final bx = _xkInt(row['blyxrs']);
  if (bx != null && bx > 0) return bx;
  if (cap != null && cap > 0) return cap;
  return -1;
}

int? _xkInt(Object? v) {
  if (v == null) return null;
  if (v is num) return v.toInt();
  final parsed = int.tryParse('$v'.trim());
  return parsed;
}

/// 官网 `$("#kcmc_"+kch_id).text()`：`(课号)课名 - xf 学分`（学分前有空格）。
String xkOfficialKcmcText({
  required String kch,
  required String kcmc,
  required String xf,
}) {
  final head = kch.isEmpty ? kcmc : '($kch)$kcmc';
  if (xf.isEmpty) return head;
  return '$head - $xf 学分';
}

/// 官网 `saveCourse`：容量控制打开时 `sxbj=1`，否则 `0`。
String xkOfficialSxbj({
  required String rlkz,
  required String cdrlkz,
  required String rlzlkz,
}) {
  if (rlkz == '1' || cdrlkz == '1' || rlzlkz == '1') return '1';
  return '0';
}

/// 官网 `saveCourse` 回调：成功返回 null，失败返回可展示的句子。
/// `flag=-1` 的 msg 是 `0,教学班id,已选人数,本轮已选`，不能直接弹给用户。
String? xkSubmitAlert(Map<dynamic, dynamic> resp) {
  final flag = '${resp['flag'] ?? resp['jg'] ?? ''}';
  final msg = '${resp['msg'] ?? ''}'.trim();
  if (flag == '1' || flag == '6' || flag == '3') return null;
  if (flag == '-1' || xkLooksCapacityMsg(msg)) {
    return '对不起，该教学班已无余量，不可选！';
  }
  if (flag == '2') {
    return msg.isEmpty ? '上课时间冲突' : msg;
  }
  if (msg.isEmpty) return '选课失败';
  return msg;
}

bool xkLooksCapacityMsg(String msg) {
  if (msg.isEmpty || RegExp(r'[\u4e00-\u9fff]').hasMatch(msg)) return false;
  final parts = msg.split(',');
  if (parts.length < 3) return false;
  return int.tryParse(parts.first.trim()) != null &&
      int.tryParse(parts[2].trim()) != null;
}

/// 官网 `saveCourse` POST 体，键名与插入顺序与 zzxkYzbChoosedZy.js 一致。
Map<String, String> xkSaveCourseBody({
  required String jxbIds,
  required String kchId,
  required String kcmc,
  required String rwlx,
  required String rlkz,
  required String cdrlkz,
  required String rlzlkz,
  required String sxbj,
  required String xxkbj,
  required String qz,
  required String cxbj,
  required String xkkzId,
  required String njdmId,
  required String zyhId,
  required String kklxdm,
  required String xklc,
  required String xkxnm,
  required String xkxqm,
  String jcxxId = '',
}) {
  return {
    'jxb_ids': jxbIds,
    'kch_id': kchId,
    'kcmc': kcmc,
    'rwlx': rwlx,
    'rlkz': rlkz,
    'cdrlkz': cdrlkz,
    'rlzlkz': rlzlkz,
    'sxbj': sxbj,
    'xxkbj': xxkbj,
    'qz': qz,
    'cxbj': cxbj,
    'xkkz_id': xkkzId,
    'njdm_id': njdmId,
    'zyh_id': zyhId,
    'kklxdm': kklxdm,
    'xklc': xklc,
    'xkxnm': xkxnm,
    'xkxqm': xkxqm,
    'jcxx_id': jcxxId,
  };
}

/// 已选教学班 id 集合（ChoosedDisplay JSON / Choosed.html hidden）。
Set<String> xkChoosedIds(List<dynamic> items) {
  const keys = [
    'jxb_id',
    'kch_id',
    't_kch_id',
    'right_jxb_id',
    'right_sub_kchid',
    'right_kchid',
  ];
  return {
    for (final it in items)
      if (it is Map)
        for (final k in keys)
          if ('${it[k] ?? ''}'.isNotEmpty) '${it[k]}',
  };
}

bool xkRowPicked(Map<String, dynamic> row, Set<String> choosed) {
  if (choosed.isEmpty) return false;
  for (final k in const ['jxb_id', 'kch_id', 't_kch_id']) {
    final v = '${row[k] ?? ''}';
    if (v.isNotEmpty && choosed.contains(v)) return true;
  }
  final extra = row['jxb_ids'];
  if (extra is Iterable) {
    for (final id in extra) {
      if (choosed.contains('$id')) return true;
    }
  }
  return false;
}

List<dynamic> xkRowsOf(Object? d) {
  if (d is List) return d;
  if (d is Map) {
    for (final k in const ['tmpList', 'items', 'list', 'data']) {
      final v = d[k];
      if (v is List) return v;
    }
  }
  return const [];
}

/// 官网 Choosed.html 右侧已选：hidden `right_jxb_id` / `right_sub_kchid`。
List<Map<String, String>> xkParseChoosed(String html) {
  final jxb = RegExp(r'name="right_jxb_id"[^>]*value="([^"]*)"|value="([^"]*)"[^>]*name="right_jxb_id"');
  final kch = RegExp(r'name="right_sub_kchid"[^>]*value="([^"]*)"|value="([^"]*)"[^>]*name="right_sub_kchid"');
  final jxbs = [for (final m in jxb.allMatches(html)) m.group(1) ?? m.group(2) ?? ''];
  final kchs = [for (final m in kch.allMatches(html)) m.group(1) ?? m.group(2) ?? ''];
  final n = jxbs.length > kchs.length ? jxbs.length : kchs.length;
  return [
    for (var i = 0; i < n; i++)
      {
        'jxb_id': i < jxbs.length ? jxbs[i] : '',
        'kch_id': i < kchs.length ? kchs[i] : '',
      },
  ];
}
