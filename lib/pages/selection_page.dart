import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/httpx.dart';
import '../api/jwxt.dart';
import '../demo/demo_data.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';

/// 自主选课：轮次 tab + 课程列表 + 教学班确认后提交。
class SelectionPage extends StatefulWidget {
  const SelectionPage({super.key});

  @override
  State<SelectionPage> createState() => _SelectionPageState();
}

class _SelectionPageState extends State<SelectionPage> {
  Future<Map<String, dynamic>>? _entry;
  var _busy = false;
  var _tab = 0;
  var _keyword = '';
  final _search = TextEditingController();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _entry ??= context.read<Session>().loadSelectionEntry();
  }

  void _reload() {
    if (_busy) return;
    final fut = context.read<Session>().loadSelectionEntry(force: true);
    setState(() {
      _busy = true;
      _tab = 0;
      _entry = fut;
    });
    fut.whenComplete(() {
      if (mounted) setState(() => _busy = false);
    });
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('选课'),
        actions: [RefreshBusyButton(busy: _busy, onPressed: _reload)],
      ),
      body: _body(context),
    );
  }

  Widget _body(BuildContext context) {
    final entry = _entry;
    if (entry == null) return const Center(child: SwunLoader());
    return FutureBuilder<Map<String, dynamic>>(
      future: entry,
      builder: (context, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return const Center(child: SwunLoader());
        }
        if (snap.hasError) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    publicError(snap.error!),
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: context.muted,
                      fontSize: 14,
                      height: 1.4,
                    ),
                  ),
                  const SizedBox(height: 16),
                  FilledButton.tonal(
                    onPressed: _busy ? null : _reload,
                    child: const Text('重试'),
                  ),
                ],
              ),
            ),
          );
        }
        final data = snap.data ?? const <String, dynamic>{};
        final rounds = [
          for (final r in (data['rounds'] as List?) ?? const [])
            Map<String, dynamic>.from(r as Map),
        ];
        if (rounds.isEmpty) {
          return Center(
            child: Text(
              '当前没有开放的选课轮次',
              style: TextStyle(color: context.muted, fontSize: 14),
            ),
          );
        }
        final tab = _tab.clamp(0, rounds.length - 1);
        final profile = Map<String, String>.from(
          data['profile'] as Map? ?? {},
        );
        return Column(
              children: [
                if (rounds.length > 1)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
                    child: _roundTabs(rounds, tab),
                  ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
                  child: TextField(
                    controller: _search,
                    textInputAction: TextInputAction.search,
                    decoration: InputDecoration(
                      isDense: true,
                      hintText: '搜索课程名',
                      prefixIcon: const Icon(Icons.search_rounded, size: 20),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                    ),
                    onSubmitted: (v) => setState(() => _keyword = v.trim()),
                  ),
                ),
                Expanded(
                  child: _XkBoard(
                    key: ValueKey('${rounds[tab]['xkkz_id']}|$_keyword'),
                    round: rounds[tab],
                    profile: profile,
                    keyword: _keyword,
                  ),
                ),
              ],
        );
      },
    );
  }

  Widget _roundTabs(List<Map<String, dynamic>> rounds, int tab) {
    return Row(
      children: [
        for (var i = 0; i < rounds.length; i++) ...[
          if (i > 0) const SizedBox(width: 8),
          Expanded(
            child: _roundTab(rounds[i], i == tab, () {
              if (i == _tab) return;
              setState(() => _tab = i);
            }),
          ),
        ],
      ],
    );
  }

  Widget _roundTab(Map<String, dynamic> r, bool active, VoidCallback onTap) {
    final label = '${r['kklxmc'] ?? r['kklxdm'] ?? '轮次'}';
    final bg = active ? context.primary : context.panel;
    final fg = active ? Colors.white : context.muted;
    return Pressable(
      radius: 12,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 9),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: context.line.withValues(alpha: 0.7)),
        ),
        child: Center(
          child: Text(
            label,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: fg,
            ),
          ),
        ),
      ),
    );
  }
}

class _XkBoard extends StatefulWidget {
  const _XkBoard({
    super.key,
    required this.round,
    required this.profile,
    required this.keyword,
  });

  final Map<String, dynamic> round;
  final Map<String, String> profile;
  final String keyword;

  @override
  State<_XkBoard> createState() => _XkBoardState();
}

