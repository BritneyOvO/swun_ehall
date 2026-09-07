import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/room_sync.dart';
import 'package:swun_ehall/state/rooms.dart';

void main() {
  test('roomSyncEndpoint trims slash and empty', () {
    expect(roomSyncEndpoint(''), '');
    expect(roomSyncEndpoint('http://127.0.0.1:8787'), 'http://127.0.0.1:8787/v1/rooms');
    expect(
      roomSyncEndpoint('http://127.0.0.1:8787/'),
      'http://127.0.0.1:8787/v1/rooms',
    );
  });

  test('roomSyncBody keeps room and coords only', () {
    final room = RoomFix(
      id: '1',
      room: 'BS-217',
      latitude: 30.5,
      longitude: 103.9,
      accuracy: 12,
      course: '实训',
      at: DateTime.utc(2026, 9, 7, 11, 30),
    );
    final body = roomSyncBody(room);
    expect(body['id'], '1');
    expect(body['room'], 'BS-217');
    expect(body['latitude'], 30.5);
    expect(body['longitude'], 103.9);
    expect(body['accuracy'], 12);
    expect(body['course'], '实训');
    expect(body.containsKey('studentId'), isFalse);
    expect(body.containsKey('studentName'), isFalse);
    expect(body['at'], isNotEmpty);
  });
}
