# Design QA

## Comparison target

- Source visual truth: `C:\Users\Eric\AppData\Local\Temp\codex-clipboard-610f177d-a831-402f-8266-d6536879b0e5.png` (459 x 695 px).
- Implementation screenshots:
  - `D:\Workspace\streambee\app\build\outputs\screenshotTest-results\preview\debug\github\rendered\com\kelsos\mbrc\screenshots\LibraryScreenshotPreviewsKt\GenreListItemLight_748aa731_0.png` (1080 x 338 px).
  - `D:\Workspace\streambee\app\build\outputs\screenshotTest-results\preview\debug\github\rendered\com\kelsos\mbrc\screenshots\LibraryScreenshotPreviewsKt\GenreListItemDark_748aa731_0.png` (1080 x 338 px).
  - `D:\Workspace\streambee\app\build\outputs\screenshotTest-results\preview\debug\github\rendered\com\kelsos\mbrc\screenshots\LibraryScreenshotPreviewsKt\GenreCategoryListItemLight_748aa731_0.png` (1080 x 176 px).
  - `D:\Workspace\streambee\app\build\outputs\screenshotTest-results\preview\debug\github\rendered\com\kelsos\mbrc\screenshots\LibraryScreenshotPreviewsKt\GenreCategoryListItemDark_748aa731_0.png` (1080 x 176 px).
- Implementation viewport: 360 dp wide at density 3.0. Both genre and genre-category rows preserve the original single-row height and horizontal rhythm. The source is a reference image without app density metadata, so comparison used the content regions rather than pixel equality.
- Normalization: source and implementation states were opened together in visual comparison inputs; no browser chrome, device frame, or surrounding canvas was included.

## State

- The source shows a RYM-like list with rounded cards, stacked album artwork, prominent titles, supporting copy, and a trailing action area.
- The implementation captures both Android `GenreCategoryListItem` and `GenreListItem` in the original `SingleLineRow` layout. Only each row's leading icon slot is replaced with the compact stacked-cover visual; the rows' original positions, rhythm, text treatment, trailing menu, and click behavior remain unchanged.
- The previews do not provide `albumPreviews`, so they intentionally show the existing no-cover fallback. Runtime `GenresTab` and `CategoryGenresScreen` query real album records and pass them to the same stack component.
- The state difference means the comparison validates the shared row composition, typography, tokens, spacing, and control placement without claiming that an empty preview proves the artwork pixels.

## Comparison evidence

### Full view

Both genre layers retain the original single-line list row rather than adopting a new card layout. Their leading icon areas stay in the same location and are the only visual surfaces changed, matching the supplied mobile screenshot's row treatment.

### Focused region

Each row's left visual slot is implemented by `StackedAlbumCovers`: it takes up to three distinct albums, draws the later covers first, keeps the first cover front-left, and offsets back layers by the configured horizontal and vertical offsets. With two or one album, the covers are centered within the full three-layer footprint, while the container still reserves that footprint so every row's title column aligns. Both genre layers use the compact icon-sized stack so the original row geometry is preserved. With no album, the existing QueueMusic icon is centered in the same footprint as a fallback. The automated previews verify the slot size, clipping, border, and fallback treatment; live cached artwork remains a runtime-only state in this fixture.

## Required fidelity surfaces

- Fonts and typography: both genre layers retain the original `SingleLineRow` typography and truncation; track positions are enlarged while stepping down for four- and five-digit playlists.
- Spacing and layout rhythm: both genre layers preserve the original `SingleLineRow` spacing and leading slot; only the compact stack is placed inside that slot.
- Colors and visual tokens: both rows use the app's Material tokens, including the orange/amber `primary` treatment rather than copying the source's blue accent literally.
- Image quality and asset fidelity: runtime layers use the existing cached `AlbumCoverByKey` loader and real album records; no generated or handcrafted artwork was introduced. The empty state uses the existing library icon.
- Copy and content: genre names and category names come from the existing library data, with no new placeholder copy.

## Interaction findings

- List and grid content no longer receive end padding to make room for the scrollbar, so trailing controls keep their original positions.
- The scrollbar is visual-only at the overlay layer. A parent-level initial-pass observer leaves taps in the right-edge region unconsumed so the underlying row controls receive them; it takes over only after a vertical movement crosses touch slop.
- No actionable P0, P1, or P2 visual or interaction findings remain.
- [P3] The screenshot fixture does not currently seed cached album artwork, so a three-cover runtime stack is not visible in the automated image. This is a test-fixture coverage gap, not a user-facing regression; the repository query, 3/2/1 limiting logic, and runtime wiring are covered by compile and repository tests.

## Comparison history

- Initial screenshot validation found the stacked-cover treatment on the first genre layer; category and genre row baselines were refreshed and filtered validation passed.
- A visual/code pass corrected the stack draw order so the first cover remains front-left rather than being offset behind later layers.
- The latest refinement applied the same leading-icon replacement to `GenreListItem` while retaining its original single-row layout, centered shorter stacks within a three-layer footprint for consistent text alignment, enlarged track-position typography with a narrower 35dp slot and large-playlist fallbacks, removed list/grid scrollbar-gutter padding, corrected the scrollbar's maximum-index mapping, and kept gesture handling at the parent with touch-slop takeover. Both genre layers' Light/Dark screenshot tests and click-through/bottom-drag regression tests passed.

## Implementation checklist

- [x] Keep the original single-row layout for both genre layers and replace only their leading icons with compact album stacks.
- [x] Load up to three real album covers per visible genre/category row.
- [x] Reduce the stack to two or one cover when fewer albums are available.
- [x] Preserve genre click, long-press, and overflow menu behavior.
- [x] Keep list/grid content at its original horizontal position.
- [x] Let taps pass through the scrollbar region and take over only for vertical drags after touch slop.
- [x] Validate module tests, filtered screenshot tests, and release build.
- [x] Install the latest signed Release APK in place on the authorized USB device without clearing app data.

## Follow-up polish

- Add a screenshot fixture with deterministic cover files to visually exercise the 3-cover, 2-cover, and 1-cover states in future QA.

final result: passed
