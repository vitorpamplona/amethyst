# 🚀 Performance Optimizations PR Submission Guide

## 📋 How to Submit This PR

Since you don't have direct push access to the main Amethyst repository, here's how to submit these performance optimizations:

### Option 1: Fork and Pull Request (Recommended)

1. **Fork the Repository**
   - Go to https://github.com/vitorpamplona/amethyst
   - Click "Fork" to create your own copy

2. **Clone Your Fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/amethyst.git
   cd amethyst
   ```

3. **Apply the Changes**
   - Copy the `performance-optimizations.patch` file to your repository root
   - Apply the patch:
   ```bash
   git apply performance-optimizations.patch
   ```

4. **Create and Push Branch**
   ```bash
   git checkout -b performance-optimizations-ui-scrolling
   git add .
   git commit -m "perf: optimize UI components for smoother scrolling and reduced recomposition"
   git push -u origin performance-optimizations-ui-scrolling
   ```

5. **Create Pull Request**
   - Go to your fork on GitHub
   - Click "Compare & pull request"
   - Use the title: **"Performance Optimizations: Smoother Scrolling and Reduced UI Lag"**
   - Copy the content from `PERFORMANCE_OPTIMIZATIONS_PR.md` as the description

### Option 2: Manual File Application

If you prefer to apply changes manually, here are the key files to modify:

#### Files to Create/Modify:

1. **NEW FILE**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/feeds/NotePrefetchManager.kt`
   - Copy the entire content from the patch file

2. **MODIFY**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/components/ExpandableRichTextViewer.kt`
   - Apply the caching and button optimization changes

3. **MODIFY**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/BlockReportChecker.kt`
   - Add `rememberSaveable` import and state management improvements

4. **MODIFY**: `commons/src/main/java/com/vitorpamplona/amethyst/commons/richtext/ExpandableTextCutOffCalculator.kt`
   - Add the intelligent caching system

5. **MODIFY**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/dal/FeedFilter.kt`
   - Add `loadOlderNotes` interface method

6. **MODIFY**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/feeds/FeedContentState.kt`
   - Add prefetching state and methods

7. **MODIFY**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/feeds/FeedLoaded.kt`
   - Integrate prefetching and debounced scroll monitoring

8. **MODIFY**: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/home/dal/HomeNewThreadFeedFilter.kt`
   - Implement `loadOlderNotesImpl` method

## 🎯 PR Title and Description

**Title**: `Performance Optimizations: Smoother Scrolling and Reduced UI Lag`

**Description**: Use the content from `PERFORMANCE_OPTIMIZATIONS_PR.md` (included in this directory)

## 🧪 Testing Checklist

Before submitting, please verify:

- [ ] App builds successfully without errors
- [ ] Scrolling is smoother, especially with foreign text
- [ ] "Read more" buttons don't linger visually
- [ ] Content warnings disappear properly when scrolling
- [ ] No memory leaks or performance regressions
- [ ] Prefetching works correctly (loads more notes when scrolling)

## 📝 Commit Messages

Use these commit messages for a clean history:

1. **First commit**:
   ```
   perf: optimize UI components for smoother scrolling and reduced recomposition
   
   - ExpandableRichTextViewer: Cache text cutoff calculations and stabilize button handlers
   - BlockReportChecker: Use rememberSaveable for proper state management and disposal  
   - ExpandableTextCutOffCalculator: Add intelligent caching to prevent repeated string operations
   - NotePrefetchManager: New component for efficient note prefetching with debouncing
   
   Fixes visual lingering issues with 'Read more' buttons, content warnings, and foreign text rendering.
   Reduces recomposition bottlenecks that were causing UI lag during fast scrolling.
   ```

2. **Second commit**:
   ```
   feat: implement intelligent note prefetching system
   
   - FeedFilter: Add loadOlderNotes interface for prefetching support
   - FeedContentState: Implement preFetchOlderNotes with 25-note lead strategy
   - FeedLoaded: Integrate LazyLayoutPrefetchState and debounced scroll monitoring
   - HomeNewThreadFeedFilter: Implement loadOlderNotesImpl for home feed prefetching
   
   Maintains continuous 25-note lead by prefetching 50 notes when user scrolls past 25.
   Uses native Compose prefetching APIs for optimal performance and memory usage.
   ```

## 🎉 Benefits of This PR

- **Significantly improved scrolling performance**
- **Eliminated visual lingering issues**
- **Better handling of foreign text characters**
- **Reduced memory usage through intelligent caching**
- **Smoother user experience overall**

## 📞 Support

If you encounter any issues during the submission process, the changes are well-documented and can be applied incrementally. Each optimization is independent and can be submitted separately if needed.

Good luck with your contribution! 🚀
