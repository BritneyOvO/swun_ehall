import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path_provider/path_provider.dart';
import 'package:swun_ehall/state/accounts.dart';
import 'package:swun_ehall/state/vault.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Android Keystore encrypts and decrypts account vault', (tester) async {
    const probe = '_keystore_probe';
    const secret = 'probe-secret-not-user';
    const legacy = 'legacy-plain-password';

    final sealed = await AccountVault.encrypt(secret);
    expect(sealed.iv, isNotEmpty);
    expect(sealed.ct, isNotEmpty);
    expect(sealed.ct.contains(secret), isFalse);
    expect(await AccountVault.decrypt(sealed.iv, sealed.ct), secret);

    final support = await getApplicationSupportDirectory();
    final store = AccountStore();
    await store.load(support);
    final previous = store.currentId;
    final dir = await store.ensureDir(probe);
    final vault = File('${dir.path}/vault.json');

    try {
      await vault.writeAsString(jsonEncode({'password': legacy}));
      expect(jsonDecode(await vault.readAsString())['password'], legacy);

      final migrated = await store.passwordOf(probe);
      expect(migrated, legacy);

      final body = await vault.readAsString();
      expect(body.contains(legacy), isFalse);
      expect(body.contains('"password"'), isFalse);
      final map = jsonDecode(body) as Map;
      expect(map['k'], 'AndroidKeyStore');
      expect(map['iv'], isNotEmpty);
      expect(map['ct'], isNotEmpty);

      expect(await store.passwordOf(probe), legacy);

      await store.upsert(id: probe, name: 'probe', password: secret);
      final after = jsonDecode(await vault.readAsString()) as Map;
      expect(after['password'], isNull);
      expect((await vault.readAsString()).contains(secret), isFalse);
      expect(await store.passwordOf(probe), secret);
    } finally {
      await store.remove(probe);
      if (previous != null && previous.isNotEmpty) {
        await store.setCurrent(previous);
      }
    }
  });
}
