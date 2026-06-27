# Virtual Override fix — investigation notes

Status: **fixed & verified in production** (Supplementaries). Changes committed on branch
`master-test` (`e48697b`, `455c52d`). 12/12 tests pass.

## Symptom (what was broken)

After the two "sus" commits (`8a4a0da`, `c757e84`) on top of the known-good baseline `0229ea5`:

- Real `@VirtualOverride("neoforge")` methods showed **red** ("does not override any method from
  platform 'neoforge'").
- Gutter icons appeared on **every other** (regular) method, pointing to NeoForge impls.
- Other inspections worked fine. Worked correctly at baseline `0229ea5`.

Later, after the first round of fixes, a cosmetic issue remained: the gutter showed the correct
**1** icon but reserved horizontal space for **3** (icons flashed at startup then settled to 1).

## Hard constraints

- Detection must be **automatic via supertype-hierarchy walk** — NO hardcoded list of extension
  interfaces (`IBlockExtension`, etc.). (User requirement.)
- Must work on real parent-folder projects, especially **Supplementaries**.

## Root causes (three distinct bugs)

1. **Frozen index cache.** The recent commits wrapped the per-class platform-method index
   (`VirtualOverrideUtils.getVirtualMethodIndex`) in `CachedValuesManager` (gated by
   `CandleSettings.psiCachingEnabled`, default `true`). Deps were `PsiModificationTracker` +
   `ProjectRootManager`, **neither of which ticks on the dumb→smart (indexing-complete)
   transition**. So an index computed while platform libraries were still unresolved got cached as
   a partial/empty result and stayed frozen until the file was edited. Baseline never cached this.

2. **Duplicate-FQN class resolution.** In an Architectury project the NeoForge module scope exposes
   **two** classes named `net.minecraft.world.level.block.Block`: the common/vanilla copy (no
   extensions) and the loader-patched copy (`Block implements
   net.neoforged.neoforge.common.extensions.IBlockExtension`, which declares
   `isLadder(BlockState, LevelReader, BlockPos, LivingEntity)`). The engine used
   `facade.findClass()` (**singular**) → could pick the vanilla copy → `isLadder` missing from the
   NeoForge index → annotation red. The baseline had this latent bug too; the cache just changed
   how it surfaced.
   - Verified jar facts (MC 1.21.1): NeoForge patched MC is at
     `~/.gradle/caches/neoformruntime/intermediate_results/compiledWithNeoForge_*.jar`; confirmed
     `Block implements ...IBlockExtension` and the `isLadder` default method.

3. **Startup marker flash → gutter width latch.** Three Java line-marker providers are registered
   (`CommonMethod`, `PlatformMethod`, `VirtualOverride`). While indexing, the partial index/resolver
   produced false-positive markers that briefly stacked on a line; IntelliJ latches the gutter
   icon-area width to the peak it sees and doesn't shrink it back → permanent empty space for 3.

## Fixes applied (only 3 files touched)

`src/main/kotlin/net/mehvahdjukaar/candle/util/`:

### VirtualOverrideUtils.kt
- **Removed index caching** → compute fresh each call (baseline behavior). Fixes bug #1.
- `findClass()` → **`findClasses()` (plural)**, iterate every same-FQN copy. Fixes bug #2.
  Monotonic: can only ADD platform coverage, so it never breaks anything that worked.
- Added `if (DumbService.isDumb(project)) return emptyMap()` guard. Fixes bug #3 (and avoids
  `IndexNotReadyException`).
- Kept (harmless in real multi-module projects; needed for single-module light tests & odd layouts):
  the `allScope` fallback when `findModuleForPlatform` returns null, and the
  `detectPlatformFromPackage` guard preventing cross-platform attribution under that fallback.

### Platform.kt
- Reverted `findModuleForPlatform` to baseline `.${id}.main` module-name match (was routed through
  `ModuleRoleDetector`). `.neoforge.main` correctly does NOT match `.forge.main`.
- Reverted `isIn` / removed `markerPackages` broadening → single `identifyingPackage` check.

### PlatformUtil.kt
- Added `DumbService.isDumb` guards to `commonMethods` and `platformMethodsByPlatform` **before**
  their caches (so a dumb-mode empty result is never cached/frozen). Fixes bug #3 for the other two
  gutter providers.

## What was lost / tradeoffs

- **Only real removal:** virtual-override index caching → minor extra CPU on huge classes (same as
  the working baseline). Can be reintroduced as a **dumb-safe cache** (skip while
  `DumbService.isDumb`; depend on a tracker that ticks post-indexing) if perf ever matters.
- `markerPackages` broadening and `ModuleRoleDetector`-based platform-module resolution were reverted
  for the engine only. Differs from `ModuleRoleDetector` solely for unusual module naming (e.g. a
  module literally named `fabric` with no `.main`); the `allScope` fallback covers those.
- Everything else from the recent commits is intact: settings UI, presentation/goto providers,
  project-view & tab-title decorators, usage grouping, rename processor, i18n,
  `ImplementPlatformImpl` fix, and `ModuleRoleDetector` (still used by all those features).

## Build / test environment

- Machine default Java is 25, which breaks Gradle 8.14.2's bundled Kotlin-DSL script compiler at
  config time (`IllegalArgumentException: 25.0.2`). **Run the daemon on JDK 17:**
  ```
  export JAVA_HOME=/home/matteo/.jdks/corretto-17.0.18
  ./gradlew test buildPlugin
  ```
  (JDK 17 matches the project's Kotlin jvmTarget 17; JDK 21 trips an "Inconsistent JVM Target"
  error.) Artifact: `build/distributions/candlelight-idea-plugin-<ver>.zip`.

## Key facts for resuming

- Baseline (good): `0229ea5`. Broke at: `8a4a0da` / `c757e84` / `061fda2`.
- Supplementaries layout: `settings.gradle.kts` includes only `common`, `fabric`, `neoforge`
  (no forge). IntelliJ module names `Supplementaries.<role>.main`. All overrides are
  `@VirtualOverride("neoforge")`.
- Inspection red path: `VirtualOverrideInspection` → `isValidVirtualOverrideForPlatform(plat)` →
  `getVirtualMethodIndex()[name][plat].any { matches }`.
- Gutter path: `VirtualOverrideLineMarkerProvider` → `findPlatformVirtualOverrides()` (no annotation
  required; fires whenever a method exists in some-but-not-all platforms).
- If anything regresses, the cheapest diagnostic is to log what `getVirtualMethodIndex()` resolves
  per platform for one known class (e.g. `ItemShelfBlock` / `isLadder`).
