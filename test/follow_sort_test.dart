import 'package:flutter_test/flutter_test.dart';
import 'package:amethyst/models/contact.dart';
import 'package:amethyst/follow_sort.dart';

void main() {
  final contacts = [
    Contact(pubkey: 'a', createdAt: DateTime.parse('2023-01-01'), satsSent: 100, satsReceived: 50, followerCount: 10, displayName: 'Alice', avatarUrl: ''),
    Contact(pubkey: 'b', createdAt: DateTime.parse('2023-02-01'), satsSent: 200, satsReceived: 150, followerCount: 20, displayName: 'Bob', avatarUrl: ''),
    Contact(pubkey: 'c', createdAt: DateTime.parse('2023-01-15'), satsSent: 150, satsReceived: 200, followerCount: 5, displayName: 'Carol', avatarUrl: ''),
  ];

  test('chronological sorting', () {
    final sorted = sortContacts(contacts, FollowSortOption.chronological);
    expect(sorted.map((c) => c.pubkey).toList(), ['a', 'c', 'b']);
  });

  test('sats sent sorting', () {
    final sorted = sortContacts(contacts, FollowSortOption.satsSent);
    expect(sorted.map((c) => c.pubkey).toList(), ['b', 'c', 'a']);
  });

  test('sats received sorting', () {
    final sorted = sortContacts(contacts, FollowSortOption.satsReceived);
    expect(sorted.map((c) => c.pubkey).toList(), ['c', 'b', 'a']);
  });

  test('follower count sorting', () {
    final sorted = sortContacts(contacts, FollowSortOption.followerCount);
    expect(sorted.map((c) => c.pubkey).toList(), ['b', 'a', 'c']);
  });
}