class _XkBoardState extends State<_XkBoard> {
  Map<String, String> _panel = {};
  final _choosed = <String>{};
  var _loading = true;
  Object? _error;
  List<dynamic> _rows = const [];
  var _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    _run();
  }

  Future<void> _run() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final s = context.read<Session>();
      List<dynamic> rows;
      if (s.demoMode) {
        rows = [
          for (final c in demoXkCourses)
            if (widget.keyword.isEmpty ||
                '${c['kcmc']}'.contains(widget.keyword))
              c,
        ];
      } else {
        await s.ensureJwxt().timeout(const Duration(seconds: 25));
        if (!mounted) return;
        _panel = await s.loadSelectionPanel(widget.round, widget.profile);
        if (!mounted) return;
        rows = await s.loadSelectionCourses(
          widget.round,
          widget.profile,
          keyword: widget.keyword,
          panel: _panel,
          force: true,
        );
        if (!mounted) return;
        _loadChoosed();
      }
      if (!mounted) return;
      setState(() {
        _rows = rows;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Future<void> _loadChoosed() async {
    final s = context.read<Session>();
    if (s.demoMode || s.jwxt == null) return;
    try {
      final chosen = await s.jwxt!.selectionChoosed(const {});
      if (!mounted) return;
      setState(() {
        _choosed
          ..clear()
          ..addAll(xkChoosedIds(chosen));
      });
    } catch (e) {
      debugPrint('[xk] choosed $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: SwunLoader());
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                publicError(_error!),
                textAlign: TextAlign.center,
                style: TextStyle(color: context.muted, fontSize: 14, height: 1.4),
              ),
              const SizedBox(height: 16),
              FilledButton.tonal(onPressed: _run, child: const Text('重试')),
            ],
          ),
        ),
      );
    }
    return ColoredBox(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              '共 ${_rows.length} 门',
              style: TextStyle(fontSize: 13, color: context.muted),
            ),
          ),
          for (final raw in _rows)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _courseTile(
                raw is Map
                    ? Map<String, dynamic>.from(raw)
                    : <String, dynamic>{'kcmc': '$raw'},
              ),
            ),
        ],
      ),
    );
  }

  Widget _courseTile(Map<String, dynamic> row) {
    final remain = xkRemain(row);
    final jxbId = '${row['jxb_id'] ?? ''}';
    final kchId = '${row['kch_id'] ?? ''}';
    final picked =
        (jxbId.isNotEmpty && _choosed.contains(jxbId)) ||
        (jxbId.isEmpty && kchId.isNotEmpty && _choosed.contains(kchId));
    final accent = picked
        ? const Color(0xFF2E9E5B)
        : remain > 0
        ? context.primary
        : context.muted;
    return Material(
      color: Colors.white,
      elevation: 1,
      borderRadius: BorderRadius.circular(12),
      child: ListTile(
        title: Text(
          '${row['kcmc'] ?? ''}',
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w600,
            color: Colors.black,
          ),
        ),
        subtitle: Text(
          [
            if ('${row['kch'] ?? ''}'.isNotEmpty)
              '${row['kch']}'
            else if (kchId.isNotEmpty && kchId.length <= 16)
              kchId,
            '${row['xf'] ?? '?'}学分',
            picked ? '已选上' : (remain > 0 ? '余 $remain' : '已满'),
          ].join('  '),
          style: const TextStyle(color: Colors.black54),
        ),
        trailing: Text(
          picked ? '已选' : '选课',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: accent,
          ),
        ),
        onTap: picked ? null : () => _submit(row),
      ),
    );
  }

  Future<void> _submit(Map<String, dynamic> course) async {
    final s = context.read<Session>();
    final name = '${course['kcmc'] ?? ''}';

    Future<void> mask() {
      return showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (_) => const Center(child: SwunLoader()),
      );
    }

    void unmask() {
      final nav = Navigator.of(context, rootNavigator: true);
      if (nav.canPop()) nav.pop();
    }

    Map<String, dynamic>? jxb;
    try {
      if (s.demoMode) {
        jxb = course;
      } else {
        mask();
        List<Map<String, dynamic>> jxbs;
        try {
          final query = buildXkQuery(
            round: widget.round,
            profile: widget.profile,
            panel: _panel,
          );
          final raw = await s.jwxt!.selectionJxbs(
            query,
            '${course['kch_id'] ?? ''}',
            '${course['kcmc'] ?? ''}',
            cxbj: '${course['cxbj'] ?? ''}',
            fxbj: '${course['fxbj'] ?? ''}',
          );
          jxbs = [
            for (final e in raw)
              if (e is Map) Map<String, dynamic>.from(e),
          ];
        } finally {
          unmask();
        }
        if (!mounted) return;
        if (jxbs.isEmpty) throw Exception('没有可选教学班');
        jxb = await _pickJxb(name, jxbs);
        if (jxb == null || !mounted) return;
        final chosen = jxb;
        final doId = '${chosen['do_jxb_id'] ?? ''}';
        if (doId.isEmpty) {
          throw Exception('未拿到教学班加密串，请刷新后重试');
        }
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('确认选课'),
            content: Text(
              '确定选择「$name」\n${chosen['jsxx'] ?? chosen['jsmc'] ?? ''}\n${chosen['sksj'] ?? ''}？',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('确定'),
              ),
            ],
          ),
        );
        if (ok != true || !mounted) return;
        mask();
        try {
          final rlkz = _panel['rlkz'] ?? '0';
          final cdrlkz = _panel['cdrlkz'] ?? '0';
          final rlzlkz = _panel['rlzlkz'] ?? '0';
          final qzRaw = '${course['qz'] ?? '0'}';
          final resp = await s.jwxt!.selectionSubmit(
            jxbIds: doId,
            kchId: '${course['kch_id'] ?? ''}',
            kcmc: xkOfficialKcmcText(
              kch: '${course['kch'] ?? ''}',
              kcmc: name,
              xf: '${course['xf'] ?? chosen['xf'] ?? ''}',
            ),
            rwlx: _panel['rwlx'] ?? '',
            rlkz: rlkz,
            cdrlkz: cdrlkz,
            rlzlkz: rlzlkz,
            sxbj: xkOfficialSxbj(rlkz: rlkz, cdrlkz: cdrlkz, rlzlkz: rlzlkz),
            xxkbj: '${course['xxkbj'] ?? '0'}',
            qz: qzRaw.isEmpty ? '0' : qzRaw,
            cxbj: '${course['cxbj'] ?? '0'}',
            kklxdm: '${widget.round['kklxdm'] ?? ''}',
            xklc: _panel['xklc'] ?? '',
            xkkzId: '${widget.round['xkkz_id'] ?? ''}',
            njdmId: '${widget.round['njdm_id'] ?? ''}',
            zyhId: '${widget.round['zyh_id'] ?? ''}',
            xkxnm: widget.profile['xkxnm'] ?? '',
            xkxqm: widget.profile['xkxqm'] ?? '',
          );
          final alert = xkSubmitAlert(resp);
          if (alert != null) throw Exception(alert);
        } finally {
          unmask();
        }
      }
    } catch (e) {
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('选课失败'),
          content: Text(publicError(e)),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('知道了'),
            ),
          ],
        ),
      );
      return;
    }
    if (!mounted) return;
    final picked = jxb;
    final jxbId = '${picked['jxb_id'] ?? course['jxb_id'] ?? ''}';
    final kchId = '${course['kch_id'] ?? ''}';
    setState(() {
      if (jxbId.isNotEmpty) _choosed.add(jxbId);
      if (kchId.isNotEmpty) _choosed.add(kchId);
    });
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(
        content: Text('「$name」选课成功'),
        duration: const Duration(seconds: 3),
      ),
    );
  }

  String _jxbTitle(Map<String, dynamic> j) {
    final t = [
      if ('${j['jxbmc'] ?? ''}'.isNotEmpty) '${j['jxbmc']}',
      if ('${j['jsxx'] ?? j['jsmc'] ?? ''}'.isNotEmpty)
        '${j['jsxx'] ?? j['jsmc']}',
    ].join(' · ');
    return t.isEmpty ? '教学班' : t;
  }

  Future<Map<String, dynamic>?> _pickJxb(
    String courseName,
    List<Map<String, dynamic>> jxbs,
  ) async {
    return showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (ctx) {
        return SafeArea(
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            children: [
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Text(
                  '选择上课班级',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: ctx.ink,
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  courseName,
                  style: TextStyle(fontSize: 13, color: ctx.muted),
                ),
              ),
              for (final j in jxbs)
                Builder(
                  builder: (_) {
                    final remain = xkRemain(j);
                    final rl = '${j['jxbrl'] ?? '?'}';
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(_jxbTitle(j)),
                      subtitle: Text(
                        [
                          if ('${j['sksj'] ?? ''}'.isNotEmpty) '${j['sksj']}',
                          if ('${j['jxdd'] ?? ''}'.isNotEmpty) '${j['jxdd']}',
                          remain > 0
                              ? '余 $remain / $rl'
                              : '已满 ${j['yxzrs'] ?? '?'} / $rl',
                        ].join(' · '),
                      ),
                      onTap: () => Navigator.pop(ctx, j),
                    );
                  },
                ),
            ],
          ),
        );
      },
    );
  }
}
