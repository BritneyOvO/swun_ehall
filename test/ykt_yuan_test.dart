import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/ykt.dart';

void main() {
  test('parses 一卡通个人档案账户余额', () {
    expect(parseYktYuan('3.45元'), 3.45);
    expect(parseYktYuan('账户余额：3.45元'), 3.45);
    expect(parseYktYuan(''), isNull);
  });

  test('parses 官网 fun_bill.do {bill: [...]}', () {
    final items = parseYktBills({
      'bill': [
        {
          'area': '武侯校区',
          'tradeBranchName': '学生食堂',
          'consumeAmount': '8.00',
          'consumeTime': '2026-09-07 12:31:00',
          'generalOperateTypeName': '消费',
          'clientNo': '001',
        },
        {
          'area': '武侯校区',
          'tradeBranchName': '圈存机',
          'consumeAmount': '50.00',
          'consumeTime': '2026-09-06 09:12:00',
          'generalOperateTypeName': '圈存',
        },
        {
          'area': '航空港',
          'tradeBranchName': '支付宝',
          'consumeAmount': '100.00',
          'consumeTime': '2026-09-05 08:00:00',
          'generalOperateTypeName': '银行转账',
        },
        {
          'area': '航空港1-2食堂',
          'tradeBranchName': '一楼',
          'consumeAmount': '-9.5',
          'consumeTime': '2026-09-07 12:53:19',
          'generalOperateTypeName': '消费',
        },
      ],
    });
    expect(items, hasLength(4));
    expect(items[0].amountYuan, -8);
    expect(items[1].amountYuan, 50);
    expect(items[2].amountYuan, 100);
    expect(items[3].amountYuan, -9.5);
  });

  test('parses 账单 JSON list', () {
    final items = parseYktBills([
      {
        'dealName': '学生食堂',
        'dealTime': '2026-09-07 12:31',
        'monDeal': '-8.00',
        'accStatus': '10.70',
      },
    ]);
    expect(items, hasLength(1));
    expect(items.first.title, '学生食堂');
    expect(items.first.amountYuan, -8);
    expect(items.first.balanceYuan, 10.7);
  });

  test('parses 账单表格 cells and ignores 账户余额 line', () {
    final items = parseYktBills([
      {
        'cells': ['2026-09-07 12:31', '学生食堂', '-8.00', '10.70'],
      },
    ]);
    expect(items, hasLength(1));
    expect(items.first.title, '学生食堂');
    expect(items.first.time, '2026-09-07 12:31');
    expect(items.first.amountYuan, -8);

    expect(parseYktBillsFromHtml('账户余额：3.45元'), isEmpty);
    final fromText = parseYktBillsFromHtml(
      '2026-09-07 18:04 校园超市 -2.50\n账户余额：3.45元',
    );
    expect(fromText, hasLength(1));
    expect(fromText.first.title, '校园超市');
    expect(fromText.first.amountYuan, -2.5);
  });
}
