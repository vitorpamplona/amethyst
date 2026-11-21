import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:amethyst/models/contact.dart';
import 'package:amethyst/follow_sort.dart';

class FollowersPage extends StatefulWidget {
  final List<Contact> initialContacts;
  const FollowersPage({Key? key, required this.initialContacts}) : super(key: key);

  @override
  State<FollowersPage> createState() => _FollowersPageState();
}

class _FollowersPageState extends State<FollowersPage> {
  static const _prefKey = 'follow_sort_option';
  late FollowSortOption _currentOption;
  late List<Contact> _displayedContacts;

  @override
  void initState() {
    super.initState();
    _currentOption = FollowSortOption.chronological;
    _loadPreference();
    _applySorting();
  }

  Future<void> _loadPreference() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_prefKey);
    if (saved != null) {
      setState(() {
        _currentOption = FollowSortOption.values.firstWhere(
          (e) => e.toString() == saved,
          orElse: () => FollowSortOption.chronological,
        );
        _applySorting();
      });
    }
  }

  Future<void> _savePreference() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefKey, _currentOption.toString());
  }

  void _applySorting() {
    _displayedContacts = sortContacts(widget.initialContacts, _currentOption);
  }

  void _onSortChanged(FollowSortOption? option) {
    if (option == null) return;
    setState(() {
      _currentOption = option;
      _applySorting();
      _savePreference();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Followers'),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8.0),
            child: DropdownButton<FollowSortOption>(
              value: _currentOption,
              underline: const SizedBox(),
              icon: const Icon(Icons.sort, color: Colors.white),
              dropdownColor: Colors.grey[900],
              items: FollowSortOption.values.map((opt) {
                return DropdownMenuItem(
                  value: opt,
                  child: Text(opt.displayName, style: const TextStyle(color: Colors.white)),
                );
              }).toList(),
              onChanged: _onSortChanged,
            ),
          ),
        ],
      ),
      body: ListView.builder(
        itemCount: _displayedContacts.length,
        itemBuilder: (context, index) {
          final contact = _displayedContacts[index];
          return ListTile(
            leading: CircleAvatar(backgroundImage: NetworkImage(contact.avatarUrl)),
            title: Text(contact.displayName),
            subtitle: Text('Followers: ${contact.followerCount} • Sats Sent: ${contact.satsSent}'),
            onTap: () {
              // Navigate to contact profile (existing behaviour)
            },
          );
        },
      ),
    );
  }
}
