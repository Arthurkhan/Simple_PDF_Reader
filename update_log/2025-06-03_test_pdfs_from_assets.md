# Simple PDF Reader - Update Log

## 2025-06-03: Add Test PDFs from Assets Feature

### Task Description
Added functionality to load test PDFs from the app's assets folder, allowing developers to bundle test PDFs directly in the APK for testing purposes without needing external storage access.

### Implementation Details
- Created an assets folder structure at `app/src/main/assets/pdfs/` for storing test PDFs
- Added a new FAB button to access test PDFs from the menu
- Created a dialog to list and select test PDFs from assets
- Modified MainActivity to support loading PDFs from both external storage and assets
- Added temporary file handling for asset-based PDFs (required for PdfRenderer API)
- Disabled favorites functionality for asset-based PDFs since they're bundled with the app

### Files Modified/Created
- `app/src/main/assets/pdfs/README.md` - Created instructions for adding test PDFs
- `app/src/main/res/layout/activity_main.xml` - Added fabTestPdfs FAB button
- `app/src/main/res/drawable/ic_test_pdf.xml` - Created icon for test PDFs button
- `app/src/main/java/com/simplepdf/reader/dialogs/TestPdfsDialog.kt` - Created dialog for selecting test PDFs
- `app/src/main/res/layout/dialog_test_pdfs.xml` - Created layout for test PDFs dialog
- `app/src/main/res/layout/item_test_pdf.xml` - Created item layout for PDF list
- `app/src/main/java/com/simplepdf/reader/MainActivity.kt` - Updated to support asset loading

### Technical Changes
1. **Asset Loading**: PDFs from assets are copied to a temporary file in cache directory because PdfRenderer requires a ParcelFileDescriptor
2. **UI Updates**: Added new FAB button in the menu hierarchy above the lock button
3. **State Management**: Added `isAssetPdf` flag to track whether current PDF is from assets
4. **Cleanup**: Added temp file cleanup in onDestroy() to remove cached asset PDFs

### Testing Notes
- Place test PDF files in `app/src/main/assets/pdfs/` directory
- Build and install the app
- Tap the FAB menu (+) button
- Select the test PDF icon (document with checkmarks)
- Choose a PDF from the list
- Verify PDF loads and displays correctly
- Verify favorites button is hidden for asset PDFs
- Check that temp files are cleaned up when app closes

### Status
✅ Complete

### Next Steps
- None required for basic functionality
- Could add preview thumbnails in the test PDFs dialog
- Could add PDF metadata display (file size, page count) in the selection dialog

### Where to Upload PDFs
Upload your test PDF files to: **`app/src/main/assets/pdfs/`**

The PDFs placed in this folder will be bundled with the APK and available in the "Test PDFs" menu option.
