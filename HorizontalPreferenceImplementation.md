# Horizontal Slots Preference Implementation

## Overview
This implementation creates a custom preference for the AerialViews Android app that displays preference categories side by side horizontally instead of the default vertical layout.

## Files Created/Modified

### 1. Custom Preference Class
**File:** `app/src/main/java/com/neilturner/aerialviews/ui/preferences/HorizontalSlotsPreference.kt`
- Extends Android's `Preference` class
- Handles custom horizontal layout binding
- Implements click listeners for each slot
- Manages AlertDialog selection for overlay types
- Updates summaries with current preference values
- Handles duplicate overlay removal logic

### 2. Custom Layout Files
**File:** `app/src/main/res/layout/preference_horizontal_slots.xml`
- Portrait orientation layout with horizontal LinearLayout
- Contains left and right columns for displaying preferences side by side
- Includes divider between columns
- Proper styling and click backgrounds

**File:** `app/src/main/res/layout-land/preference_horizontal_slots.xml`
- Landscape-specific layout variant for better tablet support
- Optimized spacing and layout for landscape mode

### 3. Updated Preference XML
**File:** `app/src/main/res/xml/settings_overlays_row_top.xml`
- Replaced separate PreferenceCategory elements with single custom preference
- Uses the new `HorizontalSlotsPreference` class

### 4. Updated Fragment Class
**File:** `app/src/main/java/com/neilturner/aerialviews/ui/settings/OverlaysRowTopFragment.kt`
- Simplified to work with the new custom preference
- Removed manual preference list management
- Custom preference handles its own updates

## Key Features

### Horizontal Layout
- Left and right columns display preference categories side by side
- Visual divider between columns
- Category titles for "Top Left" and "Top Right"
- Two slots per column (Upper Slot and Lower Slot)

### Interactive Elements
- Each slot is clickable with proper background feedback
- AlertDialog selection with current value pre-selected
- Real-time summary updates showing current overlay selection
- Duplicate overlay removal - selecting an overlay removes it from other slots

### Responsive Design
- Separate layouts for portrait and landscape orientations
- Proper padding and spacing for different screen sizes
- Consistent styling with existing app theme

### Integration
- Uses existing string resources (no new strings required)
- Integrates with existing `SlotHelper` utility class
- Works with existing `GeneralPrefs` preference management
- Maintains compatibility with existing preference system

## Usage
The custom preference automatically displays in the "Top Row" settings screen, replacing the previous vertical layout with a more intuitive horizontal side-by-side interface.

## Benefits
1. **Better Space Utilization**: Uses screen real estate more efficiently
2. **Improved UX**: Visual representation matches the actual overlay positioning (left/right)
3. **Faster Navigation**: All slots visible at once, no scrolling needed
4. **Consistent Behavior**: Maintains all existing functionality like duplicate removal
5. **Responsive**: Adapts to different screen sizes and orientations
