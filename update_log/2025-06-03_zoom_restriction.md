# Simple PDF Reader - Update Log

## 2025-06-03: Zoom Restriction Feature

### Task Description
Implement zoom restrictions to ensure the PDF always fills the screen. The minimum zoom level should be determined by the width or height (whichever is smaller) to ensure the PDF fills the entire screen. Users can still zoom in, but zooming out is limited to this minimum level.

### Implementation Details
- Added PhotoView library for advanced zoom functionality
- Replaced standard ImageView with PhotoView for pinch-to-zoom support
- Implemented dynamic minimum zoom calculation based on PDF and screen dimensions
- Set initial zoom to fill screen without black bars
- Configured zoom levels: minimum (fills screen), medium (1.5x), maximum (3x)

### Files Modified/Created
- `app/build.gradle` - Added PhotoView dependency (com.github.chrisbanes:PhotoView:2.3.0)
- `settings.gradle` - Added JitPack repository for PhotoView dependency
- `app/src/main/res/layout/activity_main.xml` - Replaced ImageView with PhotoView component
- `app/src/main/java/com/simplepdf/reader/MainActivity.kt` - Major changes:
  - Added PhotoView import
  - Modified showPage() function to calculate minimum zoom scale
  - Added logic to determine scale based on screen vs PDF dimensions
  - Set PhotoView zoom constraints (minimum, medium, maximum scales)
  - Implemented automatic initial zoom to fill screen

### Technical Decisions
- **PhotoView Library**: Chosen for its mature, well-tested zoom functionality and easy integration
- **Zoom Calculation**: Uses `maxOf(scaleX, scaleY)` to ensure the PDF fills the entire screen, preventing any black bars
- **Scale Levels**: 
  - Minimum: Calculated to fill screen
  - Medium: 1.5x minimum (reasonable zoom for reading)
  - Maximum: 3x minimum (prevents excessive zoom that could impact performance)
- **Post Layout**: Zoom calculation happens in `photoView.post{}` to ensure view dimensions are available

### Testing Notes
- Test with PDFs of different aspect ratios (portrait, landscape, square)
- Verify that pinch-to-zoom works smoothly
- Confirm minimum zoom prevents black bars on all sides
- Test on different screen sizes and orientations
- Verify zoom persists when navigating between pages

### Status
✅ Complete

### Next Steps
- Consider adding double-tap to zoom functionality
- Could add zoom controls (zoom in/out buttons) for accessibility
- May want to save zoom level preference per PDF
- Consider adding pan limits to prevent scrolling beyond PDF bounds when zoomed in
