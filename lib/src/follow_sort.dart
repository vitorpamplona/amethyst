import 'package:amethyst/models/contact.dart';

enum FollowSortOption { chronological, satsSent, satsReceived, followerCount }

extension FollowSortOptionX on FollowSortOption {
  String get displayName {
    switch (this) {
      case FollowSortOption.chronological:
        return 'Chronological';
      case FollowSortOption.satsSent:
        return 'Sats Sent';
      case FollowSortOption.satsReceived:
        return 'Sats Received';
      case FollowSortOption.followerCount:
        return 'Follower Count';
    }
  }
}

List<Contact> sortContacts(List<Contact> contacts, FollowSortOption option) {
  final List<Contact> sorted = List.of(contacts);
  switch (option) {
    case FollowSortOption.chronological:
      sorted.sort((a, b) => a.createdAt.compareTo(b.createdAt));
      break;
    case FollowSortOption.satsSent:
      sorted.sort((b, a) => a.satsSent.compareTo(b.satsSent));
      break;
    case FollowSortOption.satsReceived:
      sorted.sort((b, a) => a.satsReceived.compareTo(b.satsReceived));
      break;
    case FollowSortOption.followerCount:
      sorted.sort((b, a) => a.followerCount.compareTo(b.followerCount));
      break;
  }
  return sorted;
}
