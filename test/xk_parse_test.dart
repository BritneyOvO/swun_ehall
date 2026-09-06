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

  test('buildXkQuery carries round, profile and paging', () {
    final rounds = parseXkRounds(entryHtml);
    final profile = extractXkProfile(entryHtml);
    final q = buildXkQuery(round: rounds[0], profile: profile, kcmc: '英语', page: 2, size: 10);
    expect(q['xkkz_id'], '58B9C612CBFE5DB7');
    expect(q['xkkz_xh'], 'abc123hex');
    expect(q['kklxdm'], '01');
    expect(q['njdm_id'], '2024');
    expect(q['zyh_id'], '1106');
    expect(q['xqh_id'], '2');
    expect(q['xkxnm'], '2026');
    expect(q['xkxqm'], '3');
    expect(q['kcmc'], '英语');
    expect(q['kspage'], 2);
    expect(q['jspage'], 11); // page*size - size + 1
    expect(q['gnjkxdnj'], '');
  });

  test('xkRemain prefers blzyl then blyxrs then capacity-used', () {
    expect(xkRemain({'blzyl': '3'}), 3);
    expect(xkRemain({'blzyl': null, 'blyxrs': 5}), 5);
    expect(xkRemain({'jxbrl': '54', 'yxzrs': '44'}), 10);
    expect(xkRemain({'jxbrl': '50', 'yxzrs': '50'}), 0);
    expect(xkRemain({}), 0);
  });

  test('xkChoosedIds collects non-empty jxb ids', () {
    final ids = xkChoosedIds([
      {'jxb_id': 'A1'},
      {'jxb_id': ''},
      {'other': 'x'},
      {'jxb_id': 'B2'},
    ]);
    expect(ids, {'A1', 'B2'});
  });
}
