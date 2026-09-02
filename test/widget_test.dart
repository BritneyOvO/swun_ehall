import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/main.dart';

void main() {
  testWidgets('app boots', (tester) async {
    await tester.pumpWidget(const SwunApp());
    await tester.pump(const Duration(milliseconds: 900));
    expect(find.byType(SwunApp), findsOneWidget);
  });
}
