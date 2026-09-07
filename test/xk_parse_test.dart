import 'package:flutter_test/flutter_test.dart';

import 'package:swun_ehall/api/jwxt.dart';

void main() {
  const entryHtml = '''
  <input type="hidden" name="xh_id" id="xh_id" value="202430809152"/>
  <input type="hidden" name="xqh_id" id="xqh_id" value="2"/>
  <input type="hidden" name="xkxnm" id="xkxnm" value="2026"/>
  <input type="hidden" name="xkxqm" id="xkxqm" value="3"/>
  <input type="hidden" name="bh_id" id="bh_id" value="42480C5A"/>
  <ul class="nav nav-tabs" id="nav_tab">
    <li class="active"><a onclick="queryCourse(this,'01','58B9C612CBFE5DB7','2024','1106','abc123hex')">
      主修课程</a></li>
    <input type="hidden" name="firstKklxdm" id="firstKklxdm" value="01"/>
    <li><a onclick="queryCourse(this,'10','58B9C612CC285DB7','2024','1106','def456hex')">
      通识选修课</a></li>
  </ul>
  ''';

  test('parses selection rounds from entry html', () {
    final rounds = parseXkRounds(entryHtml);
    expect(rounds, hasLength(2));
    expect(rounds[0]['kklxdm'], '01');
    expect(rounds[0]['xkkz_id'], '58B9C612CBFE5DB7');
    expect(rounds[0]['njdm_id'], '2024');
    expect(rounds[0]['zyh_id'], '1106');
    expect(rounds[0]['xkkz_xh'], 'abc123hex');
    expect(rounds[0]['kklxmc'], '主修课程');
    expect(rounds[1]['kklxdm'], '10');
    expect(rounds[1]['xkkz_xh'], 'def456hex');
  });

  test('extracts profile hidden fields', () {
    final p = extractXkProfile(entryHtml);
    expect(p['xh_id'], '202430809152');
    expect(p['xqh_id'], '2');
    expect(p['xkxnm'], '2026');
    expect(p['xkxqm'], '3');
    expect(p['bh_id'], '42480C5A');
    expect(p.containsKey('jg_id_1'), isFalse);
  });

  test('buildXkQuery carries round, profile, panel and paging', () {
    final rounds = parseXkRounds(entryHtml);
    final profile = extractXkProfile(entryHtml);
    final q = buildXkQuery(
      round: rounds[0],
      profile: profile,
      kcmc: '英语',
      page: 2,
      size: 10,
      panel: {'rwlx': '2', 'xklc': '3', 'jg_id': '99'},
    );
    expect(q['xkkz_id'], '58B9C612CBFE5DB7');
    expect(q['xkkz_xh'], 'abc123hex');
    expect(q['kklxdm'], '01');
    expect(q['njdm_id'], '2024');
    expect(q['zyh_id'], '1106');
    expect(q['xqh_id'], '2');
    expect(q['xh_id'], '202430809152');
    expect(q['xkxnm'], '2026');
    expect(q['xkxqm'], '3');
    expect(q['kcmc'], '英语');
    expect(q['rwlx'], '2');
    expect(q['xklc'], '3');
    expect(q['jg_id'], '99');
    expect(q['kspage'], 11);
    expect(q['jspage'], 20);
  });

  test('first page is a closed range kspage=1 jspage=size, not jspage=0', () {
    expect(xkPageRange(1, size: 10), (1, 10));
    expect(xkPageRange(2, size: 10), (11, 20));
    expect(xkPageRange(1, size: 30), (1, 30));
    final rounds = parseXkRounds(entryHtml);
    final profile = extractXkProfile(entryHtml);
    final q = buildXkQuery(round: rounds[0], profile: profile);
    expect(q['kspage'], 1);
    expect(q['jspage'], 10);
  });

  test('xkPartDisplayDone uses kcrow span not tmpList length', () {
    expect(xkPartDisplayDone(const [], 10), isTrue);
    expect(
      xkPartDisplayDone([
        {'kcrow': '1'},
        {'kcrow': '1'},
        {'kcrow': '10'},
      ], 10),
      isFalse,
    );
    expect(
      xkPartDisplayDone([
        {'kcrow': '11'},
        {'kcrow': '15'},
      ], 10),
      isTrue,
    );
  });

  test('xkCollapseByCourse keeps first teaching class per course', () {
    final rows = xkCollapseByCourse([
      {'kch_id': 'A', 'jxb_id': '1', 'kcmc': '英'},
      {'kch_id': 'A', 'jxb_id': '2', 'kcmc': '英'},
      {'kch_id': 'B', 'jxb_id': '3', 'kcmc': '数'},
    ]);
    expect(rows, hasLength(2));
    expect(rows[0]['jxb_id'], '1');
    expect(rows[1]['kch_id'], 'B');
  });

  test('parseXkPanel reads Display hidden fields', () {
    const html = '''
      <input type="hidden" name="rwlx" id="rwlx" value="1"/>
      <input type="hidden" id="xklc" name="xklc" value="2"/>
      <input type="hidden" name="xkly" value="0"/>
    ''';
    final p = parseXkPanel(html);
    expect(p['rwlx'], '1');
    expect(p['xklc'], '2');
    expect(p['xkly'], '0');
  });

  test('xkRemain uses jxbrl-yxzrs like official setRlxxAddZzxk', () {
    expect(xkRemain({'jxbrl': '54', 'yxzrs': '44'}), 10);
    expect(xkRemain({'jxbrl': '50', 'yxzrs': '50'}), 0);
    expect(xkRemain({'blzyl': '0', 'blyxrs': '0', 'jxbrl': '50', 'yxzrs': '46'}), 4);
    expect(xkRemain({'blzyl': '0', 'blyxrs': '0'}), -1);
    expect(xkRemain({'blzyl': '3'}), 3);
    expect(xkRemain({'blyxrs': 5}), 5);
    expect(xkRemain({}), -1);
  });

  test('xkRowPicked matches course id even when jxb differs', () {
    expect(
      xkRowPicked({'jxb_id': 'J1', 'kch_id': 'C1'}, {'C1'}),
      isTrue,
    );
    expect(
      xkRowPicked({'jxb_id': 'J1', 'kch_id': 'C1', 'jxb_ids': ['J2']}, {'J2'}),
      isTrue,
    );
    expect(xkRowPicked({'jxb_id': 'J1', 'kch_id': 'C1'}, {'X'}), isFalse);
  });

  test('xkChoosedIds collects non-empty jxb ids', () {
    final ids = xkChoosedIds([
      {'jxb_id': 'A1'},
      {'jxb_id': ''},
      {'other': 'x'},
      {'jxb_id': 'B2'},
    ]);
    expect(ids, {'A1', 'B2'});
    expect(xkChoosedIds([{'t_kch_id': 'C1', 'kch_id': 'C1', 'jxb_id': 'J1'}]), {'C1', 'J1'});
  });

  test('xkRowsOf reads tmpList then items', () {
    expect(xkRowsOf({'tmpList': [{'kch_id': '1'}], 'items': []}), hasLength(1));
    expect(xkRowsOf({'items': [{'kch_id': '2'}]}), hasLength(1));
    expect(xkRowsOf(0), isEmpty);
    expect(xkRowsOf('<html></html>'), isEmpty);
  });

  test('xkParseChoosed reads right_jxb_id and right_sub_kchid', () {
    const html = '''
      <input type="hidden" name="right_jxb_id" value="J1"/>
      <input type="hidden" name="right_sub_kchid" value="C1"/>
    ''';
    final rows = xkParseChoosed(html);
    expect(rows, hasLength(1));
    expect(rows[0]['jxb_id'], 'J1');
    expect(rows[0]['kch_id'], 'C1');
    expect(xkChoosedIds(rows), {'J1', 'C1'});
  });

  test('xkOfficialKcmcText matches #kcmc_kch_id .text()', () {
    expect(
      xkOfficialKcmcText(kch: '11180500', kcmc: '专业英语（网络工程）', xf: '2.0'),
      '(11180500)专业英语（网络工程） - 2.0 学分',
    );
  });

  test('xkOfficialSxbj is 1 when any capacity lock is on', () {
    expect(xkOfficialSxbj(rlkz: '0', cdrlkz: '0', rlzlkz: '1'), '1');
    expect(xkOfficialSxbj(rlkz: '1', cdrlkz: '0', rlzlkz: '0'), '1');
    expect(xkOfficialSxbj(rlkz: '0', cdrlkz: '0', rlzlkz: '0'), '0');
  });

  test('xkSaveCourseBody is saveCourse fields in official order', () {
    final body = xkSaveCourseBody(
      jxbIds: 'DO',
      kchId: '11180500',
      kcmc: '(11180500)专业英语（网络工程） - 2.0 学分',
      rwlx: '1',
      rlkz: '0',
      cdrlkz: '0',
      rlzlkz: '1',
      sxbj: '1',
      xxkbj: '0',
      qz: '0',
      cxbj: '0',
      xkkzId: 'XKKZ',
      njdmId: '2024',
      zyhId: '1106',
      kklxdm: '01',
      xklc: '9',
      xkxnm: '2026',
      xkxqm: '3',
    );
    expect(body.keys.toList(), [
      'jxb_ids',
      'kch_id',
      'kcmc',
      'rwlx',
      'rlkz',
      'cdrlkz',
      'rlzlkz',
      'sxbj',
      'xxkbj',
      'qz',
      'cxbj',
      'xkkz_id',
      'njdm_id',
      'zyh_id',
      'kklxdm',
      'xklc',
      'xkxnm',
      'xkxqm',
      'jcxx_id',
    ]);
    expect(body.containsKey('jxb_id'), isFalse);
    expect(body.containsKey('do_jxb_id'), isFalse);
    expect(body.containsKey('cxkz'), isFalse);
    expect(body['sxbj'], '1');
    expect(body['jcxx_id'], '');
  });

  test('xkSubmitAlert maps flag -1 capacity tuple to 已无余量', () {
    expect(xkSubmitAlert({'flag': '1'}), isNull);
    expect(xkSubmitAlert({'flag': '6'}), isNull);
    expect(
      xkSubmitAlert({
        'flag': '-1',
        'msg': '0,502B0AD390FA1F50E0630C0EF00A4749,44,',
      }),
      '对不起，该教学班已无余量，不可选！',
    );
    expect(
      xkSubmitAlert({'flag': '0', 'msg': '警告:你正在非法操作！'}),
      '警告:你正在非法操作！',
    );
    expect(
      xkSubmitAlert({'flag': '2', 'msg': '专业英语'}),
      '专业英语',
    );
  });
}
